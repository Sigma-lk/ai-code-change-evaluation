package com.sigma.ai.evaluation.domain.codesnippet.impl;

import com.sigma.ai.evaluation.domain.codesnippet.CodeSnippetReadService;
import com.sigma.ai.evaluation.domain.codesnippet.model.CodeSnippetReadResult;
import com.sigma.ai.evaluation.domain.repository.adapter.RepositoryPort;
import com.sigma.ai.evaluation.domain.repository.model.RepositoryInfo;
import com.sigma.ai.evaluation.types.ErrorCode;
import com.sigma.ai.evaluation.types.exception.ParamValidationException;
import com.sigma.ai.evaluation.types.exception.ResourceNotFoundException;
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
class CodeSnippetReadServiceImplTest {

    @Mock
    private RepositoryPort repositoryPort;

    private CodeSnippetReadService service;

    @TempDir
    Path repoRoot;

    @BeforeEach
    void setUp() {
        service = new CodeSnippetReadServiceImpl(repositoryPort);
    }

    @Test
    void readRelativePathSmallFile() throws Exception {
        Path f = repoRoot.resolve("src/Foo.java");
        Files.createDirectories(f.getParent());
        Files.writeString(f, "line1\nline2\n", StandardCharsets.UTF_8);

        when(repositoryPort.findById("r1")).thenReturn(RepositoryInfo.builder()
                .id("r1")
                .localPath(repoRoot.toString())
                .build());

        CodeSnippetReadResult r = service.readSnippet("r1", "src/Foo.java", 1, 2, null);
        assertThat(r.getContent()).isEqualTo("line1\nline2");
        assertThat(r.getResolvedAbsolutePath()).isEqualTo(f.toRealPath().toString());
        assertThat(r.getRelativePath()).isEqualTo("src/Foo.java");
        assertThat(r.getTotalLinesInFile()).isNull();
        assertThat(r.isTruncated()).isFalse();
    }

    @Test
    void readLineRange() throws Exception {
        Path f = repoRoot.resolve("big.txt");
        Files.writeString(f, "a\nb\nc\nd\n", StandardCharsets.UTF_8);

        when(repositoryPort.findById("r1")).thenReturn(RepositoryInfo.builder()
                .id("r1")
                .localPath(repoRoot.toString())
                .build());

        CodeSnippetReadResult r = service.readSnippet("r1", "big.txt", 2, 3, 1000);
        assertThat(r.getContent()).isEqualTo("b\nc");
        assertThat(r.getReturnedStartLine()).isEqualTo(2);
        assertThat(r.getReturnedEndLine()).isEqualTo(3);
        assertThat(r.getTotalLinesInFile()).isNull();
    }

    @Test
    void rejectsMissingLineRange() {
        assertThatThrownBy(() -> service.readSnippet("r1", "any.java", null, 5, null))
                .isInstanceOf(ParamValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_LINE_RANGE_INVALID);
    }

    @Test
    void rejectsPathOutsideRepo() throws Exception {
        Path other = repoRoot.getParent().resolve("other-secret");
        Files.createDirectories(other);
        Path secret = other.resolve("x.txt");
        Files.writeString(secret, "no", StandardCharsets.UTF_8);

        when(repositoryPort.findById("r1")).thenReturn(RepositoryInfo.builder()
                .id("r1")
                .localPath(repoRoot.toString())
                .build());

        assertThatThrownBy(() -> service.readSnippet("r1", secret.toString(), 1, 1, null))
                .isInstanceOf(ParamValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_PATH_OUTSIDE_REPO);
    }
}
