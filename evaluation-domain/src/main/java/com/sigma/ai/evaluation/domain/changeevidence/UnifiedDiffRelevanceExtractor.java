package com.sigma.ai.evaluation.domain.changeevidence;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 unified diff 文本中，按「新文件」行号区间抽取相关 hunk（含 @@ 头与 +/- 行）。
 */
public final class UnifiedDiffRelevanceExtractor {

    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@");

    private UnifiedDiffRelevanceExtractor() {
    }

    /**
     * 抽取与新文件行号区间（闭区间，1-based）相交的 hunk；若无匹配则回退为全文截断。
     *
     * @param unifiedDiff   {@link com.sigma.ai.evaluation.domain.repository.adapter.GitAdapter#unifiedDiffForJavaFile} 产出
     * @param newLineStart  新文件起始行（1-based），无效时视为 1
     * @param newLineEnd    新文件结束行（1-based）
     * @param paddingLines  向上下扩展的行数
     * @param maxChars        结果最大字符数
     * @return 相关 diff 片段，可能为空串
     */
    public static String extractForNewLineRange(String unifiedDiff, int newLineStart, int newLineEnd,
                                                  int paddingLines, int maxChars) {
        if (unifiedDiff == null || unifiedDiff.isBlank()) {
            return "";
        }
        if (newLineStart <= 0 || newLineEnd <= 0) {
            return truncate(unifiedDiff, maxChars);
        }
        int lo = Math.max(1, newLineStart - Math.max(0, paddingLines));
        int hi = Math.max(lo, newLineEnd + Math.max(0, paddingLines));

        List<String> lines = splitLines(unifiedDiff);
        int firstHunkIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("@@ ")) {
                firstHunkIdx = i;
                break;
            }
        }
        if (firstHunkIdx < 0) {
            return truncate(unifiedDiff, maxChars);
        }

        List<String> prefix = new ArrayList<>();
        if (firstHunkIdx > 0) {
            for (int i = 0; i < firstHunkIdx; i++) {
                prefix.add(lines.get(i));
            }
        }

        StringBuilder out = new StringBuilder();
        boolean anyHunk = false;
        int i = firstHunkIdx >= 0 ? firstHunkIdx : 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            if (!line.startsWith("@@ ")) {
                i++;
                continue;
            }
            Matcher m = HUNK_HEADER.matcher(line);
            if (!m.find()) {
                i++;
                continue;
            }
            int newStart = Integer.parseInt(m.group(3));
            int newCount = m.group(4) != null && !m.group(4).isEmpty()
                    ? Integer.parseInt(m.group(4))
                    : 1;
            int newEnd = newCount == 0 ? newStart - 1 : newStart + newCount - 1;
            boolean overlap = !(newEnd < lo || newStart > hi);
            int hunkStart = i;
            i++;
            while (i < lines.size() && !lines.get(i).startsWith("@@ ")) {
                i++;
            }
            if (overlap) {
                if (!anyHunk && !prefix.isEmpty()) {
                    for (String p : prefix) {
                        out.append(p).append('\n');
                    }
                }
                for (int j = hunkStart; j < i; j++) {
                    out.append(lines.get(j)).append('\n');
                }
                anyHunk = true;
            }
        }
        String result = anyHunk ? out.toString() : unifiedDiff;
        return truncate(result, maxChars);
    }

    private static List<String> splitLines(String s) {
        List<String> out = new ArrayList<>();
        int p = 0;
        while (p <= s.length()) {
            int nl = s.indexOf('\n', p);
            if (nl < 0) {
                out.add(s.substring(p));
                break;
            }
            out.add(s.substring(p, nl));
            p = nl + 1;
        }
        return out;
    }

    private static String truncate(String s, int maxChars) {
        if (s == null) {
            return "";
        }
        if (s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars) + "\n... [truncated]\n";
    }
}
