package com.sigma.ai.evaluation.domain.codesnippet.impl;

import com.sigma.ai.evaluation.domain.codesnippet.CodeSnippetReadService;
import com.sigma.ai.evaluation.domain.codesnippet.model.CodeSnippetReadResult;
import com.sigma.ai.evaluation.domain.repository.adapter.RepositoryPort;
import com.sigma.ai.evaluation.domain.repository.model.RepositoryInfo;
import com.sigma.ai.evaluation.types.ErrorCode;
import com.sigma.ai.evaluation.types.exception.BusinessException;
import com.sigma.ai.evaluation.types.exception.ParamValidationException;
import com.sigma.ai.evaluation.types.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * 基于 {@link RepositoryPort} 解析本地路径并按行区间安全读取 UTF-8 文本。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeSnippetReadServiceImpl implements CodeSnippetReadService {

    /** 单次允许的最大行跨度（含首尾） */
    private static final int MAX_LINE_SPAN = 2_000;

    private static final int DEFAULT_MAX_RETURN_CHARS = 262_144;

    private final RepositoryPort repositoryPort;

    @Override
    public CodeSnippetReadResult readSnippet(String repoId, String filePath,
                                             Integer startLine, Integer endLine,
                                             Integer maxReturnChars) {
        if (repoId == null || repoId.isBlank()) {
            throw ParamValidationException.repoIdEmpty();
        }
        if (filePath == null || filePath.isBlank()) {
            throw ParamValidationException.filePathEmpty();
        }
        if (startLine == null || endLine == null) {
            throw ParamValidationException.fileLineRangeInvalid("必须同时提供 startLine 与 endLine（从 1 起算的闭区间）");
        }

        int maxChars = maxReturnChars == null || maxReturnChars <= 0
                ? DEFAULT_MAX_RETURN_CHARS
                : Math.min(maxReturnChars, DEFAULT_MAX_RETURN_CHARS * 4);

        RepositoryInfo repo = repositoryPort.findById(repoId);
        if (repo == null) {
            throw ResourceNotFoundException.repositoryNotFound(repoId);
        }

        Path base = Path.of(repo.getLocalPath()).toAbsolutePath().normalize();
        if (!Files.exists(base) || !Files.isDirectory(base)) {
            throw ResourceNotFoundException.repoLocalWorkspaceNotFound(repo.getLocalPath());
        }

        Path realBase;
        try {
            realBase = base.toRealPath();
        } catch (IOException e) {
            log.warn("无法解析仓库本地路径: {}", base, e);
            throw ResourceNotFoundException.repoLocalWorkspaceNotFound(repo.getLocalPath());
        }

        Path raw = Path.of(filePath.trim());
        Path candidate = (raw.isAbsolute() ? raw : realBase.resolve(raw)).normalize();

        Path realTarget;
        try {
            realTarget = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            log.info("代码片段目标不存在: repoId={}, candidate={}", repoId, candidate);
            throw ResourceNotFoundException.repoFileNotFound(candidate.toString());
        }

        if (!realTarget.startsWith(realBase)) {
            log.warn("代码片段路径越出仓库目录: repoId={}, realTarget={}", repoId, realTarget);
            throw ParamValidationException.filePathOutsideRepo();
        }

        if (!Files.isRegularFile(realTarget)) {
            throw ResourceNotFoundException.repoFileNotFound(realTarget.toString());
        }

        try {
            validateLineRange(startLine, endLine);
            return readLineRange(repoId, realBase, realTarget, startLine, endLine, maxChars);
        } catch (IOException e) {
            log.error("读取代码片段 IO 异常: repoId={}, filePath={}", repoId, filePath, e);
            throw new BusinessException(ErrorCode.FILE_READ_FAILED,
                    ErrorCode.FILE_READ_FAILED.getMessage() + ": " + realTarget, e);
        }
    }

    private static void validateLineRange(int startLine, int endLine) {
        if (startLine < 1 || endLine < startLine) {
            throw ParamValidationException.fileLineRangeInvalid(
                    "要求 1 <= startLine <= endLine，实际 startLine=" + startLine + ", endLine=" + endLine);
        }
        if ((long) endLine - startLine + 1 > MAX_LINE_SPAN) {
            throw ParamValidationException.fileLineRangeInvalid(
                    "单次最多读取 " + MAX_LINE_SPAN + " 行");
        }
    }

    private CodeSnippetReadResult readLineRange(String repoId, Path realBase, Path realTarget,
                                                int startLine, int endLine, int maxChars) throws IOException {
        StringBuilder sb = new StringBuilder();
        int current = 0;
        int linesTaken = 0;
        try (BufferedReader br = Files.newBufferedReader(realTarget, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                current++;
                if (current < startLine) {
                    continue;
                }
                if (current > endLine) {
                    break;
                }
                if (linesTaken > 0) {
                    sb.append('\n');
                }
                sb.append(line);
                linesTaken++;
            }
        }

        if (linesTaken == 0) {
            throw ParamValidationException.fileLineRangeInvalid(
                    "指定行区间内无内容（文件可能不足 " + startLine + " 行）");
        }

        String content = sb.toString();
        boolean truncated = false;
        String note = null;
        if (content.length() > maxChars) {
            content = content.substring(0, maxChars);
            truncated = true;
            note = "正文超过 maxReturnChars，已按字符截断";
        }

        return CodeSnippetReadResult.builder()
                .repoId(repoId)
                .resolvedAbsolutePath(realTarget.toString())
                .relativePath(relativizeToPosix(realBase, realTarget))
                .encoding(StandardCharsets.UTF_8.name())
                .totalLinesInFile(null)
                .returnedStartLine(startLine)
                .returnedEndLine(startLine + linesTaken - 1)
                .truncated(truncated)
                .truncationNote(note)
                .content(content)
                .build();
    }

    private static String relativizeToPosix(Path realBase, Path realTarget) {
        Path rel = realBase.relativize(realTarget);
        return rel.toString().replace('\\', '/');
    }
}
