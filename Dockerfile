# ============================================================
# Stage 1: Build
# ============================================================
FROM registry.access.redhat.com/ubi9/openjdk-21:1.18 AS builder

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
# Stage 2: Runtime with Playwright + Chromium
# ============================================================
# Gleicher openjdk-21 wie Stage 1 (kein ubi-minimal nötig, Java bereits enthalten)
FROM registry.access.redhat.com/ubi9/openjdk-21:1.18

USER root

# Chrome RPM aus Artifactory installieren (kein package repository benötigt)
# Übergabe per Build-Arg: docker build --build-arg CHROME_RPM_URL=https://artifactory.../chrome.rpm
ARG CHROME_RPM_URL
RUN curl -L "${CHROME_RPM_URL}" -o /tmp/chrome.rpm && \
    rpm -ivh --nodeps /tmp/chrome.rpm && \
    rm /tmp/chrome.rpm

WORKDIR /app

# Copy the built JAR
COPY --from=builder /build/target/*.jar app.jar

# Create directories and set permissions for OpenShift (arbitrary UID, GID 0)
RUN mkdir -p /app/test-results && \
    chown -R 1001:0 /app && \
    chmod -R g=u /app

USER 1001

# Expose application and actuator ports
EXPOSE 8080 8081

# PVC mount point for test results
VOLUME ["/app/test-results"]

# Container-aware JVM tuning
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -Djava.security.egd=file:/dev/./urandom"

ENV SPRING_PROFILES_ACTIVE=prod
ENV TEST_RESULTS_PATH=/app/test-results

# Browser aus dem Artifactory (z.B. per RPM installiert, kein Playwright-eigener Download)
# Spring Relaxed Binding: BROWSER_EXECUTABLE_PATH → browser.executable.path
# Pfad auf den tatsächlichen Installationspfad des Browsers aus dem Artifactory anpassen.
ENV BROWSER_EXECUTABLE_PATH=/usr/bin/google-chrome-stable

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
