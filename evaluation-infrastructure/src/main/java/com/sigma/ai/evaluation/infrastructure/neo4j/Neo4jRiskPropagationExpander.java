package com.sigma.ai.evaluation.infrastructure.neo4j;

import com.sigma.ai.evaluation.domain.riskpropagation.RiskPropagationDepthPolicy;
import com.sigma.ai.evaluation.domain.riskpropagation.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 风险传播有界 Cypher 查询，与 {@link Neo4jSubgraphExpander} 解耦，独立使用最大 30 跳策略。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Neo4jRiskPropagationExpander {

    private static final int DEFAULT_MAX_NODES = 500;
    private static final int DEFAULT_MAX_EDGES = 2000;
    private static final int PATHS_PER_CATEGORY = 80;

    private final Driver driver;

    /**
     * 执行风险传播查询并组装 {@link RiskPropagationResult}。
     */
    public RiskPropagationResult expand(RiskPropagationQuery query) {
        String repoId = query.getRepoId();
        int k = query.getPropagationMaxDepth() == null
                ? RiskPropagationDepthPolicy.MAX_PROPAGATION_DEPTH
                : Math.min(RiskPropagationDepthPolicy.MAX_PROPAGATION_DEPTH,
                Math.max(1, query.getPropagationMaxDepth()));
        int maxNodes = query.getMaxNodes() != null && query.getMaxNodes() > 0
                ? query.getMaxNodes() : DEFAULT_MAX_NODES;
        int maxEdges = query.getMaxEdges() != null && query.getMaxEdges() > 0
                ? query.getMaxEdges() : DEFAULT_MAX_EDGES;

        List<String> warnings = new ArrayList<>();
        List<RiskSeedPropagationResult> results = new ArrayList<>();
        ChainBudget budget = new ChainBudget(maxNodes, maxEdges);

        List<CodeRiskNodeRef> seeds = query.getRiskSeeds() == null ? List.of() : query.getRiskSeeds();
        for (CodeRiskNodeRef seed : seeds) {
            RiskSeedPropagationResult one = expandOneSeed(repoId, seed, k, budget, warnings);
            results.add(one);
        }

        if (budget.truncated) {
            warnings.add("已达到 maxNodes/max_edges 预算，部分传播链未返回");
        }

        RiskPropagationTruncation trunc = warnings.isEmpty()
                ? null
                : RiskPropagationTruncation.builder().warnings(warnings).build();

        return RiskPropagationResult.builder()
                .repoId(repoId)
                .effectiveDepth(k)
                .results(results)
                .truncation(trunc)
                .build();
    }

    private RiskSeedPropagationResult expandOneSeed(String repoId, CodeRiskNodeRef seed, int k,
                                                    ChainBudget budget, List<String> warnings) {
        CodeRiskNodeRef seedNorm = normalizeSeed(seed);
        RiskSeedPropagationResult.RiskSeedPropagationResultBuilder rb = RiskSeedPropagationResult.builder()
                .seed(seedNorm)
                .impactChains(new ArrayList<>());

        if (seedNorm.getQualifiedName() == null || seedNorm.getQualifiedName().isBlank()) {
            warnings.add("种子缺少 qualifiedName，已跳过");
            return rb.matchedInGraph(false).build();
        }

        String kind = seedNorm.getKind() == null ? "" : seedNorm.getKind().trim().toUpperCase(Locale.ROOT);
        boolean matched = switch (kind) {
            case "METHOD" -> matchMethodInRepo(repoId, seedNorm);
            case "TYPE" -> matchTypeInRepo(repoId, seedNorm);
            case "FIELD" -> matchFieldInRepo(repoId, seedNorm);
            default -> {
                warnings.add("未知种子 kind=" + seedNorm.getKind() + "，qualifiedName=" + seedNorm.getQualifiedName());
                yield false;
            }
        };

        if (!matched) {
            warnings.add("图中未命中种子: kind=" + kind + ", qualifiedName=" + seedNorm.getQualifiedName());
            return rb.matchedInGraph(false).build();
        }

        List<RiskImpactChain> chains = new ArrayList<>();
        switch (kind) {
            case "METHOD" -> chains.addAll(expandMethod(repoId, seedNorm, k, budget, warnings));
            case "TYPE" -> chains.addAll(expandType(repoId, seedNorm, k, budget, warnings));
            case "FIELD" -> chains.addAll(expandField(repoId, seedNorm, k, budget, warnings));
            default -> {
            }
        }

        return rb.matchedInGraph(true).impactChains(chains).build();
    }

    private static CodeRiskNodeRef normalizeSeed(CodeRiskNodeRef s) {
        return CodeRiskNodeRef.builder()
                .kind(s.getKind())
                .qualifiedName(s.getQualifiedName() == null ? null : s.getQualifiedName().trim())
                .filePath(s.getFilePath() == null || s.getFilePath().isBlank() ? null : s.getFilePath().trim())
                .build();
    }

    private boolean matchMethodInRepo(String repoId, CodeRiskNodeRef seed) {
        String cypher = """
                MATCH (m:Method {id: $qn})<-[:HAS_METHOD]-(:Type)<-[:DEFINES_TYPE]-(jf:JavaFile)
                WHERE jf.repoId = $repoId
                  AND ($fp IS NULL OR m.filePath = $fp OR jf.path = $fp)
                RETURN count(m) > 0 AS hit
                """;
        return runBool(cypher, repoId, seed.getQualifiedName(), seed.getFilePath());
    }

    private boolean matchTypeInRepo(String repoId, CodeRiskNodeRef seed) {
        String cypher = """
                MATCH (t:Type {qualifiedName: $qn})<-[:DEFINES_TYPE]-(jf:JavaFile)
                WHERE jf.repoId = $repoId
                  AND ($fp IS NULL OR t.filePath = $fp OR jf.path = $fp)
                RETURN count(t) > 0 AS hit
                """;
        return runBool(cypher, repoId, seed.getQualifiedName(), seed.getFilePath());
    }

    private boolean matchFieldInRepo(String repoId, CodeRiskNodeRef seed) {
        String cypher = """
                MATCH (f:Field {id: $qn})<-[:HAS_FIELD]-(:Type)<-[:DEFINES_TYPE]-(jf:JavaFile)
                WHERE jf.repoId = $repoId
                  AND ($fp IS NULL OR f.filePath = $fp OR jf.path = $fp)
                RETURN count(f) > 0 AS hit
                """;
        return runBool(cypher, repoId, seed.getQualifiedName(), seed.getFilePath());
    }

    private boolean runBool(String cypher, String repoId, String qn, String fp) {
        try (Session session = driver.session()) {
            var result = session.run(cypher, Values.parameters("repoId", repoId, "qn", qn, "fp", fp));
            if (result.hasNext()) {
                return result.next().get("hit").asBoolean();
            }
        } catch (Exception e) {
            log.warn("Neo4j 种子存在性查询失败: {}", e.getMessage());
        }
        return false;
    }

    private List<RiskImpactChain> expandMethod(String repoId, CodeRiskNodeRef seed, int k,
                                               ChainBudget budget, List<String> warnings) {
        List<RiskImpactChain> out = new ArrayList<>();
        String sid = seed.getQualifiedName();
        String fp = seed.getFilePath();

        String up = """
                MATCH (seed:Method {id: $sid})<-[:HAS_METHOD]-(:Type)<-[:DEFINES_TYPE]-(jf:JavaFile)
                WHERE jf.repoId = $repoId AND ($fp IS NULL OR seed.filePath = $fp OR jf.path = $fp)
                MATCH p = (caller:Method)-[:CALLS*1..%d]->(seed)
                WITH nodes(p) AS ns, relationships(p) AS rs, length(p) AS hop
                RETURN [n IN ns | n.id] AS mids, [n IN ns | coalesce(n.filePath, '')] AS fps,
                       [r IN rs | type(r)] AS rts, hop
                ORDER BY hop ASC
                LIMIT $lim
                """.formatted(k);

        String down = """
                MATCH (seed:Method {id: $sid})<-[:HAS_METHOD]-(:Type)<-[:DEFINES_TYPE]-(jf:JavaFile)
                WHERE jf.repoId = $repoId AND ($fp IS NULL OR seed.filePath = $fp OR jf.path = $fp)
                MATCH p = (seed)-[:CALLS*1..%d]->(callee:Method)
                WITH nodes(p) AS ns, relationships(p) AS rs, length(p) AS hop
                RETURN [n IN ns | n.id] AS mids, [n IN ns | coalesce(n.filePath, '')] AS fps,
                       [r IN rs | type(r)] AS rts, hop
                ORDER BY hop ASC
                LIMIT $lim
                """.formatted(k);

        addPathChains(out, budget, up, repoId, sid, fp, "CALLS_UPSTREAM", warnings);
        addPathChains(out, budget, down, repoId, sid, fp, "CALLS_DOWNSTREAM", warnings);

        // Method → Field → Method（两跳）
        String m2m = """
                MATCH (seed:Method {id: $sid})<-[:HAS_METHOD]-(:Type)<-[:DEFINES_TYPE]-(jf:JavaFile)
                WHERE jf.repoId = $repoId AND ($fp IS NULL OR seed.filePath = $fp OR jf.path = $fp)
                MATCH (seed)-[r1:READS_FIELD|WRITES_FIELD]->(f:Field)<-[r2:READS_FIELD|WRITES_FIELD]-(other:Method)
                WHERE other <> seed
                RETURN f.id AS fid, coalesce(f.filePath,'') AS ffp,
                       other.id AS oid, coalesce(other.filePath,'') AS ofp,
                       type(r1) AS t1, type(r2) AS t2
                LIMIT 200
                """;
        runM2mFieldChains(out, budget, m2m, repoId, sid, fp, warnings);

        // Method → Field 一跳
        String m1f = """
                MATCH (seed:Method {id: $sid})<-[:HAS_METHOD]-(:Type)<-[:DEFINES_TYPE]-(jf:JavaFile)
                WHERE jf.repoId = $repoId AND ($fp IS NULL OR seed.filePath = $fp OR jf.path = $fp)
                MATCH (seed)-[r:READS_FIELD|WRITES_FIELD]->(f:Field)
                RETURN f.id AS fid, coalesce(f.filePath,'') AS ffp, type(r) AS rt
                LIMIT 200
                """;
        runSimpleMF(out, budget, m1f, repoId, sid, fp, warnings);

        // 聚合到声明类型
        String ownType = """
                MATCH (seed:Method {id: $sid})<-[:HAS_METHOD]-(t:Type)<-[:DEFINES_TYPE]-(jf:JavaFile)
                WHERE jf.repoId = $repoId AND ($fp IS NULL OR seed.filePath = $fp OR jf.path = $fp)
                RETURN t.qualifiedName AS tqn, coalesce(t.filePath,'') AS tfp
                LIMIT 1
                """;
        runMethodOwnerType(out, budget, ownType, repoId, sid, fp, warnings);

        return out;
    }

    private void runMethodOwnerType(List<RiskImpactChain> out, ChainBudget budget, String cypher,
                                    String repoId, String sid, String fp, List<String> warnings) {
        try (Session session = driver.session()) {
            var result = session.run(cypher, Values.parameters("repoId", repoId, "sid", sid, "fp", fp));
            if (!result.hasNext()) {
                return;
            }
            var rec = result.next();
            String tqn = rec.get("tqn").asString(null);
            if (tqn == null || tqn.isBlank()) {
                return;
            }
            String tfp = rec.get("tfp").asString("");
            List<CodeRiskNodeRef> nodes = List.of(
                    methodRef(sid, fp == null ? null : fp),
                    typeRef(tqn, tfp.isBlank() ? null : tfp)
            );
            RiskImpactChain chain = RiskImpactChain.builder()
                    .chainKind("TYPE_MEMBERS")
                    .hopCount(1)
                    .nodes(new ArrayList<>(nodes))
                    .edgeTypes(List.of("HAS_METHOD"))
                    .build();
            tryAdd(out, budget, chain, warnings);
        } catch (Exception e) {
            log.warn("Neo4j Method→Type 聚合查询失败: {}", e.getMessage());
        }
    }

    private void runSimpleMF(List<RiskImpactChain> out, ChainBudget budget, String cypher,
                             String repoId, String sid, String fp, List<String> warnings) {
        try (Session session = driver.session()) {
            var result = session.run(cypher, Values.parameters("repoId", repoId, "sid", sid, "fp", fp));
            while (result.hasNext()) {
                var rec = result.next();
                String fid = rec.get("fid").asString();
                String ffp = rec.get("ffp").asString("");
                String rt = rec.get("rt").asString();
                List<CodeRiskNodeRef> nodes = List.of(
                        methodRef(sid, fp),
                        fieldRef(fid, ffp.isBlank() ? null : ffp)
                );
                RiskImpactChain chain = RiskImpactChain.builder()
                        .chainKind("FIELD_ACCESS")
                        .hopCount(1)
                        .nodes(new ArrayList<>(nodes))
                        .edgeTypes(List.of(rt))
                        .build();
                tryAdd(out, budget, chain, warnings);
            }
        } catch (Exception e) {
            log.warn("Neo4j Method→Field 查询失败: {}", e.getMessage());
        }
    }

    private void runM2mFieldChains(List<RiskImpactChain> out, ChainBudget budget, String cypher,
                                   String repoId, String sid, String fp, List<String> warnings) {
        try (Session session = driver.session()) {
            var result = session.run(cypher, Values.parameters("repoId", repoId, "sid", sid, "fp", fp));
            while (result.hasNext()) {
                var rec = result.next();
                String fid = rec.get("fid").asString();
                String ffp = rec.get("ffp").asString("");
                String oid = rec.get("oid").asString();
                String ofp = rec.get("ofp").asString("");
                String t1 = rec.get("t1").asString();
                String t2 = rec.get("t2").asString();
                List<CodeRiskNodeRef> nodes = List.of(
                        methodRef(sid, fp),
                        fieldRef(fid, ffp.isBlank() ? null : ffp),
                        methodRef(oid, ofp.isBlank() ? null : ofp)
                );
                RiskImpactChain chain = RiskImpactChain.builder()
                        .chainKind("FIELD_ACCESS")
                        .hopCount(2)
                        .nodes(new ArrayList<>(nodes))
                        .edgeTypes(List.of(t1, t2))
                        .build();
                tryAdd(out, budget, chain, warnings);
            }
        } catch (Exception e) {
            log.warn("Neo4j Method→Field→Method 查询失败: {}", e.getMessage());
        }
    }

    private List<RiskImpactChain> expandType(String repoId, CodeRiskNodeRef seed, int k,
                                             ChainBudget budget, List<String> warnings) {
        List<RiskImpactChain> out = new ArrayList<>();
        String tqn = seed.getQualifiedName();
        String fp = seed.getFilePath();

        String methods = """
                MATCH (t:Type {qualifiedName: $qn})<-[:DEFINES_TYPE]-(jf:JavaFile)
                WHERE jf.repoId = $repoId AND ($fp IS NULL OR t.filePath = $fp OR jf.path = $fp)
                MATCH (t)-[:HAS_METHOD]->(m:Method)
                RETURN m.id AS mid, coalesce(m.filePath,'') AS mfp
                LIMIT 200
                """;
        runTypeMembersMethod(out, budget, methods, repoId, tqn, fp, warnings);

        String fields = """
                MATCH (t:Type {qualifiedName: $qn})<-[:DEFINES_TYPE]-(jf:JavaFile)
                WHERE jf.repoId = $repoId AND ($fp IS NULL OR t.filePath = $fp OR jf.path = $fp)
                MATCH (t)-[:HAS_FIELD]->(f:Field)
                RETURN f.id AS fid, coalesce(f.filePath,'') AS ffp
                LIMIT 200
                """;
        runTypeMembersField(out, budget, fields, repoId, tqn, fp, warnings);

        String subs = """
                MATCH (t:Type {qualifiedName: $qn})<-[:DEFINES_TYPE]-(jf:JavaFile)
                WHERE jf.repoId = $repoId AND ($fp IS NULL OR t.filePath = $fp OR jf.path = $fp)
                MATCH p = (sub:Type)-[:EXTENDS|IMPLEMENTS*1..%d]->(t)
                WITH nodes(p) AS ns, relationships(p) AS rs, length(p) AS hop
                RETURN [n IN ns | n.qualifiedName] AS tqns, [n IN ns | coalesce(n.filePath,'')] AS tfps,
                       [r IN rs | type(r)] AS rts, hop
                ORDER BY hop ASC
                LIMIT $lim
                """.formatted(k);
        addTypePathChains(out, budget, subs, repoId, tqn, fp, "TYPE_HIERARCHY", warnings);

        String supers = """
                MATCH (t:Type {qualifiedName: $qn})<-[:DEFINES_TYPE]-(jf:JavaFile)
                WHERE jf.repoId = $repoId AND ($fp IS NULL OR t.filePath = $fp OR jf.path = $fp)
                MATCH p = (t)-[:EXTENDS|IMPLEMENTS*1..%d]->(sup:Type)
                WITH nodes(p) AS ns, relationships(p) AS rs, length(p) AS hop
                RETURN [n IN ns | n.qualifiedName] AS tqns, [n IN ns | coalesce(n.filePath,'')] AS tfps,
                       [r IN rs | type(r)] AS rts, hop
                ORDER BY hop ASC
                LIMIT $lim
                """.formatted(k);
        addTypePathChains(out, budget, supers, repoId, tqn, fp, "TYPE_HIERARCHY", warnings);

        String depOut = """
                MATCH (t:Type {qualifiedName: $qn})<-[:DEFINES_TYPE]-(jf:JavaFile)
                WHERE jf.repoId = $repoId AND ($fp IS NULL OR t.filePath = $fp OR jf.path = $fp)
                MATCH p = (t)-[:DEPENDS_ON*1..%d]->(d:Type)
                WITH nodes(p) AS ns, relationships(p) AS rs, length(p) AS hop
                RETURN [n IN ns | n.qualifiedName] AS tqns, [n IN ns | coalesce(n.filePath,'')] AS tfps,
                       [r IN rs | type(r)] AS rts, hop
                ORDER BY hop ASC
                LIMIT $lim
                """.formatted(k);
        addTypePathChains(out, budget, depOut, repoId, tqn, fp, "DEPENDS_ON", warnings);

        String depIn = """
                MATCH (t:Type {qualifiedName: $qn})<-[:DEFINES_TYPE]-(jf:JavaFile)
                WHERE jf.repoId = $repoId AND ($fp IS NULL OR t.filePath = $fp OR jf.path = $fp)
                MATCH p = (a:Type)-[:DEPENDS_ON*1..%d]->(t)
                WITH nodes(p) AS ns, relationships(p) AS rs, length(p) AS hop
                RETURN [n IN ns | n.qualifiedName] AS tqns, [n IN ns | coalesce(n.filePath,'')] AS tfps,
                       [r IN rs | type(r)] AS rts, hop
                ORDER BY hop ASC
                LIMIT $lim
                """.formatted(k);
        addTypePathChains(out, budget, depIn, repoId, tqn, fp, "DEPENDS_ON", warnings);

        return out;
    }

    private void runTypeMembersMethod(List<RiskImpactChain> out, ChainBudget budget, String cypher,
                                      String repoId, String tqn, String fp, List<String> warnings) {
        try (Session session = driver.session()) {
            var result = session.run(cypher, Values.parameters("repoId", repoId, "qn", tqn, "fp", fp));
            while (result.hasNext()) {
                var rec = result.next();
                String mid = rec.get("mid").asString();
                String mfp = rec.get("mfp").asString("");
                List<CodeRiskNodeRef> nodes = List.of(
                        typeRef(tqn, fp),
                        methodRef(mid, mfp.isBlank() ? null : mfp)
                );
                RiskImpactChain chain = RiskImpactChain.builder()
                        .chainKind("TYPE_MEMBERS")
                        .hopCount(1)
                        .nodes(new ArrayList<>(nodes))
                        .edgeTypes(List.of("HAS_METHOD"))
                        .build();
                tryAdd(out, budget, chain, warnings);
            }
        } catch (Exception e) {
            log.warn("Neo4j Type→Method 成员查询失败: {}", e.getMessage());
        }
    }

    private void runTypeMembersField(List<RiskImpactChain> out, ChainBudget budget, String cypher,
                                     String repoId, String tqn, String fp, List<String> warnings) {
        try (Session session = driver.session()) {
            var result = session.run(cypher, Values.parameters("repoId", repoId, "qn", tqn, "fp", fp));
            while (result.hasNext()) {
                var rec = result.next();
                String fid = rec.get("fid").asString();
                String ffp = rec.get("ffp").asString("");
                List<CodeRiskNodeRef> nodes = List.of(
                        typeRef(tqn, fp),
                        fieldRef(fid, ffp.isBlank() ? null : ffp)
                );
                RiskImpactChain chain = RiskImpactChain.builder()
                        .chainKind("TYPE_MEMBERS")
                        .hopCount(1)
                        .nodes(new ArrayList<>(nodes))
                        .edgeTypes(List.of("HAS_FIELD"))
                        .build();
                tryAdd(out, budget, chain, warnings);
            }
        } catch (Exception e) {
            log.warn("Neo4j Type→Field 成员查询失败: {}", e.getMessage());
        }
    }

    private List<RiskImpactChain> expandField(String repoId, CodeRiskNodeRef seed, @SuppressWarnings("unused") int k,
                                              ChainBudget budget, List<String> warnings) {
        List<RiskImpactChain> out = new ArrayList<>();
        String fid = seed.getQualifiedName();
        String fp = seed.getFilePath();

        String hub = """
                MATCH (f:Field {id: $fid})<-[:HAS_FIELD]-(:Type)<-[:DEFINES_TYPE]-(jf:JavaFile)
                WHERE jf.repoId = $repoId AND ($fp IS NULL OR f.filePath = $fp OR jf.path = $fp)
                MATCH (m:Method)-[r:READS_FIELD|WRITES_FIELD]->(f)
                RETURN m.id AS mid, coalesce(m.filePath,'') AS mfp, type(r) AS rt
                LIMIT 300
                """;
        try (Session session = driver.session()) {
            var result = session.run(hub, Values.parameters("repoId", repoId, "fid", fid, "fp", fp));
            while (result.hasNext()) {
                var rec = result.next();
                String mid = rec.get("mid").asString();
                String mfp = rec.get("mfp").asString("");
                String rt = rec.get("rt").asString();
                List<CodeRiskNodeRef> nodes = List.of(
                        fieldRef(fid, fp),
                        methodRef(mid, mfp.isBlank() ? null : mfp)
                );
                RiskImpactChain chain = RiskImpactChain.builder()
                        .chainKind("FIELD_ACCESS")
                        .hopCount(1)
                        .nodes(new ArrayList<>(nodes))
                        .edgeTypes(List.of(rt))
                        .build();
                tryAdd(out, budget, chain, warnings);
            }
        } catch (Exception e) {
            log.warn("Neo4j Field 读写方查询失败: {}", e.getMessage());
        }

        // Field → 所属 Type
        String owner = """
                MATCH (f:Field {id: $fid})<-[:HAS_FIELD]-(t:Type)<-[:DEFINES_TYPE]-(jf:JavaFile)
                WHERE jf.repoId = $repoId AND ($fp IS NULL OR f.filePath = $fp OR jf.path = $fp)
                RETURN t.qualifiedName AS tqn, coalesce(t.filePath,'') AS tfp
                LIMIT 1
                """;
        try (Session session = driver.session()) {
            var result = session.run(owner, Values.parameters("repoId", repoId, "fid", fid, "fp", fp));
            if (result.hasNext()) {
                var rec = result.next();
                String tqn = rec.get("tqn").asString(null);
                if (tqn != null && !tqn.isBlank()) {
                    String tfp = rec.get("tfp").asString("");
                    List<CodeRiskNodeRef> nodes = List.of(
                            fieldRef(fid, fp),
                            typeRef(tqn, tfp.isBlank() ? null : tfp)
                    );
                    RiskImpactChain chain = RiskImpactChain.builder()
                            .chainKind("TYPE_MEMBERS")
                            .hopCount(1)
                            .nodes(new ArrayList<>(nodes))
                            .edgeTypes(List.of("HAS_FIELD"))
                            .build();
                    tryAdd(out, budget, chain, warnings);
                }
            }
        } catch (Exception e) {
            log.warn("Neo4j Field→Type 查询失败: {}", e.getMessage());
        }

        return out;
    }

    private void addPathChains(List<RiskImpactChain> out, ChainBudget budget, String cypher,
                               String repoId, String sid, String fp, String chainKind, List<String> warnings) {
        try (Session session = driver.session()) {
            var result = session.run(cypher, Values.parameters("repoId", repoId, "sid", sid, "fp", fp, "lim", PATHS_PER_CATEGORY));
            while (result.hasNext()) {
                var rec = result.next();
                List<String> mids = rec.get("mids").asList(v -> v.asString());
                List<String> fps = rec.get("fps").asList(v -> v.asString(""));
                List<String> rts = rec.get("rts").asList(v -> v.asString());
                int hop = rec.get("hop").asInt();
                List<CodeRiskNodeRef> nodes = new ArrayList<>();
                for (int i = 0; i < mids.size(); i++) {
                    String mfp = i < fps.size() ? fps.get(i) : "";
                    nodes.add(methodRef(mids.get(i), mfp.isBlank() ? null : mfp));
                }
                RiskImpactChain chain = RiskImpactChain.builder()
                        .chainKind(chainKind)
                        .hopCount(hop)
                        .nodes(nodes)
                        .edgeTypes(rts)
                        .build();
                tryAdd(out, budget, chain, warnings);
            }
        } catch (Exception e) {
            log.warn("Neo4j CALLS 路径查询失败 chainKind={}: {}", chainKind, e.getMessage());
        }
    }

    private void addTypePathChains(List<RiskImpactChain> out, ChainBudget budget, String cypher,
                                   String repoId, String tqn, String fp, String chainKind, List<String> warnings) {
        try (Session session = driver.session()) {
            var result = session.run(cypher, Values.parameters("repoId", repoId, "qn", tqn, "fp", fp, "lim", PATHS_PER_CATEGORY));
            while (result.hasNext()) {
                var rec = result.next();
                List<String> tqns = rec.get("tqns").asList(v -> v.asString());
                List<String> tfps = rec.get("tfps").asList(v -> v.asString(""));
                List<String> rts = rec.get("rts").asList(v -> v.asString());
                int hop = rec.get("hop").asInt();
                List<CodeRiskNodeRef> nodes = new ArrayList<>();
                for (int i = 0; i < tqns.size(); i++) {
                    String p = i < tfps.size() ? tfps.get(i) : "";
                    nodes.add(typeRef(tqns.get(i), p.isBlank() ? null : p));
                }
                RiskImpactChain chain = RiskImpactChain.builder()
                        .chainKind(chainKind)
                        .hopCount(hop)
                        .nodes(nodes)
                        .edgeTypes(rts)
                        .build();
                tryAdd(out, budget, chain, warnings);
            }
        } catch (Exception e) {
            log.warn("Neo4j Type 路径查询失败 chainKind={}: {}", chainKind, e.getMessage());
        }
    }

    private void tryAdd(List<RiskImpactChain> out, ChainBudget budget, RiskImpactChain chain, List<String> warnings) {
        if (!budget.canAdd(chain)) {
            budget.truncated = true;
            return;
        }
        budget.add(chain);
        out.add(chain);
    }

    private static CodeRiskNodeRef methodRef(String id, String filePath) {
        return CodeRiskNodeRef.builder().kind("METHOD").qualifiedName(id).filePath(filePath).build();
    }

    private static CodeRiskNodeRef typeRef(String qn, String filePath) {
        return CodeRiskNodeRef.builder().kind("TYPE").qualifiedName(qn).filePath(filePath).build();
    }

    private static CodeRiskNodeRef fieldRef(String id, String filePath) {
        return CodeRiskNodeRef.builder().kind("FIELD").qualifiedName(id).filePath(filePath).build();
    }

    private static final class ChainBudget {
        private final int maxDistinctNodes;
        private final int maxEdges;
        private final Set<String> keys = new HashSet<>();
        private int edgeSum;
        private boolean truncated;

        private ChainBudget(int maxDistinctNodes, int maxEdges) {
            this.maxDistinctNodes = Math.max(1, maxDistinctNodes);
            this.maxEdges = Math.max(1, maxEdges);
        }

        private static String key(CodeRiskNodeRef n) {
            return n.getKind() + "|" + n.getQualifiedName();
        }

        boolean canAdd(RiskImpactChain c) {
            Set<String> trial = new HashSet<>(keys);
            for (CodeRiskNodeRef n : c.getNodes()) {
                trial.add(key(n));
            }
            if (trial.size() > maxDistinctNodes) {
                return false;
            }
            return edgeSum + c.getHopCount() <= maxEdges;
        }

        void add(RiskImpactChain c) {
            for (CodeRiskNodeRef n : c.getNodes()) {
                keys.add(key(n));
            }
            edgeSum += c.getHopCount();
        }
    }
}
