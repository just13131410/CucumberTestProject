# Remote-Browser (Playwright-Sidecar) auf OpenShift mit ArgoCD

Anleitung für den Nachbau des auf k3s verifizierten Setups (`k8s/`) auf OpenShift, ausgerollt per ArgoCD.

> **Stand:** Auf k3s (Rancher Desktop) end-to-end verifiziert. `@GUI` 18/18 grün in beiden Modi,
> Zephyr-Upload und Report-Anhänge mit 201, Liveness-Restart des Sidecars geprüft.
> **Auf OpenShift noch nicht getestet.** Die OpenShift-spezifischen Punkte unten (SCC, beliebige UID,
> Registry, NetworkPolicies) sind deshalb als Prüfpunkte formuliert.

## Prinzip in einem Satz

Die App läuft mit `PLAYWRIGHT_MODE=remote` und startet keinen Browser. Ein zweiter Container im selben Pod
(offizielles Playwright-Node-Image, `playwright run-server` auf `127.0.0.1:3000`) stellt den Browser
bereit. Die Verbindung läuft über `localhost` und verlässt den Pod nicht. Lokal bleibt `PLAYWRIGHT_MODE=local`
(Default) unverändert nutzbar.

---

## 1. Was du vorab brauchst (Checkliste)

| # | Was | Von wem | Wofür |
|---|-----|---------|-------|
| 1 | Pull-Zugriff auf `mcr.microsoft.com/playwright:v1.58.0-noble`, als Spiegel in Artifactory/Nexus oder als Remote-Repo | Plattform-/Registry-Team | Basis-Image des Sidecars |
| 2 | Build-Weg mit Zugriff auf npm-Registry und Ubuntu-apt (direkt oder über Artifactory-Remote-Repos) | Plattform-Team / CI | `npm install -g playwright@1.58.0` und `dumb-init` beim Image-Build |
| 3 | Push-Ziel für zwei Images (`cucumber-test-service`, `playwright-server`) | Registry-Team | Ausrollen per ArgoCD |
| 4 | Namespace-Quota für zusätzlich ca. 1 Gi Request / 3 Gi Limit RAM und 0,5 bis 2 CPU | OpenShift-Admin | Sidecar inkl. Chromium |
| 5 | **Egress-Freigabe des Pods** zum Testziel (Angular-App), zu Zephyr/Jira und zu DNS | Netzwerk/Plattform | siehe Abschnitt 5 |
| 6 | ArgoCD-`AppProject` mit Rechten auf den Namespace und das Git-Repo | ArgoCD-Admins | Deployment |
| 7 | Secret-Lösung (Vault ist in `openshift/deployment.yaml` vorbereitet, alternativ Sealed/External Secrets) | Plattform-Team | Zephyr-/Jira-Token **nicht** im Git |
| 8 | Klärung: Ist das Testziel per **HTTPS** erreichbar? | Fachteam der Ziel-App | Bei HTTP sind Zusatz-Flags nötig (Abschnitt 4.2) |

Zur Laufzeit braucht der Sidecar **keinen** Internetzugang: Browser und npm-Paket stecken im Image.

---

## 2. Images bauen

### 2.1 Playwright-Server (Sidecar)

Quelle: `k8s/playwright-server/Dockerfile`. Die Version muss zur Maven-Dependency
`com.microsoft.playwright:playwright` im `pom.xml` passen (aktuell **1.58.0**), sonst lehnt der Server die
Verbindung mit einem Protokollfehler ab. Bei einem Playwright-Upgrade also **immer beide** anheben.

Ohne Internet im Build die Quellen auf Artifactory umbiegen, z.B.:

```dockerfile
FROM artifactory.example.com/mcr-remote/playwright:v1.58.0-noble
# npm über Artifactory-Remote-Repo
RUN npm config set registry https://artifactory.example.com/api/npm/npm-remote/
```

Ist apt nicht erreichbar, kann `dumb-init` entfallen: Die `apt-get`-Zeile entfernen und als ENTRYPOINT
`["playwright", "run-server"]` setzen. `dumb-init` sammelt nur verwaiste Prozesse ein. Playwright beendet
seine Browser selbst; auf k3s waren nach jedem Lauf 0 Chromium-Prozesse übrig.

**Tag:** unveränderlich, z.B. `playwright-server:1.58.0`. Kein `latest`, denn ArgoCD erkennt ein neues `latest` nicht.

Optional als OpenShift-`BuildConfig` (Docker-Strategie) mit dem Dockerfile aus dem Repo. Dann übernimmt die
interne Registry das Image (`image-registry.openshift-image-registry.svc:5000/test-automation/playwright-server:1.58.0`).

### 2.2 App-Image

Wie bisher (`k8s/Dockerfile`, exploded Start). Auch hier mit Versions- oder Commit-Tag statt `latest` taggen.

---

## 3. Änderungen an den OpenShift-Manifesten (`openshift/`)

Die bestehenden Manifeste sind noch auf den **Mirror-Modus** ausgelegt. Für Remote:

### 3.1 `configmap.yaml`

```yaml
  # ---- Browser läuft im Sidecar ----
  PLAYWRIGHT_MODE: "remote"
  PLAYWRIGHT_REMOTE_ENDPOINT: "ws://localhost:3000/"
  BROWSER_HEADLESS: "true"
  # Testziel, so wie es der BROWSER im Pod erreicht (Service-DNS oder Route/Ingress-URL)
  BASEURL: "https://<ziel-app>/"
```

Entfernen bzw. nicht mehr nötig: `PLAYWRIGHT_MIRROR_ENABLED`, `PLAYWRIGHT_DOWNLOAD_HOST`,
`PLAYWRIGHT_BROWSERS_PATH`, `PLAYWRIGHT_INSTALL_ON_STARTUP`. Im Remote-Modus werden sie ignoriert, sie
verwirren aber beim Lesen.

### 3.2 `deployment.yaml`

1. **Sidecar-Container** ergänzen. Den Block aus `k8s/20-app.yaml` übernehmen (`name: playwright-server`,
   inkl. `startupProbe`/`livenessProbe` als `exec`) und nur das Image auf eure Registry umstellen.
   Absichtlich **ohne** readinessProbe: Ein nicht bereiter Sidecar würde sonst den ganzen Pod aus dem
   Service nehmen und auch API-Tests blockieren.
2. **`/dev/shm`-Volume** ergänzen (Chromium stürzt mit den 64 Mi des Container-Defaults ab):
   ```yaml
       volumes:
         - name: dshm
           emptyDir:
             medium: Memory
             sizeLimit: 1Gi
   ```
   und im Sidecar `volumeMounts: [{ name: dshm, mountPath: /dev/shm }]`.
3. **`ms-playwright`-Volume** entfernen (Volume + Mount). Den Browser-Cache braucht die App nicht mehr.
4. **Ressourcen der App** prüfen: Das 4-Gi-Limit enthielt bisher den Browser. Ohne Browser reichen erfahrungsgemäß
   die 2 Gi aus `k8s/20-app.yaml` (siehe `docs/lasttest-analyse-und-umsetzungsplan.md`).

### 3.3 `pvc.yaml`

`ms-playwright-pvc` wird nicht mehr gebraucht. Mit ArgoCD und `prune: true` verschwindet er beim Entfernen
aus dem Repo, **inkl. Daten**, was hier unkritisch ist.

---

## 4. OpenShift-Besonderheiten (Prüfpunkte)

### 4.1 Security Context / SCC (`restricted-v2`)

| Punkt | Erwartung | Prüfen mit |
|---|---|---|
| Beliebige UID statt `pwuser` | Unkritisch: `HOME=/tmp` ist im Image gesetzt, `/ms-playwright` muss nur lesbar sein | `oc exec <pod> -c playwright-server -- sh -c 'id; ls -ld /ms-playwright/*'` |
| Chromium-Sandbox | Playwright startet ohne Sandbox (`chromiumSandbox: false`), es sind **keine** Extra-Capabilities nötig | Sidecar-Log bei Testlauf |
| seccomp `RuntimeDefault` | `--no-zygote` aus dem `prod`-Profil wird mit an den Sidecar übergeben | Testlauf grün |
| `emptyDir` mit `medium: Memory` | In `restricted-v2` erlaubt, zählt aber gegen das Memory-Limit des Pods | `oc describe pod` |

### 4.2 Testziel nur per HTTP erreichbar?

Die Angular-App nutzt `crypto.randomUUID()` und die Clipboard-API. Beides gibt es nur im **Secure
Context** (HTTPS oder `localhost`). Gegen `http://<service>` bleibt der Login sonst auf `/login` hängen.
Abhilfe, nur bei HTTP-Zielen:

```yaml
  PLAYWRIGHT_REMOTE_CHANNEL: "chromium"   # volles Chromium; die Headless-Shell ignoriert das Flag
  # ACHTUNG: überschreibt browser.extra.args aus dem prod-Profil → dessen Werte mit aufnehmen
  BROWSER_EXTRA_ARGS: "--no-zygote,--disable-crash-reporter,--disable-gpu,--unsafely-treat-insecure-origin-as-secure=http://<ziel-host>:<port>"
```

Bei einem HTTPS-Ziel (z.B. über eine Route) entfällt beides.

### 4.3 HTTP-Proxy im Unternehmen

Muss der Browser über einen Proxy zum Testziel, `--proxy-server=http://proxy:port` (und ggf.
`--proxy-bypass-list=...`) in `BROWSER_EXTRA_ARGS` aufnehmen. Der Proxy gilt dann für den Browser im Sidecar.

---

## 5. NetworkPolicies, wichtig

NetworkPolicies wirken **pro Pod, nicht pro Container**. Die Verbindung App ↔ Sidecar über `localhost`
ist davon nicht betroffen. Für alles, was den Pod verlässt, braucht der **gesamte Pod** (also beide
Container) Freigaben:

| Richtung | Ziel | Wer nutzt es |
|---|---|---|
| Egress | Testziel (`BASEURL`, Host/Port bzw. Route) | Browser im Sidecar |
| Egress | Zephyr/Jira (`ZEPHYR_BASE_URL`) | App |
| Egress | weitere API-Ziele (`APIURL`, `TODOAPIURL`, …) | App (API-Tests) |
| Egress | DNS (`openshift-dns`, UDP/TCP 53 bzw. 5353) | beide |
| Ingress | Route/Router → Port 8080 | REST-API, Reports |

Beispiel-Egress zum Testziel in einem anderen Namespace:

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-egress-to-gui
  namespace: test-automation
spec:
  podSelector:
    matchLabels: { app: cucumber-test-service }
  policyTypes: [Egress]
  egress:
    - to:
        - namespaceSelector:
            matchLabels: { kubernetes.io/metadata.name: <ziel-namespace> }
      ports:
        - { protocol: TCP, port: 8080 }
```

Nicht benötigt (das war der Kern des Problems): Egress zu `playwright.azureedge.net`, CDN oder Mirror.

---

## 6. ArgoCD

### 6.1 Repo-Struktur (Empfehlung: Kustomize)

```
openshift/
  base/            # deployment, service, route, configmap, pvc (ohne Secret-Werte)
  overlays/
    dev/           # BASEURL, Image-Tags, Ressourcen
    prod/
```

### 6.2 Application

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: cucumber-test-service
  namespace: openshift-gitops          # bzw. euer ArgoCD-Namespace
spec:
  project: test-automation             # AppProject mit Rechten auf Namespace + Repo
  source:
    repoURL: https://git.example.com/qa/cucumber-test-project.git
    targetRevision: main
    path: openshift/overlays/prod
  destination:
    server: https://kubernetes.default.svc
    namespace: test-automation
  syncPolicy:
    automated: { prune: true, selfHeal: true }
    syncOptions: [CreateNamespace=true]
```

### 6.3 Worauf achten

- **Image-Tags:** Versions- oder Commit-Tags in der Kustomization (`images:`) pflegen, kein `latest`.
  Optional nimmt der ArgoCD Image Updater das ab.
- **Secrets:** `openshift/secret.yaml` enthält Platzhalter und gehört **nicht** in den ArgoCD-Pfad.
  Mit `selfHeal` würde ArgoCD ein manuell gepflegtes Secret sonst überschreiben. Vault (Annotationen sind
  im Deployment vorhanden) oder External/Sealed Secrets verwenden.
- **Vault-Injector** verändert nur den laufenden Pod, nicht das Deployment. Das erzeugt in ArgoCD keinen Drift.
- **Health:** ArgoCD meldet `Healthy`, sobald beide Container laufen und die App-readinessProbe grün ist.
  Der Sidecar hat bewusst keine readinessProbe.
- **Versions-Kopplung:** App-Image (Maven-Playwright) und Sidecar-Image (npm-Playwright) im **selben**
  Commit anheben, damit ArgoCD nie eine inkompatible Kombination ausrollt.

---

## 7. Abnahme nach dem ersten Sync

```bash
oc -n test-automation rollout status deploy/cucumber-test-service
oc -n test-automation get pod -l app=cucumber-test-service          # READY 2/2
oc -n test-automation logs deploy/cucumber-test-service -c playwright-server
#   → "Listening on ws://127.0.0.1:3000/"
oc -n test-automation logs deploy/cucumber-test-service -c cucumber-test-service | grep "playwright.mode=remote"
#   → "playwright.mode=remote (ws://localhost:3000/) – kein Browser-Download beim Start."

# GUI-Lauf über die Route anstoßen
curl -s -X POST https://<route>/api/v1/test/execute -H 'Content-Type: application/json' \
  -d '{"environment":"prod","tags":["@GUI"]}'
curl -s https://<route>/api/v1/test/status/<runId>       # COMPLETED, metadata.zephyrCycleKey gesetzt
```

Erwartet: Status `COMPLETED`, Screenshots und Axe-Reports unter `/reports/<runId>/…`, Zephyr-Cycle mit
Ergebnissen und Report-Anhängen.

---

## 8. Fehlerbilder (alle auf k3s aufgetreten und gelöst)

| Symptom | Ursache | Lösung |
|---|---|---|
| `connect` scheitert mit Protokoll-/Versionsfehler | npm- und Maven-Playwright-Version unterschiedlich | beide auf dieselbe Major.Minor |
| `Looks like you launched a headed browser without having a XServer running` | ein Profil setzt `browser.headless=false` (z.B. `dev`) | `BROWSER_HEADLESS=true` |
| Login bleibt auf `/login`, Console: `crypto.randomUUID is not a function` | HTTP-Ziel, kein Secure Context | HTTPS-Ziel oder Abschnitt 4.2 |
| Flag `--unsafely-treat-insecure-origin-as-secure` wirkt nicht | Headless-Shell ignoriert es | `PLAYWRIGHT_REMOTE_CHANNEL=chromium` |
| Tabs stürzen ab / „Aw, Snap“ | `/dev/shm` zu klein | `emptyDir` `medium: Memory` (Abschnitt 3.2) |
| Sidecar wird neu gestartet | Liveness-Probe 3× fehlgeschlagen (Server hängt oder Node stark ausgelastet) | `oc describe pod`, Events prüfen; ggf. `timeoutSeconds` erhöhen |
| `Navigation failed` / Timeout auf `BASEURL` | Egress des Pods zum Testziel fehlt | NetworkPolicy (Abschnitt 5) |
| App startet nicht: `Unbekannter playwright.mode` | Tippfehler in `PLAYWRIGHT_MODE` | `local` oder `remote` |
