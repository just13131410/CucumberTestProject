# ============================================================
# Stage 1: Build
# ============================================================
FROM registry.access.redhat.com/ubi9/openjdk-25:latest AS builder

USER root
WORKDIR /build

# Copy pom first for dependency caching (kein Maven Wrapper nötig - mvn ist im Image enthalten)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build (skip tests - they run at runtime)
COPY src ./src
RUN mvn test -B
RUN mvn package -DskipTests -B

# ============================================================
# Stage 2: Runtime – Playwright lädt Chromium zur Laufzeit über den Mirror
# ============================================================
FROM registry.access.redhat.com/ubi9/openjdk-25:latest

USER root

# System-Bibliotheken, die der von Playwright heruntergeladene Chromium zum Starten braucht.
# (Kein Browser-RPM mehr – der Browser kommt zur Laufzeit über den Playwright-Mirror.)
RUN dnf install -y --setopt=install_weak_deps=False \
        nss nspr atk at-spi2-atk at-spi2-core cups-libs libdrm libgbm \
        libxcb libX11 libXcomposite libXdamage libXext libXfixes libXrandr \
        libxkbcommon pango cairo alsa-lib libXtst \
        liberation-fonts && \
    dnf clean all && rm -rf /var/cache/dnf

WORKDIR /app

# Copy the built JAR
COPY --from=builder /build/target/*.jar app.jar

# Verzeichnisse anlegen und Rechte für OpenShift (arbitrary UID, GID 0) setzen.
# /ms-playwright ist der persistente Browser-Cache (PLAYWRIGHT_BROWSERS_PATH).
RUN mkdir -p /app/test-results /ms-playwright && \
    chown -R 1001:0 /app /ms-playwright && \
    chmod -R g=u /app /ms-playwright

USER 1001

# Expose application and actuator ports
EXPOSE 8080 8081

# PVC mount points: test-results + persistenter Playwright-Browser-Cache
VOLUME ["/app/test-results", "/ms-playwright"]

# Container-aware JVM tuning
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -Djava.security.egd=file:/dev/./urandom \
    -Dhttp.proxyHost= \
    -Dhttps.proxyHost="

# Proxy auch für non-Java-Tools (curl, npm, etc.) deaktivieren
ENV no_proxy="*"
ENV NO_PROXY="*"

ENV SPRING_PROFILES_ACTIVE=prod
ENV TEST_RESULTS_PATH=/app/test-results

# Playwright-Browser-Download über den internen Mirror (keine Internet-Verbindung im Pod nötig).
# Diese Defaults können per OpenShift-ConfigMap überschrieben werden.
#   PLAYWRIGHT_DOWNLOAD_HOST  → mirror.enabled/host (Chromium-Host wird intern als
#                               {host}/builds/cft abgeleitet)
#   PLAYWRIGHT_BROWSERS_PATH  → persistenter Cache (PVC-Mount, Reuse über Pod-Restarts)
ENV PLAYWRIGHT_MIRROR_ENABLED=true
ENV PLAYWRIGHT_DOWNLOAD_HOST=http://playwright-mirror:10123
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
ENV PLAYWRIGHT_INSTALL_ON_STARTUP=true

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
