package com.sigma.ai.evaluation.domain.aicontext.impl;

import com.sigma.ai.evaluation.domain.aicontext.AiContextAssemblyInput;
import com.sigma.ai.evaluation.domain.aicontext.AiContextAssemblyOutput;
import com.sigma.ai.evaluation.domain.aicontext.AiContextProperties;
import com.sigma.ai.evaluation.domain.codegraph.adapter.GraphAdapter;
import com.sigma.ai.evaluation.domain.embedding.adapter.EmbeddingStoreAdapter;
import com.sigma.ai.evaluation.domain.repository.adapter.GitAdapter;
import com.sigma.ai.evaluation.domain.repository.adapter.RepositoryPort;
import com.sigma.ai.evaluation.types.ErrorCode;
import com.sigma.ai.evaluation.types.exception.ParamValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * {@link AiContextAssemblyServiceImpl} 校验与「仅语义且不展开图」分支单测。
 */
@ExtendWith(MockitoExtension.class)
class AiContextAssemblyServiceImplTest {

    @Mock
    private GraphAdapter graphAdapter;
    @Mock
    private EmbeddingStoreAdapter embeddingStoreAdapter;
    @Mock
    private RepositoryPort repositoryPort;
    @Mock
    private GitAdapter gitAdapter;

    private AiContextProperties properties;
    private AiContextAssemblyServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new AiContextProperties();
        service = new AiContextAssemblyServiceImpl(
                graphAdapter, embeddingStoreAdapter, repositoryPort, gitAdapter, properties);
    }

    @Test
    void assemble_repoIdBlank_throwsRepoIdEmpty() {
        AiContextAssemblyInput in = AiContextAssemblyInput.builder()
                .repoId("  ")
                .semanticQueries(List.of("x"))
                .useSemanticHitsAsGraphSeeds(false)
                .build();

        assertThatThrownBy(() -> service.assemble(in))
                .isInstanceOf(ParamValidationException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.REPO_ID_EMPTY.getCode());
    }

    @Test
    void assemble_noInput_throws() {
        AiContextAssemblyInput in = AiContextAssemblyInput.builder()
                .repoId("r1")
                .build();
        assertThatThrownBy(() -> service.assemble(in))
                .isInstanceOf(ParamValidationException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.AI_CONTEXT_NO_INPUT.getCode());
    }

    @Test
    void assemble_semanticOnly_noGraphSeeds_returnsWarningsAndNoGraphExpand() {
        when(embeddingStoreAdapter.semanticSearchRich(any())).thenReturn(List.of());
        AiContextAssemblyInput in = AiContextAssemblyInput.builder()
                .repoId("r1")
                .semanticQueries(List.of("权限校验"))
                .useSemanticHitsAsGraphSeeds(false)
                .build();

        AiContextAssemblyOutput out = service.assemble(in);

        assertThat(out.getMeta().getTruncation().getWarnings())
                .anyMatch(w -> w.contains("未执行图多跳展开"));
        // 未并入种子时不应调用 expandSubgraph
        org.mockito.Mockito.verify(graphAdapter, org.mockito.Mockito.never()).expandSubgraph(any());
    }
}
