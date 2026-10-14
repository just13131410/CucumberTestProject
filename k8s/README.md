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
  `POST /rest/atm/1.0/testrun/QA-C1/testresults`, `POST /rest/atm/1.0/testrun/QA-C1/attachments`
  (Cucumber- + Axe-Report, multipart) und bei Fehl-Runs `POST /rest/api/2/issue`
  — jeweils mit JSON-Body.
- Im Status-JSON der App: `metadata.zephyrCycleKey = QA-C1`, bei Fehlern `jiraTicketKey = QA-1`.

Schnellcheck der empfangenen Calls:
```bash
curl -s http://localhost:8081/__admin/requests | jq '.requests[].request.url'
```

## 6. GUI-Tests mit Remote-Browser (Playwright-Sidecar)

Der Java-Container startet keinen Browser. Im App-Pod läuft als zweiter Container das
offizielle Playwright-Node-Image (`k8s/playwright-server/Dockerfile`, `playwright run-server`
auf `127.0.0.1:3000`); die App verbindet sich per `PLAYWRIGHT_MODE=remote`
(+ `PLAYWRIGHT_REMOTE_ENDPOINT=ws://localhost:3000/`, ist auch der Default).

| `PLAYWRIGHT_MODE` | Browser läuft … | typischer Einsatz |
|---|---|---|
| `local` (Default) | in der App selbst (Mirror bzw. `browser.executable.path`) | lokale Entwicklung, `localhost:4200` |
| `remote` | im Playwright-Server (Sidecar) | Cluster mit restriktiven NetworkPolicies |
Testziel ist das Angular-Dashboard als nginx-Deployment (`40-angular-gui.yaml`,
`BASEURL=http://angular-gui:8080/`).

Images direkt in den k3s-Store bauen (`nerdctl -n k8s.io` erreicht ihn von Windows aus –
kein save/import nötig):
```bash
# Angular bauen und Kontext vorbereiten
(cd /f/Dokumente/Dev/GUIProjekt/guiprojekt && npx ng build)
GD="$TEMP/gui-imgbuild"; mkdir -p "$GD"
cp k8s/angular-gui/Dockerfile k8s/angular-gui/nginx.conf "$GD/"
cp -r /f/Dokumente/Dev/GUIProjekt/guiprojekt/dist/guiprojekt/browser "$GD/"
nerdctl -n k8s.io build -t angular-gui:latest "$GD"

# Playwright-Server (Version = Maven-Dependency playwright im pom.xml!)
nerdctl -n k8s.io build -t playwright-server:1.58.0 k8s/playwright-server

kubectl apply -f k8s/40-angular-gui.yaml -f k8s/20-app.yaml
```
GUI-Lauf triggern:
```bash
curl -s -X POST http://localhost:8080/api/v1/test/execute -H 'Content-Type: application/json'   -d '{"environment":"dev","tags":["@GUI"]}'
```
Im Manifest gesetzt (alle per `x-playwright-launch-options` an den Server durchgereicht):
`BROWSER_HEADLESS=true` (das dev-Profil setzt sonst `false` → kein X-Server), sowie
`PLAYWRIGHT_REMOTE_CHANNEL=chromium` + `BROWSER_EXTRA_ARGS=--unsafely-treat-insecure-origin-as-secure=http://angular-gui:8080`:
Die Angular-App nutzt `crypto.randomUUID()`, das es nur im Secure Context (HTTPS/localhost) gibt.
Das Flag wirkt nur im vollen Chromium, nicht in der Headless-Shell. Bei HTTPS-Zielen entfällt beides.

**Sidecar-Probes:** `startupProbe` + `livenessProbe` als `exec` (`node -e` → `GET http://127.0.0.1:3000/`
liefert `200 "Running"`), weil der Server nur auf localhost lauscht. Ein hängender Server wird nach
~60 s neu gestartet – nur der Sidecar, nicht die App. Bewusst **keine** readinessProbe: sonst fiele
der ganze Pod aus dem Service und blockierte auch API-Tests.

> **Verifiziert am 09.10.2026:** Liveness-Restart per `kill -STOP` auf den Server-Prozess geprüft; Zephyr-Attachments → 201. Modus `local` (App auf dem Host, `localhost:4200`) ebenfalls 18/18. `@GUI` → 18/18 Szenarien grün, Screenshots + Axe-Reports im App-Container.

Firmen-Setup: Das Playwright-Image einmalig in die interne Registry spiegeln/bauen – zur
Laufzeit braucht der Sidecar keinen Internetzugang. NetworkPolicies wirken pro **Pod**: Der Pod
braucht Egress zum Testziel (`BASEURL`), App↔Sidecar über localhost ist davon nicht betroffen.
OpenShift/ArgoCD-Nachbau: `docs/OpenShift-ArgoCD-Remote-Browser.md`.

## Bausteine

| Datei | Inhalt |
|-------|--------|
| `00-namespace.yaml` | Namespace `test-automation` |
| `10-wiremock-mappings.yaml` | Stub-Mappings (ATM v1 + Jira v2) als ConfigMap |
| `11-wiremock.yaml` | WireMock Deployment + Service + Ingress |
| `20-app.yaml` | cucumber-test-service Deployment (+ Playwright-Sidecar) + Service + Ingress (Integration → WireMock) |
| `30-todo-api.yaml` | Todo-API-Fixture für die @API-Test-Szenarien |
| `40-angular-gui.yaml` | Angular-Dashboard (nginx) als Testziel der @GUI-Szenarien |
| `playwright-server/Dockerfile` | Offizielles Playwright-Node-Image + `playwright run-server` (Sidecar) |
| `angular-gui/` | Dockerfile + nginx.conf (SPA-Fallback) für das Angular-Dashboard |

## Ausblick: eigene GUI (später)
Geplant als eigenes Ergebnis-Dashboard, das das WireMock-Journal ausliest und die
empfangenen Test-Cycles/Executions/Tickets test-management-artig darstellt.
