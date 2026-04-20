package com.sigma.ai.evaluation.domain.changeevidence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifiedDiffRelevanceExtractorTest {

    @Test
    void extract_includesOverlappingHunk() {
        String diff = """
                diff --git a/Foo.java b/Foo.java
                --- a/Foo.java
                +++ b/Foo.java
                @@ -1,3 +1,4 @@
                 class Foo {
                +  int x;
                   void bar() {}
                 }
                """.stripIndent();
        String got = UnifiedDiffRelevanceExtractor.extractForNewLineRange(diff, 3, 3, 0, 10_000);
        assertTrue(got.contains("@@"));
        assertTrue(got.contains("+  int x"));
    }
}
