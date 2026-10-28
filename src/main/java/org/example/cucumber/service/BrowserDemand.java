package org.example.cucumber.service;

import io.cucumber.gherkin.GherkinParser;
import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.Pickle;
import io.cucumber.messages.types.PickleTag;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Entscheidet vor dem Einreihen, ob ein Lauf einen Browser braucht und damit in den kleineren
 * Browser-Pool gehört.
 * <p>
 * Grundlage sind die Pickles des Gherkin-Parsers: Jedes Pickle trägt bereits alle wirksamen Tags
 * (Feature, Rule, Szenario, Examples). Ein Lauf braucht einen Browser, sobald ein Pickle, das zum
 * Tag-Filter des Requests passt, einen der Browser-Tags ({@code test.execution.browser-tags})
 * trägt. Im Zweifel (Features nicht lesbar, unbekannte Feature-Datei) gilt "Browser" - das ist
 * strenger, aber nie falsch.
 */
@Slf4j
class BrowserDemand {

    private final Set<String> browserTags;
    private final Supplier<Map<String, byte[]>> featureSource;
    /** Lazy geparst: Feature-Datei (relativ zu features/) → wirksame Tags je Pickle. */
    private volatile Map<String, List<Set<String>>> pickleTagsByFeature;

    BrowserDemand(String browserTags) {
        this(browserTags, BrowserDemand::loadClasspathFeatures);
    }

    BrowserDemand(String browserTags, Supplier<Map<String, byte[]>> featureSource) {
        this.browserTags = Arrays.stream(browserTags == null ? new String[0] : browserTags.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .map(BrowserDemand::withAt)
                .collect(Collectors.toUnmodifiableSet());
        this.featureSource = featureSource;
    }

    /**
     * @param tags     Tags des Requests (ODER-verknüpft, mit oder ohne {@code @}); leer = alle Szenarien
     * @param features Feature-Dateien des Requests relativ zu {@code features/}, optional mit
     *                 Zeilennummern; leer = alle Feature-Dateien
     */
    boolean needsBrowser(List<String> tags, List<String> features) {
        if (browserTags.isEmpty()) {
            return false;
        }
        Map<String, List<Set<String>>> parsed = pickleTagsByFeature();
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
                boolean selected = filter.isEmpty() || pickleTags.stream().anyMatch(filter::contains);
                if (selected && pickleTags.stream().anyMatch(browserTags::contains)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Map<String, List<Set<String>>> pickleTagsByFeature() {
        Map<String, List<Set<String>>> result = pickleTagsByFeature;
        if (result == null) {
            result = parse(featureSource.get());
            pickleTagsByFeature = result;
        }
        return result;
    }

    static Map<String, List<Set<String>>> parse(Map<String, byte[]> sources) {
        GherkinParser parser = GherkinParser.builder()
                .includeSource(false)
                .includeGherkinDocument(false)
                .includePickles(true)
                .build();
        Map<String, List<Set<String>>> result = new HashMap<>();
        sources.forEach((file, content) -> result.put(file, parser.parse(file, content)
                .map(Envelope::getPickle)
                .flatMap(Optional::stream)
                .map(BrowserDemand::tagsOf)
                .toList()));
        return result;
    }

    private static Set<String> tagsOf(Pickle pickle) {
        return pickle.getTags().stream().map(PickleTag::getName).collect(Collectors.toUnmodifiableSet());
    }

    /** Alle {@code *.feature} unter {@code classpath:features}; leer, wenn nicht als Verzeichnis lesbar. */
    private static Map<String, byte[]> loadClasspathFeatures() {
        try {
            java.net.URL url = BrowserDemand.class.getClassLoader().getResource("features");
            if (url == null) {
                return Map.of();
            }
            Path root = Path.of(url.toURI());
            try (Stream<Path> walk = Files.walk(root)) {
                Map<String, byte[]> result = new HashMap<>();
                for (Path p : walk.filter(p -> p.toString().endsWith(".feature")).toList()) {
                    result.put(root.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("Feature-Dateien für die Browser-Klassifizierung nicht lesbar: {}", e.getMessage());
            return Map.of();
        }
    }

    private static String stripLineNumbers(String feature) {
        int colon = feature.indexOf(':');
        return colon < 0 ? feature : feature.substring(0, colon);
    }

    private static String withAt(String tag) {
        return tag.startsWith("@") ? tag : "@" + tag;
    }
}
