package com.sigma.ai.evaluation.infrastructure.neo4j;

import com.sigma.ai.evaluation.domain.codegraph.model.expand.*;
import com.sigma.ai.evaluation.types.GraphRelationGroup;
import com.sigma.ai.evaluation.types.RelationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于 Neo4j 的子图多跳展开，供 {@link Neo4jGraphAdapterImpl#expandSubgraph} 委托。
 *
 * <p>多种子归因：节点上维护各 {@link ExpandedNode#getMinHopsBySeed()}，主种子取全局最小 hop，
 * 若多个种子同为最小 hop，在 {@link ExpandedGraph#getTruncationWarnings()} 中写入 tie-break 说明。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Neo4jSubgraphExpander {

    private static final int HOP_HARD_CAP = 8;

    private static final String WARN_MULTI_SEED_TIE =
            "多个种子节点到部分图节点的最小 hop 相同，primarySeedId 已按字典序选取 reference";

    private final Driver driver;

    /**
     * 执行子图展开。
     */
    public ExpandedGraph expand(GraphExpandQuery query) {
        List<String> seedIds = query.getSeedMethodIds() == null ? List.of() : query.getSeedMethodIds();
        if (seedIds.isEmpty()) {
            return ExpandedGraph.builder().build();
        }

        int upH = clamp(query.getUpstreamMaxHops(), 1, HOP_HARD_CAP);
        int downH = clamp(query.getDownstreamMaxHops(), 1, HOP_HARD_CAP);
        Set<GraphRelationGroup> groups = query.getIncludeRelationGroups() == null
                ? EnumSet.noneOf(GraphRelationGroup.class)
                : query.getIncludeRelationGroups();

        boolean callChain = groups.contains(GraphRelationGroup.CALL_CHAIN);
        boolean typeHierarchy = groups.contains(GraphRelationGroup.TYPE_HIERARCHY);
        boolean fieldAccess = groups.contains(GraphRelationGroup.FIELD_ACCESS);
        boolean typeDepends = groups.contains(GraphRelationGroup.TYPE_DEPENDS);
        boolean imports = groups.contains(GraphRelationGroup.IMPORTS);
        boolean structure = groups.contains(GraphRelationGroup.STRUCTURE);

        List<String> warnings = new ArrayList<>();

        // nodeId -> seedId -> min hop（CALLS 语义）
        Map<String, Map<String, Integer>> upHop = new HashMap<>();
        Map<String, Map<String, Integer>> downHop = new HashMap<>();

        for (String sid : seedIds) {
            upHop.computeIfAbsent(sid, k -> new HashMap<>()).put(sid, 0);
            downHop.computeIfAbsent(sid, k -> new HashMap<>()).put(sid, 0);
        }

        Set<String> methodScope = new LinkedHashSet<>(seedIds);

        if (callChain) {
            mergeHopMap(upHop, queryUpstreamCallers(seedIds, upH));
            mergeHopMap(downHop, queryDownstreamCallees(seedIds, downH));
            // upHop / downHop 以外层 key 为节点 id（Method.id）
            methodScope.clear();
            methodScope.addAll(upHop.keySet());
            methodScope.addAll(downHop.keySet());
        } else {
            methodScope.addAll(seedIds);
        }

        boolean truncated = false;
        int maxNodes = Math.max(1, query.getMaxNodes());
        if (methodScope.size() > maxNodes) {
            LinkedHashSet<String> kept = prioritizeNodes(seedIds, methodScope, upHop, downHop, maxNodes);
            methodScope.clear();
            methodScope.addAll(kept);
            truncated = true;
            warnings.add("已达到 maxNodes 上限，部分方法节点已从子图中移除");
        }

        List<ExpandedNode> nodes = new ArrayList<>();
        List<ExpandedEdge> edges = new ArrayList<>();
        List<ExpandedEdge> inheritanceEdges = new ArrayList<>();
        List<FieldCouplingEntry> fieldCoupling = new ArrayList<>();

        loadMethodNodes(methodScope, seedIds, upHop, downHop, warnings, nodes);

        if (callChain) {
            loadCallEdges(methodScope, seedIds, upHop, downHop, edges);
        }

        Set<String> typeScope = new HashSet<>();
        for (ExpandedNode n : nodes) {
            Object o = n.getProperties().get("ownerQualifiedName");
            if (o instanceof String s && !s.isBlank()) {
                typeScope.add(s);
            }
        }

        if (typeHierarchy) {
            loadTypeHierarchy(typeScope, inheritanceEdges, nodes);
        }

        if (fieldAccess) {
            loadFieldAccess(methodScope, nodes, edges, fieldCoupling);
        }

        if (typeDepends) {
            loadTypeDepends(typeScope, nodes, edges);
        }

        if (imports) {
            loadImports(typeScope, nodes, edges);
        }

        if (structure) {
            loadStructure(methodScope, nodes, edges);
        }

        int maxEdges = Math.max(1, query.getMaxEdges());
        if (edges.size() > maxEdges) {
            edges = new ArrayList<>(edges.subList(0, maxEdges));
            truncated = true;
            warnings.add("已达到 maxEdges 上限，部分边已截断");
        }

        List<ExpandedPath> paths = new ArrayList<>();
        if (callChain && query.getMaxPaths() > 0) {
            samplePaths(seedIds, Math.min(query.getMaxPaths(), 80), downH, paths);
        }

        return ExpandedGraph.builder()
                .nodes(nodes)
                .edges(edges)
                .paths(paths)
                .inheritanceEdges(inheritanceEdges)
                .fieldCoupling(fieldCoupling)
                .truncated(truncated)
                .truncationWarnings(warnings)
                .build();
    }

    private void mergeHopMap(Map<String, Map<String, Integer>> acc,
                             Map<String, Map<String, Integer>> delta) {
        for (Map.Entry<String, Map<String, Integer>> e : delta.entrySet()) {
            String nid = e.getKey();
            Map<String, Integer> inner = acc.computeIfAbsent(nid, k -> new HashMap<>());
            for (Map.Entry<String, Integer> se : e.getValue().entrySet()) {
                inner.merge(se.getKey(), se.getValue(), Math::min);
            }
        }
    }

    private Map<String, Map<String, Integer>> queryUpstreamCallers(List<String> seedIds, int maxH) {
        Map<String, Map<String, Integer>> out = new HashMap<>();
        String cypher = """
                UNWIND $seedIds AS sid
                MATCH (seed:Method {id: sid})
                MATCH p = (caller:Method)-[:CALLS*1..%d]->(seed)
                RETURN caller.id AS nid, sid AS seedId, length(p) AS hop
                """.formatted(maxH);
        try (Session session = driver.session()) {
            session.executeRead(tx -> {
                var result = tx.run(cypher, Values.parameters("seedIds", seedIds));
                while (result.hasNext()) {
                    var rec = result.next();
                    String nid = rec.get("nid").asString();
                    String seedId = rec.get("seedId").asString();
                    int hop = rec.get("hop").asInt();
                    out.computeIfAbsent(nid, k -> new HashMap<>()).merge(seedId, hop, Math::min);
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("Neo4j 上游 CALLS 查询失败: {}", e.getMessage());
        }
        return out;
    }

    private Map<String, Map<String, Integer>> queryDownstreamCallees(List<String> seedIds, int maxH) {
        Map<String, Map<String, Integer>> out = new HashMap<>();
        String cypher = """
                UNWIND $seedIds AS sid
                MATCH (seed:Method {id: sid})
                MATCH p = (seed)-[:CALLS*1..%d]->(callee:Method)
                RETURN callee.id AS nid, sid AS seedId, length(p) AS hop
                """.formatted(maxH);
        try (Session session = driver.session()) {
            session.executeRead(tx -> {
                var result = tx.run(cypher, Values.parameters("seedIds", seedIds));
                while (result.hasNext()) {
                    var rec = result.next();
                    String nid = rec.get("nid").asString();
                    String seedId = rec.get("seedId").asString();
                    int hop = rec.get("hop").asInt();
                    out.computeIfAbsent(nid, k -> new HashMap<>()).merge(seedId, hop, Math::min);
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("Neo4j 下游 CALLS 查询失败: {}", e.getMessage());
        }
        return out;
    }

    private LinkedHashSet<String> prioritizeNodes(List<String> seeds, Set<String> scope,
                                                    Map<String, Map<String, Integer>> upHop,
                                                    Map<String, Map<String, Integer>> downHop,
                                                    int maxNodes) {
        LinkedHashSet<String> kept = new LinkedHashSet<>(seeds);
        List<String> rest = scope.stream().filter(n -> !kept.contains(n))
                .sorted(Comparator.comparingInt(n -> minTotalHop(n, upHop, downHop)))
                .toList();
        for (String n : rest) {
            if (kept.size() >= maxNodes) {
                break;
            }
            kept.add(n);
        }
        return kept;
    }

    private int minTotalHop(String nodeId, Map<String, Map<String, Integer>> upHop,
                            Map<String, Map<String, Integer>> downHop) {
        Map<String, Integer> u = upHop.getOrDefault(nodeId, Map.of());
        Map<String, Integer> d = downHop.getOrDefault(nodeId, Map.of());
        int best = Integer.MAX_VALUE;
        for (String seed : new HashSet<>(u.keySet())) {
            int a = u.getOrDefault(seed, Integer.MAX_VALUE);
            int b = d.getOrDefault(seed, Integer.MAX_VALUE);
            best = Math.min(best, Math.min(a, b));
        }
        return best == Integer.MAX_VALUE ? 9999 : best;
    }

    private void loadMethodNodes(Set<String> methodIds, List<String> seedOrderIgnored,
                                 Map<String, Map<String, Integer>> upHop,
                                 Map<String, Map<String, Integer>> downHop,
                                 List<String> warnings,
                                 List<ExpandedNode> outNodes) {
        String cypher = """
                UNWIND $ids AS nid
                MATCH (m:Method {id: nid})
                RETURN m.id AS id, m.signature AS signature, m.ownerQualifiedName AS ownerQualifiedName,
                       m.filePath AS filePath, m.lineStart AS lineStart, m.lineEnd AS lineEnd,
                       m.simpleName AS simpleName
                """;
        Map<String, ExpandedNode> byId = new LinkedHashMap<>();
        try (Session session = driver.session()) {
            session.executeRead(tx -> {
                var result = tx.run(cypher, Values.parameters("ids", new ArrayList<>(methodIds)));
                while (result.hasNext()) {
                    var rec = result.next();
                    String id = rec.get("id").asString();
                    ExpandedNode n = new ExpandedNode();
                    n.setId(id);
                    n.setLabel("Method");
                    Map<String, Object> props = new LinkedHashMap<>();
                    putIfPresent(props, "signature", rec.get("signature").asString(null));
                    putIfPresent(props, "ownerQualifiedName", rec.get("ownerQualifiedName").asString(null));
                    putIfPresent(props, "filePath", rec.get("filePath").asString(null));
                    putIfPresent(props, "simpleName", rec.get("simpleName").asString(null));
                    if (!rec.get("lineStart").isNull()) {
                        props.put("lineStart", rec.get("lineStart").asInt());
                    }
                    if (!rec.get("lineEnd").isNull()) {
                        props.put("lineEnd", rec.get("lineEnd").asInt());
                    }
                    n.setProperties(props);
                    byId.put(id, n);
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("Neo4j 加载 Method 节点失败: {}", e.getMessage());
        }

        for (String mid : methodIds) {
            ExpandedNode n = byId.get(mid);
            if (n == null) {
                continue;
            }
            Map<String, Integer> up = upHop.getOrDefault(mid, Map.of());
            Map<String, Integer> down = downHop.getOrDefault(mid, Map.of());
            Map<String, Integer> merged = new LinkedHashMap<>();
            Set<String> allSeeds = new HashSet<>();
            allSeeds.addAll(up.keySet());
            allSeeds.addAll(down.keySet());
            for (String s : allSeeds) {
                int hu = up.getOrDefault(s, Integer.MAX_VALUE);
                int hd = down.getOrDefault(s, Integer.MAX_VALUE);
                merged.put(s, Math.min(hu, hd));
            }
            n.setMinHopsBySeed(merged);
            List<String> reachableSeeds = merged.entrySet().stream()
                    .filter(e -> e.getValue() < Integer.MAX_VALUE)
                    .sorted(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toCollection(ArrayList::new));
            n.setSeedIds(reachableSeeds);

            if (reachableSeeds.isEmpty()) {
                n.setPrimarySeedId(null);
            } else {
                int minHop = merged.get(reachableSeeds.get(0));
                List<String> tied = new ArrayList<>();
                for (String s : reachableSeeds) {
                    if (Objects.equals(merged.get(s), minHop)) {
                        tied.add(s);
                    }
                }
                String primary;
                if (tied.size() == 1) {
                    primary = tied.get(0);
                } else {
                    Collections.sort(tied);
                    primary = tied.get(0);
                    if (!warnings.contains(WARN_MULTI_SEED_TIE)) {
                        warnings.add(WARN_MULTI_SEED_TIE);
                    }
                }
                n.setPrimarySeedId(primary);
            }

            String primary = n.getPrimarySeedId();
            String dir = primary == null ? "lateral" : resolveDirection(up, down, primary);
            n.getProperties().put("directionToPrimarySeed", dir);
            outNodes.add(n);
        }
    }

    private static void putIfPresent(Map<String, Object> props, String key, String v) {
        if (v != null && !v.isBlank()) {
            props.put(key, v);
        }
    }

    private String resolveDirection(Map<String, Integer> up, Map<String, Integer> down, String primary) {
        int hu = up.getOrDefault(primary, Integer.MAX_VALUE);
        int hd = down.getOrDefault(primary, Integer.MAX_VALUE);
        boolean hasUp = hu < Integer.MAX_VALUE && hu > 0;
        boolean hasDown = hd < Integer.MAX_VALUE && hd > 0;
        if (hu == 0 && hd == 0) {
            return "seed";
        }
        if (hasUp && hasDown) {
            return "lateral";
        }
        if (hasUp) {
            return "upstream";
        }
        if (hasDown) {
            return "downstream";
        }
        return "lateral";
    }

    private void loadCallEdges(Set<String> methodIds, List<String> seeds,
                               Map<String, Map<String, Integer>> upHop,
                               Map<String, Map<String, Integer>> downHop,
                               List<ExpandedEdge> edges) {
        String cypher = """
                UNWIND $ids AS id
                MATCH (a:Method {id: id})-[r:CALLS]->(b:Method)
                WHERE b.id IN $ids
                RETURN a.id AS fromId, b.id AS toId, r.lineNo AS lineNo
                """;
        try (Session session = driver.session()) {
            session.executeRead(tx -> {
                var result = tx.run(cypher, Values.parameters("ids", new ArrayList<>(methodIds)));
                while (result.hasNext()) {
                    var rec = result.next();
                    String from = rec.get("fromId").asString();
                    String to = rec.get("toId").asString();
                    ExpandedEdge e = new ExpandedEdge();
                    e.setFromId(from);
                    e.setToId(to);
                    e.setRelationType(RelationType.CALLS.name());
                    Map<String, Object> p = new LinkedHashMap<>();
                    if (!rec.get("lineNo").isNull()) {
                        p.put("lineNo", rec.get("lineNo").asInt());
                    }
                    e.setProperties(p);

                    String ref = resolveEdgeReferenceSeed(from, to, seeds, upHop, downHop);
                    e.setReferenceSeedId(ref);
                    int hopTo = downHop.getOrDefault(to, Map.of()).getOrDefault(ref, Integer.MAX_VALUE);
                    int hopFrom = downHop.getOrDefault(from, Map.of()).getOrDefault(ref, Integer.MAX_VALUE);
                    e.setHopFromSeed(Math.min(hopTo, hopFrom == Integer.MAX_VALUE ? hopTo : hopFrom + 1));
                    e.setDirectionToSeed(downHop.getOrDefault(to, Map.of()).getOrDefault(ref, Integer.MAX_VALUE)
                            <= downHop.getOrDefault(from, Map.of()).getOrDefault(ref, Integer.MAX_VALUE)
                            ? "downstream" : "upstream");
                    e.setSeedIds(List.copyOf(seeds));
                    edges.add(e);
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("Neo4j 加载 CALLS 边失败: {}", e.getMessage());
        }
    }

    private String resolveEdgeReferenceSeed(String from, String to, List<String> seeds,
                                            Map<String, Map<String, Integer>> upHop,
                                            Map<String, Map<String, Integer>> downHop) {
        String best = seeds.get(0);
        int bestScore = Integer.MAX_VALUE;
        for (String s : seeds) {
            int df = downHop.getOrDefault(from, Map.of()).getOrDefault(s, Integer.MAX_VALUE);
            int dt = downHop.getOrDefault(to, Map.of()).getOrDefault(s, Integer.MAX_VALUE);
            int uf = upHop.getOrDefault(from, Map.of()).getOrDefault(s, Integer.MAX_VALUE);
            int ut = upHop.getOrDefault(to, Map.of()).getOrDefault(s, Integer.MAX_VALUE);
            int score = Math.min(Math.min(df, dt), Math.min(uf, ut));
            if (score < bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    private void loadTypeHierarchy(Set<String> typeScope, List<ExpandedEdge> inheritanceEdges,
                                   List<ExpandedNode> nodes) {
        if (typeScope.isEmpty()) {
            return;
        }
        String cypher = """
                UNWIND $qns AS qn
                MATCH (a:Type)-[r:EXTENDS|IMPLEMENTS]->(b:Type)
                WHERE a.qualifiedName = qn OR b.qualifiedName = qn
                RETURN a.qualifiedName AS fromId, b.qualifiedName AS toId, type(r) AS relType
                """;
        Set<String> existing = nodes.stream().map(ExpandedNode::getId).collect(Collectors.toSet());
        try (Session session = driver.session()) {
            session.executeRead(tx -> {
                var result = tx.run(cypher, Values.parameters("qns", new ArrayList<>(typeScope)));
                while (result.hasNext()) {
                    var rec = result.next();
                    ExpandedEdge e = new ExpandedEdge();
                    e.setFromId(rec.get("fromId").asString());
                    e.setToId(rec.get("toId").asString());
                    e.setRelationType(rec.get("relType").asString());
                    e.setHopFromSeed(0);
                    e.setDirectionToSeed("lateral");
                    inheritanceEdges.add(e);
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("Neo4j 类型层次查询失败: {}", e.getMessage());
        }
        // 补充 Type 节点（简版）
        Set<String> typeIds = new HashSet<>();
        for (ExpandedEdge e : inheritanceEdges) {
            typeIds.add(e.getFromId());
            typeIds.add(e.getToId());
        }
        for (String tid : typeIds) {
            if (existing.contains(tid)) {
                continue;
            }
            ExpandedNode tn = fetchTypeNode(tid);
            if (tn != null) {
                nodes.add(tn);
                existing.add(tid);
            }
        }
    }

    private ExpandedNode fetchTypeNode(String qualifiedName) {
        String cypher = """
                MATCH (t:Type {qualifiedName: $qn})
                RETURN t.qualifiedName AS qn, t.simpleName AS simpleName, t.filePath AS filePath,
                       t.lineStart AS lineStart, t.lineEnd AS lineEnd
                """;
        try (Session session = driver.session()) {
            var result = session.run(cypher, Values.parameters("qn", qualifiedName));
            if (!result.hasNext()) {
                return null;
            }
            var rec = result.next();
            ExpandedNode n = new ExpandedNode();
            n.setId(rec.get("qn").asString());
            n.setLabel("Type");
            Map<String, Object> props = new LinkedHashMap<>();
            putIfPresent(props, "qualifiedName", rec.get("qn").asString());
            putIfPresent(props, "simpleName", rec.get("simpleName").asString(null));
            putIfPresent(props, "filePath", rec.get("filePath").asString(null));
            if (!rec.get("lineStart").isNull()) {
                props.put("lineStart", rec.get("lineStart").asInt());
            }
            if (!rec.get("lineEnd").isNull()) {
                props.put("lineEnd", rec.get("lineEnd").asInt());
            }
            n.setProperties(props);
            n.setSeedIds(List.of());
            n.setPrimarySeedId(null);
            return n;
        } catch (Exception e) {
            return null;
        }
    }

    private void loadFieldAccess(Set<String> methodIds, List<ExpandedNode> nodes,
                                 List<ExpandedEdge> edges, List<FieldCouplingEntry> coupling) {
        String cypher = """
                UNWIND $ids AS mid
                MATCH (m:Method {id: mid})-[r:READS_FIELD|WRITES_FIELD]->(f:Field)
                RETURN m.id AS mid, f.id AS fid, type(r) AS rt,
                       f.ownerQualifiedName AS owner, f.simpleName AS fname
                """;
        Map<String, FieldCouplingEntry> byField = new LinkedHashMap<>();
        Set<String> existing = nodes.stream().map(ExpandedNode::getId).collect(Collectors.toSet());
        try (Session session = driver.session()) {
            session.executeRead(tx -> {
                var result = tx.run(cypher, Values.parameters("ids", new ArrayList<>(methodIds)));
                while (result.hasNext()) {
                    var rec = result.next();
                    String mid = rec.get("mid").asString();
                    String fid = rec.get("fid").asString();
                    String rt = rec.get("rt").asString();
                    ExpandedEdge e = new ExpandedEdge();
                    e.setFromId(mid);
                    e.setToId(fid);
                    e.setRelationType(rt);
                    e.setHopFromSeed(0);
                    e.setDirectionToSeed("lateral");
                    edges.add(e);

                    FieldCouplingEntry fe = byField.computeIfAbsent(fid, k -> FieldCouplingEntry.builder()
                            .fieldId(fid)
                            .ownerTypeQualifiedName(rec.get("owner").asString(null))
                            .fieldSimpleName(rec.get("fname").asString(null))
                            .build());
                    if ("READS_FIELD".equals(rt)) {
                        fe.getReaderMethodIds().add(mid);
                    } else if ("WRITES_FIELD".equals(rt)) {
                        fe.getWriterMethodIds().add(mid);
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("Neo4j 字段访问边查询失败: {}", e.getMessage());
        }
        coupling.addAll(byField.values());

        for (String fid : byField.keySet()) {
            if (existing.contains(fid)) {
                continue;
            }
            ExpandedNode fn = fetchFieldNode(fid);
            if (fn != null) {
                nodes.add(fn);
                existing.add(fid);
            }
        }
    }

    private ExpandedNode fetchFieldNode(String fieldId) {
        String cypher = """
                MATCH (f:Field {id: $id})
                RETURN f.id AS id, f.ownerQualifiedName AS owner, f.simpleName AS simpleName,
                       f.filePath AS filePath, f.lineNo AS lineNo
                """;
        try (Session session = driver.session()) {
            var result = session.run(cypher, Values.parameters("id", fieldId));
            if (!result.hasNext()) {
                return null;
            }
            var rec = result.next();
            ExpandedNode n = new ExpandedNode();
            n.setId(rec.get("id").asString());
            n.setLabel("Field");
            Map<String, Object> props = new LinkedHashMap<>();
            putIfPresent(props, "ownerQualifiedName", rec.get("owner").asString(null));
            putIfPresent(props, "simpleName", rec.get("simpleName").asString(null));
            putIfPresent(props, "filePath", rec.get("filePath").asString(null));
            if (!rec.get("lineNo").isNull()) {
                props.put("lineNo", rec.get("lineNo").asInt());
            }
            n.setProperties(props);
            return n;
        } catch (Exception e) {
            return null;
        }
    }

    private void loadTypeDepends(Set<String> typeScope, List<ExpandedNode> nodes, List<ExpandedEdge> edges) {
        if (typeScope.isEmpty()) {
            return;
        }
        String cypher = """
                UNWIND $qns AS qn
                MATCH (a:Type)-[r:DEPENDS_ON]->(b:Type)
                WHERE a.qualifiedName = qn OR b.qualifiedName = qn
                RETURN a.qualifiedName AS fromId, b.qualifiedName AS toId
                """;
        Set<String> existing = nodes.stream().map(ExpandedNode::getId).collect(Collectors.toSet());
        try (Session session = driver.session()) {
            session.executeRead(tx -> {
                var result = tx.run(cypher, Values.parameters("qns", new ArrayList<>(typeScope)));
                while (result.hasNext()) {
                    var rec = result.next();
                    ExpandedEdge e = new ExpandedEdge();
                    e.setFromId(rec.get("fromId").asString());
                    e.setToId(rec.get("toId").asString());
                    e.setRelationType(RelationType.DEPENDS_ON.name());
                    e.setHopFromSeed(0);
                    e.setDirectionToSeed("lateral");
                    edges.add(e);
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("Neo4j DEPENDS_ON 查询失败: {}", e.getMessage());
        }
        for (String tid : typeScope) {
            if (existing.contains(tid)) {
                continue;
            }
            ExpandedNode tn = fetchTypeNode(tid);
            if (tn != null) {
                nodes.add(tn);
                existing.add(tid);
            }
        }
    }

    private void loadImports(Set<String> typeScope, List<ExpandedNode> nodes, List<ExpandedEdge> edges) {
        if (typeScope.isEmpty()) {
            return;
        }
        String cypher = """
                UNWIND $qns AS qn
                MATCH (t:Type {qualifiedName: qn})<-[:DEFINES_TYPE]-(jf:JavaFile)-[r:IMPORTS]->(it:Type)
                RETURN jf.path AS fromId, it.qualifiedName AS toId
                LIMIT 200
                """;
        Set<String> existing = nodes.stream().map(ExpandedNode::getId).collect(Collectors.toSet());
        try (Session session = driver.session()) {
            session.executeRead(tx -> {
                var result = tx.run(cypher, Values.parameters("qns", new ArrayList<>(typeScope)));
                while (result.hasNext()) {
                    var rec = result.next();
                    ExpandedEdge e = new ExpandedEdge();
                    e.setFromId(rec.get("fromId").asString());
                    e.setToId(rec.get("toId").asString());
                    e.setRelationType(RelationType.IMPORTS.name());
                    e.setHopFromSeed(0);
                    e.setDirectionToSeed("lateral");
                    edges.add(e);
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("Neo4j IMPORTS 查询失败: {}", e.getMessage());
        }
        // JavaFile 节点简版
        List<ExpandedEdge> importEdges = edges.stream()
                .filter(x -> RelationType.IMPORTS.name().equals(x.getRelationType()))
                .toList();
        for (ExpandedEdge e : importEdges) {
            String path = e.getFromId();
            if (existing.contains(path) || path == null) {
                continue;
            }
            ExpandedNode jf = fetchJavaFileNode(path);
            if (jf != null) {
                nodes.add(jf);
                existing.add(path);
            }
        }
    }

    private ExpandedNode fetchJavaFileNode(String path) {
        String cypher = """
                MATCH (f:JavaFile {path: $p})
                RETURN f.path AS path, f.relativePath AS relativePath
                """;
        try (Session session = driver.session()) {
            var result = session.run(cypher, Values.parameters("p", path));
            if (!result.hasNext()) {
                return null;
            }
            var rec = result.next();
            ExpandedNode n = new ExpandedNode();
            n.setId(rec.get("path").asString());
            n.setLabel("JavaFile");
            Map<String, Object> props = new LinkedHashMap<>();
            putIfPresent(props, "relativePath", rec.get("relativePath").asString(null));
            n.setProperties(props);
            return n;
        } catch (Exception e) {
            return null;
        }
    }

    private void loadStructure(Set<String> methodIds, List<ExpandedNode> nodes, List<ExpandedEdge> edges) {
        String cypher = """
                UNWIND $ids AS mid
                MATCH (m:Method {id: mid})<-[:HAS_METHOD]-(t:Type)<-[:DEFINES_TYPE]-(jf:JavaFile)
                OPTIONAL MATCH (jf)<-[:CONTAINS_FILE]-(pkg:Package)
                RETURN DISTINCT m.id AS mid, t.qualifiedName AS tqn, jf.path AS jpath, pkg.id AS pkgId
                LIMIT 500
                """;
        Set<String> existing = nodes.stream().map(ExpandedNode::getId).collect(Collectors.toSet());
        try (Session session = driver.session()) {
            session.executeRead(tx -> {
                var result = tx.run(cypher, Values.parameters("ids", new ArrayList<>(methodIds)));
                while (result.hasNext()) {
                    var rec = result.next();
                    String tqn = rec.get("tqn").asString();
                    String jpath = rec.get("jpath").asString();
                    String pkgId = rec.get("pkgId").isNull() ? null : rec.get("pkgId").asString();
                    ExpandedEdge e1 = new ExpandedEdge();
                    e1.setFromId(jpath);
                    e1.setToId(tqn);
                    e1.setRelationType(RelationType.DEFINES_TYPE.name());
                    e1.setHopFromSeed(0);
                    e1.setDirectionToSeed("lateral");
                    edges.add(e1);
                    ExpandedEdge e2 = new ExpandedEdge();
                    e2.setFromId(tqn);
                    e2.setToId(rec.get("mid").asString());
                    e2.setRelationType(RelationType.HAS_METHOD.name());
                    e2.setHopFromSeed(0);
                    e2.setDirectionToSeed("lateral");
                    edges.add(e2);
                    if (pkgId != null) {
                        ExpandedEdge e3 = new ExpandedEdge();
                        e3.setFromId(pkgId);
                        e3.setToId(jpath);
                        e3.setRelationType(RelationType.CONTAINS_FILE.name());
                        e3.setHopFromSeed(0);
                        e3.setDirectionToSeed("lateral");
                        edges.add(e3);
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("Neo4j STRUCTURE 查询失败: {}", e.getMessage());
        }
        for (ExpandedEdge e : edges) {
            if (!RelationType.CONTAINS_FILE.name().equals(e.getRelationType())) {
                continue;
            }
            String pkg = e.getFromId();
            if (pkg != null && !existing.contains(pkg)) {
                ExpandedNode pn = fetchPackageNode(pkg);
                if (pn != null) {
                    nodes.add(pn);
                    existing.add(pkg);
                }
            }
        }
    }

    private ExpandedNode fetchPackageNode(String pkgId) {
        String cypher = """
                MATCH (p:Package {id: $id})
                RETURN p.id AS id, p.qualifiedName AS qn
                """;
        try (Session session = driver.session()) {
            var result = session.run(cypher, Values.parameters("id", pkgId));
            if (!result.hasNext()) {
                return null;
            }
            var rec = result.next();
            ExpandedNode n = new ExpandedNode();
            n.setId(rec.get("id").asString());
            n.setLabel("Package");
            Map<String, Object> props = new LinkedHashMap<>();
            putIfPresent(props, "qualifiedName", rec.get("qn").asString(null));
            n.setProperties(props);
            return n;
        } catch (Exception e) {
            return null;
        }
    }

    private void samplePaths(List<String> seedIds, int maxPaths, int maxDepth, List<ExpandedPath> out) {
        String cypher = """
                UNWIND $seedIds AS sid
                MATCH (seed:Method {id: sid})
                MATCH p = (seed)-[:CALLS*1..%d]->(m:Method)
                WITH sid, p
                ORDER BY length(p)
                LIMIT $lim
                RETURN sid AS seedId, [n IN nodes(p) | n.id] AS nids, [r IN relationships(p) | type(r)] AS rts, length(p) AS len
                """.formatted(Math.max(1, Math.min(maxDepth, HOP_HARD_CAP)));
        try (Session session = driver.session()) {
            session.executeRead(tx -> {
                var result = tx.run(cypher, Values.parameters("seedIds", seedIds, "lim", maxPaths));
                while (result.hasNext()) {
                    var rec = result.next();
                    ExpandedPath path = new ExpandedPath();
                    path.setReferenceSeedId(rec.get("seedId").asString());
                    path.setNodeIds(rec.get("nids").asList(v -> v.asString()));
                    path.setRelTypes(rec.get("rts").asList(v -> v.asString()));
                    path.setLength(rec.get("len").asInt());
                    out.add(path);
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("Neo4j 路径抽样失败: {}", e.getMessage());
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
