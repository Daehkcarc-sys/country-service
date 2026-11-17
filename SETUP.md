# Reproducing the CI/CD pipeline

This guide rebuilds the final pipeline (`Jenkinsfile-final`) on another Windows PC:

```
GitHub → Jenkins → Maven/JUnit → SonarQube → Ansible (Vault) → Docker Hub → Kubernetes → Prometheus/Grafana
```

Everything except Docker Desktop and SonarQube runs inside **WSL (Ubuntu)**. Plan on about 2 hours and around 5 GB of downloads.

## 0. Things that are mine and you must replace

| What | Where | Replace with |
|---|---|---|
| GitHub repo URL | `git branch: ... url:` in `Jenkinsfile-final` | your fork's URL |
| Docker Hub account + token | `vault.yml` (encrypted with **my** vault password) | delete it and create your own (step 6) |
| Jenkins credentials | Jenkins UI | your own (step 5) |

Fork the repo on GitHub first, then clone **your fork**.

## 1. Windows side

1. **WSL 2 + Ubuntu**: in PowerShell, run `wsl --install -d Ubuntu`.
2. **Docker Desktop** with:
   - Settings → Resources → **WSL integration** → enable for *Ubuntu*. This also gives WSL the `docker` and `kubectl` commands.
   - Settings → **Kubernetes** → *Enable Kubernetes* (kubeadm) → Apply & restart.
   - Wait until `kubectl --context docker-desktop get nodes` shows `Ready`.
3. **SonarQube** as a container:
   ```powershell
   docker run -d --name sonarqube -p 9000:9000 sonarqube
   ```
   Then open http://localhost:9000 (admin/admin, change the password), go to My Account → Security, and generate a **user token**.

## 2. WSL packages (as root: `wsl -u root`)

```bash
apt update
apt install -y openjdk-21-jdk maven git curl ansible python3-kubernetes
ansible-galaxy collection install community.docker kubernetes.core

# Jenkins (official repo)
wget -O /usr/share/keyrings/jenkins-keyring.asc https://pkg.jenkins.io/debian-stable/jenkins.io-2023.key
echo "deb [signed-by=/usr/share/keyrings/jenkins-keyring.asc] https://pkg.jenkins.io/debian-stable binary/" > /etc/apt/sources.list.d/jenkins.list
apt update && apt install -y jenkins
usermod -aG docker jenkins && systemctl restart jenkins     # Jenkins must be able to run docker

# Helm (kube-state-metrics is installed with it)
wget -qO- https://get.helm.sh/helm-v3.19.0-linux-amd64.tar.gz | tar -xz linux-amd64/helm
mv linux-amd64/helm /usr/local/bin/
```

Checks:
- `sudo -u jenkins docker ps` works.
- `ansible --version` shows core 2.16 or newer.

## 3. Prometheus, node_exporter, Grafana (WSL)

Follow the course PDFs (`ch10_install_Prometheus.pdf`, `ch10_install_Grafana.pdf`) with these differences:
- **Grafana:** `apt-key` no longer exists on recent Ubuntu. Use a keyring instead:
  ```bash
  mkdir -p /etc/apt/keyrings
  wget -qO- https://apt.grafana.com/gpg.key | gpg --dearmor -o /etc/apt/keyrings/grafana.gpg
  echo "deb [signed-by=/etc/apt/keyrings/grafana.gpg] https://apt.grafana.com stable main" > /etc/apt/sources.list.d/grafana.list
  apt update && apt install -y grafana && systemctl enable --now grafana-server
  ```
- **Prometheus config:** don't type it by hand. Copy [`monitoring/prometheus.yml`](monitoring/prometheus.yml) to `/etc/prometheus/prometheus.yml` **after step 8** (it needs the files created there).

## 4. Jenkins plugins and tools

Open http://localhost:8080 (initial password: `cat /var/lib/jenkins/secrets/initialAdminPassword`) and install the suggested plugins.

Then, under **Plugins**, install:
- Pipeline: Stage View
- Maven Integration
- SonarQube Scanner
- Ansible
- Kubernetes CLI
- Workspace Cleanup
- Prometheus metrics
- GitHub

Then, under **Manage Jenkins → Tools** (the names must match exactly; the Jenkinsfile uses them):

| Tool | Name | Value |
|---|---|---|
| Maven | `M2_HOME` | path `/usr/share/maven` (untick *install automatically*) |
| Ansible | `myansible` | path `/usr/bin/` |

And under **Manage Jenkins → System → SonarQube servers**: name `MySonarQubeServer`, URL `http://localhost:9000`, token credential `sonarqubePWD` (from step 5).

## 5. Jenkins credentials

Manage Jenkins → Credentials → *(global)* → Add. The IDs must match exactly:

| ID | Kind | Content |
|---|---|---|
| `sonarqubePWD` | Secret text | the SonarQube token from step 1 |
| `kubeconfig-file` | Secret file | a docker-desktop-only kubeconfig (below) |
| `ansible-vault-pass` | Secret text | the vault password you choose in step 6 |

Create the kubeconfig in PowerShell. `--minify` keeps **only** the docker-desktop context, so you never upload credentials for other clusters:
```powershell
kubectl config view --minify --flatten --context docker-desktop > kubeconfig-docker-desktop
```

## 6. Your own Ansible Vault (Docker Hub access)

On Docker Hub, go to Account settings → Personal access tokens and create a token with **Read & Write**. Then, in WSL inside your clone:
```bash
rm vault.yml
EDITOR=nano ansible-vault create vault.yml        # it asks you to choose the vault password
```
Contents:
```yaml
dockerhub_username: "<your docker hub user>"
dockerhub_password: "<your docker hub token>"
```
Put the same vault password in the `ansible-vault-pass` credential (step 5), then commit and push `vault.yml`. It's encrypted, so it's safe on GitHub.

The image is pushed as `<your user>/my-country-service:<build number>`. No other file needs your username.

## 7. The pipeline job

1. In `Jenkinsfile-final`, set the `git url:` to your fork, then commit and push.
2. In Jenkins: New Item → `CD_Final` → *Pipeline* → Definition: **Pipeline script from SCM** → Git → your fork, branch `*/main`, Script Path `Jenkinsfile-final`.
3. Click **Build Now**. GitHub can't reach a Jenkins on `localhost`, so the first build (and every build, unless you add a tunnel) is started by hand.

A green run means:
- **Tests:** 26 passed, and the Quality Gate is OK.
- **Kubernetes:** 3 pods in namespace `jenkins`, and the app answers on http://localhost:30008/getcountries.
- **kube-state-metrics:** installed with Helm in namespace `monitoring` (NodePort 30080).

## 8. Per-pod metrics (CPU / memory) for Prometheus

Prometheus reads pod CPU and memory from the kubelet with a read-only account:
```bash
export KUBECONFIG=/home/<your wsl user>/.kube/config     # Docker Desktop writes it there
kubectl --context docker-desktop apply -f monitoring/prometheus-scraper-rbac.yaml
kubectl --context docker-desktop -n monitoring get secret prometheus-scraper-token -o jsonpath='{.data.token}'  | base64 -d > /etc/prometheus/k8s-token
kubectl --context docker-desktop -n monitoring get secret prometheus-scraper-token -o jsonpath='{.data.ca\.crt}' | base64 -d > /etc/prometheus/k8s-ca.crt
chown root:prometheus /etc/prometheus/k8s-token /etc/prometheus/k8s-ca.crt && chmod 640 /etc/prometheus/k8s-token

cp monitoring/prometheus.yml /etc/prometheus/prometheus.yml && chown prometheus:prometheus /etc/prometheus/prometheus.yml
promtool check config /etc/prometheus/prometheus.yml && systemctl restart prometheus
```
At http://localhost:9090/targets, all 5 targets must be **UP**: prometheus, Node_Exporter, jenkins, kube-state-metrics, cadvisor.

## 9. Grafana

The dashboards expect a Prometheus data source with uid **`prometheus`**. The UI generates a random uid, so create it with the API:
```bash
curl -u admin:<grafana password> -H 'Content-Type: application/json' http://localhost:3000/api/datasources \
  -d '{"name":"Prometheus","uid":"prometheus","type":"prometheus","url":"http://localhost:9090","access":"proxy","isDefault":true}'
```
Then go to Dashboards → New → **Import**:
- upload [`monitoring/grafana-dashboard-13332-pods.json`](monitoring/grafana-dashboard-13332-pods.json): the pod health dashboard with the **Pod** dropdown;
- by ID, `1860` (Node Exporter Full) and `9964` (Jenkins), selecting the Prometheus data source.

**Email alerts (optional):** in `/etc/grafana/grafana.ini`, fill the `[smtp]` section:
- `host = smtp.gmail.com:587`
- `user` and `from_address` = your Gmail address
- `password` = a Gmail **app password** (2-step verification required)

Then restart Grafana and create the contact point and alert rule as in the course.

## Ports used

| Port | Service |
|---|---|
| 8080 | Jenkins |
| 9000 | SonarQube |
| 9090 | Prometheus |
| 9100 | node_exporter |
| 3000 | Grafana |
| 30008 | the app (NodePort) |
| 30080 | kube-state-metrics (NodePort) |

## Known pitfalls

- **Quality Gate waits 5 minutes, then aborts.** The SonarQube container can't call Jenkins back through the webhook. The pipeline already works around this: it waits 20 s, then reads the result from the Sonar API. Don't remove that `sleep`.
- **`bind: address already in use` for Prometheus.** Another program owns port 9090. Set `--web.listen-address=127.0.0.1:9090` in `/etc/systemd/system/prometheus.service`.
- **Builds fail at "Build Docker image" / "Log in to Docker Hub" with timeouts or `unexpected EOF`.** That's the network. Pre-pull the base image once with `docker pull eclipse-temurin:21-jre`, and after switching networks restart Docker Desktop (it keeps the old DNS).
- **Kubernetes steps fail with `connection refused`.** Docker Desktop is stopped, or Kubernetes is disabled in its settings.
- **Dashboard 13332 shows "No data".** The Prometheus job `kube-state-metrics` must add the label `cluster: "docker-desktop"`. It's already in `monitoring/prometheus.yml`.
