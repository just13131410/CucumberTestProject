# Runtime-Image für die k3s/WireMock-Integrationsprüfung.
# WICHTIG – exploded statt Fat-JAR: Cucumber wirft beim Scannen von classpath:features
# aus einer verschachtelten Spring-Boot-Jar `FileSystemAlreadyExistsException`. Entpackt
# als echtes Verzeichnis (BOOT-INF/classes/features) funktioniert das Scanning.
#
# Build-Kontext muss die gebaute Fat-JAR als `app.jar` enthalten (das Repo-.dockerignore
# schließt target/ aus – daher separater Kontext, siehe README Schritt 2).
FROM registry.access.redhat.com/ubi9/openjdk-25:latest
USER root
WORKDIR /app
COPY app.jar /app/app.jar
RUN jar xf app.jar && rm app.jar && chmod -R g=u /app
USER 185
EXPOSE 8080 8081
# Start-Class aus dem Jar-Manifest (org.example.App).
ENTRYPOINT ["java", "-cp", "/app/BOOT-INF/classes:/app/BOOT-INF/lib/*", "org.example.App"]
