package com.sigma.ai.evaluation.domain.codesearch.impl;

import com.sigma.ai.evaluation.domain.codesearch.CodeTextSearchService;
import com.sigma.ai.evaluation.domain.codesearch.model.TextSearchHit;
import com.sigma.ai.evaluation.domain.codesearch.model.TextSearchResult;
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
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 在仓库目录内按 glob 过滤文件并逐行扫描，实现类 ripgrep 的精确/正则搜索。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeTextSearchServiceImpl implements CodeTextSearchService {

    private static final int MAX_HITS_CAP = 500;
    private static final int MAX_FILES_CAP = 200_000;
    private static final int MAX_LINE_DISPLAY_CHARS = 4_000;

    private static final Set<String> IGNORED_DIR_SEGMENTS = Set.of(
            ".git", "target", "build", "node_modules", ".idea", "out", ".gradle",
            "__pycache__", "dist", ".svn", ".hg");

    private final RepositoryPort repositoryPort;

    @Override
    public TextSearchResult search(String repoId, String query, boolean useRegex, boolean caseInsensitive,
                                   String subPath, String glob, int maxHits, int maxFilesScanned, long maxFileBytes,
                                   boolean skipCommonIgnoredDirs) {
        if (repoId == null || repoId.isBlank()) {
            throw ParamValidationException.repoIdEmpty();
        }
        if (query == null || query.isBlank()) {
            throw ParamValidationException.textSearchQueryEmpty();
        }

        int capHits = Math.min(Math.max(maxHits, 1), MAX_HITS_CAP);
        int capFiles = Math.min(Math.max(maxFilesScanned, 1), MAX_FILES_CAP);
        long capBytes = Math.max(maxFileBytes, 1024);

        RepositoryInfo repo = repositoryPort.findById(repoId);
        if (repo == null) {
            throw ResourceNotFoundException.repositoryNotFound(repoId);
        }

        Path realBase = resolveRealBase(repo);
        Path searchRoot = resolveSearchRoot(realBase, subPath);

        String globNorm = normalizeGlob(glob == null || glob.isBlank() ? "**/*.java" : glob);
        PathMatcher pathMatcher = searchRoot.getFileSystem().getPathMatcher("glob:" + globNorm);

        final Pattern linePattern;
        if (useRegex) {
            try {
                int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;
                linePattern = Pattern.compile(query, flags);
            } catch (PatternSyntaxException e) {
                throw ParamValidationException.textSearchRegexInvalid(e.getMessage());
            }
        } else {
            linePattern = null;
        }
        String literal = query;
        String literalLower = caseInsensitive ? query.toLowerCase(Locale.ROOT) : null;

        List<TextSearchHit> hits = new ArrayList<>();
        AtomicInteger scanned = new AtomicInteger();
        AtomicBoolean truncatedHits = new AtomicBoolean();
        AtomicBoolean truncatedFiles = new AtomicBoolean();

        try {
            Files.walkFileTree(searchRoot, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (skipCommonIgnoredDirs && !dir.equals(searchRoot)) {
                        Path name = dir.getFileName();
                        if (name != null && IGNORED_DIR_SEGMENTS.contains(name.toString())) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (hits.size() >= capHits) {
                        truncatedHits.set(true);
                        return FileVisitResult.TERMINATE;
                    }
                    if (scanned.get() >= capFiles) {
                        truncatedFiles.set(true);
                        return FileVisitResult.TERMINATE;
                    }
                    if (!attrs.isRegularFile()) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path rel = searchRoot.relativize(file);
                    Path relForGlob = searchRoot.getFileSystem()
                            .getPath(rel.toString().replace('\\', '/'));
                    if (!pathMatcher.matches(rel) && !pathMatcher.matches(relForGlob)) {
                        return FileVisitResult.CONTINUE;
                    }
                    long sz = attrs.size();
                    if (sz > capBytes) {
                        return FileVisitResult.CONTINUE;
                    }

                    scanned.incrementAndGet();
                    scanFile(file, realBase, linePattern, literal, caseInsensitive, literalLower, hits, capHits);
                    if (hits.size() >= capHits) {
                        truncatedHits.set(true);
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("文本搜索 walkFileTree 失败: repoId={}, root={}", repoId, searchRoot, e);
            throw new BusinessException(ErrorCode.TEXT_SEARCH_WALK_FAILED,
                    ErrorCode.TEXT_SEARCH_WALK_FAILED.getMessage() + ": " + e.getMessage(), e);
        }

        return TextSearchResult.builder()
                .hits(hits)
                .scannedFiles(scanned.get())
                .truncatedByMaxHits(truncatedHits.get())
                .truncatedByMaxFiles(truncatedFiles.get())
                .build();
    }

    private static Path resolveRealBase(RepositoryInfo repo) {
        Path base = Path.of(repo.getLocalPath()).toAbsolutePath().normalize();
        if (!Files.exists(base) || !Files.isDirectory(base)) {
            throw ResourceNotFoundException.repoLocalWorkspaceNotFound(repo.getLocalPath());
        }
        try {
            return base.toRealPath();
        } catch (IOException e) {
            throw ResourceNotFoundException.repoLocalWorkspaceNotFound(repo.getLocalPath());
        }
    }

    private static Path resolveSearchRoot(Path realBase, String subPath) {
        if (subPath == null || subPath.isBlank()) {
            return realBase;
        }
        Path raw = Path.of(subPath.trim());
        Path candidate = (raw.isAbsolute() ? raw : realBase.resolve(raw)).normalize();
        Path realSub;
        try {
            realSub = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            throw ResourceNotFoundException.repoFileNotFound(candidate.toString());
        }
        if (!realSub.startsWith(realBase)) {
            throw ParamValidationException.filePathOutsideRepo();
        }
        if (!Files.isDirectory(realSub)) {
            throw ResourceNotFoundException.repoFileNotFound(realSub.toString());
        }
        return realSub;
    }

    /**
     * 将简写 glob（如 *.java）规范为可递归匹配的形式（在模式前追加递归段）。
     */
    static String normalizeGlob(String glob) {
        String g = glob.trim();
        if (g.startsWith("glob:")) {
            g = g.substring("glob:".length()).trim();
        }
        if (g.startsWith("{") && g.endsWith("}")) {
            return g;
        }
        // Java PathMatcher：双星递归 glob 对「仅位于根目录的 *.ext」常不匹配，故合并为花括号备选。
        if (!g.contains(",") && g.startsWith("**/") && g.length() > 3) {
            String tail = g.substring(3);
            if (tail.startsWith("*.")) {
                return "{" + tail + "," + g + "}";
            }
        }
        if (!g.contains(",") && g.startsWith("*.") && g.indexOf('/') < 0) {
            return "{" + g + ",**/" + g + "}";
        }
        if (!g.contains("**") && g.startsWith("*.")) {
            return "**/" + g;
        }
        return g;
    }

    private void scanFile(Path file, Path realBase, Pattern linePattern, String literal,
                          boolean caseInsensitive, String literalLower,
                          List<TextSearchHit> hits, int capHits) {
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        try (Reader reader = new InputStreamReader(Files.newInputStream(file), decoder);
             BufferedReader br = new BufferedReader(reader, 8192)) {
            String line;
            int lineNo = 0;
            while ((line = br.readLine()) != null) {
                lineNo++;
                if (lineMatches(line, linePattern, literal, caseInsensitive, literalLower)) {
                    hits.add(TextSearchHit.builder()
                            .absolutePath(file.toString())
                            .relativePath(relativizeToPosix(realBase, file))
                            .lineNumber(lineNo)
                            .lineText(truncateLine(line))
                            .build());
                    if (hits.size() >= capHits) {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            log.debug("跳过不可读文件: {}", file, e);
        }
    }

    private static boolean lineMatches(String line, Pattern linePattern, String literal,
                                       boolean caseInsensitive, String literalLower) {
        if (linePattern != null) {
            return linePattern.matcher(line).find();
        }
        if (caseInsensitive) {
            return line.toLowerCase(Locale.ROOT).contains(literalLower);
        }
        return line.contains(literal);
    }

    private static String truncateLine(String line) {
        if (line.length() <= MAX_LINE_DISPLAY_CHARS) {
            return line;
        }
        return line.substring(0, MAX_LINE_DISPLAY_CHARS) + "…";
    }

    private static String relativizeToPosix(Path realBase, Path file) {
        return realBase.relativize(file).toString().replace('\\', '/');
    }
}
