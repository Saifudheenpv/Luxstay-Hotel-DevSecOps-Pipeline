# 🏨 Luxstay Hotel Booking System — DevSecOps CI/CD Pipeline 🚀

> **A full-scale, production-grade Java Spring Boot application** deployed through a modern **DevSecOps pipeline** using Jenkins, Docker, SonarQube, OWASP, Trivy, Kubernetes (EKS), and AWS Cloud.

---

## 🌟 Project Overview

**Luxstay Hotel Booking System** is a **Spring Boot web application** that provides room booking, user management, and transaction handling — all deployed through a **secure, automated CI/CD pipeline** following **DevSecOps best practices**.

The pipeline integrates **security (OWASP, Trivy)**, **quality (SonarQube)**, **automation (Jenkins)**, and **scalable deployment (EKS)**.

---

## 🧰 Tech Stack

### 🧱 Backend (Application)
- **Java 17**
- **Spring Boot 3.3.11 (secured version)**
- **Spring Data JPA + Hibernate**
- **MySQL 8.x**
- **Thymeleaf** for lightweight UI
- **Spring Boot Actuator** for health checks

### 🧩 DevOps / Cloud Stack
- **Jenkins (Declarative Pipeline)** – CI/CD Orchestrator  
- **Docker + DockerHub** – Containerization  
- **OWASP Dependency Check** – Dependency vulnerability scanning  
- **Trivy** – Container image security scan  
- **SonarQube** – Static code analysis  
- **Kubernetes (AWS EKS)** – Deployment & scaling  
- **AWS CLI + IAM** – EKS access  
- **Email Notification (Gmail SMTP)** – Automated build alerts  

---

## ⚙️ CI/CD Pipeline Workflow

Every GitHub push automatically triggers this **DevSecOps pipeline**:

```mermaid
graph TD
    A[GitHub Push] -->|Webhook Trigger| B[Jenkins Pipeline]
    B --> C[Build & Test (Maven)]
    C --> D[OWASP Dependency Scan]
    D --> E[SonarQube Code Quality Analysis]
    E --> F[Docker Build & Push to DockerHub]
    F --> G[Trivy Image Vulnerability Scan]
    G --> H[Deploy to AWS EKS (Blue/Green)]
    H --> I[Email Notification (Success/Failure)]

🔐 Security Stages
1️⃣ OWASP Dependency Check

Scans all Maven dependencies for CVEs

Suppression file used to handle known safe Tomcat false positives

Generates an HTML security report

2️⃣ Trivy Container Scan

Scans Docker images for OS and application-layer vulnerabilities

Only allows 0 CRITICAL and 0 HIGH vulnerabilities to pass

🧩 Jenkins Pipeline Breakdown
| Stage                   | Purpose                                               | Tools Used        |
| ----------------------- | ----------------------------------------------------- | ----------------- |
| **Environment Setup**   | Verify Java, Maven, Docker, Kubectl versions          | Shell             |
| **Checkout Code**       | Pull latest source from GitHub                        | Jenkins SCM       |
| **Build & Test**        | Compile and run JUnit tests                           | Maven             |
| **OWASP Scan**          | Detect dependency vulnerabilities                     | OWASP             |
| **SonarQube Analysis**  | Evaluate code quality and coverage                    | SonarQube         |
| **Docker Build & Push** | Build container image and push to DockerHub           | Docker            |
| **Trivy Scan**          | Scan image for vulnerabilities                        | Trivy             |
| **Deploy to EKS**       | Deploy to AWS EKS with rolling or blue-green strategy | AWS CLI + kubectl |
| **Blue-Green Switch**   | Automatically switch traffic to the new version       | kubectl patch     |
| **Email Notification**  | Send HTML report to developer                         | Gmail SMTP        |


☸️ Kubernetes Deployment
🟩 Blue-Green Deployment Strategy

Two environments (blue and green) run simultaneously.

New version (green) deployed alongside the current (blue).

Once ready, service traffic switches from blue → green.

blue scaled down, ensuring zero downtime.

flowchart LR
    A[Users] -->|Requests| B[LoadBalancer Service]
    B --> C1[Blue Deployment v1]
    B --> C2[Green Deployment v2]
    C2 -->|Healthy| D[Traffic Switch]
    D -->|New Traffic| C2
    C1 -->|Scaled Down| X[Idle]


🐳 Docker Setup
🧩 Multi-Stage Dockerfile

# Build Stage
FROM maven:3.9.5-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime Stage
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y curl wget && rm -rf /var/lib/apt/lists/*
RUN useradd -ms /bin/bash spring
USER spring
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","-Dspring.profiles.active=prod","app.jar"]

☁️ AWS EKS Deployment Flow

flowchart TD
    A[Jenkins CI Server] --> B[Build Docker Image]
    B --> C[Push to DockerHub]
    C --> D[AWS EKS Cluster]
    D --> E[Blue Deployment]
    D --> F[Green Deployment]
    F --> G[LoadBalancer Service]
    G --> H[Users Access Latest App]

🧠 SonarQube Integration

Jenkins connects to SonarQube using a configured token.

SonarQube analyzes:

Code Smells

Test Coverage

Duplications

Maintainability

Dashboard:
👉 http://13.203.26.99:9000/dashboard?id=hotel-booking-system

📬 Email Notification Examples
✅ Success Email

Subject: ✅ LIVE: Luxstay Hotel v94
Body:

🎉 Your App is LIVE!
Version: v94
URL: http://<LoadBalancer-DNS>
Jenkins: <Build URL>


❌ Failure Email

Subject: ❌ FAILED: Luxstay Hotel v94
Body:

🚨 Build Failed!
Check: http://jenkins-url/job/94/console


🧪 Quality and Security Verification

| Check           | Tool      | Status   |
| --------------- | --------- | -------- |
| Unit Tests      | JUnit     | ✅ Passed |
| Code Quality    | SonarQube | ✅ Clean  |
| Dependency Scan | OWASP     | ✅ Safe   |
| Container Scan  | Trivy     | ✅ Secure |
| Deployment      | AWS EKS   | ✅ Stable |


🐳 DockerHub Image
👉 https://hub.docker.com/r/saifudheenpv/hotel-booking-system

🌐 Live Demo (AWS EKS)
http://<loadbalancer-dns>.elb.ap-south-1.amazonaws.com

👨‍💻 Author

Saifudheen P.V
💼 DevOps & Cloud Engineer | AWS | Jenkins | Kubernetes | Security Automation
📧 mesaifudheenpv@gmail.com