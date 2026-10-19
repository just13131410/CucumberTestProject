package org.example.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestEnvironmentsTest {

    @Test
    void names_TrimmedAndDeduplicated() {
        TestEnvironments environments = new TestEnvironments(" dev, int-a ,abn,dev,");

        assertEquals(List.of("dev", "int-a", "abn"), environments.names());
    }

    @Test
    void isKnown_OnlyConfiguredNamesCaseSensitive() {
        TestEnvironments environments = new TestEnvironments("dev,feature");

        assertTrue(environments.isKnown("feature"));
        assertFalse(environments.isKnown("Feature"));
        assertFalse(environments.isKnown("test"));
        assertFalse(environments.isKnown(null));
    }

    @Test
    void constructor_EmptyList_Throws() {
        assertThrows(IllegalStateException.class, () -> new TestEnvironments(" , "));
    }
}
