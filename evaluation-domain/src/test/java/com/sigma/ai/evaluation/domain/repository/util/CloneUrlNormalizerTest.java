package com.sigma.ai.evaluation.domain.repository.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CloneUrlNormalizerTest {

    @Test
    void normalize_https_gitSuffix_case() {
        assertEquals("https://github.com/org/repo",
                CloneUrlNormalizer.normalize("https://github.com/Org/Repo.git/"));
    }

    @Test
    void normalize_ssh_toComparable() {
        assertEquals("https://github.com/org/repo",
                CloneUrlNormalizer.normalize("git@github.com:org/repo.git"));
    }
}
