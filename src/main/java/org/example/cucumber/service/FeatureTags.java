package org.example.cucumber.service;

import io.cucumber.gherkin.GherkinParser;
import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.Pickle;
import io.cucumber.messages.types.PickleTag;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Wirksame Tags aller Pickles je Feature-Datei (Feature, Rule, Szenario, Examples bereits vererbt),
 * lazy geparst und danach gecacht. Gemeinsame Grundlage für {@link BrowserDemand} und
 * {@link EnvironmentTagFilter} sowie für die Zephyr-Markierung nicht ausgeführter Testfälle.
 * Die Tags je Pickle behalten die Gherkin-Reihenfolge (Feature, Rule, Szenario, Examples).
 */
@Slf4j
public class FeatureTags {

    private final Supplier<Map<String, byte[]>> featureSource;
    /** Feature-Datei (relativ zu features/) → wirksame Tags je Pickle. */
    private volatile Map<String, List<List<String>>> pickleTagsByFeature;

    public FeatureTags() {
        this(FeatureTags::loadClasspathFeatures);
    }

    public FeatureTags(Supplier<Map<String, byte[]>> featureSource) {
        this.featureSource = featureSource;
    }

    /** Leer, wenn keine Feature-Datei lesbar ist. */
    public Map<String, List<List<String>>> byFeature() {
        Map<String, List<List<String>>> result = pickleTagsByFeature;
        if (result == null) {
            result = parse(featureSource.get());
            pickleTagsByFeature = result;
        }
        return result;
    }

    /** Alle in den Features per {@code @env:<name>} genannten Umgebungen, sortiert. */
    Set<String> declaredEnvironments() {
        return byFeature().values().stream()
                .flatMap(List::stream)
                .flatMap(tags -> EnvironmentTagFilter.environmentsOf(tags).stream())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    static Map<String, List<List<String>>> parse(Map<String, byte[]> sources) {
        GherkinParser parser = GherkinParser.builder()
                .includeSource(false)
                .includeGherkinDocument(false)
                .includePickles(true)
                .build();
        Map<String, List<List<String>>> result = new HashMap<>();
        sources.forEach((file, content) -> result.put(file, parser.parse(file, content)
                .map(Envelope::getPickle)
                .flatMap(Optional::stream)
                .map(FeatureTags::tagsOf)
                .toList()));
        return result;
    }

    private static List<String> tagsOf(Pickle pickle) {
        return pickle.getTags().stream().map(PickleTag::getName).distinct().toList();
    }

    /** Alle {@code *.feature} unter {@code classpath:features}; leer, wenn nicht als Verzeichnis lesbar. */
    static Map<String, byte[]> loadClasspathFeatures() {
        try {
            java.net.URL url = FeatureTags.class.getClassLoader().getResource("features");
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
            log.warn("Feature-Dateien für die Tag-Auswertung nicht lesbar: {}", e.getMessage());
            return Map.of();
        }
    }
}
