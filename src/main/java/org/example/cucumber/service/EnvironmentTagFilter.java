package org.example.cucumber.service;

import lombok.extern.slf4j.Slf4j;
import org.example.config.KnownEnvironmentValidator;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Beschränkt Szenarien per Tag {@code @env:<umgebung>} auf bestimmte Ziel-Umgebungen.
 * <p>
 * Regel: Ein Pickle ohne {@code @env:}-Tag läuft überall; eines mit {@code @env:}-Tags nur, wenn die
 * Ziel-Umgebung darunter ist. Umgesetzt als Cucumber-Tag-Ausdruck, der mit dem Tag-Filter des Requests
 * UND-verknüpft wird - ausgefilterte Szenarien tauchen so weder in Reports noch in Zephyr auf.
 * <p>
 * Cucumber kennt keine Tag-Wildcards, deshalb werden alle anderen Umgebungen explizit ausgeschlossen:
 * konfigurierte ({@code test.environments}) plus alle in den Features genannten. Letzteres sorgt dafür,
 * dass ein Tippfehler ({@code @env:stagign}) das Szenario nirgends laufen lässt statt überall; zusätzlich
 * wird er als Warnung geloggt.
 */
@Slf4j
class EnvironmentTagFilter {

    static final String PREFIX = "@env:";

    private final FeatureTags featureTags;
    private final Supplier<Collection<String>> configuredEnvironments;

    EnvironmentTagFilter(FeatureTags featureTags) {
        this(featureTags, KnownEnvironmentValidator::allowedEnvironments);
    }

    EnvironmentTagFilter(FeatureTags featureTags, Supplier<Collection<String>> configuredEnvironments) {
        this.featureTags = featureTags;
        this.configuredEnvironments = configuredEnvironments;
    }

    /** Tag-Ausdruck für die Ziel-Umgebung oder {@code null}, wenn nichts auszuschließen ist. */
    String expressionFor(String environment) {
        if (environment == null || environment.isBlank()) {
            return null;
        }
        Collection<String> configured = configuredEnvironments.get();
        Set<String> declared = featureTags.declaredEnvironments();
        Set<String> unknown = new TreeSet<>(declared);
        unknown.removeAll(configured);
        if (!unknown.isEmpty()) {
            log.warn("Feature-Dateien nennen unbekannte Umgebungen {} (test.environments={}) - diese "
                    + "Szenarien laufen in keiner konfigurierten Umgebung", unknown, configured);
        }
        Set<String> all = new TreeSet<>(configured);
        all.addAll(declared);
        return expression(environment, all);
    }

    static String expression(String environment, Collection<String> knownEnvironments) {
        String others = knownEnvironments.stream()
                .filter(e -> !e.equals(environment))
                .distinct()
                .sorted()
                .map(e -> PREFIX + e)
                .collect(Collectors.joining(" or "));
        if (others.isEmpty()) {
            return null;
        }
        return PREFIX + environment + " or not (" + others + ")";
    }

    /** UND-Verknüpfung zweier optionaler Tag-Ausdrücke. */
    static String and(String left, String right) {
        if (left == null || left.isBlank()) return right;
        if (right == null || right.isBlank()) return left;
        return "(" + left + ") and (" + right + ")";
    }

    /** Dieselbe Regel wie {@link #expression} direkt auf den Tags eines Pickles. */
    static boolean selects(Set<String> pickleTags, String environment) {
        if (environment == null || environment.isBlank()) {
            return true;
        }
        Set<String> envs = environmentsOf(pickleTags);
        return envs.isEmpty() || envs.contains(environment);
    }

    static Set<String> environmentsOf(Set<String> tags) {
        return tags.stream()
                .filter(t -> t.startsWith(PREFIX) && t.length() > PREFIX.length())
                .map(t -> t.substring(PREFIX.length()))
                .collect(Collectors.toSet());
    }
}
