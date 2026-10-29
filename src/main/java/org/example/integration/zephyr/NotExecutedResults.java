package org.example.integration.zephyr;

import org.example.cucumber.model.TestExecutionRequest;
import org.example.cucumber.service.EnvironmentTagFilter;
import org.example.cucumber.service.FeatureTags;
import org.example.cucumber.service.ScenarioSelection;
import org.example.cucumber.service.ScenarioSelection.Outcome;
import org.example.integration.model.ZephyrTestExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Ergebnisse für Testfälle, die im Zephyr-Testrun stehen (aus dem Template geklont), aber kein Ergebnis
 * aus Cucumber bekommen haben - sonst blieben sie in Zephyr leer. Der Grund wird aus den Feature-Dateien
 * und dem Request abgeleitet und als Kommentar mitgegeben:
 * <ul>
 *   <li>alle Szenarien des Testfalls per {@code @env:} für andere Umgebungen → {@code zephyr.env-excluded-status}</li>
 *   <li>nicht über Tags/Features ausgewählt, ausgewählt ohne Ergebnis oder kein Szenario vorhanden →
 *       {@code zephyr.not-executed-status}</li>
 * </ul>
 */
@Component
public class NotExecutedResults {

    static final String DEFAULT_STATUS = "Not Executed";

    private final FeatureTags featureTags;
    private final String notExecutedStatus;
    private final String envExcludedStatus;

    @Autowired
    public NotExecutedResults(@Value("${zephyr.not-executed-status:" + DEFAULT_STATUS + "}") String notExecutedStatus,
                              @Value("${zephyr.env-excluded-status:" + DEFAULT_STATUS + "}") String envExcludedStatus) {
        this(new FeatureTags(), notExecutedStatus, envExcludedStatus);
    }

    public NotExecutedResults(FeatureTags featureTags, String notExecutedStatus, String envExcludedStatus) {
        this.featureTags = featureTags;
        this.notExecutedStatus = orDefault(notExecutedStatus);
        this.envExcludedStatus = orDefault(envExcludedStatus);
    }

    /**
     * @param plannedKeys  Testfall-Keys im Zephyr-Testrun (Template)
     * @param reportedKeys Keys, für die bereits ein Ergebnis hochgeladen wird
     */
    public List<ZephyrTestExecution> build(List<String> plannedKeys, Collection<String> reportedKeys,
                                           TestExecutionRequest request) {
        Set<String> missing = new LinkedHashSet<>(plannedKeys);
        missing.removeAll(reportedKeys);
        if (missing.isEmpty()) {
            return List.of();
        }
        ScenarioSelection selection = new ScenarioSelection(
                request.getTags(), request.getFeatures(), request.getEnvironment());
        Map<String, List<Pickle>> picklesByKey = picklesByKey();

        List<ZephyrTestExecution> results = new ArrayList<>();
        for (String key : missing) {
            results.add(resultFor(key, picklesByKey.getOrDefault(key, List.of()), selection, request));
        }
        return results;
    }

    private ZephyrTestExecution resultFor(String key, List<Pickle> pickles, ScenarioSelection selection,
                                          TestExecutionRequest request) {
        if (pickles.isEmpty()) {
            return result(key, notExecutedStatus, "Kein automatisiertes Szenario mit Tag @" + key + " vorhanden");
        }
        Set<Outcome> outcomes = new LinkedHashSet<>();
        Set<String> allowedEnvironments = new TreeSet<>();
        for (Pickle pickle : pickles) {
            Outcome outcome = selection.classify(pickle.file(), pickle.tags());
            if (outcome == Outcome.SELECTED && selection.isLineFiltered(pickle.file())) {
                outcome = Outcome.NOT_SELECTED;
            }
            outcomes.add(outcome);
            allowedEnvironments.addAll(EnvironmentTagFilter.environmentsOf(pickle.tags()));
        }
        if (outcomes.contains(Outcome.SELECTED)) {
            return result(key, notExecutedStatus, "Szenario war ausgewählt, hat aber kein Ergebnis geliefert "
                    + "(Lauf abgebrochen oder vorher fehlgeschlagen?)");
        }
        if (outcomes.contains(Outcome.NOT_SELECTED)) {
            return result(key, notExecutedStatus, "Nicht im Lauf ausgewählt (" + describeFilter(request) + ")");
        }
        return result(key, envExcludedStatus, "Nicht für Umgebung <b>" + request.getEnvironment()
                + "</b> freigegeben (nur: " + String.join(", ", allowedEnvironments) + ")");
    }

    private Map<String, List<Pickle>> picklesByKey() {
        Map<String, List<Pickle>> result = new LinkedHashMap<>();
        featureTags.byFeature().forEach((file, pickles) -> pickles.forEach(tags -> {
            String key = CucumberResultReader.testCaseKeyOf(tags);
            if (key != null) {
                result.computeIfAbsent(key, k -> new ArrayList<>()).add(new Pickle(file, tags));
            }
        }));
        return result;
    }

    private static String describeFilter(TestExecutionRequest request) {
        List<String> parts = new ArrayList<>();
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            parts.add("Tags: " + String.join(" ", request.getTags()));
        }
        if (request.getFeatures() != null && !request.getFeatures().isEmpty()) {
            parts.add("Features: " + String.join(", ", request.getFeatures()));
        }
        return String.join("; ", parts);
    }

    private static ZephyrTestExecution result(String key, String status, String reason) {
        return ZephyrTestExecution.builder()
                .testCaseKey(key)
                .status(status)
                .comment("Nicht ausgeführt: " + reason)
                .build();
    }

    private static String orDefault(String status) {
        return status == null || status.isBlank() ? DEFAULT_STATUS : status.strip();
    }

    private record Pickle(String file, List<String> tags) {
    }
}
