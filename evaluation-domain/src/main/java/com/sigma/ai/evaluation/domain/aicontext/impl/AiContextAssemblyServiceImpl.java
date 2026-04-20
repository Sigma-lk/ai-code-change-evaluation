package com.sigma.ai.evaluation.domain.aicontext.impl;

import com.sigma.ai.evaluation.domain.aicontext.AiContextAssemblyInput;
import com.sigma.ai.evaluation.domain.aicontext.AiContextAssemblyOutput;
import com.sigma.ai.evaluation.domain.aicontext.AiContextAssemblyService;
import com.sigma.ai.evaluation.domain.aicontext.AiContextProperties;
import com.sigma.ai.evaluation.domain.codegraph.adapter.GraphAdapter;
import com.sigma.ai.evaluation.domain.codegraph.model.expand.*;
import com.sigma.ai.evaluation.domain.embedding.adapter.EmbeddingStoreAdapter;
import com.sigma.ai.evaluation.domain.embedding.model.EmbeddingSearchHit;
import com.sigma.ai.evaluation.domain.embedding.model.EmbeddingSearchQuery;
import com.sigma.ai.evaluation.domain.repository.adapter.GitAdapter;
import com.sigma.ai.evaluation.domain.repository.adapter.RepositoryPort;
import com.sigma.ai.evaluation.domain.repository.model.ChangedFile;
import com.sigma.ai.evaluation.domain.repository.model.RepositoryInfo;
import com.sigma.ai.evaluation.types.FileChangeType;
import com.sigma.ai.evaluation.types.GraphRelationGroup;
import com.sigma.ai.evaluation.types.exception.ParamValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 编排图展开、语义检索与种子解析，生成 AI 可用的结构化上下文。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiContextAssemblyServiceImpl implements AiContextAssemblyService {

    private static final String EVIDENCE_SCOPE = "当前证据包主要覆盖 Java 类型/方法/字段及 CALLS、继承、字段访问、类型依赖等静态图关系；"
            + "HTTP 入口、MQ 消费、定时任务等若未与 Method 在图中显式关联，本接口不承诺全链路入口覆盖。";

    private final GraphAdapter graphAdapter;
    private final EmbeddingStoreAdapter embeddingStoreAdapter;
    private final RepositoryPort repositoryPort;
    private final GitAdapter gitAdapter;
    private final AiContextProperties aiContextProperties;

    @Override
    public AiContextAssemblyOutput assemble(AiContextAssemblyInput in) {
        long t0 = System.currentTimeMillis();

        boolean wantGraph = in.getInclude() == null || Boolean.TRUE.equals(in.getInclude().getGraph());
        boolean wantSemantic = in.getInclude() == null || Boolean.TRUE.equals(in.getInclude().getSemantic());
        boolean wantSummary = in.getInclude() == null || Boolean.TRUE.equals(in.getInclude().getSummary());

        boolean useSemSeeds = in.getUseSemanticHitsAsGraphSeeds() == null
                || in.getUseSemanticHitsAsGraphSeeds();

        List<String> warnings = new ArrayList<>();
        Set<GraphRelationGroup> groups = in.getIncludeRelationGroups();
        if (groups == null || groups.isEmpty()) {
            groups = new LinkedHashSet<>(GraphRelationGroup.defaultExpansionGroups());
        }
        if (!groups.contains(GraphRelationGroup.IMPORTS) && !groups.contains(GraphRelationGroup.STRUCTURE)) {
            warnings.add("默认未展开 IMPORTS 与目录结构（STRUCTURE），跨编译单元静态牵连可能不完整");
        }

        boolean hasSemantic = in.getSemanticQueries() != null && !in.getSemanticQueries().isEmpty();
        boolean hasExplicit = hasExplicitStructuralInput(in);
        boolean hasCommit = in.getCommitHash() != null && !in.getCommitHash().isBlank();
        if (!hasSemantic && !hasExplicit && !hasCommit) {
            throw ParamValidationException.aiContextNoInput();
        }

        LinkedHashSet<String> methodSeeds = new LinkedHashSet<>();
        Map<String, String> methodSeedSource = new HashMap<>();

        addMethods(in.getChangedMethodIds(), "request", methodSeeds, methodSeedSource);
        addMethodsFromTypes(in.getChangedTypeQualifiedNames(), "request", methodSeeds, methodSeedSource);
        addMethodsFromFiles(in.getChangedFilePaths(), "request", methodSeeds, methodSeedSource);

        if (hasCommit) {
            // TODO: 缺乏repoId的非空校验
            resolveCommitSeeds(in.getRepoId(), in.getCommitHash(), methodSeeds, methodSeedSource, warnings);
        }

        int maxSem = Math.max(1, aiContextProperties.getMaxSemanticQueries());
        List<String> queries = hasSemantic ? new ArrayList<>(in.getSemanticQueries()) : List.of();
        if (queries.size() > maxSem) {
            queries = new ArrayList<>(queries.subList(0, maxSem));
            warnings.add("semanticQueries 已超过上限，仅处理前 " + maxSem + " 条");
        }

        int topK = clamp(in.getSemanticTopK(), 10, 1, 100);
        float minScore = in.getSemanticMinScore() == null ? 0.2f : in.getSemanticMinScore();

        List<AiContextAssemblyOutput.SemanticQueryHits> semanticBlocks = new ArrayList<>();
        boolean anySemanticHit = false;

        for (String q : queries) {
            if (!wantSemantic) {
                break;
            }
            List<EmbeddingSearchHit> hits = embeddingStoreAdapter.semanticSearchRich(EmbeddingSearchQuery.builder()
                    .queryText(q)
                    .repoId(in.getRepoId())
                    .topK(topK)
                    .minScore(minScore)
                    .build());
            if (!hits.isEmpty()) {
                anySemanticHit = true;
            }
            if (useSemSeeds) {
                mergeSemanticSeeds(hits, methodSeeds, methodSeedSource);
            }
            List<AiContextAssemblyOutput.SemanticHit> hitDtos = new ArrayList<>();
            for (EmbeddingSearchHit h : hits) {
                AiContextAssemblyOutput.SemanticHit sh = AiContextAssemblyOutput.SemanticHit.builder()
                        .nodeId(h.getNodeId())
                        .nodeType(h.getNodeType())
                        .qualifiedName(h.getQualifiedName())
                        .score(h.getScore())
                        .evidenceSnippet(h.getEvidenceSnippet())
                        .build();
                hitDtos.add(sh);
            }
            semanticBlocks.add(AiContextAssemblyOutput.SemanticQueryHits.builder()
                    .query(q)
                    .hits(hitDtos)
                    .build());
        }

        if (hasSemantic && !useSemSeeds && methodSeeds.isEmpty()) {
            warnings.add("本次未执行图多跳展开（useSemanticHitsAsGraphSeeds 为 false 且无其它结构化种子）");
        } else if (hasSemantic && useSemSeeds && !anySemanticHit) {
            warnings.add("语义检索无命中，未从语义产生图种子");
        }

        ExpandedGraph expanded = ExpandedGraph.builder().build();
        if (wantGraph && !methodSeeds.isEmpty()) {
            int maxH = clamp(in.getGraph() == null ? null : in.getGraph().getMaxHops(), 3, 1, 8);
            int up = in.getGraph() != null && in.getGraph().getUpstreamMaxHops() != null
                    ? clamp(in.getGraph().getUpstreamMaxHops(), maxH, 1, 8) : maxH;
            int down = in.getGraph() != null && in.getGraph().getDownstreamMaxHops() != null
                    ? clamp(in.getGraph().getDownstreamMaxHops(), maxH, 1, 8) : maxH;
            int maxNodes = clamp(in.getGraph() == null ? null : in.getGraph().getMaxNodes(), 500, 1,
                    aiContextProperties.getMaxNodesHardCap());
            int maxEdges = clamp(in.getGraph() == null ? null : in.getGraph().getMaxEdges(), 2000, 1,
                    aiContextProperties.getMaxEdgesHardCap());
            int maxPaths = clamp(in.getGraph() == null ? null : in.getGraph().getMaxPaths(), 50, 0,
                    aiContextProperties.getMaxPathsHardCap());

            GraphExpandQuery gx = GraphExpandQuery.builder()
                    .repoId(in.getRepoId())
                    .seedMethodIds(new ArrayList<>(methodSeeds))
                    .includeRelationGroups(groups)
                    .upstreamMaxHops(up)
                    .downstreamMaxHops(down)
                    .maxNodes(maxNodes)
                    .maxEdges(maxEdges)
                    .maxPaths(maxPaths)
                    .build();
            log.info("AI 上下文图展开: repoId={}, seedMethods={}, groups={}",
                    in.getRepoId(), methodSeeds.size(), groups);
            expanded = graphAdapter.expandSubgraph(gx);
            warnings.addAll(expanded.getTruncationWarnings());
        }

        Set<String> graphNodeIds = expanded.getNodes().stream()
                .map(ExpandedNode::getId)
                .collect(Collectors.toCollection(HashSet::new));

        // 富化语义命中并计算 inGraph
        if (wantSemantic) {
            for (AiContextAssemblyOutput.SemanticQueryHits block : semanticBlocks) {
                for (AiContextAssemblyOutput.SemanticHit hit : block.getHits()) {
                    enrichHit(hit, graphNodeIds, wantGraph, expanded.isTruncated());
                }
            }
        }

        AiContextAssemblyOutput.Seeds seeds = buildSeeds(methodSeeds, methodSeedSource);

        AiContextAssemblyOutput.GraphBlock graphBlock = null;
        if (wantGraph) {
            graphBlock = mapGraph(expanded);
        }

        AiContextAssemblyOutput.SemanticBlock semBlock = null;
        if (wantSemantic) {
            semBlock = AiContextAssemblyOutput.SemanticBlock.builder()
                    .queries(semanticBlocks)
                    .build();
        }

        AiContextAssemblyOutput.Summary summary = null;
        if (wantSummary) {
            summary = buildSummary(expanded, semanticBlocks, graphBlock);
        }

        boolean truncatedFlag = expanded.isTruncated();

        AiContextAssemblyOutput.Meta meta = AiContextAssemblyOutput.Meta.builder()
                .repoId(in.getRepoId())
                .commitHash(in.getCommitHash())
                .generatedAt(Instant.now().toString())
                .evidenceScope(EVIDENCE_SCOPE)
                .elapsedMs(System.currentTimeMillis() - t0)
                .truncation(AiContextAssemblyOutput.Truncation.builder()
                        .truncated(truncatedFlag || !warnings.isEmpty())
                        .warnings(warnings)
                        .build())
                .build();

        return AiContextAssemblyOutput.builder()
                .schemaVersion("1.0")
                .meta(meta)
                .seeds(seeds)
                .graph(graphBlock)
                .semantic(semBlock)
                .summary(summary)
                .build();
    }

    private static boolean hasExplicitStructuralInput(AiContextAssemblyInput in) {
        return notEmpty(in.getChangedMethodIds())
                || notEmpty(in.getChangedTypeQualifiedNames())
                || notEmpty(in.getChangedFilePaths());
    }

    private static boolean notEmpty(List<?> l) {
        return l != null && !l.isEmpty();
    }

    private void addMethods(List<String> ids, String source, Set<String> seeds, Map<String, String> src) {
        if (ids == null) {
            return;
        }
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }
            seeds.add(id);
            src.putIfAbsent(id, source);
        }
    }

    private void addMethodsFromTypes(List<String> qns, String source, Set<String> seeds, Map<String, String> src) {
        if (qns == null) {
            return;
        }
        for (String qn : qns) {
            if (qn == null || qn.isBlank()) {
                continue;
            }
            List<String> mids = graphAdapter.findMethodIdsByTypeQualifiedNames(List.of(qn));
            for (String mid : mids) {
                seeds.add(mid);
                src.putIfAbsent(mid, source);
            }
        }
    }

    private void addMethodsFromFiles(List<String> paths, String source, Set<String> seeds, Map<String, String> src) {
        if (paths == null) {
            return;
        }
        List<String> nonBlank = paths.stream().filter(p -> p != null && !p.isBlank()).toList();
        if (nonBlank.isEmpty()) {
            return;
        }
        List<String> mids = graphAdapter.findMethodIdsByJavaFilePaths(nonBlank);
        for (String mid : mids) {
            seeds.add(mid);
            src.putIfAbsent(mid, source);
        }
    }

    private void resolveCommitSeeds(String repoId, String commitHash, Set<String> methodSeeds,
                                    Map<String, String> src, List<String> warnings) {
        int sizeBeforeSnap = methodSeeds.size();
        CommitSeedSnapshot snap = graphAdapter.findCommitSeedsInGraph(repoId, commitHash);
        for (String mid : snap.getMethodIds()) {
            methodSeeds.add(mid);
            src.putIfAbsent(mid, "commit");
        }
        for (String tqn : snap.getTypeQualifiedNames()) {
            List<String> mids = graphAdapter.findMethodIdsByTypeQualifiedNames(List.of(tqn));
            for (String mid : mids) {
                methodSeeds.add(mid);
                src.putIfAbsent(mid, "commit");
            }
        }
        // 图中 CHANGED_IN 未贡献任何新种子时，使用 Git diff 兜底
        if (methodSeeds.size() != sizeBeforeSnap) {
            return;
        }
        RepositoryInfo repo = repositoryPort.findById(repoId);
        if (repo == null || repo.getLocalPath() == null || repo.getLocalPath().isBlank()) {
            if (!snap.isGraphHit()) {
                warnings.add("未在图中找到 Commit/CHANGED_IN 关联且无法访问本地仓库路径，commitHash 可能无法解析种子");
            }
            return;
        }
        try {
            List<ChangedFile> files = gitAdapter.diffCommitAgainstFirstParent(repo.getLocalPath(), commitHash);
            List<String> paths = files.stream()
                    .filter(f -> f.getChangeType() != FileChangeType.DELETED)
                    .map(ChangedFile::getAbsolutePath)
                    .filter(p -> p != null && !p.isBlank())
                    .toList();
            List<String> midsFromGit = paths.isEmpty()
                    ? List.of()
                    : graphAdapter.findMethodIdsByJavaFilePaths(paths);
            for (String mid : midsFromGit) {
                methodSeeds.add(mid);
                src.putIfAbsent(mid, "commit");
            }
            if (midsFromGit.isEmpty()) {
                warnings.add("commitHash 已通过 Git diff 尝试解析，但变更 Java 文件下未找到已索引的方法种子");
            }
        } catch (Exception e) {
            log.warn("commitHash Git diff 兜底失败: repoId={}, commit={}", repoId, commitHash, e);
            warnings.add("commitHash Git diff 解析失败，可能缺少父提交或本地仓库不可用");
        }
    }

    private void mergeSemanticSeeds(List<EmbeddingSearchHit> hits, Set<String> methodSeeds,
                                    Map<String, String> src) {
        for (EmbeddingSearchHit h : hits) {
            if (h.getNodeType() == null) {
                continue;
            }
            if ("METHOD".equalsIgnoreCase(h.getNodeType())) {
                methodSeeds.add(h.getNodeId());
                src.putIfAbsent(h.getNodeId(), "semantic");
            } else if ("TYPE".equalsIgnoreCase(h.getNodeType())) {
                List<String> mids = graphAdapter.findMethodIdsByTypeQualifiedNames(List.of(h.getNodeId()));
                for (String mid : mids) {
                    methodSeeds.add(mid);
                    src.putIfAbsent(mid, "semantic");
                }
            }
        }
    }

    private void enrichHit(AiContextAssemblyOutput.SemanticHit hit, Set<String> graphNodeIds,
                           boolean wantGraph, boolean graphTruncated) {
        List<CodeNodeDetail> details = graphAdapter.loadCodeNodeDetails(List.of(hit.getNodeId()));
        if (!details.isEmpty()) {
            CodeNodeDetail d = details.get(0);
            hit.setSignature(d.getSignature());
            hit.setFilePath(d.getFilePath());
            hit.setLineStart(d.getLineStart());
            hit.setLineEnd(d.getLineEnd());
        }
        boolean inGraph = graphNodeIds.contains(hit.getNodeId());
        hit.setInGraph(inGraph);
        if (!inGraph) {
            if (!wantGraph) {
                hit.setNote("请求 include.graph=false，未生成图节点集合");
            } else if (graphTruncated) {
                hit.setNote("图已截断或该节点未落入本次子图节点集合");
            } else {
                hit.setNote("语义命中节点未包含在本次 graph.nodes 中（可能未并入种子或未索引）");
            }
        }
    }

    private AiContextAssemblyOutput.Seeds buildSeeds(Set<String> methodSeeds, Map<String, String> src) {
        List<CodeNodeDetail> details = graphAdapter.loadCodeNodeDetails(new ArrayList<>(methodSeeds));
        Map<String, CodeNodeDetail> byId = details.stream().collect(Collectors.toMap(CodeNodeDetail::getId, d -> d, (a, b) -> a));
        List<AiContextAssemblyOutput.SeedMethod> methods = new ArrayList<>();
        for (String mid : methodSeeds) {
            CodeNodeDetail d = byId.get(mid);
            methods.add(AiContextAssemblyOutput.SeedMethod.builder()
                    .methodId(mid)
                    .signature(d != null ? d.getSignature() : null)
                    .ownerType(d != null ? d.getOwnerQualifiedName() : null)
                    .filePath(d != null ? d.getFilePath() : null)
                    .lineStart(d != null ? d.getLineStart() : null)
                    .lineEnd(d != null ? d.getLineEnd() : null)
                    .source(src.getOrDefault(mid, "request"))
                    .build());
        }
        return AiContextAssemblyOutput.Seeds.builder()
                .methods(methods)
                .build();
    }

    private AiContextAssemblyOutput.GraphBlock mapGraph(ExpandedGraph eg) {
        List<AiContextAssemblyOutput.GraphNode> nodes = new ArrayList<>();
        for (ExpandedNode n : eg.getNodes()) {
            nodes.add(AiContextAssemblyOutput.GraphNode.builder()
                    .id(n.getId())
                    .label(n.getLabel())
                    .seedIds(n.getSeedIds())
                    .primarySeedId(n.getPrimarySeedId())
                    .properties(new LinkedHashMap<>(n.getProperties()))
                    .build());
        }
        List<AiContextAssemblyOutput.GraphEdge> edges = new ArrayList<>();
        for (ExpandedEdge e : eg.getEdges()) {
            edges.add(AiContextAssemblyOutput.GraphEdge.builder()
                    .from(e.getFromId())
                    .to(e.getToId())
                    .type(e.getRelationType())
                    .hopFromSeed(e.getHopFromSeed())
                    .directionToSeed(e.getDirectionToSeed())
                    .referenceSeedId(e.getReferenceSeedId())
                    .seedIds(e.getSeedIds())
                    .properties(new LinkedHashMap<>(e.getProperties()))
                    .build());
        }
        List<AiContextAssemblyOutput.GraphPath> paths = new ArrayList<>();
        for (ExpandedPath p : eg.getPaths()) {
            paths.add(AiContextAssemblyOutput.GraphPath.builder()
                    .nodeIds(p.getNodeIds())
                    .relTypes(p.getRelTypes())
                    .length(p.getLength())
                    .referenceSeedId(p.getReferenceSeedId())
                    .build());
        }
        List<AiContextAssemblyOutput.GraphEdge> inh = new ArrayList<>();
        for (ExpandedEdge e : eg.getInheritanceEdges()) {
            inh.add(AiContextAssemblyOutput.GraphEdge.builder()
                    .from(e.getFromId())
                    .to(e.getToId())
                    .type(e.getRelationType())
                    .hopFromSeed(e.getHopFromSeed())
                    .directionToSeed(e.getDirectionToSeed())
                    .referenceSeedId(e.getReferenceSeedId())
                    .seedIds(e.getSeedIds())
                    .properties(new LinkedHashMap<>(e.getProperties()))
                    .build());
        }
        List<AiContextAssemblyOutput.FieldCoupling> fc = new ArrayList<>();
        for (FieldCouplingEntry fe : eg.getFieldCoupling()) {
            fc.add(AiContextAssemblyOutput.FieldCoupling.builder()
                    .fieldId(fe.getFieldId())
                    .ownerTypeQualifiedName(fe.getOwnerTypeQualifiedName())
                    .fieldSimpleName(fe.getFieldSimpleName())
                    .readers(fe.getReaderMethodIds())
                    .writers(fe.getWriterMethodIds())
                    .build());
        }
        return AiContextAssemblyOutput.GraphBlock.builder()
                .nodes(nodes)
                .edges(edges)
                .paths(paths)
                .inheritance(inh)
                .fieldCoupling(fc)
                .build();
    }

    private AiContextAssemblyOutput.Summary buildSummary(ExpandedGraph eg,
                                                          List<AiContextAssemblyOutput.SemanticQueryHits> sem,
                                                          AiContextAssemblyOutput.GraphBlock graph) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (graph != null) {
            counts.put("graphNodes", graph.getNodes().size());
            counts.put("graphEdges", graph.getEdges().size());
            counts.put("graphPaths", graph.getPaths().size());
        } else {
            counts.put("graphNodes", 0);
            counts.put("graphEdges", 0);
            counts.put("graphPaths", 0);
        }
        int hits = sem.stream().mapToInt(q -> q.getHits().size()).sum();
        counts.put("semanticHits", hits);

        Map<String, Integer> hist = new LinkedHashMap<>();
        if (graph != null) {
            for (AiContextAssemblyOutput.GraphEdge e : graph.getEdges()) {
                hist.merge(e.getType(), 1, Integer::sum);
            }
        }
        return AiContextAssemblyOutput.Summary.builder()
                .counts(counts)
                .relationTypeHistogram(hist)
                .build();
    }

    private static int clamp(Integer v, int defaultVal, int lo, int hi) {
        int x = v == null ? defaultVal : v;
        return Math.max(lo, Math.min(hi, x));
    }
}
