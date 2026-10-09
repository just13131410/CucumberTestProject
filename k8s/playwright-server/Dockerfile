# Playwright-Server als Sidecar für den cucumber-test-service (Remote-Browser per WebSocket).
#
# Basis ist das offizielle Node-Image mit vorinstallierten Browsern (/ms-playwright). Das npm-
# Paket `playwright` wird zur BUILD-Zeit installiert – zur Laufzeit braucht der Container damit
# keinerlei ausgehende Verbindungen (kein `npx -y`, kein Browser-Download), was restriktiven
# NetworkPolicies entgegenkommt. Für die Firma: dieses Image einmalig in die interne Registry
# spiegeln bzw. dort bauen.
#
# WICHTIG: Version muss zur Maven-Dependency com.microsoft.playwright:playwright (pom.xml)
# passen (Major.Minor), sonst lehnt der Server die Verbindung mit einem Protokollfehler ab.
ARG PLAYWRIGHT_VERSION=1.58.0
FROM mcr.microsoft.com/playwright:v${PLAYWRIGHT_VERSION}-noble
ARG PLAYWRIGHT_VERSION

# dumb-init als PID 1: reapt die vom Server gestarteten Browser-Prozesse (Pendant zu `docker run --init`).
RUN apt-get update \
    && apt-get install -y --no-install-recommends dumb-init \
    && rm -rf /var/lib/apt/lists/* \
    && PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 npm install -g playwright@${PLAYWRIGHT_VERSION} \
    && npm cache clean --force

# HOME=/tmp: Browser-Profile landen in einem beschreibbaren Verzeichnis (auch bei beliebiger UID, OpenShift).
ENV HOME=/tmp
USER pwuser
EXPOSE 3000

# Standard: nur pod-intern lauschen (Sidecar, Zugriff via localhost). Für einen separaten
# Service per args "--host 0.0.0.0" überschreiben.
ENTRYPOINT ["dumb-init", "--", "playwright", "run-server"]
CMD ["--port", "3000", "--host", "127.0.0.1"]
