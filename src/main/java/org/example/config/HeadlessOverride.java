package org.example.config;

/**
 * Headless-Wert für den aktuellen Lauf.
 * <p>
 * {@link BrowserConfig} bindet {@code browser.headless} einmal beim Start des Spring-Kontexts. Im
 * {@code in-process}-Modus wird dieser Kontext pro JVM gecacht, der erste Lauf würde den Wert also
 * für alle folgenden festlegen. Ein Lauf setzt das Request-Feld {@code headless} als System-Property
 * {@value #PROPERTY} (in-process) bzw. {@code -D} (Kind-JVM); dieser Wert hat deshalb Vorrang.
 */
public final class HeadlessOverride {

    /** System-Property, über die ein Lauf headless setzt ({@code TestExecutionService.runProperties}). */
    public static final String PROPERTY = "browser.headless";

    private HeadlessOverride() {
    }

    /** Headless gemäß Lauf-Override (System-Property {@value #PROPERTY}), sonst {@code configured}. */
    public static boolean current(boolean configured) {
        return resolve(System.getProperty(PROPERTY), configured);
    }

    /**
     * Nur {@code true}/{@code false} (getrimmt, Groß-/Kleinschreibung egal) überschreiben;
     * {@code null}, leer oder ungültig → {@code configured}.
     */
    static boolean resolve(String runOverride, boolean configured) {
        if (runOverride == null) {
            return configured;
        }
        String value = runOverride.trim();
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        return configured;
    }
}
