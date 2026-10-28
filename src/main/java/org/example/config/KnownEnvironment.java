package org.example.config;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Die Ziel-Umgebung muss in {@code test.environments} (Env {@code TEST_ENVIRONMENTS}) aufgeführt
 * sein. {@code null} gilt als gültig, Pflichtfeld-Prüfung übernimmt {@code @NotBlank}.
 */
@Documented
@Constraint(validatedBy = KnownEnvironmentValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface KnownEnvironment {

    String message() default "Unbekannte Ziel-Umgebung";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
