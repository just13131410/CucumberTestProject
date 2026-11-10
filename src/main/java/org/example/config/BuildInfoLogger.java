package org.example.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Properties;

/**
 * Loggt beim Start, aus welchem Build die laufende App stammt (Version, Build-Zeitpunkt, Git-Commit).
 * Damit lässt sich im Pod-Log prüfen, ob ein Fix schon im deployten Image steckt.
 * <p>
 * Quellen: {@code META-INF/build-info.properties} (spring-boot-maven-plugin, Goal {@code build-info})
 * und {@code git.properties} (git-commit-id-maven-plugin). Fehlt eine Datei, etwa {@code git.properties}
 * beim Docker-Build ohne {@code .git}, steht dort {@value #UNKNOWN}.
 */
@Component
public class BuildInfoLogger {

    private static final Logger log = LoggerFactory.getLogger(BuildInfoLogger.class);

    static final String BUILD_INFO_RESOURCE = "META-INF/build-info.properties";
    static final String GIT_INFO_RESOURCE = "git.properties";
    static final String UNKNOWN = "unbekannt";

    record BuildInfo(String version, String buildTime, String commit, String branch,
                     String commitTime, String dirty) {

        String toLogLine() {
            return "Build-Info: version=" + version + ", gebaut=" + buildTime + ", commit=" + commit
                    + " (branch=" + branch + ", commit-zeit=" + commitTime + ", dirty=" + dirty + ")";
        }
    }

    @EventListener(ApplicationStartedEvent.class)
    public void logBuildInfo() {
        log.info(read(BuildInfoLogger.class.getClassLoader()).toLogLine());
    }

    /** Liest die Build-Infos aus dem Classpath. Package-private für Unit-Tests. */
    static BuildInfo read(ClassLoader classLoader) {
        Properties build = load(classLoader, BUILD_INFO_RESOURCE);
        Properties git = load(classLoader, GIT_INFO_RESOURCE);
        return new BuildInfo(
                value(build, "build.version"),
                value(build, "build.time"),
                value(git, "git.commit.id.abbrev"),
                value(git, "git.branch"),
                value(git, "git.commit.time"),
                value(git, "git.dirty"));
    }

    private static Properties load(ClassLoader classLoader, String resource) {
        Properties props = new Properties();
        try (InputStream is = classLoader.getResourceAsStream(resource)) {
            if (is != null) {
                props.load(is);
            }
        } catch (Exception e) {
            log.warn("Fehler beim Lesen von {}: {}", resource, e.getMessage());
        }
        return props;
    }

    private static String value(Properties props, String key) {
        String value = props.getProperty(key);
        return value == null || value.isBlank() ? UNKNOWN : value.strip();
    }
}
