# CRC (OpenShift Local) Setup-Anleitung

Lokale OpenShift-nahe Testumgebung auf Windows 11 mit CRC (OKD-Preset).
Alle bestehenden `openshift/`-Manifeste laufen **ohne Änderung**.

---

## Voraussetzungen

- Windows 11 Pro/Enterprise (Hyper-V erforderlich)
- 16–32 GB RAM (14 GB werden CRC zugewiesen)
- 60 GB freier Speicher
- 6 CPU-Kerne empfohlen

---

## Phase 1: CRC installieren und starten

### Hyper-V aktivieren (PowerShell als Administrator)

```powershell
Enable-WindowsOptionalFeature -Online -FeatureName Microsoft-Hyper-V -All
Restart-Computer
```

### CRC herunterladen

CRC (OpenShift Local): https://crc.dev/crc
Kostenlos, kein Red Hat Account für das OKD-Preset erforderlich.

### CRC konfigurieren und starten

```bash
crc config set preset okd
crc config set cpus 6
crc config set memory 14336   # 14 GB
crc config set disk-size 60
crc setup
crc start
```

Der erste Start dauert 10–20 Minuten (VM-Image-Download und Cluster-Initialisierung).

### Login

```bash
eval $(crc oc-env)
oc login -u kubeadmin https://api.crc.testing:6443
```

Das kubeadmin-Passwort wird nach `crc start` angezeigt oder per `crc console --credentials` abgerufen.

---

## Phase 2: Namespace und Basis-Manifeste anwenden

```bash
oc apply -f openshift/namespace.yaml
oc apply -f openshift/pvc.yaml
oc apply -f openshift/configmap.yaml
oc apply -f openshift/service.yaml
oc apply -f openshift/route.yaml
oc apply -f openshift/allure-route.yaml
```

---

## Phase 3: HashiCorp Vault (via OperatorHub)

### Vault Operator installieren

```bash
oc apply -f openshift/vault/vault-operator-subscription.yaml
# Warten bis Operator-Pod läuft
oc get pods -n test-automation -w
```

### Vault-Instanz erstellen

```bash
oc apply -f openshift/vault/vault-instance.yaml
# Warten bis Vault-Pod läuft
oc get pods -n test-automation -w
```

### Vault initialisieren und unseal

```bash
# Port-Forward für lokalen Zugriff
oc port-forward svc/vault 8200:8200 -n test-automation &

export VAULT_ADDR=http://localhost:8200

# Initialisieren (speichere Unseal-Keys und Root-Token sicher!)
vault operator init -key-shares=1 -key-threshold=1

# Unseal
vault operator unseal <unseal-key>

# Login mit Root-Token
vault login <root-token>
```

### Secrets anlegen

```bash
# KV-Engine aktivieren (falls nicht bereits durch vault-instance.yaml)
vault secrets enable -path=secret kv-v2

# Secrets eintragen
vault kv put secret/cucumber-test \
  ZEPHYR_API_TOKEN=dein-zephyr-token \
  JIRA_API_TOKEN=dein-jira-token
```

### Kubernetes Auth konfigurieren

```bash
vault auth enable kubernetes

vault write auth/kubernetes/config \
  kubernetes_host="https://kubernetes.default.svc"

# Policy anwenden
vault policy write cucumber-test openshift/vault/vault-policy.hcl

# Kubernetes-Rolle anlegen
vault write auth/kubernetes/role/cucumber-test \
  bound_service_account_names="default,cucumber-test-service" \
  bound_service_account_namespaces="test-automation" \
  policies="cucumber-test" \
  ttl=1h
```

### Vault verifizieren

```bash
oc exec -it vault-0 -n test-automation -- vault kv get secret/cucumber-test
```

---

## Phase 4: Container-Image bauen und in CRC-Registry pushen

### CRC-Registry freigeben

```bash
oc registry login --insecure=true
```

### Image bauen

```bash
mvn package -DskipTests
docker build -t default-route-openshift-image-registry.apps-crc.testing/test-automation/cucumber-test-service:latest .
```

### Image pushen

```bash
docker push default-route-openshift-image-registry.apps-crc.testing/test-automation/cucumber-test-service:latest
```

### deployment.yaml anpassen (nur für CRC)

In `openshift/deployment.yaml` die Image-Zeile auf die CRC-Registry umstellen:

```yaml
image: default-route-openshift-image-registry.apps-crc.testing/test-automation/cucumber-test-service:latest
```

### Deployment anwenden

```bash
oc apply -f openshift/deployment.yaml
```

**Hinweis:** Wenn Vault nicht genutzt wird, zuerst `openshift/secret.yaml` mit echten Werten befüllen und anwenden, sowie den `secretRef`-Block in `deployment.yaml` aktiv lassen.

---

## Phase 5: Verifikation

```bash
# Pod-Status prüfen
oc get pods -n test-automation

# Routes anzeigen
oc get route -n test-automation
```

### Erreichbare URLs (nach erfolgreichem Deployment)

| Ziel | URL |
|------|-----|
| Health Check | `https://cucumber-test-service-test-automation.apps-crc.testing/actuator/health` |
| REST API | `https://cucumber-test-service-test-automation.apps-crc.testing/api/v1/test/execute` |
| Allure-Report | `https://cucumber-test-service-test-automation.apps-crc.testing/reports/combined/allure-report/` |
| Allure (direkter Route) | `https://allure-report-test-automation.apps-crc.testing/reports/combined/allure-report/` |
| OpenShift Web Console | `https://console-openshift-console.apps-crc.testing` |

### API-Testaufruf

```bash
curl -k -X POST \
  https://cucumber-test-service-test-automation.apps-crc.testing/api/v1/test/execute \
  -H "Content-Type: application/json" \
  -d '{"tags": ["@SmokeTest"]}'
```

---

## Hinweise zu Fallback ohne Vault

Falls Vault nicht eingerichtet wird, kann `openshift/secret.yaml` als Fallback genutzt werden:

```bash
# Entweder die Datei mit echten Werten befüllen und anwenden:
oc apply -f openshift/secret.yaml

# Oder direkt erstellen:
oc create secret generic cucumber-test-secrets \
  --from-literal=ZEPHYR_API_TOKEN=dein-token \
  --from-literal=JIRA_API_TOKEN=dein-token \
  -n test-automation
```

Den `secretRef`-Block in `deployment.yaml` aktiv lassen (nicht auskommentieren).

---

## Alternative: Oracle Cloud Free Tier (OKD SNO)

Für einen dauerhaft erreichbaren Cloud-Betrieb:

- **Oracle A1.Flex**: 4 OCPU (ARM), 24 GB RAM, 200 GB Storage – dauerhaft kostenlos
- OKD Single-Node OpenShift (SNO) darauf installieren
- Echter Cloud-Betrieb, von überall erreichbar
- Höhere Komplexität bei der Einrichtung (empfohlen als zweite Stufe)

---

## Nützliche CRC-Befehle

```bash
crc status          # Cluster-Status
crc stop            # Cluster stoppen
crc start           # Cluster starten
crc delete          # Cluster löschen (destruktiv!)
crc console         # OpenShift Web Console im Browser öffnen
crc console --credentials  # Login-Daten anzeigen
```
