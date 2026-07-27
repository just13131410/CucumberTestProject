# Lasttest, Council-Analyse & Umsetzungsplan — `cucumber-test-service`

**Datum:** 2026-07-27 · **Cluster:** k3s (Rancher Desktop, Single-Node, ~16 Gi) · **Branch:** `feat/playwright-mirror-browser-download`

Anlass: Beim Nachbau des Projekts stürzt der Pod während des Testdurchlaufs ab und wird neu
aufgesetzt. Vermutung: Speicher/Rechenleistung geht aus. Dieses Dokument hält den reproduzierten
Lasttest, die Analyse eines Experten-Councils (JVM-Runtime, K8s/SRE, Architektur) und den
Umsetzungsplan fest. Die Graphify-Auswertung (`graphify-out/GRAPH_REPORT.md`) ist einbezogen.

---

## 1. Lasttest — Crash reproduziert

Burst von 15–30 gleichzeitigen `POST /api/v1/test/execute` gegen den laufenden Pod
(`MAX_CONCURRENT_RUNS=5`, Rest in unbegrenzter Queue). Gemessen via Actuator (`jvm.memory.*`,
`jvm.threads.live`) und `kubectl top` / Pod-Status.

| Zeit | Heap used | Pod-RSS | Threads | CPU | Zustand |
|-----:|----------:|--------:|--------:|----:|---------|
| Idle (frisch) | ~75 Mb | **221 Mi** | 68 | 3m | ok |
| Idle (nach Läufen) | ~75 Mb | **817 Mi** | 68 | 2m | Native-Leak-Verdacht |
| Last +7 s | ~75 Mb | 822 Mi | **137** | steigt | 5 Runs starten |
| Last +26 s | ~75 Mb | **2047 Mi** | 137 | **1981m** | am Limit |
| Last +37 s | — | — | — | — | **OOMKilled (137)** → Restart |
| Last +99 s | — | 2045 Mi | — | — | **OOMKilled (137)** → Restart #2 |

**Harte Fakten:**
1. **Nicht der Heap.** JVM-Heap durchgehend 70–80 Mb (von 512 Mb Max). Der Container-RSS
   explodierte 822 → 2047 Mb in ~3 s durch **Native-/Off-Heap-Speicher**. → **kernel-cgroup-OOMKill**
   (SIGKILL, exit 137), kein Java-`OutOfMemoryError` — daher lautlos.
2. **CPU sättigt bei 2 Kernen** → Actuator antwortet nicht → Readiness/Liveness-Probes laufen in
   Timeout (zusätzliches Restart-Risiko oben drauf).
3. **Alle Läufe verloren.** Run-State liegt nur im In-Memory-`statusMap`; Restart wischt
   Queue + laufende Runs + deren Zephyr/Jira-Uploads (WireMock-Journal danach leer).

Deckt sich exakt mit dem Symptom „der Pod schmiert ab und wird neu aufgesetzt".

---

## 2. Root Cause (Council-Konsens)

Alle drei Perspektiven konvergieren auf **eine Ursache**:

> **In-Process-Ausführung von bis zu 5 gleichzeitigen Cucumber-Runs in einer einzigen, dauerhaft
> laufenden Web-Service-JVM auf einem 2-Kern/2-Gi-Pod.**

`CucumberRunnerService.executeRun()` ruft `io.cucumber.core.cli.Main.run(...)` direkt im
Web-Service-Thread (`TestExecutionService:140`). Der gesamte Test-Workload (Cucumber-Engine,
Playwright-Treiber, REST-Assured, In-Process-Allure-Generierung) teilt sich JVM, Heap, Metaspace,
CPU und Lebenszyklus mit der API. Graphify bestätigt die Kopplung: `TestExecutionService` ist ein
**God-Node** (38 Kanten, Betweenness 0.051 — Cross-Community-Brücke).

### 2.1 Warum RSS explodiert, Heap aber niedrig bleibt (JVM-Runtime-Sicht)
Das cgroup-Limit zählt **alle** anonymen Mappings; die „Heap-used"-Metrik zeigt nur einen Posten.
Ohne `-Xmx`/`MaxRAMPercentage` setzt Java 25 Max-Heap = 512 Mi (25 % von 2 Gi) und lässt **~1536 Mi
„unsichtbares" Budget** für den Rest — der in Sekunden gefüllt wird:

| Region | Warum sie hier wächst | Schätzung unter Last |
|---|---|---|
| Committed Heap (G1) | G1 gibt Regionen nicht zeitnah zurück (817-Mi-Floor bleibt) | 300–512 Mi |
| Metaspace + Class Space | **default ungedeckelt**; Cucumber/Spring/Selenium/REST-Assured/Freemarker(Allure) | 200–350 Mi |
| Code Cache (JIT) | steigt unter Last | 80–150 Mi |
| Thread-Stacks | 68→137 Threads (5 Runs + je 1 Progress-Tracker + GC/JIT) | 70–140 Mi |
| Direct/NIO-Buffer | `MaxDirectMemorySize` default = Xmx (512 Mi) | bis 200 Mi |
| `zip.Inflater` (nativ) | Classpath-Scan + Allure entpackt Webapp aus JARs, ×5 gleichzeitig | 50–150 Mi |
| glibc-malloc-Arenen (UBI9) | `MALLOC_ARENA_MAX` unset → bis 8×nCPU Arenen halten Speicher | 100–300 Mi |
| G1-GC-Strukturen | RSet, Card Table, Marking-Bitmaps | 50–80 Mi |

Summe der stetigen Posten liegt schon bei ~1,0–1,6 Gi; Allure-/Inflater-/Arenen-Spitzen unter
5-facher Parallelität drücken in einem Burst über 2 Gi.

### 2.2 Zuverlässigkeits-Fehlermodi (K8s/SRE-Sicht)
- **Burstable QoS mit halbiertem Memory-Request.** `k8s/20-app.yaml` fordert 768 Mi, limitiert 2 Gi
  (2,6×). Der Scheduler reserviert nur 768 Mi → Overcommit + früher OOM-Kandidat. Das ältere
  `openshift/deployment.yaml` forderte **2 Gi / Limit 4 Gi** — beim k3s-Port halbiert.
- **Probes am CPU-schweren Aggregat-Endpoint.** Liveness **und** Readiness treffen dasselbe
  `/actuator/health` (8081). Beim k3s-Port wurden **`timeoutSeconds`/`failureThreshold` und der
  `startupProbe` entfernt** → Defaults `timeoutSeconds:1`, `failureThreshold:3`. 1 s gegen einen
  CPU-gesättigten Actuator scheitert trivial → **Probe-induzierter Restart** zusätzlich zum OOM.
- **Keine Blast-Radius-Isolation.** Ein schwerer Run tötet die ganze JVM inkl. API und aller
  parallelen Runs. Single Replica, keine HPA/PDB/ResourceQuota.
- **Run-State nicht durabel.** In-Memory → jeder Restart verliert Arbeit **und** ausstehende
  Zephyr/Jira-Uploads. Kein PVC-Mount im k3s-Manifest (im OpenShift-Manifest vorhanden).

### 2.3 Architektur- & Korrektheits-Gefahren (Architektur-Sicht)
- **KRITISCHER KORREKTHEITS-BUG:** `Allure.setLifecycle(new AllureLifecycle(...))`
  (`CucumberRunnerService:54`) ist ein **prozessweiter Singleton**, ebenso
  `System.setProperty("allure.results.directory"/"browser"/env)` (`TestExecutionService:53,104–111`).
  Bei paralleln Runs überschreibt Run B die Ziel-Directory/Config, **während Run A noch schreibt** →
  A's Ergebnisse landen in B's Verzeichnis oder gehen verloren, Browser/Env werden vertauscht.
  **Die Per-Run-Isolation ist nur bei effektiver Parallelität = 1 korrekt.** N=5 ist damit
  gleichzeitig OOM-Ursache **und** stiller Daten-Integritäts-Bug.
- **Semaphore verhindert nichts.** `Semaphore(5)` wird **innerhalb** des Tasks (`:90`), also *nach*
  `submit()`, acquired; der `newFixedThreadPool(5)` hat eine **unbegrenzte Queue**. Der Controller
  gibt **immer 202** zurück, das dokumentierte **429** feuert nie. Keine echte Backpressure.
- **Leaks:** `statusMap`/`runningFutures` werden nie evictet (nur per manuellem `DELETE`) →
  unbegrenztes Wachstum; `cancel(true)` beendet native Browser-Prozesse nicht sauber.

---

## 3. Umsetzungsplan (phasiert)

Prinzip: **erst die Blutung stoppen (Manifest/Env), dann Korrektheit isolieren, dann die eigentliche
Kur (Out-of-Process).** Zielbudget: committed Footprint ≤ ~1700 Mi im 2-Gi-Limit.

### Phase 0 — Sofort-Stabilisierung · Manifest + Env, **kein Rebuild** (Minuten)
Betrifft `k8s/20-app.yaml`.

1. **glibc-Arenen zähmen** (höchster ROI, risikolos): `MALLOC_ARENA_MAX=2`.
2. **Native Pools deckeln** via `JAVA_TOOL_OPTIONS`:
   ```
   -Xms256m -Xmx640m
   -XX:MaxMetaspaceSize=384m -XX:ReservedCodeCacheSize=256m -XX:MaxDirectMemorySize=256m
   -Xss512k -XX:+UseG1GC -XX:G1PeriodicGCInterval=30000
   -XX:ParallelGCThreads=2 -XX:ConcGCThreads=1
   -XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/test-results/dumps
   ```
   Budget: 640+384+256+256+~68(Stacks)+~80(GC) ≈ **1684 Mi < 2048 Mi**. Die Meta-/Direct-Caps
   wandeln stilles RSS-Kriechen in einen fangbaren `OutOfMemoryError` → `ExitOnOutOfMemoryError`
   macht daraus einen sauberen, schnellen Restart statt eines hängenden Pods.
   *(Fixes `-Xmx` statt `MaxRAMPercentage`: mehr Heap-% würde Native aushungern und trotzdem OOMen.)*
3. **Probes reparieren & entkoppeln** (Spring-Boot-Health-Groups):
   ```yaml
   startupProbe:  { httpGet: {path: /actuator/health/readiness, port: actuator}, periodSeconds: 5, failureThreshold: 24 }
   livenessProbe: { httpGet: {path: /actuator/health/liveness,  port: actuator}, initialDelaySeconds: 40, periodSeconds: 15, timeoutSeconds: 5, failureThreshold: 6 }
   readinessProbe:{ httpGet: {path: /actuator/health/readiness, port: actuator}, initialDelaySeconds: 20, periodSeconds: 10, timeoutSeconds: 5, failureThreshold: 3 }
   ```
   In `application.properties`: `management.endpoint.health.probes.enabled=true`,
   `management.health.livenessstate.enabled=true`, `...readinessstate.enabled=true`.
4. **Pod right-sizen → Guaranteed QoS:** `requests.memory: 2Gi` (== Limit), `requests.cpu: 1000m`.
5. **PVC-Mount ergänzen** (`test-results`, fehlt im k3s-Manifest) — Voraussetzung für Phase 2 +
   Heap-Dumps.
6. **NMT einschalten** zur empirischen Verifikation: `-XX:NativeMemoryTracking=summary`
   (`jcmd <pid> VM.native_memory summary`). Alerting auf `container_memory_working_set_bytes` ≥ 80 %.

### Phase 1 — Konfig + kleine Codeänderung (ein Rebuild)
7. **`MAX_CONCURRENT_RUNS` externalisieren** (`@Value`/Env, `TestExecutionService:32`).
   **Sofortwert = 1** — aus Korrektheitsgründen (Allure-Singleton, s. 2.3). Speicher erlaubt 2,
   aber >1 ist erst nach Out-of-Process-Isolation (Phase 3) daten-korrekt.
8. **Echte Admission Control:** Permit **vor** `submit()` in `queueTestExecution` acquiren; bei
   voller (nun **begrenzter**) Queue **429** zurückgeben statt 202.
9. **`statusMap` evicten** (TTL / max-size LRU), entkoppelt von der Datei-Löschung.

### Phase 2 — Persistenz & Off-Thread-Reporting
10. **Run-Journal auf PVC persistieren** (JSON pro runId), beim Start **reconcilen**: verwaiste Runs
    → `INTERRUPTED`; **Integration-Outbox** für at-least-once Zephyr/Jira-Uploads (heute lost).
11. **Allure-Generierung von den Ausführungs-Threads lösen** (dedizierter Single-Thread-Executor),
    damit 5 Abschlüsse nicht gleichzeitig Freemarker+Assets in den Speicher ziehen.
12. **System-Properties whitelisten & nach dem Run löschen** (kein Leak beliebiger JVM-Props).

### Phase 3 — Die eigentliche Kur: Out-of-Process-Ausführung
13. **`CucumberRunnerMain` extrahieren** (`main()` mit runId/tags/features/browser als Args+Env →
    `TestContext.init` + `Main.run` → Exit-Code).
14. **Kind-JVM pro Run** via `ProcessBuilder`: Config über **Env/Args statt System.setProperty**,
    stdout/stderr in per-Run-Logfile, **Timeout mit `destroyForcibly`** (fixt das kaputte Cancel).
15. **Concurrency am Speicher-Budget** bemessen (Permits = Pod-Memory / Native-Peak pro Run) statt
    fixer Anzahl. Danach ist N>1 wieder korrekt (jeder Run eigene JVM/Singletons/Env).
16. **Optional (b):** `ProcessBuilder`-Fork gegen **k8s-Job-pro-Run** tauschen (fabric8-Client,
    RBAC, eigenes cgroup je Run, `ttlSecondsAfterFinished`, `activeDeadlineSeconds`). Nur bei
    echtem Multi-Node/Multi-Team-Bedarf — für den kleinen k3s ist der Subprozess (13–15) die
    passendere Kur.

---

## 4. Quick Wins vs. tiefe Fixes

| Quick Wins (Minuten, nur Manifest/Env) | Tiefe Fixes (Code/Design) |
|---|---|
| `MALLOC_ARENA_MAX=2` | Out-of-Process-Run (Subprozess/Job) — löst die ganze Problemklasse |
| `JAVA_TOOL_OPTIONS` mit Pool-Caps + `ExitOnOutOfMemoryError` | Persistenz + Integration-Outbox mit Startup-Reconcile |
| Probes: `timeoutSeconds:5`, `failureThreshold:6`, `startupProbe` zurück, Health-Groups | Off-Thread-Allure-Generierung |
| Memory-Request 2 Gi → Guaranteed QoS, CPU-Request 1000m | Concurrency am Speicher-Budget statt fixer Zahl |
| PVC-Mount ergänzen; NMT=summary | `statusMap`-Eviction; System-Property-Whitelist |

**Kernursache in einem Satz:** Ein Burstable-Pod mit halbiertem Memory-Request, 1-s-Default-Probe-
Timeout und 5-facher In-Heap-Parallelität garantiert, dass ein Lastspike entweder OOMKillt (Limit)
oder von der Liveness getötet wird (CPU-gedrosselter Actuator) — und weil der Run-State nur im
Speicher liegt, vernichtet jeder Kill die Arbeit. **Fix-Reihenfolge:** Probe-Timeouts → QoS/Request
→ Concurrency-Cap (=1) → Persistenz → Out-of-Process.

---

## 5. Betroffene Dateien (Referenz)
- `k8s/20-app.yaml` — Ressourcen, Probes, Env/`JAVA_TOOL_OPTIONS`, PVC-Mount (Phase 0)
- `openshift/deployment.yaml` / `openshift/pvc.yaml` — Referenz: hatte startupProbe + Probe-Timeouts
  + 2 Gi/4 Gi + PVC-Muster, die beim k3s-Port verloren gingen
- `src/main/java/org/example/cucumber/service/TestExecutionService.java` — `:32` Concurrency,
  `:37` unbounded `statusMap`, `:70/:90` Semaphore-nach-submit, `:104–111` System-Property-Leak,
  `:178` In-Thread-Allure
- `src/main/java/org/example/CucumberRunnerService.java` — `:54` Allure-Singleton, `:82` `Main.run`
  in-process
- `src/main/java/org/example/cucumber/controller/TestExecutionController.java` — `:72` immer 202,
  dokumentiertes `429` ungenutzt
