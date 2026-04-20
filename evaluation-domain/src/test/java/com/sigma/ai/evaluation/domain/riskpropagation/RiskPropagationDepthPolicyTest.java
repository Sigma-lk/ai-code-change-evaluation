package com.sigma.ai.evaluation.domain.riskpropagation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RiskPropagationDepthPolicyTest {

    @Test
    void nullMeansMax() {
        assertEquals(30, RiskPropagationDepthPolicy.resolveEffectiveDepth(null));
    }

    @Test
    void clampsAboveCap() {
        assertEquals(30, RiskPropagationDepthPolicy.resolveEffectiveDepth(100));
        assertEquals(30, RiskPropagationDepthPolicy.resolveEffectiveDepth(30));
    }

    @Test
    void positiveBelowCap() {
        assertEquals(5, RiskPropagationDepthPolicy.resolveEffectiveDepth(5));
        assertEquals(1, RiskPropagationDepthPolicy.resolveEffectiveDepth(1));
    }

    @Test
    void invalidFallsBackToMax() {
        assertEquals(30, RiskPropagationDepthPolicy.resolveEffectiveDepth(0));
        assertEquals(30, RiskPropagationDepthPolicy.resolveEffectiveDepth(-3));
    }
}
