package org.example.cucumber.service;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Entscheidet vor dem Einreihen, ob ein Lauf einen Browser braucht und damit in den kleineren
 * Browser-Pool gehört.
 * <p>
 * Grundlage sind die Pickles des Gherkin-Parsers: Jedes Pickle trägt bereits alle wirksamen Tags
 * (Feature, Rule, Szenario, Examples). Ein Lauf braucht einen Browser, sobald ein Pickle, das zum
 * Tag-Filter des Requests und zur Ziel-Umgebung ({@link EnvironmentTagFilter}) passt, einen der Browser-Tags ({@code test.execution.browser-tags})
 * trägt. Im Zweifel (Features nicht lesbar, unbekannte Feature-Datei) gilt "Browser" - das ist
 * strenger, aber nie falsch.
 */
@Slf4j
class BrowserDemand {

    private final Set<String> browserTags;
    private final FeatureTags featureTags;

    BrowserDemand(String browserTags) {
        this(browserTags, new FeatureTags());
    }

    BrowserDemand(String browserTags, Supplier<Map<String, byte[]>> featureSource) {
        this(browserTags, new FeatureTags(featureSource));
    }

    BrowserDemand(String browserTags, FeatureTags featureTags) {
        this.browserTags = Arrays.stream(browserTags == null ? new String[0] : browserTags.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .map(BrowserDemand::withAt)
                .collect(Collectors.toUnmodifiableSet());
        this.featureTags = featureTags;
    }

    /**
     * @param tags     Tags des Requests (ODER-verknüpft, mit oder ohne {@code @}); leer = alle Szenarien
     * @param features Feature-Dateien des Requests relativ zu {@code features/}, optional mit
     *                 Zeilennummern; leer = alle Feature-Dateien
     * @param environment Ziel-Umgebung für die {@code @env:}-Regel; {@code null} = keine Einschränkung
     */
    boolean needsBrowser(List<String> tags, List<String> features, String environment) {
        if (browserTags.isEmpty()) {
            return false;
        }
        Map<String, List<Set<String>>> parsed = featureTags.byFeature();
        if (parsed.isEmpty()) {
            log.debug("Keine Feature-Dateien lesbar – Lauf wird als Browser-Lauf behandelt");
            return true;
        }
        Set<String> filter = tags == null ? Set.of() : tags.stream()
                .map(BrowserDemand::withAt)
                .collect(Collectors.toSet());

        List<String> files = (features == null || features.isEmpty())
                ? List.copyOf(parsed.keySet())
                : features.stream().map(BrowserDemand::stripLineNumbers).toList();

        for (String file : files) {
            List<Set<String>> pickles = parsed.get(file);
            if (pickles == null) {
                log.debug("Feature-Datei '{}' unbekannt – Lauf wird als Browser-Lauf behandelt", file);
                return true;
            }
            for (Set<String> pickleTags : pickles) {
                boolean selected = (filter.isEmpty() || pickleTags.stream().anyMatch(filter::contains))
                        && EnvironmentTagFilter.selects(pickleTags, environment);
                if (selected && pickleTags.stream().anyMatch(browserTags::contains)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String stripLineNumbers(String feature) {
        int colon = feature.indexOf(':');
        return colon < 0 ? feature : feature.substring(0, colon);
    }

    private static String withAt(String tag) {
        return tag.startsWith("@") ? tag : "@" + tag;
    }
}
