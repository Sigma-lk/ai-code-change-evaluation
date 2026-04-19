package com.sigma.ai.evaluation.domain.codesearch.impl;

import com.sigma.ai.evaluation.domain.codesearch.CodeTextSearchService;
import com.sigma.ai.evaluation.domain.codesearch.model.TextSearchResult;
import com.sigma.ai.evaluation.domain.repository.adapter.RepositoryPort;
import com.sigma.ai.evaluation.domain.repository.model.RepositoryInfo;
import com.sigma.ai.evaluation.types.ErrorCode;
import com.sigma.ai.evaluation.types.exception.ParamValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodeTextSearchServiceImplTest {

    @Mock
    private RepositoryPort repositoryPort;

    private CodeTextSearchService service;

    @TempDir
    Path repoRoot;

    @BeforeEach
    void setUp() {
        service = new CodeTextSearchServiceImpl(repositoryPort);
    }

    @Test
    void normalizeGlobAddsRecursivePrefixForStarDot() {
        assertThat(CodeTextSearchServiceImpl.normalizeGlob("*.java")).isEqualTo("{*.java,**/*.java}");
        assertThat(CodeTextSearchServiceImpl.normalizeGlob("glob:*.java")).isEqualTo("{*.java,**/*.java}");
        assertThat(CodeTextSearchServiceImpl.normalizeGlob("**/*.java")).isEqualTo("{*.java,**/*.java}");
        assertThat(CodeTextSearchServiceImpl.normalizeGlob("**/*.kt")).isEqualTo("{*.kt,**/*.kt}");
    }

    @Test
    void literalSearchFindsLine() throws Exception {
        Path f = repoRoot.resolve("src/Hello.java");
        Files.createDirectories(f.getParent());
        Files.writeString(f, "package x;\nclass Hello { void foo() { bar(); } }\n", StandardCharsets.UTF_8);

        when(repositoryPort.findById("r1")).thenReturn(RepositoryInfo.builder()
                .id("r1")
                .localPath(repoRoot.toString())
                .build());

        TextSearchResult r = service.search("r1", "bar()", false, false, null, "**/*.java",
                50, 10_000, 2L * 1024 * 1024, true);
        assertThat(r.getHits()).hasSize(1);
        assertThat(r.getHits().get(0).getLineText()).contains("bar()");
        assertThat(r.getHits().get(0).getRelativePath()).isEqualTo("src/Hello.java");
        assertThat(r.getScannedFiles()).isEqualTo(1);
        assertThat(r.isTruncatedByMaxHits()).isFalse();
    }

    @Test
    void maxHitsTruncates() throws Exception {
        Path f = repoRoot.resolve("a.java");
        Files.writeString(f, "x\nx\nx\n", StandardCharsets.UTF_8);
        when(repositoryPort.findById("r1")).thenReturn(RepositoryInfo.builder()
                .id("r1")
                .localPath(repoRoot.toString())
                .build());

        TextSearchResult r = service.search("r1", "x", false, false, null, "*.java",
                2, 10_000, 2L * 1024 * 1024, true);
        assertThat(r.getHits()).hasSize(2);
        assertThat(r.isTruncatedByMaxHits()).isTrue();
    }

    @Test
    void regexSearch() throws Exception {
        Path f = repoRoot.resolve("M.java");
        Files.writeString(f, "id 123\nid 999\n", StandardCharsets.UTF_8);
        when(repositoryPort.findById("r1")).thenReturn(RepositoryInfo.builder()
                .id("r1")
                .localPath(repoRoot.toString())
                .build());

        TextSearchResult r = service.search("r1", "id\\s+\\d+", true, false, null, "**/*.java",
                20, 10_000, 2L * 1024 * 1024, true);
        assertThat(r.getHits()).hasSize(2);
    }

    @Test
    void invalidRegex() {
        when(repositoryPort.findById("r1")).thenReturn(RepositoryInfo.builder()
                .id("r1")
                .localPath(repoRoot.toString())
                .build());

        assertThatThrownBy(() -> service.search("r1", "(unclosed", true, false, null, "**/*.java",
                10, 10, 1024, true))
                .isInstanceOf(ParamValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TEXT_SEARCH_REGEX_INVALID);
    }
}
