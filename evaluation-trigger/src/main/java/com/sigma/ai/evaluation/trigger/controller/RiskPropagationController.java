package com.sigma.ai.evaluation.trigger.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sigma.ai.evaluation.api.controller.RiskPropagationApi;
import com.sigma.ai.evaluation.api.dto.RiskImpactChainDto;
import com.sigma.ai.evaluation.api.dto.RiskNodeRefDto;
import com.sigma.ai.evaluation.api.dto.RiskPropagationRequest;
import com.sigma.ai.evaluation.api.dto.RiskPropagationResponse;
import com.sigma.ai.evaluation.api.dto.RiskPropagationTruncationDto;
import com.sigma.ai.evaluation.api.dto.RiskSeedPropagationResultDto;
import com.sigma.ai.evaluation.domain.riskpropagation.RiskPropagationService;
import com.sigma.ai.evaluation.domain.riskpropagation.model.*;
import com.sigma.ai.evaluation.types.exception.ParamValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 风险传播链 REST 入口。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class RiskPropagationController implements RiskPropagationApi {

    private final RiskPropagationService riskPropagationService;
    private final ObjectMapper objectMapper;

    @Override
    public ResponseEntity<RiskPropagationResponse> propagate(RiskPropagationRequest request) {
        List<RiskNodeRefDto> nodeDtos = parseRiskNodesJson(request.getNodes());
        log.info("风险传播请求: repoId={}, nodeCount={}, propagationMaxDepth={}",
                request.getRepoId(),
                nodeDtos.size(),
                request.getPropagationMaxDepth());

        List<CodeRiskNodeRef> seeds = nodeDtos.stream()
                .map(RiskPropagationController::toDomainSeed)
                .collect(Collectors.toList());

        RiskPropagationQuery query = RiskPropagationQuery.builder()
                .repoId(request.getRepoId())
                .riskSeeds(seeds)
                .propagationMaxDepth(request.getPropagationMaxDepth())
                .maxNodes(request.getMaxNodes())
                .maxEdges(request.getMaxEdges())
                .build();

        RiskPropagationResult result = riskPropagationService.propagate(query);
        RiskPropagationResponse body = toResponse(result);

        log.info("风险传播响应: repoId={}, effectiveDepth={}, resultRows={}",
                body.getRepoId(), body.getEffectiveDepth(),
                body.getResults() == null ? 0 : body.getResults().size());

        return ResponseEntity.ok(body);
    }

    /**
     * 将请求体中的 {@code nodes} JSON 数组字符串反序列化为 DTO 列表；未传或空白视为空列表。
     *
     * @param nodesJson JSON 数组文本，元素字段与 {@link RiskNodeRefDto} 对齐
     * @return 节点 DTO 列表，永不为 {@code null}
     * @throws ParamValidationException 非合法 JSON 或非数组结构时抛出，由全局处理器映射为 400
     */
    private List<RiskNodeRefDto> parseRiskNodesJson(String nodesJson) {
        if (nodesJson == null || nodesJson.isBlank()) {
            return List.of();
        }
        String trimmed = nodesJson.trim();
        try {
            List<RiskNodeRefDto> list = objectMapper.readValue(trimmed, new TypeReference<List<RiskNodeRefDto>>() {
            });
            return list != null ? list : List.of();
        } catch (JsonProcessingException e) {
            log.warn("风险传播 nodes JSON 解析失败", e);
            throw ParamValidationException.riskPropagationNodesJsonInvalid();
        }
    }

    private static CodeRiskNodeRef toDomainSeed(RiskNodeRefDto dto) {
        if (dto == null) {
            return CodeRiskNodeRef.builder().build();
        }
        return CodeRiskNodeRef.builder()
                .kind(dto.getKind())
                .qualifiedName(dto.getQualifiedName())
                .filePath(dto.getFilePath())
                .build();
    }

    private static RiskPropagationResponse toResponse(RiskPropagationResult in) {
        List<RiskSeedPropagationResultDto> rows = new ArrayList<>();
        if (in.getResults() != null) {
            for (RiskSeedPropagationResult r : in.getResults()) {
                rows.add(RiskSeedPropagationResultDto.builder()
                        .seed(toDto(r.getSeed()))
                        .matchedInGraph(r.isMatchedInGraph())
                        .impactChains(r.getImpactChains() == null ? List.of() : r.getImpactChains().stream()
                                .map(RiskPropagationController::toChainDto)
                                .collect(Collectors.toList()))
                        .build());
            }
        }
        RiskPropagationTruncationDto trunc = null;
        if (in.getTruncation() != null && in.getTruncation().getWarnings() != null
                && !in.getTruncation().getWarnings().isEmpty()) {
            trunc = RiskPropagationTruncationDto.builder()
                    .warnings(new ArrayList<>(in.getTruncation().getWarnings()))
                    .build();
        }
        return RiskPropagationResponse.builder()
                .repoId(in.getRepoId())
                .effectiveDepth(in.getEffectiveDepth())
                .results(rows)
                .truncation(trunc)
                .build();
    }

    private static RiskImpactChainDto toChainDto(RiskImpactChain c) {
        return RiskImpactChainDto.builder()
                .chainKind(c.getChainKind())
                .hopCount(c.getHopCount())
                .nodes(c.getNodes() == null ? List.of() : c.getNodes().stream()
                        .map(RiskPropagationController::toDto)
                        .collect(Collectors.toList()))
                .edgeTypes(c.getEdgeTypes() == null ? List.of() : new ArrayList<>(c.getEdgeTypes()))
                .build();
    }

    private static RiskNodeRefDto toDto(CodeRiskNodeRef n) {
        if (n == null) {
            return null;
        }
        return RiskNodeRefDto.builder()
                .kind(n.getKind())
                .qualifiedName(n.getQualifiedName())
                .filePath(n.getFilePath())
                .build();
    }
}
