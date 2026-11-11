package org.example.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeadlessOverrideTest {

    @Test
    void resolve_NullOverride_UsesConfigured() {
        assertTrue(HeadlessOverride.resolve(null, true));
        assertFalse(HeadlessOverride.resolve(null, false));
    }

    @Test
    void resolve_BlankOverride_UsesConfigured() {
        assertFalse(HeadlessOverride.resolve("  ", false));
        assertTrue(HeadlessOverride.resolve("", true));
    }

    @Test
    void resolve_OverrideWins() {
        assertFalse(HeadlessOverride.resolve("false", true));
        assertTrue(HeadlessOverride.resolve(" TRUE ", false));
    }

    @Test
    void resolve_InvalidOverride_UsesConfigured() {
        assertTrue(HeadlessOverride.resolve("yes", true));
        assertFalse(HeadlessOverride.resolve("yes", false));
    }
}
