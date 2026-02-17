# ============================================================
# Stage 1: Build
# ============================================================
FROM registry.access.redhat.com/ubi9/openjdk-21:1.18 AS builder

USER root
WORKDIR /build

# Copy Maven wrapper and pom first for dependency caching
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source and build (skip tests - they run at runtime)
COPY src ./src
RUN ./mvnw package -DskipTests -B

# ============================================================
# Stage 2: Runtime with Playwright + Chromium
# ============================================================
FROM registry.access.redhat.com/ubi9/ubi-minimal:9.3

USER root

# Install JRE 21 and Chromium system dependencies
RUN microdnf install -y \
    java-21-openjdk-headless \
    # Chromium dependencies
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
    wget \
    fontconfig \
    freetype \
    && microdnf clean all

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
