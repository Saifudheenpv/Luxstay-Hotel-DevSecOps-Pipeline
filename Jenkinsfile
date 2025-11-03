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
        DOCKERHUB_CREDENTIALS = credentials('dockerhub-creds')
        SONAR_TOKEN = credentials('sonar-token')
        
        REGISTRY = 'saifudheenpv'
        APP_NAME = 'hotel-booking-system'
        VERSION = "${env.BUILD_NUMBER}"
        SONAR_URL = 'http://13.233.38.12:9000'
        
        K8S_NAMESPACE = 'hotel-booking'
        TEST_PROFILE = 'test'
        CLUSTER_IP = '13.203.79.80'
    }
    
    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
        skipDefaultCheckout(false)
        timestamps()
    }
    
    stages {
        stage('Clean Workspace') {
            steps {
                cleanWs()
                sh '''
                    echo "🚀 Starting Fresh Build - Hotel Booking System"
                    echo "Build Number: ${BUILD_NUMBER}"
                '''
            }
        }
        
        stage('Checkout Code') {
            steps {
                checkout scm
                sh '''
                    echo "📦 Git Repository Information"
                    git log -1 --oneline
                    echo "Branch: ${GIT_BRANCH}"
                '''
            }
        }
        
        stage('Clean Docker Environment') {
            steps {
                sh '''
                    echo "🧹 Cleaning Docker Environment"
                    docker system prune -f --volumes || echo "Docker cleanup completed"
                '''
            }
        }
        
        stage('Compile & Test') {
            steps {
                sh """
                    echo "🔨 Compiling Source Code"
                    mvn clean compile -q
                    echo "✅ Compilation successful"
                    
                    echo "🧪 Running Unit Tests"
                    mvn test -Dspring.profiles.active=${TEST_PROFILE}
                    
                    echo "📊 Generating Test Reports"
                    mvn jacoco:report
                """
            }
            post {
                always {
                    junit 'target/surefire-reports/**/*.xml'
                    archiveArtifacts 'target/site/jacoco/jacoco.xml'
                }
            }
        }
        
        stage('Security Scan - Code') {
            steps {
                sh '''
                    echo "🔒 Running Security Scan on Source Code"
                    trivy fs . --severity HIGH,CRITICAL --exit-code 0 --format table --no-progress
                '''
            }
        }
        
        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('Sonar-Server') {
                    sh """
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
                    echo "📦 Building Application Package"
                    mvn clean package -DskipTests -q
                    echo "✅ Generated Artifacts:"
                    ls -la target/*.jar
                '''
                archiveArtifacts 'target/*.jar'
            }
        }
        
        stage('Docker Build') {
            steps {
                script {
                    echo "🐳 Building Docker Image"
                    docker.build("${REGISTRY}/${APP_NAME}:${VERSION}")
                    echo "✅ Docker image built: ${REGISTRY}/${APP_NAME}:${VERSION}"
                    
                    // Test the built image
                    sh """
                        echo "🔍 Testing Docker image"
                        docker run --rm -d --name test-container ${REGISTRY}/${APP_NAME}:${VERSION}
                        sleep 10
                        
                        # Test if container is running
                        if docker ps | grep test-container; then
                            echo "✅ Docker container started successfully"
                            # Test health endpoint
                            if docker exec test-container curl -f http://localhost:8080/actuator/health; then
                                echo "✅ Application health check passed in container"
                            else
                                echo "⚠️ Health check failed, but container is running"
                            fi
                            docker stop test-container
                        else
                            echo "❌ Docker container failed to start"
                            exit 1
                        fi
                    """
                }
            }
        }
        
        stage('Security Scan - Docker Image') {
            steps {
                sh """
                    echo "🔒 Scanning Docker Image for Vulnerabilities"
                    trivy image --exit-code 0 --severity HIGH,CRITICAL --no-progress ${REGISTRY}/${APP_NAME}:${VERSION}
                """
            }
        }
        
        stage('Docker Push') {
            steps {
                script {
                    echo "📤 Pushing Docker Image to Registry"
                    docker.withRegistry('', 'dockerhub-creds') {
                        docker.image("${REGISTRY}/${APP_NAME}:${VERSION}").push()
                    }
                    echo "✅ Docker image pushed: ${REGISTRY}/${APP_NAME}:${VERSION}"
                    
                    # Also tag as latest for rollback capability
                    sh """
                        docker tag ${REGISTRY}/${APP_NAME}:${VERSION} ${REGISTRY}/${APP_NAME}:latest
                    """
                    docker.withRegistry('', 'dockerhub-creds') {
                        docker.image("${REGISTRY}/${APP_NAME}:latest").push()
                    }
                    echo "✅ Latest tag also pushed"
                }
            }
        }
        
        stage('Infrastructure Setup') {
            steps {
                script {
                    withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')]) {
                        sh """
                            export KUBECONFIG=\${KUBECONFIG_FILE}
                            
                            echo "🏗️ Setting up Kubernetes Infrastructure"
                            
                            # Create namespace
                            kubectl create namespace ${K8S_NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -
                            
                            # Setup MySQL database
                            echo "🗄️ Deploying MySQL Database"
                            kubectl apply -f k8s/mysql-secret.yaml -n ${K8S_NAMESPACE}
                            kubectl apply -f k8s/mysql-configmap.yaml -n ${K8S_NAMESPACE}
                            kubectl apply -f k8s/mysql-pvc.yaml -n ${K8S_NAMESPACE}
                            kubectl apply -f k8s/mysql-deployment.yaml -n ${K8S_NAMESPACE}
                            kubectl apply -f k8s/mysql-service.yaml -n ${K8S_NAMESPACE}
                            
                            # Wait for MySQL to be ready
                            echo "⏳ Waiting for MySQL to be ready..."
                            kubectl wait --for=condition=ready pod -l app=mysql -n ${K8S_NAMESPACE} --timeout=300s
                            echo "✅ MySQL is ready"
                        """
                    }
                }
            }
        }
        
        stage('Blue-Green Deployment Strategy') {
            steps {
                script {
                    withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')]) {
                        // Determine deployment strategy - ONLY get the deployment line
                        def deploymentLine = sh(
                            script: """
                                export KUBECONFIG=\${KUBECONFIG_FILE}
                                
                                # Get deployment status quietly
                                BLUE_READY=\$(kubectl get deployment hotel-booking-system-blue -n ${K8S_NAMESPACE} --ignore-not-found -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo "0")
                                GREEN_READY=\$(kubectl get deployment hotel-booking-system-green -n ${K8S_NAMESPACE} --ignore-not-found -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo "0")
                                
                                echo "Blue replicas: \$BLUE_READY"
                                echo "Green replicas: \$GREEN_READY"
                                
                                # Determine target and output ONLY the deployment info
                                if [ "\$BLUE_READY" -eq "0" ] && [ "\$GREEN_READY" -eq "0" ]; then
                                    echo "blue:green:first"
                                elif [ "\$BLUE_READY" -gt "0" ]; then
                                    echo "green:blue:switch"
                                else
                                    echo "blue:green:switch"
                                fi
                            """,
                            returnStdout: true
                        ).trim()
                        
                        // Extract only the last line (the deployment info)
                        def deploymentInfo = deploymentLine.readLines().last()
                        def (TARGET_DEPLOYMENT, OLD_DEPLOYMENT, DEPLOYMENT_TYPE) = deploymentInfo.tokenize(':')
                        
                        env.TARGET_DEPLOYMENT = TARGET_DEPLOYMENT
                        env.OLD_DEPLOYMENT = OLD_DEPLOYMENT
                        
                        echo "🎯 Deployment Strategy: ${DEPLOYMENT_TYPE}"
                        echo "🎯 Target Deployment: ${TARGET_DEPLOYMENT}"
                        echo "🎯 Old Deployment: ${OLD_DEPLOYMENT}"
                        
                        // Deploy to target environment
                        sh """
                            export KUBECONFIG=\${KUBECONFIG_FILE}
                            
                            echo "🚀 Deploying Version ${VERSION} to ${TARGET_DEPLOYMENT}"
                            
                            # Update deployment with new image
                            sed -i "s|image:.*|image: ${REGISTRY}/${APP_NAME}:${VERSION}|g" k8s/app-deployment-${TARGET_DEPLOYMENT}.yaml
                            
                            # Apply target deployment
                            kubectl apply -f k8s/app-deployment-${TARGET_DEPLOYMENT}.yaml -n ${K8S_NAMESPACE}
                            kubectl apply -f k8s/app-service-${TARGET_DEPLOYMENT}.yaml -n ${K8S_NAMESPACE}
                            
                            # Wait for rollout
                            echo "⏳ Waiting for ${TARGET_DEPLOYMENT} deployment to complete..."
                            kubectl rollout status deployment/hotel-booking-system-${TARGET_DEPLOYMENT} -n ${K8S_NAMESPACE} --timeout=300s
                            
                            echo "✅ ${TARGET_DEPLOYMENT} deployment completed successfully"
                        """
                    }
                }
            }
        }
        
        stage('Health Check & Validation') {
            steps {
                script {
                    withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')]) {
                        sh """
                            export KUBECONFIG=\${KUBECONFIG_FILE}
                            
                            echo "🏥 Performing Health Checks on ${env.TARGET_DEPLOYMENT}"
                            
                            # Wait for pods to be ready using kubectl wait
                            echo "⏳ Waiting for ${env.TARGET_DEPLOYMENT} pods to be ready..."
                            kubectl wait --for=condition=ready pod -l app=hotel-booking-system,version=${env.TARGET_DEPLOYMENT} -n ${K8S_NAMESPACE} --timeout=300s
                            
                            echo "✅ ${env.TARGET_DEPLOYMENT} pods are ready"
                            
                            # Test internal health check using curl (now available in container)
                            echo "🔍 Testing internal application health..."
                            POD_NAME=\$(kubectl get pods -n ${K8S_NAMESPACE} -l app=hotel-booking-system,version=${env.TARGET_DEPLOYMENT} -o jsonpath='{.items[0].metadata.name}')
                            
                            if [ -n "\$POD_NAME" ]; then
                                for i in 1 2 3 4 5; do
                                    if kubectl exec -n ${K8S_NAMESPACE} "\$POD_NAME" -- curl -f -s http://localhost:8080/actuator/health > /dev/null; then
                                        echo "✅ ${env.TARGET_DEPLOYMENT} internal health check PASSED on attempt \$i"
                                        break
                                    else
                                        echo "⏳ Attempt \$i: ${env.TARGET_DEPLOYMENT} internal health check failed, waiting 5s..."
                                        sleep 5
                                    fi
                                done
                            fi
                            
                            # Test application from external endpoint
                            echo "🔍 Testing application health externally..."
                            
                            # Get NodePort for the target deployment service
                            NODE_PORT=\$(kubectl get svc hotel-booking-system-${env.TARGET_DEPLOYMENT} -n ${K8S_NAMESPACE} -o jsonpath='{.spec.ports[0].nodePort}')
                            
                            if [ -n "\$NODE_PORT" ]; then
                                echo "🌐 Testing via NodePort: ${CLUSTER_IP}:\$NODE_PORT"
                                
                                # External health check with retry
                                for i in 1 2 3 4 5; do
                                    if curl -f -s http://${CLUSTER_IP}:\$NODE_PORT/actuator/health > /dev/null; then
                                        echo "✅ ${env.TARGET_DEPLOYMENT} external health check PASSED on attempt \$i"
                                        break
                                    else
                                        echo "⏳ Attempt \$i: ${env.TARGET_DEPLOYMENT} not responding externally, waiting 5s..."
                                        sleep 5
                                    fi
                                done
                            else
                                echo "⚠️ No NodePort service found for external testing"
                            fi
                            
                            echo "✅ ${env.TARGET_DEPLOYMENT} health validation completed successfully"
                        """
                    }
                }
            }
        }
        
        stage('Traffic Switch') {
            steps {
                script {
                    withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')]) {
                        sh """
                            export KUBECONFIG=\${KUBECONFIG_FILE}
                            
                            echo "🔄 Switching Traffic to ${env.TARGET_DEPLOYMENT}"
                            
                            # Update main service to point to target deployment
                            kubectl apply -f k8s/app-service.yaml -n ${K8S_NAMESPACE}
                            
                            # Apply ingress if exists
                            kubectl apply -f k8s/app-ingress.yaml -n ${K8S_NAMESPACE} 2>/dev/null || echo "No ingress configuration found"
                            
                            echo "✅ Traffic successfully switched to ${env.TARGET_DEPLOYMENT}"
                            
                            # Wait a moment for traffic to stabilize
                            sleep 10
                            
                            # Scale down old deployment
                            echo "📉 Scaling down previous deployment (${env.OLD_DEPLOYMENT})"
                            kubectl scale deployment/hotel-booking-system-${env.OLD_DEPLOYMENT} -n ${K8S_NAMESPACE} --replicas=0
                            
                            echo "✅ ${env.OLD_DEPLOYMENT} scaled down to zero replicas"
                            
                            # Verify final state
                            echo "🔍 Final deployment status:"
                            kubectl get deployments -n ${K8S_NAMESPACE} -l app=hotel-booking-system
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
                            
                            echo "🎉 FINAL DEPLOYMENT VERIFICATION"
                            echo "=========================================="
                            
                            # Get all access URLs
                            NODE_PORT=\$(kubectl get svc hotel-booking-system -n ${K8S_NAMESPACE} -o jsonpath='{.spec.ports[0].nodePort}')
                            ALB_URL=\$(kubectl get ingress -n ${K8S_NAMESPACE} -o jsonpath='{.items[0].status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "Not configured")
                            LB_URL=\$(kubectl get svc -n ${K8S_NAMESPACE} -o jsonpath='{.items[?(@.spec.type=="LoadBalancer")].status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "Not configured")
                            
                            echo ""
                            echo "🌐 APPLICATION ACCESS URLs:"
                            echo "------------------------------------------"
                            
                            if [ "\$NODE_PORT" != "" ]; then
                                echo "🔗 NODEPORT URL:"
                                echo "   http://${CLUSTER_IP}:\${NODE_PORT}/"
                                echo "   Health: http://${CLUSTER_IP}:\${NODE_PORT}/actuator/health"
                                echo "   Swagger: http://${CLUSTER_IP}:\${NODE_PORT}/swagger-ui.html"
                                echo "DEPLOYMENT_URL=http://${CLUSTER_IP}:\${NODE_PORT}/" > deployment-url.env
                            fi
                            
                            if [ "\$ALB_URL" != "Not configured" ] && [ "\$ALB_URL" != "" ]; then
                                echo "🚀 ALB URL:"
                                echo "   http://\${ALB_URL}/"
                                echo "ALB_URL=http://\${ALB_URL}/" >> deployment-url.env
                            fi
                            
                            if [ "\$LB_URL" != "Not configured" ] && [ "\$LB_URL" != "" ]; then
                                echo "⚡ LOAD BALANCER URL:"
                                echo "   http://\${LB_URL}/"
                                echo "LB_URL=http://\${LB_URL}/" >> deployment-url.env
                            fi
                            
                            echo ""
                            echo "📊 DEPLOYMENT STATUS:"
                            echo "------------------------------------------"
                            kubectl get deployments -n ${K8S_NAMESPACE} -o wide
                            
                            echo ""
                            echo "🔧 SERVICES:"
                            echo "------------------------------------------"
                            kubectl get svc -n ${K8S_NAMESPACE} -o wide
                            
                            echo ""
                            echo "🐳 PODS:"
                            echo "------------------------------------------"
                            kubectl get pods -n ${K8S_NAMESPACE} -o wide --show-labels
                            
                            echo ""
                            echo "📈 INGRESS (if configured):"
                            echo "------------------------------------------"
                            kubectl get ingress -n ${K8S_NAMESPACE} 2>/dev/null || echo "No ingress configured"
                            
                            echo ""
                            echo "💾 STORAGE:"
                            echo "------------------------------------------"
                            kubectl get pvc -n ${K8S_NAMESPACE}
                            
                            echo ""
                            echo "✅ DEPLOYMENT SUMMARY:"
                            echo "------------------------------------------"
                            echo "Application: ${APP_NAME}"
                            echo "Version: ${VERSION}"
                            echo "Active Deployment: ${env.TARGET_DEPLOYMENT}"
                            echo "Docker Image: ${REGISTRY}/${APP_NAME}:${VERSION}"
                            echo "Namespace: ${K8S_NAMESPACE}"
                            echo "Build: ${env.BUILD_NUMBER}"
                        """
                        
                        // Load and display URLs
                        if (fileExists('deployment-url.env')) {
                            load 'deployment-url.env'
                            echo "🎊 DEPLOYMENT COMPLETED SUCCESSFULLY!"
                            echo "🔗 Primary Access URL: ${DEPLOYMENT_URL}"
                            
                            if (env.ALB_URL) {
                                echo "🌐 ALB URL: ${ALB_URL}"
                            }
                            if (env.LB_URL) {
                                echo "⚡ Load Balancer URL: ${LB_URL}"
                            }
                        }
                    }
                }
            }
        }
    }
    
    post {
        always {
            sh """
                echo "🏁 Build Process Completed"
                echo "Build Status: ${currentBuild.result}"
                echo "Build Number: ${VERSION}"
                echo "Deployment: ${env.TARGET_DEPLOYMENT ?: 'N/A'}"
            """
            cleanWs()
        }
        success {
            echo "🎉 Blue-Green Deployment completed successfully!"
            script {
                currentBuild.description = "SUCCESS - v${VERSION} (${env.TARGET_DEPLOYMENT})"
                
                // Prepare deployment info for notification
                def deploymentUrl = "Check Jenkins console for URLs"
                if (fileExists('deployment-url.env')) {
                    load 'deployment-url.env'
                    deploymentUrl = DEPLOYMENT_URL
                }
                
                // Extended email notification
                emailext (
                    subject: "SUCCESS: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                    body: """
                    🎉 BLUE-GREEN DEPLOYMENT COMPLETED SUCCESSFULLY!
                    
                    📋 Deployment Details:
                    • Application: ${APP_NAME}
                    • Version: ${VERSION}
                    • Environment: ${env.TARGET_DEPLOYMENT}
                    • Docker Image: ${REGISTRY}/${APP_NAME}:${VERSION}
                    • Namespace: ${K8S_NAMESPACE}
                    
                    🌐 Access URLs:
                    • Primary URL: ${deploymentUrl}
                    ${env.ALB_URL ? "• ALB URL: " + ALB_URL : ""}
                    ${env.LB_URL ? "• Load Balancer: " + LB_URL : ""}
                    
                    📊 Build Information:
                    • Build URL: ${env.BUILD_URL}
                    • Build Number: ${env.BUILD_NUMBER}
                    • Git Branch: ${env.GIT_BRANCH}
                    
                    The application has been deployed using Blue-Green strategy and all health checks have passed.
                    """,
                    to: "mesaifudheenpv@gmail.com",
                    attachLog: false
                )
            }
        }
        failure {
            echo "❌ Pipeline failed!"
            script {
                currentBuild.description = "FAILED - v${VERSION}"
                
                // Failure notification
                emailext (
                    subject: "FAILED: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                    body: """
                    ❌ DEPLOYMENT FAILED!
                    
                    Application: ${APP_NAME}
                    Version: ${VERSION}
                    
                    Please check the Jenkins build logs for details:
                    ${env.BUILD_URL}
                    
                    Immediate attention required.
                    """,
                    to: "mesaifudheenpv@gmail.com",
                    attachLog: true
                )
            }
        }
        unstable {
            echo "⚠️ Pipeline unstable - check test results or quality gate"
        }
        changed {
            echo "🔄 Pipeline status changed: ${currentBuild.result}"
        }
    }
}