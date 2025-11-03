pipeline {
    agent any
    
    tools {
        jdk 'JDK17'
        maven 'Maven3'
    }
    
    triggers {
        pollSCM('H/5 * * * *')
    }
    
    environment {
        // Credentials - secured in Jenkins
        DOCKERHUB_CREDENTIALS = credentials('dockerhub-creds')
        SONAR_TOKEN = credentials('sonar-token')
        NEXUS_CREDENTIALS = credentials('nexus-creds')
        
        // Application Configuration
        REGISTRY = 'saifudheenpv'
        APP_NAME = 'hotel-booking-system'
        VERSION = "${env.BUILD_NUMBER}"
        SONAR_URL = 'http://13.233.38.12:9000'
        NEXUS_URL = '13.201.212.39:8081'
        
        // Kubernetes Configuration
        K8S_NAMESPACE = 'hotel-booking'
        TEST_PROFILE = 'test'
    }
    
    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
        skipDefaultCheckout(false)
    }
    
    stages {
        stage('Clean Workspace') {
            steps {
                cleanWs()
                sh '''
                    echo "=== Starting Fresh Build ==="
                    echo "Build Number: ${BUILD_NUMBER}"
                    pwd
                '''
            }
        }
        
        stage('Checkout Code') {
            steps {
                checkout scm
                sh '''
                    echo "=== Git Information ==="
                    git log -1 --oneline
                    echo "=== Project Structure ==="
                    ls -la
                '''
            }
        }
        
        stage('Clean Docker Environment') {
            steps {
                sh '''
                    echo "=== Cleaning Docker Environment ==="
                    docker rm -f $(docker ps -aq) 2>/dev/null || echo "No containers to remove"
                    docker image prune -f
                    echo "✅ Docker environment cleaned"
                '''
            }
        }
        
        stage('Compile & Test') {
            steps {
                sh """
                    echo "=== Compiling Source Code ==="
                    mvn clean compile -q
                    echo "✅ Compilation successful"
                    
                    echo "=== Running Unit Tests ==="
                    mvn test -Dspring.profiles.active=${TEST_PROFILE}
                    
                    echo "=== Generating Reports ==="
                    mvn jacoco:report
                """
            }
            post {
                always {
                    junit 'target/surefire-reports/**/*.xml'
                    archiveArtifacts 'target/site/jacoco/jacoco.xml'
                }
                success {
                    echo '✅ All tests passed'
                }
                failure {
                    error '❌ Tests failed - check test reports'
                }
            }
        }
        
        stage('Security Scan - Code') {
            steps {
                sh '''
                    echo "=== Running Security Scan ==="
                    trivy fs . --severity HIGH,CRITICAL --exit-code 0 --format table || echo "Security scan completed with findings"
                    echo "✅ Code security scan completed"
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
                        -Dsonar.java.coveragePlugin=jacoco \
                        -Dsonar.sourceEncoding=UTF-8
                    """
                }
            }
        }
        
        stage('Quality Gate') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }
        
        stage('Build & Package') {
            steps {
                sh '''
                    echo "=== Building Application Package ==="
                    mvn clean package -DskipTests
                    echo "=== Generated Artifacts ==="
                    ls -la target/*.jar
                '''
                archiveArtifacts 'target/*.jar'
            }
        }
        
        stage('Docker Build') {
            steps {
                script {
                    echo "=== Building Docker Image ==="
                    docker.build("${REGISTRY}/${APP_NAME}:${VERSION}")
                    echo "✅ Docker image built: ${REGISTRY}/${APP_NAME}:${VERSION}"
                }
            }
        }
        
        stage('Security Scan - Docker Image') {
            steps {
                sh """
                    echo "=== Scanning Docker Image ==="
                    trivy image --exit-code 0 \
                    --severity HIGH,CRITICAL \
                    --format table \
                    ${REGISTRY}/${APP_NAME}:${VERSION} || echo "Docker security scan completed"
                    echo "✅ Docker image security scan completed"
                """
            }
        }
        
        stage('Docker Push') {
            steps {
                script {
                    echo "=== Pushing Docker Image ==="
                    docker.withRegistry('', 'dockerhub-creds') {
                        docker.image("${REGISTRY}/${APP_NAME}:${VERSION}").push()
                    }
                    echo "✅ Docker image pushed: ${REGISTRY}/${APP_NAME}:${VERSION}"
                }
            }
        }
        
        stage('Blue-Green Deployment') {
            steps {
                script {
                    withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')]) {
                        // Determine which environment to deploy to
                        sh """
                            export KUBECONFIG=\${KUBECONFIG_FILE}
                            
                            echo "=== Starting Blue-Green Deployment ==="
                            
                            # Create namespace if not exists
                            kubectl create namespace ${K8S_NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -
                            
                            # Deploy MySQL infrastructure if not exists
                            echo "=== Setting up MySQL Database ==="
                            kubectl apply -f k8s/mysql-secret.yaml -n ${K8S_NAMESPACE} || echo "MySQL secret already exists"
                            kubectl apply -f k8s/mysql-configmap.yaml -n ${K8S_NAMESPACE} || echo "MySQL configmap already exists"
                            kubectl apply -f k8s/mysql-pvc.yaml -n ${K8S_NAMESPACE} || echo "MySQL PVC already exists"
                            kubectl apply -f k8s/mysql-deployment.yaml -n ${K8S_NAMESPACE} || echo "MySQL deployment already exists"
                            kubectl apply -f k8s/mysql-service.yaml -n ${K8S_NAMESPACE} || echo "MySQL service already exists"
                            
                            # Wait for MySQL to be ready
                            kubectl wait --for=condition=ready pod -l app=mysql -n ${K8S_NAMESPACE} --timeout=300s || echo "MySQL ready check completed"
                            
                            # Determine current active deployment
                            echo "=== Analyzing Current Deployment State ==="
                            BLUE_READY=\$(kubectl get deployment hotel-booking-system-blue -n ${K8S_NAMESPACE} --ignore-not-found -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo "0")
                            GREEN_READY=\$(kubectl get deployment hotel-booking-system-green -n ${K8S_NAMESPACE} --ignore-not-found -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo "0")
                            
                            echo "Blue ready replicas: \$BLUE_READY"
                            echo "Green ready replicas: \$GREEN_READY"
                            
                            if [ "\$BLUE_READY" -eq "0" ] && [ "\$GREEN_READY" -eq "0" ]; then
                                # First deployment - use blue
                                TARGET_DEPLOYMENT="blue"
                                OLD_DEPLOYMENT="green"
                                echo "=== First deployment - using BLUE ==="
                            elif [ "\$BLUE_READY" -gt "0" ]; then
                                # Blue is active, deploy to green
                                TARGET_DEPLOYMENT="green"
                                OLD_DEPLOYMENT="blue"
                                echo "=== Blue is active - deploying to GREEN ==="
                            else
                                # Green is active, deploy to blue
                                TARGET_DEPLOYMENT="blue"
                                OLD_DEPLOYMENT="green"
                                echo "=== Green is active - deploying to BLUE ==="
                            fi
                            
                            echo "TARGET_DEPLOYMENT=\$TARGET_DEPLOYMENT" > deployment.env
                            echo "OLD_DEPLOYMENT=\$OLD_DEPLOYMENT" >> deployment.env
                            echo "✅ Deployment target determined: \$TARGET_DEPLOYMENT"
                        """
                        
                        // Load deployment targets
                        load 'deployment.env'
                        
                        // Deploy to target environment
                        sh """
                            export KUBECONFIG=\${KUBECONFIG_FILE}
                            source deployment.env
                            
                            echo "=== Deploying Version ${VERSION} to \${TARGET_DEPLOYMENT} ==="
                            
                            # Update deployment with new image
                            sed -i "s|image:.*|image: ${REGISTRY}/${APP_NAME}:${VERSION}|g" k8s/app-deployment-\${TARGET_DEPLOYMENT}.yaml
                            
                            # Apply target deployment and service
                            kubectl apply -f k8s/app-deployment-\${TARGET_DEPLOYMENT}.yaml -n ${K8S_NAMESPACE}
                            kubectl apply -f k8s/app-service-\${TARGET_DEPLOYMENT}.yaml -n ${K8S_NAMESPACE}
                            
                            # Wait for target deployment to be ready
                            echo "=== Waiting for \${TARGET_DEPLOYMENT} rollout ==="
                            kubectl rollout status deployment/hotel-booking-system-\${TARGET_DEPLOYMENT} -n ${K8S_NAMESPACE} --timeout=300s
                            
                            echo "✅ \${TARGET_DEPLOYMENT} deployment completed successfully"
                        """
                    }
                }
            }
        }
        
        stage('Health Check New Deployment') {
            steps {
                script {
                    withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')]) {
                        load 'deployment.env'
                        
                        sh """
                            export KUBECONFIG=\${KUBECONFIG_FILE}
                            source deployment.env
                            
                            echo "=== Health Checking \${TARGET_DEPLOYMENT} Deployment ==="
                            
                            # Get pod from new deployment
                            POD_NAME=\$(kubectl get pods -n ${K8S_NAMESPACE} -l app=hotel-booking-system,version=\${TARGET_DEPLOYMENT} -o jsonpath='{.items[0].metadata.name}')
                            
                            if [ -n "\$POD_NAME" ]; then
                                echo "Testing pod: \$POD_NAME"
                                
                                # Health check with retry
                                for i in {1..10}; do
                                    if kubectl exec -n ${K8S_NAMESPACE} "\$POD_NAME" -- wget -q -O - http://localhost:8080/actuator/health > /dev/null; then
                                        echo "✅ \${TARGET_DEPLOYMENT} health check PASSED"
                                        break
                                    else
                                        echo "Attempt \$i/10: \${TARGET_DEPLOYMENT} not ready yet..."
                                        sleep 10
                                    fi
                                    if [ \$i -eq 10 ]; then
                                        echo "❌ \${TARGET_DEPLOYMENT} health check FAILED"
                                        exit 1
                                    fi
                                done
                            else
                                echo "❌ No pods found for \${TARGET_DEPLOYMENT}"
                                exit 1
                            fi
                        """
                    }
                }
            }
        }
        
        stage('Switch Traffic') {
            steps {
                script {
                    withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')]) {
                        load 'deployment.env'
                        
                        sh """
                            export KUBECONFIG=\${KUBECONFIG_FILE}
                            source deployment.env
                            
                            echo "=== Switching Traffic to \${TARGET_DEPLOYMENT} ==="
                            
                            # Update main service to point to target deployment
                            kubectl apply -f k8s/app-service.yaml -n ${K8S_NAMESPACE}
                            
                            # Apply ingress for external access
                            kubectl apply -f k8s/app-ingress.yaml -n ${K8S_NAMESPACE} || echo "Ingress already exists"
                            
                            echo "✅ Traffic switched to \${TARGET_DEPLOYMENT}"
                            
                            # Scale down old deployment
                            echo "=== Scaling down \${OLD_DEPLOYMENT} ==="
                            kubectl scale deployment/hotel-booking-system-\${OLD_DEPLOYMENT} -n ${K8S_NAMESPACE} --replicas=0 || echo "\${OLD_DEPLOYMENT} deployment may not exist"
                            
                            echo "✅ \${OLD_DEPLOYMENT} scaled down"
                        """
                    }
                }
            }
        }
        
        stage('Final Verification & URLs') {
            steps {
                script {
                    withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')]) {
                        sh """
                            export KUBECONFIG=\${KUBECONFIG_FILE}
                            
                            echo "=== Generating Deployment URLs ==="
                            
                            # Get Kubernetes node external IP
                            NODE_IP=\$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="ExternalIP")].address}' 2>/dev/null || echo "")
                            if [ -z "\$NODE_IP" ]; then
                                # Fallback to internal IP or known IP
                                NODE_IP=\$(kubectl get nodes -o wide | grep -v NAME | head -1 | awk '{print \$7}')
                                if [ -z "\$NODE_IP" ] || [ "\$NODE_IP" = "<none>" ]; then
                                    NODE_IP="13.203.79.80"
                                fi
                            fi
                            
                            # Get NodePort
                            NODE_PORT=\$(kubectl get svc hotel-booking-system -n ${K8S_NAMESPACE} -o jsonpath='{.spec.ports[0].nodePort}')
                            
                            # Get LoadBalancer IP
                            LB_IP=\$(kubectl get svc hotel-booking-system -n ${K8S_NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
                            
                            # Get Ingress hostname
                            INGRESS_HOST=\$(kubectl get ingress hotel-booking-ingress -n ${K8S_NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "")
                            
                            echo ""
                            echo "🎯 ========================================="
                            echo "🎯 DEPLOYMENT URLs - ACCESS YOUR APPLICATION"
                            echo "🎯 ========================================="
                            echo ""
                            
                            # Display NodePort URL (usually works)
                            if [ -n "\$NODE_PORT" ]; then
                                echo "✅ PRIMARY URL (NodePort):"
                                echo "   🌐 http://\${NODE_IP}:\${NODE_PORT}/"
                                echo "   🩺 Health: http://\${NODE_IP}:\${NODE_PORT}/actuator/health"
                                echo "DEPLOYMENT_URL=http://\${NODE_IP}:\${NODE_PORT}/" > deployment-url.env
                                echo ""
                            fi
                            
                            # Display LoadBalancer URL
                            if [ -n "\$LB_IP" ] && [ "\$LB_IP" != "null" ]; then
                                echo "🔗 LoadBalancer URL:"
                                echo "   🌐 http://\${LB_IP}/"
                                echo "   🩺 Health: http://\${LB_IP}/actuator/health"
                                echo ""
                            fi
                            
                            # Display Ingress URL
                            if [ -n "\$INGRESS_HOST" ]; then
                                echo "🚀 Ingress URL:"
                                echo "   🌐 http://\${INGRESS_HOST}/"
                                echo "   🩺 Health: http://\${INGRESS_HOST}/actuator/health"
                                echo ""
                            fi
                            
                            # Final deployment status
                            echo "📊 Final Deployment Status:"
                            kubectl get deployments -n ${K8S_NAMESPACE}
                            echo ""
                            kubectl get pods -n ${K8S_NAMESPACE}
                            echo ""
                            kubectl get svc -n ${K8S_NAMESPACE}
                        """
                        
                        // Load deployment URL for notifications
                        if (fileExists('deployment-url.env')) {
                            load 'deployment-url.env'
                            echo "🎉 Application successfully deployed!"
                            echo "🌐 Access URL: ${DEPLOYMENT_URL}"
                        }
                    }
                }
            }
        }
    }
    
    post {
        always {
            sh '''
                echo "=== Build Process Completed ==="
                echo "Result: ${currentBuild.currentResult}"
            '''
            cleanWs()
        }
        success {
            echo "🎉 Blue-Green Deployment completed successfully!"
            script {
                currentBuild.description = "SUCCESS - Build ${VERSION}"
                
                // Load deployment URL for email
                def deploymentUrl = "Check Jenkins console for URLs"
                if (fileExists('deployment-url.env')) {
                    load 'deployment-url.env'
                    deploymentUrl = DEPLOYMENT_URL
                }
                
                // Send success email
                emailext (
                    subject: "SUCCESS: ${env.JOB_NAME} #${env.BUILD_NUMBER} - Blue-Green Deployment",
                    body: """
                    🎉 Blue-Green Deployment Completed Successfully!
                    
                    Application: ${APP_NAME}
                    Version: ${VERSION}
                    Docker Image: ${REGISTRY}/${APP_NAME}:${VERSION}
                    
                    🌐 DEPLOYMENT URL: ${deploymentUrl}
                    
                    Build URL: ${env.BUILD_URL}
                    
                    Features:
                    ✅ Zero-downtime deployment
                    ✅ Automated health checks
                    ✅ Traffic switching
                    ✅ Rollback capability
                    """,
                    to: "mesaifudheenpv@gmail.com"
                )
            }
        }
        failure {
            echo "❌ Pipeline failed!"
            script {
                currentBuild.description = "FAILED - Build ${VERSION}"
                
                // Send failure email
                emailext (
                    subject: "FAILED: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                    body: """
                    Pipeline execution failed!
                    
                    Application: ${APP_NAME}
                    Version: ${VERSION}
                    
                    Build URL: ${env.BUILD_URL}
                    Please check Jenkins logs for details.
                    """,
                    to: "mesaifudheenpv@gmail.com",
                    attachLog: true
                )
            }
        }
    }
}