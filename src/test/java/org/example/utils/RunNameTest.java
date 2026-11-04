package org.example.utils;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RunNameTest {

    private static final LocalDate DATE = LocalDate.of(2026, 2, 23);
    private static final UUID RUN_ID = UUID.fromString("abc12345-0000-0000-0000-000000000000");

    @Test
    void of_FullName() {
        assertEquals("20260223 [staging] abc12345 @SmokeTest @API-Test",
                RunName.of(DATE, RUN_ID, "staging", List.of("@SmokeTest", "@API-Test")));
    }

    @Test
    void of_WithoutTags() {
        assertEquals("20260223 [dev] abc12345", RunName.of(DATE, RUN_ID, "dev", null));
        assertEquals("20260223 [dev] abc12345", RunName.of(DATE, RUN_ID, "dev", List.of()));
        assertEquals("20260223 [dev] abc12345", RunName.of(DATE, RUN_ID, "dev", Arrays.asList(" ", null)));
    }

    @Test
    void of_BlankEnvironmentIsOmitted() {
        assertEquals("20260223 abc12345 @SmokeTest", RunName.of(DATE, RUN_ID, null, List.of("@SmokeTest")));
        assertEquals("20260223 abc12345 @SmokeTest", RunName.of(DATE, RUN_ID, "  ", List.of("@SmokeTest")));
    }

    @Test
    void of_WithoutDate() {
        assertEquals("[dev] abc12345 @SmokeTest", RunName.of(null, RUN_ID, "dev", List.of("@SmokeTest")));
    }
}
