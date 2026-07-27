# Rancher Desktop (k3s) + WireMock – lokale Integrationsprüfung

Ersetzt den OpenShift/Jira-Weg. Statt eines echten Jira/Zephyr läuft **WireMock**, das die
Zephyr-ATM-v1- (`/rest/atm/1.0`) und Jira-v2-API (`/rest/api/2/issue`) mockt. Die
`cucumber-test-service`-App spielt ihre Ergebnisse dagegen ein — verifizierbar über das
WireMock-Request-Journal. (Eine eigene GUI kommt später.)

> Die alten OpenShift-Manifeste unter `openshift/` bleiben als Referenz erhalten.

> **Verifiziert am 27.07.2026** auf Rancher Desktop (k3s v1.25, containerd-Engine): Ein Test-Run
> löst die echten Calls `GET/POST /rest/atm/1.0/folder`, `POST /rest/atm/1.0/testrun`,
> `POST /rest/atm/1.0/testrun/QA-C1/testresults` und `POST /rest/api/2/issue` aus – sichtbar im
> WireMock-Journal (`/__admin/requests`). Voraussetzung: `INTEGRATION_MOCK_ENABLED=false`
> (das `dev`-Profil setzt sonst `integration.mock.enabled=true` → simulierte Werte statt echter Calls).

## 1. Rancher Desktop installieren (privates K8s)

Windows, als Admin:
```powershell
winget install --id SUSE.RancherDesktop -e
```
Nach dem Start in den Settings:
- **Container Engine:** `dockerd (moby)` (einfacher fürs lokale Image-Bauen) oder `containerd`.
- **Kubernetes:** aktiviert (k3s). Traefik-Ingress ist standardmäßig dabei.
- Rancher Desktop setzt den `kubectl`-Context automatisch auf `rancher-desktop`.

Prüfen:
```bash
kubectl config current-context      # -> rancher-desktop
kubectl get nodes
```

## 2. App-Image bauen und in k3s laden (verifiziert 27.07.2026)

Rancher Desktop **containerd**-Engine verwenden (Settings → Container Engine → containerd).
Mit moby-Engine ist ein `docker build`-Image für k3s **nicht** sichtbar (k3s versucht dann zu
pullen → „pull access denied"). Jib scheidet aus (3.4.0 kann Java-25-Bytecode nicht lesen).

```bash
# a) Fat-JAR auf dem Host bauen (Host hat Maven + JDK 25).
#    -DskipTests wird vom pom ignoriert; die 3 PlaywrightMirrorConfig-Tests scheitern an einer
#    gesetzten Env-Var – für dieses Image irrelevant. Alte JAR aus target/ reicht ebenso.
mvn -q package -DskipTests   # oder vorhandene target/*.jar nutzen

# b) Separaten Build-Kontext anlegen (Repo-.dockerignore schließt target/ aus):
BD="$TEMP/cts-imgbuild"; mkdir -p "$BD"
cp target/cucumber-test-service-*.jar "$BD/app.jar"
cp k8s/Dockerfile "$BD/Dockerfile"

# c) Image bauen (moby-Daemon läuft bei Rancher Desktop weiter) und als tar sichern:
docker build --provenance=false -t cucumber-test-service:latest "$BD"
docker save cucumber-test-service:latest -o "$BD/app-image.tar"

# d) In den k3s-containerd (k8s.io-Namespace) importieren – der zuverlässige Weg:
RDCTL="/c/Program Files/Rancher Desktop/resources/resources/win32/bin/rdctl.exe"
MSYS_NO_PATHCONV=1 "$RDCTL" shell -- sudo k3s ctr images import \
  "/mnt/c/…/cts-imgbuild/app-image.tar"
# Prüfen: MSYS_NO_PATHCONV=1 "$RDCTL" shell -- sudo k3s crictl images | grep cucumber
```

> Das `k8s/Dockerfile` startet die App **exploded** (entpackte Jar), sonst wirft Cucumber beim
> Feature-Scan aus der Fat-JAR `FileSystemAlreadyExistsException`. `imagePullPolicy: IfNotPresent`.

## 3. Deployen

```bash
kubectl apply -f k8s/00-namespace.yaml
kubectl apply -f k8s/10-wiremock-mappings.yaml
kubectl apply -f k8s/11-wiremock.yaml
kubectl apply -f k8s/20-app.yaml
kubectl -n test-automation rollout status deploy/wiremock
kubectl -n test-automation rollout status deploy/cucumber-test-service
```

## 4. Zugriff

Am zuverlässigsten per port-forward (kein hosts-Eintrag nötig):
```bash
kubectl -n test-automation port-forward svc/cucumber-test-service 8080:8080 &
kubectl -n test-automation port-forward svc/wiremock 8081:8080 &
```
- App-API:        `http://localhost:8080/api/v1/test/...`
- WireMock-Admin: `http://localhost:8081/__admin/` (Journal: `/__admin/requests`, Docs: `/__admin/docs/`)

Alternativ über Traefik-Ingress: `http://cucumber.localhost` / `http://wiremock.localhost`
(funktioniert, wenn `*.localhost` auf 127.0.0.1 auflöst – sonst port-forward nutzen).

## 5. Integration verifizieren

```bash
# Test-Run triggern (API-Test, kein Browser nötig)
curl -s -X POST http://localhost:8080/api/v1/test/execute \
  -H 'Content-Type: application/json' \
  -d '{"projectKey":"QA","tags":["@API-Test"]}'
# -> runId merken, Status abfragen:
curl -s http://localhost:8080/api/v1/test/<runId>
```

**Erwartetes Ergebnis**
- WireMock-Journal (`/__admin/requests`) zeigt die vom Client gesendeten Calls:
  `GET /rest/atm/1.0/folder`, `POST /rest/atm/1.0/folder`, `POST /rest/atm/1.0/testrun`,
  `POST /rest/atm/1.0/testrun/QA-C1/testresults` und bei Fehl-Runs `POST /rest/api/2/issue`
  — jeweils mit JSON-Body.
- Im Status-JSON der App: `metadata.zephyrCycleKey = QA-C1`, bei Fehlern `jiraTicketKey = QA-1`.

Schnellcheck der empfangenen Calls:
```bash
curl -s http://localhost:8081/__admin/requests | jq '.requests[].request.url'
```

## Bausteine

| Datei | Inhalt |
|-------|--------|
| `00-namespace.yaml` | Namespace `test-automation` |
| `10-wiremock-mappings.yaml` | Stub-Mappings (ATM v1 + Jira v2) als ConfigMap |
| `11-wiremock.yaml` | WireMock Deployment + Service + Ingress |
| `20-app.yaml` | cucumber-test-service Deployment + Service + Ingress (Integration → WireMock) |

## Ausblick: eigene GUI (später)
Geplant als eigenes Ergebnis-Dashboard, das das WireMock-Journal ausliest und die
empfangenen Test-Cycles/Executions/Tickets test-management-artig darstellt.
