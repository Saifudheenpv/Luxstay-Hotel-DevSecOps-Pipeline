pipeline {
    agent any
    
    tools {
        jdk 'JDK17'
        maven 'Maven3'
    }
    
    environment {
        // Infrastructure URLs
        NEXUS_URL = '3.110.226.20'
        SONARQUBE_URL = '13.203.26.99'
        JENKINS_URL = '3.110.149.188'
        
        // Use Docker Hub instead of Nexus Docker registry
        DOCKER_REGISTRY = "docker.io"
        DOCKER_NAMESPACE = "saifudheenpv"  // Your Docker Hub username
        NEXUS_REPO_URL = "${NEXUS_URL}:8081"
        
        // Repository Names
        MAVEN_REPO_NAME = "maven-releases"
        
        // Credentials
        KUBECONFIG = credentials('kubeconfig')
        SONAR_TOKEN = credentials('sonar-token')
        NEXUS_CREDS = credentials('nexus-creds')
        DOCKER_CREDS = credentials('docker-token')
        GITHUB_CREDS = credentials('github-token')
        
        // Application Configuration
        APP_NAME = 'hotel-booking-system'
        APP_VERSION = "${env.BUILD_ID}"
        K8S_NAMESPACE = 'hotel-booking'
        
        // Maven Configuration
        MAVEN_OPTS = '-Xmx1024m -XX:MaxPermSize=256m'
    }
    
    stages {
        // STAGE 1: CODE CHECKOUT FROM GITHUB
        stage('GitHub Checkout') {
            steps {
                echo "📦 Checking out code from GitHub repository..."
                checkout scm
                
                sh '''
                    echo "=== CODE CHECKOUT COMPLETED ==="
                    echo "Repository: Luxstay-Hotel-Booking-System"
                    echo "Branch: main"
                    echo "Build ID: ${BUILD_ID}"
                    ls -la
                '''
            }
        }
        
        // STAGE 2: MAVEN COMPILE
        stage('Maven Compile') {
            steps {
                echo "🔨 Compiling source code with Maven..."
                sh 'mvn compile -DskipTests'
                
                sh 'echo "✅ Code compilation completed successfully"'
            }
        }
        
        // STAGE 3: UNIT TESTS EXECUTION
        stage('Unit Tests') {
            steps {
                echo "🧪 Running unit tests with Maven..."
                sh 'mvn test -Dspring.profiles.active=test'
            }
            post {
                always {
                    echo "📊 Publishing test results..."
                    junit 'target/surefire-reports/*.xml'
                }
                success {
                    echo "✅ All tests passed!"
                }
                failure {
                    echo "❌ Tests failed! Check test reports."
                    error("Unit tests failed - check test reports")
                }
            }
        }
        
        // STAGE 4: SONARQUBE CODE QUALITY
        stage('SonarQube Analysis') {
            steps {
                echo "🔍 Running SonarQube code analysis..."
                withSonarQubeEnv('sonarqube-server') {
                    sh """
                    mvn sonar:sonar \
                      -Dsonar.projectKey=hotel-booking-system \
                      -Dsonar.projectName='Hotel Booking System' \
                      -Dsonar.host.url=http://${SONARQUBE_URL}:9000 \
                      -Dsonar.login=${SONAR_TOKEN} \
                      -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                      -Dsonar.java.binaries=target/classes \
                      -Dsonar.sourceEncoding=UTF-8
                    """
                }
            }
        }
        
        // STAGE 5: QUALITY GATE CHECK
        stage('Quality Gate') {
            steps {
                echo "🚦 Checking SonarQube Quality Gate..."
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
                echo "✅ Quality Gate passed!"
            }
        }
        
        // STAGE 6: DEPENDENCY CHECK (OWASP)
        stage('Dependency Check') {
            steps {
                echo "🔒 Running OWASP Dependency Check..."
                sh '''
                    mvn org.owasp:dependency-check-maven:check \
                    -DskipTests \
                    -Dformat=HTML \
                    -Dformat=XML
                '''
                
                dependencyCheckPublisher pattern: '**/dependency-check-report.xml'
            }
        }
        
        // STAGE 7: MAVEN BUILD PACKAGE
        stage('Maven Build Package') {
            steps {
                echo "📦 Building application package..."
                sh 'mvn clean package -DskipTests'
                
                echo "✅ Application packaged successfully!"
                archiveArtifacts 'target/*.jar'
            }
        }
        
        // STAGE 8: NEXUS ARTIFACT PUBLISH
        stage('Nexus Publish Artifact') {
            steps {
                echo "📤 Publishing Maven artifact to Nexus..."
                script {
                    def jarFile = sh(script: 'ls target/*.jar', returnStdout: true).trim()
                    
                    nexusArtifactUploader(
                        nexusVersion: 'nexus3',
                        protocol: 'http',
                        nexusUrl: "${NEXUS_REPO_URL}",
                        groupId: 'com.hotel',
                        version: "${APP_VERSION}",
                        repository: "${MAVEN_REPO_NAME}",
                        credentialsId: 'nexus-creds',
                        artifacts: [
                            [artifactId: "${APP_NAME}",
                             classifier: '',
                             file: "${jarFile}",
                             type: 'jar']
                        ]
                    )
                }
                echo "✅ Maven artifact published to Nexus successfully!"
            }
        }
        
        // STAGE 9: DOCKER BUILD AND TAG
        stage('Docker Build and Tag') {
            steps {
                echo "🐳 Building Docker image for Docker Hub..."
                script {
                    sh """
                    docker login -u ${DOCKER_CREDS_USR} -p ${DOCKER_CREDS_PSW}
                    docker build -t ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} .
                    docker tag ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} ${DOCKER_NAMESPACE}/${APP_NAME}:latest
                    """
                    echo "✅ Docker image built and tagged successfully!"
                }
            }
        }
        
        // STAGE 10: TRIVY SECURITY SCAN
        stage('Trivy Security Scan') {
            steps {
                echo "🔍 Running Trivy security scan..."
                script {
                    sh '''
                    if ! command -v trivy &> /dev/null; then
                        wget https://github.com/aquasecurity/trivy/releases/download/v0.45.1/trivy_0.45.1_Linux-64bit.deb
                        sudo dpkg -i trivy_0.45.1_Linux-64bit.deb
                    fi
                    '''
                    sh """
                    trivy image --format template --template "@contrib/gitlab.tpl" --output trivy-security-report.html ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION}
                    trivy image --exit-code 1 --severity CRITICAL ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION}
                    """
                }
            }
        }
        
        // STAGE 11: DOCKER PUSH
        stage('Docker Push') {
            steps {
                echo "📤 Pushing Docker image to Docker Hub..."
                script {
                    sh """
                    docker push ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION}
                    docker push ${DOCKER_NAMESPACE}/${APP_NAME}:latest
                    """
                    echo "✅ Docker images pushed successfully!"
                }
            }
        }
        
        // STAGE 12: DEPLOY TO KUBERNETES
        stage('Deploy to Kubernetes') {
            steps {
                echo "🚀 Deploying to Kubernetes..."
                script {
                    sh """
                    kubectl apply -f k8s/namespace.yaml
                    kubectl apply -f k8s/mysql-deployment.yaml -n ${K8S_NAMESPACE}
                    kubectl apply -f k8s/mysql-service.yaml -n ${K8S_NAMESPACE}
                    """
                    sh """
                    for i in {1..30}; do
                        if kubectl get pods -n ${K8S_NAMESPACE} -l app=mysql | grep -q Running; then
                            echo "✅ MySQL is ready!"
                            break
                        fi
                        echo "Waiting for MySQL... (attempt \$i/30)"
                        sleep 10
                    done
                    """
                    sh """
                    sed -i 's|image:.*|image: ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION}|g' k8s/app-deployment-blue.yaml
                    kubectl apply -f k8s/app-deployment-blue.yaml -n ${K8S_NAMESPACE}
                    kubectl apply -f k8s/app-service.yaml -n ${K8S_NAMESPACE}
                    """
                }
            }
        }
        
        // STAGE 13: HEALTH CHECK
        stage('Health Check & Verification') {
            steps {
                echo "🏥 Running health checks..."
                script {
                    sh """
                    for i in {1..30}; do
                        if kubectl get pods -n ${K8S_NAMESPACE} -l app=hotel-booking | grep -q Running; then
                            echo "✅ Application pods are ready!"
                            break
                        fi
                        echo "Waiting for application pods... (attempt \$i/30)"
                        sleep 10
                    done
                    sleep 30
                    """
                    sh """
                    kubectl get svc -n ${K8S_NAMESPACE}
                    kubectl get pods -n ${K8S_NAMESPACE}
                    timeout 60s kubectl port-forward svc/hotel-booking-service 8080:8080 -n ${K8S_NAMESPACE} &
                    sleep 10
                    curl -f http://localhost:8080/actuator/health || exit 1
                    pkill -f "kubectl port-forward"
                    """
                }
            }
        }
    }
    
    post {
        always {
            echo "📋 Pipeline execution completed!"
            publishHTML([allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: '.', reportFiles: 'trivy-security-report.html', reportName: 'Trivy Security Report'])
            cleanWs()
        }
        success {
            echo "🎉 Pipeline executed successfully!"
        }
        failure {
            echo "❌ Pipeline failed!"
        }
    }
}
