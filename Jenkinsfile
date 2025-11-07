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
        DOCKER_NAMESPACE = "saifudheenpv"
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
        MAVEN_OPTS = '-Xmx1024m'
        
        // Email Configuration
        EMAIL_TO = 'mesaifudheenpv@gmail.com'
        EMAIL_FROM = 'mesaifudheenpv@gmail.com'
    }
    
    // WEBHOOK TRIGGERS
    triggers {
        // GitHub Webhook Trigger
        githubPush()
    }
    
    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
        gitHubProjectProperty(projectUrlStr: 'https://github.com/your-username/Luxstay-Hotel-Booking-System')
    }
    
    stages {
        // STAGE 1: CODE CHECKOUT FROM GITHUB
        stage('GitHub Checkout') {
            steps {
                echo "📦 Checking out code from GitHub repository..."
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: '*/main']],
                    extensions: [
                        [
                            $class: 'CleanBeforeCheckout'
                        ],
                        [
                            $class: 'LocalBranch',
                            localBranch: 'main'
                        ]
                    ],
                    userRemoteConfigs: [[
                        url: 'https://github.com/your-username/Luxstay-Hotel-Booking-System.git',
                        credentialsId: 'github-token'
                    ]]
                ])
                
                sh '''
                    echo "=== CODE CHECKOUT COMPLETED ==="
                    echo "Repository: Luxstay-Hotel-Booking-System"
                    echo "Branch: ${GIT_BRANCH}"
                    echo "Commit: ${GIT_COMMIT}"
                    echo "Build ID: ${BUILD_ID}"
                    echo "Triggered by: ${GIT_URL}"
                '''
            }
        }
        
        // STAGE 2: MAVEN COMPILE
        stage('Maven Compile') {
            steps {
                echo "🔨 Compiling source code with Maven..."
                sh 'mvn compile -DskipTests -q'
                sh 'echo "✅ Code compilation completed successfully"'
            }
        }
        
        // STAGE 3: UNIT TESTS EXECUTION
        stage('Unit Tests') {
            steps {
                echo "🧪 Running unit tests with Maven..."
                sh 'mvn test -Dspring.profiles.active=test -q'
            }
            post {
                always {
                    echo "📊 Publishing test results..."
                    junit 'target/surefire-reports/*.xml'
                    jacoco(
                        execPattern: 'target/jacoco.exec',
                        classPattern: 'target/classes',
                        sourcePattern: 'src/main/java'
                    )
                }
            }
        }
        
        // STAGE 4: SONARQUBE CODE QUALITY
        stage('SonarQube Analysis') {
            steps {
                echo "🔍 Running SonarQube code analysis..."
                withSonarQubeEnv('Sonar-Server') {
                    sh """
                    mvn sonar:sonar \
                      -Dsonar.projectKey=hotel-booking-system \
                      -Dsonar.projectName='Hotel Booking System' \
                      -Dsonar.host.url=http://${SONARQUBE_URL}:9000 \
                      -Dsonar.login=${SONAR_TOKEN} \
                      -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                      -Dsonar.java.binaries=target/classes \
                      -Dsonar.sourceEncoding=UTF-8 \
                      -Dsonar.scm.provider=git
                    """
                }
            }
        }
        
        // STAGE 5: QUALITY GATE CHECK (WITH WEBHOOK)
        stage('Quality Gate') {
            steps {
                echo "🚦 Waiting for SonarQube Quality Gate via Webhook..."
                script {
                    // This will wait for SonarQube webhook
                    timeout(time: 15, unit: 'MINUTES') {
                        def qg = waitForQualityGate()
                        if (qg.status != 'OK') {
                            error "Pipeline aborted due to quality gate failure: ${qg.status}"
                        }
                    }
                }
                echo "✅ Quality Gate passed!"
            }
        }
        
        // STAGE 6: DEPENDENCY CHECK
        stage('Dependency Check') {
            steps {
                echo "🔒 Running OWASP Dependency Check..."
                sh '''
                    mvn org.owasp:dependency-check-maven:check \
                    -DskipTests \
                    -Dformat=HTML \
                    -Dformat=XML \
                    -q
                '''
                dependencyCheckPublisher pattern: '**/dependency-check-report.xml'
            }
        }
        
        // STAGE 7: MAVEN BUILD PACKAGE
        stage('Maven Build Package') {
            steps {
                echo "📦 Building application package..."
                sh 'mvn clean package -DskipTests -q'
                archiveArtifacts 'target/*.jar'
            }
        }
        
        // STAGE 8: NEXUS ARTIFACT PUBLISH
        stage('Nexus Publish Artifact') {
            steps {
                echo "📤 Publishing Maven artifact to Nexus..."
                script {
                    def jarFile = sh(script: 'find target/ -name "*.jar" -not -name "*sources*" | head -1', returnStdout:true).trim()
                    if (jarFile) {
                        nexusArtifactUploader(
                            nexusVersion: 'nexus3',
                            protocol: 'http',
                            nexusUrl: "${NEXUS_REPO_URL}",
                            groupId: 'com.hotel',
                            version: "${APP_VERSION}",
                            repository: "${MAVEN_REPO_NAME}",
                            credentialsId: 'nexus-creds',
                            artifacts: [
                                [artifactId: "${APP_NAME}", file: "${jarFile}", type: 'jar']
                            ]
                        )
                        echo "✅ Maven artifact published to Nexus!"
                    }
                }
            }
        }
        
        // STAGE 9: DOCKER BUILD AND TAG
        stage('Docker Build and Tag') {
            steps {
                echo "🐳 Building Docker image..."
                script {
                    sh """
                    echo "${DOCKER_CREDS_PSW}" | docker login -u ${DOCKER_CREDS_USR} --password-stdin
                    docker build -t ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} .
                    docker tag ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} ${DOCKER_NAMESPACE}/${APP_NAME}:latest
                    """
                }
            }
        }
        
        // STAGE 10: TRIVY SECURITY SCAN
        stage('Trivy Security Scan') {
            steps {
                echo "🔍 Running Trivy security scan..."
                script {
                    sh '''
                    # Install trivy if not present
                    if ! command -v trivy &> /dev/null; then
                        wget -qO - https://aquasecurity.github.io/trivy-repo/deb/public.key | sudo apt-key add -
                        echo deb https://aquasecurity.github.io/trivy-repo/deb $(lsb_release -sc) main | sudo tee -a /etc/apt/sources.list.d/trivy.list
                        sudo apt update && sudo apt install trivy -y
                    fi
                    '''
                    sh """
                    trivy image --format template --template "@contrib/html.tpl" --output trivy-security-report.html ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION}
                    trivy image --exit-code 0 --severity HIGH,CRITICAL ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION}
                    """
                }
            }
        }
        
        // STAGE 11: DOCKER PUSH
        stage('Docker Push') {
            steps {
                echo "📤 Pushing Docker image to Docker Hub..."
                script {
                    retry(3) {
                        sh """
                        docker push ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION}
                        docker push ${DOCKER_NAMESPACE}/${APP_NAME}:latest
                        """
                    }
                }
            }
        }
        
        // STAGE 12: DEPLOY TO KUBERNETES
        stage('Deploy to Kubernetes') {
            steps {
                echo "🚀 Deploying to Kubernetes..."
                script {
                    sh """
                    kubectl apply -f k8s/namespace.yaml || true
                    kubectl apply -f k8s/ -n ${K8S_NAMESPACE} --recursive
                    """
                    
                    // Update deployment with current image
                    sh """
                    sed -i 's|IMAGE_TAG|${APP_VERSION}|g' k8s/app-deployment.yaml
                    kubectl apply -f k8s/app-deployment.yaml -n ${K8S_NAMESPACE}
                    """
                    
                    // Wait for rollout
                    sh """
                    kubectl rollout status deployment/hotel-booking-app -n ${K8S_NAMESPACE} --timeout=300s
                    """
                }
            }
        }
        
        // STAGE 13: HEALTH CHECK
        stage('Health Check') {
            steps {
                echo "🏥 Running health checks..."
                script {
                    sh """
                    echo "=== KUBERNETES STATUS ==="
                    kubectl get all -n ${K8S_NAMESPACE}
                    
                    echo "=== HEALTH CHECK ==="
                    POD_NAME=\$(kubectl get pods -n ${K8S_NAMESPACE} -l app=hotel-booking -o jsonpath='{.items[0].metadata.name}')
                    kubectl exec -n ${K8S_NAMESPACE} \$POD_NAME -- curl -s http://localhost:8080/actuator/health | grep -q '"status":"UP"' && echo "✅ Health check passed!"
                    """
                }
            }
        }
    }
    
    post {
        always {
            echo "📋 Pipeline execution completed!"
            publishHTML([
                allowMissing: true,
                reportDir: '.',
                reportFiles: 'trivy-security-report.html',
                reportName: 'Trivy Security Report'
            ])
            
            // Cleanup
            sh """
            docker rmi ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} || true
            docker rmi ${DOCKER_NAMESPACE}/${APP_NAME}:latest || true
            rm -f trivy-security-report.html || true
            """
            cleanWs()
        }
        success {
            echo "🎉 Pipeline executed successfully!"
            emailext (
                subject: "SUCCESS: Pipeline '${env.JOB_NAME} [${env.BUILD_NUMBER}]'",
                body: """
                🎉 Pipeline Completed Successfully!
                
                Application: ${APP_NAME}
                Version: ${APP_VERSION}
                Build: ${env.BUILD_URL}
                
                Trigger: GitHub Webhook
                Status: All stages passed ✅
                """,
                to: "${EMAIL_TO}"
            )
        }
        failure {
            echo "❌ Pipeline failed!"
            emailext (
                subject: "FAILED: Pipeline '${env.JOB_NAME} [${env.BUILD_NUMBER}]'",
                body: """
                ❌ Pipeline Failed!
                
                Application: ${APP_NAME}
                Build: ${env.BUILD_URL}
                
                Check Jenkins logs for details.
                """,
                to: "${EMAIL_TO}"
            )
        }
    }
}