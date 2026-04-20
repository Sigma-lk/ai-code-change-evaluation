package com.sigma.ai.evaluation.domain.repository.util;

import java.util.Locale;

/**
 * 将 Git 克隆 URL（HTTPS / SSH）规范为可比较的稳定键，用于与 {@code t_repository.clone_url} 对齐。
 */
public final class CloneUrlNormalizer {

    private CloneUrlNormalizer() {
    }

    /**
     * 生成用于比对的规范化键：小写、去尾斜杠、统一 {@code .git} 后缀、将常见 SSH 形式映射为 https 主机路径形式。
     *
     * @param raw 来自 GitHub 的 {@code clone_url}、{@code ssh_url} 或库内登记的 {@code clone_url}
     * @return 规范化键；无法处理时返回 trimmed 小写原串
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return "";
        }
        s = s.toLowerCase(Locale.ROOT);
        if (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        // git@github.com:org/repo.git -> https://github.com/org/repo
        if (s.startsWith("git@")) {
            int at = s.indexOf('@');
            int colon = s.indexOf(':');
            if (at >= 0 && colon > at) {
                String host = s.substring(at + 1, colon);
                String path = s.substring(colon + 1);
                s = "https://" + host + "/" + path;
            }
        }
        if (s.endsWith(".git")) {
            s = s.substring(0, s.length() - 4);
        }
        return s;
    }
}
