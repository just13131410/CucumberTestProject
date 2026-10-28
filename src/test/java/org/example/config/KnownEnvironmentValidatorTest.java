package org.example.config;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class KnownEnvironmentValidatorTest {

    private final KnownEnvironmentValidator validator = new KnownEnvironmentValidator();

    @AfterEach
    void cleanup() {
        System.clearProperty(KnownEnvironmentValidator.ENVIRONMENTS_KEY);
    }

    @Test
    void allowedEnvironments_DefaultsFromConfig() {
        assertEquals(List.of("dev", "staging", "prod", "performance"),
                KnownEnvironmentValidator.allowedEnvironments());
    }

    @Test
    void allowedEnvironments_ConfiguredListIsTrimmedAndSkipsEmptyEntries() {
        System.setProperty(KnownEnvironmentValidator.ENVIRONMENTS_KEY, " dev , feature,, test ");

        assertEquals(List.of("dev", "feature", "test"), KnownEnvironmentValidator.allowedEnvironments());
    }

    @Test
    void isValid_NullIsValid() {
        assertTrue(validator.isValid(null, mock(ConstraintValidatorContext.class)));
    }

    @Test
    void isValid_ConfiguredEnvironment_IsValid() {
        System.setProperty(KnownEnvironmentValidator.ENVIRONMENTS_KEY, "dev,feature");

        assertTrue(validator.isValid("feature", mock(ConstraintValidatorContext.class)));
    }

    @Test
    void isValid_UnknownEnvironment_IsInvalidAndNamesAllowedValues() {
        System.setProperty(KnownEnvironmentValidator.ENVIRONMENTS_KEY, "dev,feature");
        ConstraintValidatorContext context = mock(ConstraintValidatorContext.class);
        ConstraintValidatorContext.ConstraintViolationBuilder builder =
                mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);

        assertFalse(validator.isValid("staging", context));

        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(
                "Environment muss eine der konfigurierten Umgebungen sein: dev, feature");
        verify(builder).addConstraintViolation();
    }

    @Test
    void isValid_IsCaseSensitive() {
        ConstraintValidatorContext context = mock(ConstraintValidatorContext.class);
        when(context.buildConstraintViolationWithTemplate(anyString()))
                .thenReturn(mock(ConstraintValidatorContext.ConstraintViolationBuilder.class));

        assertFalse(validator.isValid("DEV", context));
    }
}
