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

# Download and unpack Allure CLI from Maven Central (kein GitHub-Zugriff nötig)
ARG ALLURE_VERSION=2.32.0
RUN mvn org.apache.maven.plugins:maven-dependency-plugin:3.6.1:unpack \
    -Dartifact=io.qameta.allure:allure-commandline:${ALLURE_VERSION}:zip \
    -DoutputDirectory=/tmp/allure-unpack -B && \
    mv /tmp/allure-unpack/allure-${ALLURE_VERSION} /opt/allure && \
    chmod +x /opt/allure/bin/allure && \
    rm -rf /tmp/allure-unpack

# ============================================================
# Stage 2: Runtime with Playwright + Chromium
# ============================================================
# Gleicher openjdk-21 wie Stage 1 (kein ubi-minimal nötig, Java bereits enthalten)
FROM registry.access.redhat.com/ubi9/openjdk-21:1.18

USER root

# Chromium-Abhängigkeiten installieren (Java ist im Image bereits enthalten)
RUN microdnf install -y \
    nss \
    atk \
    at-spi2-atk \
    cups-libs \
    libdrm \
    libXcomposite \
    libXdamage \
    libXrandr \
    mesa-libgbm \
    pango \
    alsa-lib \
    gtk3 \
    fontconfig \
    freetype \
    && microdnf clean all

# Allure CLI aus dem Builder-Stage kopieren (kein Download zur Laufzeit)
ARG ALLURE_VERSION=2.32.0
COPY --from=builder /opt/allure /opt/allure
RUN chown -R 1001:0 /opt/allure && \
    chmod -R g=u /opt/allure

ENV ALLURE_HOME=/opt/allure
ENV PATH="$PATH:/opt/allure/bin"

WORKDIR /app

# Copy the built JAR
COPY --from=builder /build/target/*.jar app.jar

# Set Playwright browser path
ENV PLAYWRIGHT_BROWSERS_PATH=/app/browsers

# Create directories and set permissions for OpenShift (arbitrary UID, GID 0)
RUN mkdir -p /app/browsers /app/test-results && \
    chown -R 1001:0 /app && \
    chmod -R g=u /app

# Install Playwright Chromium at build time
USER 1001
RUN java -cp app.jar com.microsoft.playwright.CLI install chromium || \
    echo "Playwright browser install completed (or skipped if unavailable)"

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

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
