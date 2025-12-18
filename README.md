# 🏨 LuxStay – Hotel Booking System

## 🚀 Enterprise-Grade CI/CD DevSecOps Pipeline

<p align="center">
  <img src="https://readme-typing-svg.demolab.com?font=Fira+Code&size=28&pause=1000&color=36BCF7&center=true&vCenter=true&width=800&lines=LuxStay+Hotel+Booking+System;Enterprise+DevSecOps+CI%2FCD+Pipeline;Zero+Downtime+%7C+Secure+%7C+Automated" />
</p>

<p align="center">
  <img src="https://img.shields.io/badge/CI%2FCD-Jenkins-blue?style=for-the-badge" />
  <img src="https://img.shields.io/badge/DevSecOps-Enabled-success?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Kubernetes-EKS-326ce5?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Security-OWASP%20%7C%20Trivy-red?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Quality-SonarQube-yellow?style=for-the-badge" />
</p>

<p align="center">
  <b>Production-ready | Secure | Zero-Downtime | Fully Automated</b>
</p>

---

## 🎬 Animated CI/CD Pipeline Flow

<p align="center">
  <img src="assets/ci-cd-animated.gif" alt="CI/CD Animated Flow" width="800" />
</p>

> 🔥 *GIF-based animation for GitHub + Mermaid for VS Code preview*

```mermaid
flowchart LR
  A[👨‍💻 GitHub Push] -->|Webhook| B[⚙️ Jenkins]
  B --> C[🧪 Maven Build & Test]
  C --> D[🔍 OWASP Dependency Check]
  D --> E[🧠 SonarQube Analysis]
  E --> F[🐳 Docker Build & Push]
  F --> G[🛡️ Trivy Image Scan]
  G --> H[☸️ AWS EKS Deploy]
  H --> I{🚦 Deployment Strategy}
  I -->|Blue-Green| J[🟩 Green Live]
  I -->|Rolling| K[🔄 Rolling Update]
  J --> L[📧 Success Email]
  K --> L
  B --> M[❌ Failure Email]
```

---

## 🧩 Tech Stack

| Category      | Tools                         |
| ------------- | ----------------------------- |
| CI/CD         | Jenkins Declarative Pipeline  |
| Build         | Maven 3                       |
| Runtime       | OpenJDK 17                    |
| Code Quality  | SonarQube                     |
| Security      | OWASP Dependency Check, Trivy |
| Container     | Docker                        |
| Registry      | Docker Hub (saifudheenpv)     |
| Orchestration | Kubernetes (AWS EKS)          |
| Notifications | Jenkins Email Extension       |

---

## 🏗️ Pipeline Stages (Step-by-Step)

### 1️⃣ Environment Validation

* Java
* Maven
* Docker
* kubectl

### 2️⃣ Source Code Checkout

* Pulls latest code from GitHub SCM

### 3️⃣ Jakarta Auto-Migration (Optional)

```bash
javax.persistence → jakarta.persistence
```

### 4️⃣ Build & Test

```bash
mvn clean test -Dspring.profiles.active=test
```

### 5️⃣ OWASP Dependency Scan

```bash
mvn org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=11
```

### 6️⃣ SonarQube Static Analysis

* Code smells
* Bugs
* Security hotspots

### 7️⃣ Docker Build & Push

```bash
docker build -t saifudheenpv/hotel-booking-system:${BUILD_ID} .
docker push saifudheenpv/hotel-booking-system:${BUILD_ID}
```

### 8️⃣ Trivy Image Scan

```bash
trivy image --severity HIGH,CRITICAL saifudheenpv/hotel-booking-system:${BUILD_ID}
```

### 9️⃣ Kubernetes Deployment (EKS)

```bash
kubectl apply -f k8s/
```

### 🔁 Blue-Green Switch

```bash
kubectl patch service hotel-booking-service -p '{"spec":{"selector":{"version":"green"}}}'
```

---

## ⚙️ Jenkins Parameters

| Name                 | Type    | Description          |
| -------------------- | ------- | -------------------- |
| DEPLOYMENT_STRATEGY  | Choice  | blue-green / rolling |
| AUTO_SWITCH          | Boolean | Auto traffic switch  |
| AUTO_MIGRATE_JAKARTA | Boolean | javax → jakarta      |

---

## 🌍 Environment Variables

| Variable         | Description      |
| ---------------- | ---------------- |
| SONARQUBE_URL    | Sonar server     |
| DOCKER_NAMESPACE | Docker Hub user  |
| APP_NAME         | Application name |
| APP_VERSION      | Build ID         |
| CLUSTER_NAME     | EKS Cluster      |
| REGION           | AWS Region       |

---

## 📧 Email Notifications

### ✅ Success

* Live Application URL
* Version
* Jenkins Build Link

### ❌ Failure

* Error Summary
* Console Logs

---

## 🧠 DevSecOps Best Practices

✅ Shift-left security
✅ Credential vaulting
✅ Parameterized pipelines
✅ Zero-downtime deployments
✅ Continuous quality gates

---

## 🚀 Future Enhancements

* 🔐 HashiCorp Vault
* 📊 Prometheus & Grafana
* 🤖 AI-based anomaly detection

---

## 👨‍💻 Author

**Saifudheen PV**
DevOps & Cloud Engineer
📧 [mesaifudheenpv@gmail.com](mailto:mesaifudheenpv@gmail.com)

---

⭐ *Star this repository if you find it useful!*
