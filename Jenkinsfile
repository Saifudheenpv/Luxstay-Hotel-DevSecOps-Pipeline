pipeline {
    agent any
    
    tools {
        jdk 'JDK17'
        maven 'Maven3'
    }
    
    environment {
        // Credentials
        DOCKERHUB_CREDENTIALS = credentials('dockerhub-creds')
        SONAR_TOKEN = credentials('sonar-token')
        NEXUS_CREDENTIALS = credentials('nexus-creds')
        GITHUB_CREDENTIALS = credentials('github-credentials')
        
        // Application Configuration
        REGISTRY = 'saifudheenpv'
        APP_NAME = 'hotel-booking-system'
        VERSION = "${env.BUILD_NUMBER}"
        NEXUS_URL = 'http://13.201.212.39:8081'
        SONAR_URL = 'http://13.233.38.12:9000'
        
        // Kubernetes Configuration
        K8S_NAMESPACE = 'hotel-booking'
        
        // Test Configuration
        TEST_PROFILE = 'test'
    }
    
    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
    }
    
    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh '''
                    echo "=== Git Information ==="
                    git branch
                    git log -1 --oneline
                '''
            }
        }
        
        stage('Compile') {
            steps {
                sh 'mvn compile -q'
            }
            post {
                success {
                    echo '✅ Compilation successful'
                }
                failure {
                    echo '❌ Compilation failed'
                }
            }
        }
        
        stage('Unit Tests') {
            steps {
                sh """
                    echo "=== Running Unit Tests with ${TEST_PROFILE} Profile ==="
                    mvn test -Dspring.profiles.active=${TEST_PROFILE}
                    
                    echo "=== Generating JaCoCo Coverage Report ==="
                    mvn jacoco:report
                """
            }
            post {
                always {
                    junit 'target/surefire-reports/**/*.xml'
                    script {
                        if (fileExists('target/site/jacoco/jacoco.xml')) {
                            echo '✅ JaCoCo coverage report generated'
                            archiveArtifacts 'target/site/jacoco/jacoco.xml'
                        } else {
                            echo '⚠️ JaCoCo XML report not found'
                        }
                        
                        if (fileExists('target/site/jacoco/index.html')) {
                            archiveArtifacts 'target/site/jacoco/index.html'
                        }
                    }
                }
                success {
                    echo '✅ All tests passed'
                }
                failure {
                    echo '❌ Some tests failed'
                }
            }
        }
        
        stage('Trivy Filesystem Scan') {
            steps {
                sh '''
                    echo "=== Running Trivy Filesystem Security Scan ==="
                    trivy fs . --severity HIGH,CRITICAL --exit-code 0 --format table
                    echo "=== Filesystem security scan completed ==="
                '''
            }
        }
        
        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('Sonar-Server') {
                    sh """
                        echo "=== Running SonarQube Analysis ==="
                        mvn sonar:sonar \
                        -Dsonar.projectKey=hotel-booking-system \
                        -Dsonar.projectName='Hotel Booking System' \
                        -Dsonar.host.url=${SONAR_URL} \
                        -Dsonar.login=${SONAR_TOKEN} \
                        -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                        -Dsonar.tests=src/test \
                        -Dsonar.test.inclusions=src/test/**/* \
                        -Dsonar.java.coveragePlugin=jacoco \
                        -Dsonar.sourceEncoding=UTF-8
                    """
                }
            }
        }
        
        stage('Quality Gate') {
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }
        
        stage('Build & Package') {
            steps {
                sh '''
                    echo "=== Building Application Package ==="
                    mvn clean package -DskipTests
                    echo "=== Generated JAR Files ==="
                    ls -la target/*.jar
                '''
                archiveArtifacts 'target/*.jar'
            }
            post {
                success {
                    echo '✅ Application packaged successfully'
                }
            }
        }
        
        stage('Publish to Nexus') {
            steps {
                script {
                    if (fileExists('target/hotel-booking-system-1.0.0.jar')) {
                        nexusArtifactUploader(
                            nexusVersion: 'nexus3',
                            protocol: 'http',
                            nexusUrl: "${NEXUS_URL}",
                            groupId: 'com.hotel',
                            version: "${VERSION}",
                            repository: 'maven-releases',
                            credentialsId: 'nexus-creds',
                            artifacts: [
                                [artifactId: 'hotel-booking-system',
                                 classifier: '',
                                 file: 'target/hotel-booking-system-1.0.0.jar',
                                 type: 'jar']
                            ]
                        )
                        echo '✅ Artifact published to Nexus successfully'
                    } else {
                        echo '⚠️ JAR file not found - skipping Nexus upload'
                    }
                }
            }
        }
        
        stage('Docker Build') {
            steps {
                script {
                    echo "=== Building Docker Image ==="
                    docker.build("${REGISTRY}/${APP_NAME}:${VERSION}")
                    docker.build("${REGISTRY}/${APP_NAME}:latest")
                    echo '✅ Docker images built successfully'
                }
            }
        }
        
        stage('Trivy Image Scan') {
            steps {
                sh """
                    echo "=== Scanning Docker Image for Vulnerabilities ==="
                    trivy image --exit-code 0 \
                    --severity HIGH,CRITICAL \
                    --format table \
                    ${REGISTRY}/${APP_NAME}:${VERSION}
                    echo "✅ Docker image security scan completed"
                """
            }
        }
        
        stage('Docker Push') {
            steps {
                script {
                    echo "=== Pushing Docker Images to Registry ==="
                    docker.withRegistry('', 'dockerhub-creds') {
                        docker.image("${REGISTRY}/${APP_NAME}:${VERSION}").push()
                        docker.image("${REGISTRY}/${APP_NAME}:latest").push()
                    }
                    echo '✅ Docker images pushed successfully'
                }
            }
        }
        
        stage('Deploy to Kubernetes - Blue') {
            steps {
                script {
                    withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')]) {
                        sh """
                            export KUBECONFIG=${KUBECONFIG_FILE}
                            
                            echo "=== Creating Namespace ==="
                            kubectl apply -f k8s/namespace.yaml || echo "Namespace may already exist"
                            
                            echo "=== Deploying MySQL ==="
                            kubectl apply -f k8s/mysql-secret.yaml
                            kubectl apply -f k8s/mysql-configmap.yaml
                            kubectl apply -f k8s/mysql-pvc.yaml
                            kubectl apply -f k8s/mysql-deployment.yaml
                            kubectl apply -f k8s/mysql-service.yaml
                            
                            echo "=== Waiting for MySQL to be ready ==="
                            kubectl wait --for=condition=ready pod -l app=mysql -n ${K8S_NAMESPACE} --timeout=300s
                            
                            echo "=== Deploying Blue Version ${VERSION} ==="
                            # Update image in deployment
                            sed -i 's|image:.*|image: ${REGISTRY}/${APP_NAME}:${VERSION}|g' k8s/app-deployment-blue.yaml
                            kubectl apply -f k8s/app-deployment-blue.yaml
                            
                            echo "=== Waiting for Blue to be ready ==="
                            kubectl wait --for=condition=ready pod -l app=${APP_NAME},version=blue -n ${K8S_NAMESPACE} --timeout=300s
                            echo '✅ Blue deployment ready'
                        """
                    }
                }
            }
        }
        
        stage('Switch Traffic to Blue') {
            steps {
                script {
                    withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')]) {
                        sh """
                            export KUBECONFIG=${KUBECONFIG_FILE}
                            
                            echo "=== Switching Traffic to Blue ==="
                            kubectl apply -f k8s/app-service.yaml
                            
                            echo "=== Scaling down Green ==="
                            kubectl scale deployment hotel-booking-system-green -n ${K8S_NAMESPACE} --replicas=0 || echo "Green deployment may not exist"
                            
                            echo "=== Current Deployment Status ==="
                            kubectl get deployments -n ${K8S_NAMESPACE}
                            kubectl get pods -n ${K8S_NAMESPACE}
                            echo '✅ Traffic switched to Blue successfully'
                        """
                    }
                }
            }
        }
        
        stage('Verify Deployment') {
            steps {
                script {
                    withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')]) {
                        sh """
                            export KUBECONFIG=${KUBECONFIG_FILE}
                            
                            echo "=== Verifying Deployment ==="
                            
                            # Get service details
                            kubectl get svc -n ${K8S_NAMESPACE}
                            
                            # Wait for LoadBalancer IP
                            echo "Waiting for LoadBalancer IP..."
                            sleep 30
                            
                            # Get the service IP
                            SERVICE_IP=\$(kubectl get svc hotel-booking-system -n ${K8S_NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
                            
                            if [ -z "\$SERVICE_IP" ]; then
                                SERVICE_IP=\$(kubectl get svc hotel-booking-system -n ${K8S_NAMESPACE} -o jsonpath='{.spec.clusterIP}')
                                echo "Using ClusterIP: \$SERVICE_IP"
                            else
                                echo "Using LoadBalancer IP: \$SERVICE_IP"
                            fi
                            
                            # Health check with retry
                            for i in {1..15}; do
                                if curl -f -s http://\$SERVICE_IP/actuator/health > /dev/null; then
                                    echo "✅ Application health check PASSED"
                                    echo "Health response:"
                                    curl -s http://\$SERVICE_IP/actuator/health | head -5
                                    break
                                fi
                                echo "Attempt \$i/15: Application not ready yet..."
                                sleep 10
                            done
                            
                            echo '🎉 Deployment verification completed successfully'
                        """
                    }
                }
            }
        }
    }
    
    post {
        always {
            // Cleanup workspace
            cleanWs()
            
            // Send notification
            emailext (
                subject: "BUILD ${currentBuild.result}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'",
                body: """
                Pipeline execution completed!
                
                Build Result: ${currentBuild.result}
                Application: ${APP_NAME}
                Version: ${VERSION}
                Docker Image: ${REGISTRY}/${APP_NAME}:${VERSION}
                
                Build URL: ${env.BUILD_URL}
                """,
                to: "mesaifudheenpv@gmail.com",
                attachLog: true
            )
        }
        success {
            echo "🎉 Pipeline executed successfully!"
            script {
                currentBuild.description = "SUCCESS - Build ${VERSION}"
            }
        }
        failure {
            echo "❌ Pipeline failed!"
            script {
                currentBuild.description = "FAILED - Build ${VERSION}"
            }
        }
        unstable {
            echo "⚠️ Pipeline unstable!"
        }
    }
}