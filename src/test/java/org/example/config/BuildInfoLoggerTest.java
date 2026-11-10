package org.example.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildInfoLoggerTest {

    @Test
    void read_BothFilesPresent_ReturnsAllValues(@TempDir Path dir) throws Exception {
        write(dir, BuildInfoLogger.BUILD_INFO_RESOURCE, "build.version=1.2.3\nbuild.time=2026-11-10T12\\:00\\:00Z");
        write(dir, BuildInfoLogger.GIT_INFO_RESOURCE,
                "git.commit.id.abbrev=abc1234\ngit.branch=main\ngit.commit.time=2026-11-10T11:59:00+0100\ngit.dirty=false");

        BuildInfoLogger.BuildInfo info = read(dir);

        assertEquals("1.2.3", info.version());
        assertEquals("2026-11-10T12:00:00Z", info.buildTime());
        assertEquals("abc1234", info.commit());
        assertEquals("main", info.branch());
        assertEquals("2026-11-10T11:59:00+0100", info.commitTime());
        assertEquals("false", info.dirty());
    }

    @Test
    void read_GitPropertiesMissing_CommitUnknownButBuildTimeSet(@TempDir Path dir) throws Exception {
        // Docker-Build ohne .git: nur build-info.properties vorhanden
        write(dir, BuildInfoLogger.BUILD_INFO_RESOURCE, "build.version=1.2.3\nbuild.time=2026-11-10T12\\:00\\:00Z");

        BuildInfoLogger.BuildInfo info = read(dir);

        assertEquals("2026-11-10T12:00:00Z", info.buildTime());
        assertEquals(BuildInfoLogger.UNKNOWN, info.commit());
        assertEquals(BuildInfoLogger.UNKNOWN, info.branch());
    }

    @Test
    void read_NoFiles_AllUnknown(@TempDir Path dir) throws Exception {
        BuildInfoLogger.BuildInfo info = read(dir);

        assertEquals(BuildInfoLogger.UNKNOWN, info.version());
        assertEquals(BuildInfoLogger.UNKNOWN, info.buildTime());
        assertEquals(BuildInfoLogger.UNKNOWN, info.commit());
    }

    @Test
    void read_BlankValue_TreatedAsUnknown(@TempDir Path dir) throws Exception {
        write(dir, BuildInfoLogger.GIT_INFO_RESOURCE, "git.commit.id.abbrev=   \ngit.branch= main ");

        BuildInfoLogger.BuildInfo info = read(dir);

        assertEquals(BuildInfoLogger.UNKNOWN, info.commit());
        assertEquals("main", info.branch());
    }

    @Test
    void toLogLine_ContainsSearchableMarkerAndValues() {
        var info = new BuildInfoLogger.BuildInfo("1.2.3", "2026-11-10T12:00:00Z", "abc1234", "main",
                "2026-11-10T11:59:00+0100", "false");

        String line = info.toLogLine();

        assertTrue(line.startsWith("Build-Info: "));
        assertTrue(line.contains("gebaut=2026-11-10T12:00:00Z"));
        assertTrue(line.contains("commit=abc1234"));
    }

    private static BuildInfoLogger.BuildInfo read(Path dir) throws Exception {
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, null)) {
            return BuildInfoLogger.read(classLoader);
        }
    }

    private static void write(Path dir, String resource, String content) throws Exception {
        Path file = dir.resolve(resource);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
