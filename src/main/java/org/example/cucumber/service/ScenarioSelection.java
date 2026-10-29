package org.example.cucumber.service;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Welche Szenarien ein Lauf auswählt: Request-Tags (ODER-verknüpft), Feature-Dateien und die
 * {@code @env:}-Regel der Ziel-Umgebung - dieselbe Logik, die Cucumber über den Tag-Ausdruck anwendet,
 * hier direkt auf den Tags eines Pickles.
 * <p>
 * Zeilennummern in Feature-Pfaden ({@code X.feature:12}) kennt ein Pickle nicht; Pickles solcher Dateien
 * gelten als ausgewählt, {@link #isLineFiltered(String)} weist auf die Einschränkung hin.
 */
public final class ScenarioSelection {

    public enum Outcome { SELECTED, NOT_SELECTED, ENV_EXCLUDED }

    private final Set<String> tags;
    private final Set<String> files;
    private final Set<String> lineFilteredFiles;
    private final String environment;

    /**
     * @param tags        Tags des Requests (mit oder ohne {@code @}); leer = alle Szenarien
     * @param features    Feature-Dateien relativ zu {@code features/}, optional mit Zeilennummern; leer = alle
     * @param environment Ziel-Umgebung; {@code null} = keine {@code @env:}-Einschränkung
     */
    public ScenarioSelection(List<String> tags, List<String> features, String environment) {
        this.tags = tags == null ? Set.of() : tags.stream()
                .map(t -> t.startsWith("@") ? t : "@" + t)
                .collect(Collectors.toUnmodifiableSet());
        List<String> featureList = features == null ? List.of() : features;
        this.files = featureList.stream()
                .map(ScenarioSelection::stripLineNumbers)
                .collect(Collectors.toUnmodifiableSet());
        this.lineFilteredFiles = featureList.stream()
                .filter(f -> f.indexOf(':') >= 0)
                .map(ScenarioSelection::stripLineNumbers)
                .collect(Collectors.toUnmodifiableSet());
        this.environment = environment;
    }

    public Outcome classify(String file, Collection<String> pickleTags) {
        if (!EnvironmentTagFilter.selects(pickleTags, environment)) {
            return Outcome.ENV_EXCLUDED;
        }
        if (!files.isEmpty() && !files.contains(file)) {
            return Outcome.NOT_SELECTED;
        }
        if (!tags.isEmpty() && pickleTags.stream().noneMatch(tags::contains)) {
            return Outcome.NOT_SELECTED;
        }
        return Outcome.SELECTED;
    }

    /** Die Datei wurde nur mit Zeilennummern ausgewählt, nicht jedes ihrer Pickles lief also zwingend. */
    public boolean isLineFiltered(String file) {
        return lineFilteredFiles.contains(file);
    }

    /** Ausgewählte Feature-Dateien ohne Zeilennummern; leer = alle. */
    Set<String> files() {
        return files;
    }

    private static String stripLineNumbers(String feature) {
        int colon = feature.indexOf(':');
        return colon < 0 ? feature : feature.substring(0, colon);
    }
}
