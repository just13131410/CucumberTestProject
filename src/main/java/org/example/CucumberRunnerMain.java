package org.example;

/**
 * Einstiegspunkt für die Out-of-Process-Ausführung eines Cucumber-Laufs in einer eigenen Kind-JVM.
 * {@link CucumberRunnerService} startet diese Klasse per {@code ProcessBuilder}, sodass ein Lauf ein
 * eigenes cgroup/Heap/Metaspace bekommt: Ein OOM oder Absturz eines Laufs kann den langlebigen
 * Web-Service-Pod nicht mehr mitreißen, und Native-/Metaspace-Speicher akkumuliert nicht im Server.
 *
 * <p>Argumente (positionsbasiert): {@code <runId> <tags|"null"> [features|"null"]}. Der Tag-Ausdruck
 * ist bereits normalisiert. Der Prozess beendet sich mit dem Cucumber-Exit-Code (0 = alle bestanden).
 * Ausgabeverzeichnisse werden über {@code TEST_RESULTS_PATH}/{@code -Dtest.results.path} aufgelöst
 * (vom Elternprozess durchgereicht).
 */
public final class CucumberRunnerMain {

    private CucumberRunnerMain() {}

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: CucumberRunnerMain <runId> <tags|null> [features|null]");
            System.exit(2);
        }
        String runId = args[0];
        String tags = (args.length > 1 && !"null".equals(args[1])) ? args[1] : null;
        String features = (args.length > 2 && !"null".equals(args[2])) ? args[2] : null;

        int exitCode;
        try {
            exitCode = CucumberInvoker.invoke(runId, tags, features);
        } catch (Exception e) {
            System.err.println("Cucumber run failed for runId=" + runId + ": " + e.getMessage());
            e.printStackTrace();
            exitCode = 1;
        }
        System.exit(exitCode);
    }
}
