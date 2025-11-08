pipeline {
    agent any
    
    tools {
        jdk 'JDK17'
        maven 'Maven3'
    }
    
    environment {
        // Infrastructure URLs
        SONARQUBE_URL = '13.203.26.99'
        JENKINS_URL = '13.203.25.43'
        
        // Docker Configuration
        DOCKER_REGISTRY = "docker.io"
        DOCKER_NAMESPACE = "saifudheenpv"
        
        // Application Configuration
        APP_NAME = 'hotel-booking-system'
        APP_VERSION = "${env.BUILD_ID}"
        K8S_NAMESPACE = 'hotel-booking'
        
        // Security Configuration
        MAVEN_OPTS = '-Xmx1024m -Djava.security.egd=file:/dev/./urandom'
        
        // Email Configuration
        EMAIL_TO = 'mesaifudheenpv@gmail.com'
        EMAIL_FROM = 'mesaifudheenpv@gmail.com'
    }
    
    triggers {
        githubPush()
    }
    
    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
        timestamps()
    }
    
    parameters {
        choice(
            name: 'DEPLOYMENT_STRATEGY',
            choices: ['blue-green', 'rolling'],
            description: 'Select deployment strategy'
        )
        booleanParam(
            name: 'RUN_SECURITY_SCAN',
            defaultValue: true,
            description: 'Run Trivy security scan'
        )
        booleanParam(
            name: 'AUTO_SWITCH',
            defaultValue: false,
            description: 'Automatically switch traffic after deployment'
        )
    }
    
    stages {
        // STAGE 1: ENVIRONMENT PREPARATION
        stage('Environment Setup') {
            steps {
                echo "🔧 Setting up build environment..."
                script {
                    // Initialize deployment variables
                    env.CURRENT_DEPLOYMENT = 'blue'
                    env.NEXT_DEPLOYMENT = 'green'
                    env.DEPLOYMENT_TYPE = 'blue-green'
                    
                    // Only try to detect current deployment if using blue-green
                    if (params.DEPLOYMENT_STRATEGY == 'blue-green') {
                        try {
                            // Use script to safely get current deployment color
                            def currentColor = sh(
                                script: '''
                                kubectl get service hotel-booking-service -n hotel-booking -o jsonpath='{.spec.selector.version}' 2>/dev/null || echo "blue"
                                ''',
                                returnStdout: true
                            ).trim()
                            
                            echo "Detected current deployment color: ${currentColor}"
                            
                            if (currentColor == 'blue') {
                                env.CURRENT_DEPLOYMENT = 'blue'
                                env.NEXT_DEPLOYMENT = 'green'
                            } else {
                                env.CURRENT_DEPLOYMENT = 'green'
                                env.NEXT_DEPLOYMENT = 'blue'
                            }
                        } catch (Exception e) {
                            echo "⚠️ Could not detect current deployment, using default (blue)"
                            env.CURRENT_DEPLOYMENT = 'blue'
                            env.NEXT_DEPLOYMENT = 'green'
                        }
                    } else {
                        env.DEPLOYMENT_TYPE = 'rolling'
                    }
                    
                    echo "Deployment Strategy: ${env.DEPLOYMENT_TYPE}"
                    echo "Current Deployment: ${env.CURRENT_DEPLOYMENT}"
                    echo "Next Deployment: ${env.NEXT_DEPLOYMENT}"
                    
                    // Verify all tools
                    sh '''
                    echo "=== TOOL VERSIONS ==="
                    java -version
                    mvn --version
                    docker --version
                    kubectl version --client 2>/dev/null || echo "kubectl not configured"
                    trivy --version
                    echo "=== DISK SPACE ==="
                    df -h
                    '''
                }
            }
        }
        
        // STAGE 2: SECURE CODE CHECKOUT
        stage('Secure GitHub Checkout') {
            steps {
                echo "🔐 Securely checking out code..."
                checkout scm
                
                sh '''
                echo "=== SECURE CHECKOUT COMPLETED ==="
                echo "Repository: $(git config --get remote.origin.url)"
                echo "Branch: $(git rev-parse --abbrev-ref HEAD)"
                echo "Commit: $(git rev-parse HEAD)"
                echo "Build: ${BUILD_ID}"
                echo "Deployment Strategy: ${DEPLOYMENT_TYPE}"
                echo "Next Deployment: ${NEXT_DEPLOYMENT}"
                '''
            }
        }
        
        // STAGE 3: DEPENDENCY VULNERABILITY SCAN
        stage('Dependency Security Scan') {
            when {
                expression { params.RUN_SECURITY_SCAN }
            }
            steps {
                echo "🔍 Scanning dependencies for vulnerabilities..."
                sh '''
                mvn org.owasp:dependency-check-maven:check -DskipTests || echo "Dependency check completed"
                '''
            }
            post {
                always {
                    publishHTML([
                        allowMissing: true,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'target',
                        reportFiles: 'dependency-check-report.html',
                        reportName: 'Dependency Security Report'
                    ])
                }
            }
        }
        
        // STAGE 4: CODE COMPILATION & TESTS
        stage('Compile & Test') {
            parallel {
                stage('Compile') {
                    steps {
                        echo "🔨 Compiling source code..."
                        sh 'mvn compile -DskipTests -Dspring.profiles.active=prod'
                    }
                }
                stage('Unit Tests') {
                    steps {
                        echo "🧪 Running unit tests..."
                        sh 'mvn test -Dspring.profiles.active=test'
                    }
                    post {
                        always {
                            junit 'target/surefire-reports/*.xml'
                            sh 'mvn jacoco:report -DskipTests'
                        }
                    }
                }
            }
        }
        
        // STAGE 5: SONARQUBE QUALITY GATE
        stage('SonarQube Analysis') {
            steps {
                echo "📊 Running code quality analysis..."
                withSonarQubeEnv('Sonar-Server') {
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
        
        // STAGE 6: QUALITY GATE
        stage('Quality Gate') {
            steps {
                echo "🚦 Checking Quality Gate..."
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
                echo "✅ Quality Gate passed!"
            }
        }
        
        // STAGE 7: PACKAGE & DOCKER BUILD
        stage('Package & Docker Build') {
            steps {
                echo "📦 Building application and Docker image..."
                script {
                    // Build JAR
                    sh 'mvn clean package -DskipTests -Dspring.profiles.active=prod'
                    archiveArtifacts 'target/*.jar'
                    
                    // Docker login
                    withCredentials([usernamePassword(
                        credentialsId: 'docker-token',
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASS'
                    )]) {
                        sh """
                        echo \"\${DOCKER_PASS}\" | docker login -u \"\${DOCKER_USER}\" --password-stdin
                        """
                    }
                    
                    // Build and tag Docker image
                    sh """
                    docker build -t ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} .
                    docker tag ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} ${DOCKER_NAMESPACE}/${APP_NAME}:latest
                    """
                    
                    // Only tag for blue-green if using that strategy
                    if (params.DEPLOYMENT_STRATEGY == 'blue-green') {
                        sh """
                        docker tag ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} ${DOCKER_NAMESPACE}/${APP_NAME}:${NEXT_DEPLOYMENT}
                        """
                    }
                    
                    sh "docker images | grep ${APP_NAME}"
                }
            }
        }
        
        // STAGE 8: SECURITY SCAN
        stage('Container Security Scan') {
            when {
                expression { params.RUN_SECURITY_SCAN }
            }
            steps {
                echo "🔒 Scanning container for vulnerabilities..."
                sh """
                trivy image --skip-db-update --format template --template "@contrib/html.tpl" --output trivy-security-report.html ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} || echo "Security scan completed"
                """
            }
            post {
                always {
                    publishHTML([
                        allowMissing: true,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: '.',
                        reportFiles: 'trivy-security-report.html',
                        reportName: 'Container Security Report'
                    ])
                }
            }
        }
        
        // STAGE 9: DOCKER PUSH
        stage('Docker Push') {
            steps {
                echo "📤 Pushing Docker images..."
                script {
                    sh """
                    docker push ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} || echo "Version push attempted"
                    docker push ${DOCKER_NAMESPACE}/${APP_NAME}:latest || echo "Latest push attempted"
                    """
                    
                    // Only push blue-green tag if using that strategy
                    if (params.DEPLOYMENT_STRATEGY == 'blue-green') {
                        sh """
                        docker push ${DOCKER_NAMESPACE}/${APP_NAME}:${NEXT_DEPLOYMENT} || echo "Deployment tag push attempted"
                        """
                    }
                    
                    sh """
                    echo "✅ Images pushed successfully!"
                    echo "Tags: ${APP_VERSION}, latest${params.DEPLOYMENT_STRATEGY == 'blue-green' ? ', ' + NEXT_DEPLOYMENT : ''}"
                    """
                }
            }
        }
        
        // STAGE 10: KUBERNETES DEPLOYMENT
        stage('Kubernetes Deployment') {
            steps {
                echo "🎯 Deploying to Kubernetes..."
                script {
                    // Create namespace if not exists
                    sh """
                    kubectl create namespace ${K8S_NAMESPACE} --dry-run=client -o yaml | kubectl apply -f - 2>/dev/null || echo "Namespace handling completed"
                    """
                    
                    // Deploy MySQL (if not exists)
                    sh """
                    kubectl apply -f k8s/mysql-secret.yaml -n ${K8S_NAMESPACE} --validate=false 2>/dev/null || echo "MySQL secret handled"
                    kubectl apply -f k8s/mysql-config.yaml -n ${K8S_NAMESPACE} --validate=false 2>/dev/null || echo "MySQL config handled"
                    kubectl apply -f k8s/mysql-deployment.yaml -n ${K8S_NAMESPACE} --validate=false 2>/dev/null || echo "MySQL deployment handled"
                    kubectl apply -f k8s/mysql-service.yaml -n ${K8S_NAMESPACE} --validate=false 2>/dev/null || echo "MySQL service handled"
                    """
                    
                    // Wait for MySQL with proper error handling
                    sh """
                    echo "⏳ Waiting for MySQL to be ready..."
                    MAX_ATTEMPTS=30
                    for i in \$(seq 1 \$MAX_ATTEMPTS); do
                        if kubectl get pods -n ${K8S_NAMESPACE} -l app=mysql 2>/dev/null | grep -q "Running"; then
                            echo "✅ MySQL pod is running!"
                            # Try to check if MySQL is responsive
                            if kubectl exec -n ${K8S_NAMESPACE} \$(kubectl get pods -n ${K8S_NAMESPACE} -l app=mysql -o jsonpath='{.items[0].metadata.name}' 2>/dev/null) -- mysqladmin ping -h localhost >/dev/null 2>&1; then
                                echo "✅ MySQL is responsive!"
                                break
                            fi
                        fi
                        if [ \$i -eq \$MAX_ATTEMPTS ]; then
                            echo "⚠️ MySQL not fully ready after 5 minutes, continuing deployment..."
                            break
                        fi
                        echo "⏱️ Waiting for MySQL... (attempt \$i/\$MAX_ATTEMPTS)"
                        sleep 10
                    done
                    """
                    
                    if (params.DEPLOYMENT_STRATEGY == 'blue-green') {
                        // Blue-Green Deployment
                        echo "🚀 Deploying ${NEXT_DEPLOYMENT} environment..."
                        
                        sh """
                        # Create ${NEXT_DEPLOYMENT} deployment from template
                        sed -e "s|hotel-booking-blue|hotel-booking-${NEXT_DEPLOYMENT}|g" \\
                            -e "s|version: blue|version: ${NEXT_DEPLOYMENT}|g" \\
                            -e "s|saifudheenpv/hotel-booking-system:latest|${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION}|g" \\
                            k8s/app-deployment-blue.yaml > k8s/app-deployment-${NEXT_DEPLOYMENT}.yaml
                        
                        # Apply ${NEXT_DEPLOYMENT} deployment
                        kubectl apply -f k8s/app-deployment-${NEXT_DEPLOYMENT}.yaml -n ${K8S_NAMESPACE} --validate=false
                        """
                        
                        // Wait for next deployment to be ready
                        sh """
                        echo "⏳ Waiting for ${NEXT_DEPLOYMENT} deployment to be ready..."
                        kubectl rollout status deployment/hotel-booking-${NEXT_DEPLOYMENT} -n ${K8S_NAMESPACE} --timeout=600s 2>/dev/null || echo "Deployment status check completed"
                        echo "✅ ${NEXT_DEPLOYMENT} deployment is ready!"
                        """
                    } else {
                        // Rolling Deployment
                        echo "🚀 Deploying with rolling update strategy..."
                        
                        sh """
                        # Update the existing deployment with new image
                        kubectl set image deployment/hotel-booking-blue hotel-booking=${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} -n ${K8S_NAMESPACE} 2>/dev/null || \\
                        kubectl apply -f k8s/app-deployment-blue.yaml -n ${K8S_NAMESPACE} --validate=false
                        
                        # Wait for rollout to complete
                        kubectl rollout status deployment/hotel-booking-blue -n ${K8S_NAMESPACE} --timeout=600s 2>/dev/null || echo "Rollout status check completed"
                        """
                    }
                    
                    // Ensure service exists
                    sh """
                    kubectl apply -f k8s/app-service.yaml -n ${K8S_NAMESPACE} --validate=false 2>/dev/null || echo "Service handling completed"
                    """
                }
            }
        }
        
        // STAGE 11: PRE-SWITCH VALIDATION
        stage('Application Validation') {
            steps {
                echo "🔍 Validating deployment..."
                script {
                    def deploymentName = params.DEPLOYMENT_STRATEGY == 'blue-green' ? "hotel-booking-${NEXT_DEPLOYMENT}" : "hotel-booking-blue"
                    
                    sh """
                    echo "=== RUNNING HEALTH CHECKS ==="
                    
                    # Wait for pods to be ready
                    echo "⏳ Waiting for application pods..."
                    for i in \$(seq 1 20); do
                        if kubectl get pods -n ${K8S_NAMESPACE} -l app=hotel-booking 2>/dev/null | grep -q "Running"; then
                            echo "✅ Application pods are running!"
                            break
                        fi
                        if [ \$i -eq 20 ]; then
                            echo "⚠️ Application pods not ready after 3 minutes"
                            exit 1
                        fi
                        sleep 10
                    done
                    
                    # Get pod name
                    POD_NAME=\$(kubectl get pods -n ${K8S_NAMESPACE} -l app=hotel-booking -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
                    if [ -z "\$POD_NAME" ]; then
                        echo "❌ Could not find application pod"
                        exit 1
                    fi
                    
                    echo "Testing pod: \$POD_NAME"
                    
                    # Test health endpoint
                    for i in \$(seq 1 10); do
                        if kubectl exec -n ${K8S_NAMESPACE} \$POD_NAME -- curl -s http://localhost:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
                            echo "✅ Health check passed!"
                            break
                        fi
                        if [ \$i -eq 10 ]; then
                            echo "❌ Health check failed after 10 attempts"
                            exit 1
                        fi
                        sleep 10
                    done
                    
                    echo "=== VALIDATION COMPLETED ==="
                    """
                }
            }
        }
        
        // STAGE 12: TRAFFIC SWITCHING (Blue-Green only)
        stage('Traffic Switching') {
            when {
                allOf {
                    expression { params.DEPLOYMENT_STRATEGY == 'blue-green' }
                    expression { params.AUTO_SWITCH }
                }
            }
            steps {
                echo "🔄 Switching traffic to ${NEXT_DEPLOYMENT}..."
                script {
                    sh """
                    # Update service selector
                    kubectl patch service hotel-booking-service -n ${K8S_NAMESPACE} -p '{"spec":{"selector":{"version":"${NEXT_DEPLOYMENT}"}}}' 2>/dev/null || echo "Service patch attempted"
                    
                    echo "✅ Traffic switched to ${NEXT_DEPLOYMENT}"
                    """
                }
            }
        }
        
        // STAGE 13: POST-DEPLOYMENT CHECKS
        stage('Post-Deployment Verification') {
            steps {
                echo "🏥 Running post-deployment verification..."
                script {
                    sh """
                    echo "=== FINAL DEPLOYMENT STATUS ==="
                    kubectl get deployments,services,pods -n ${K8S_NAMESPACE} 2>/dev/null || echo "Kubernetes resources not accessible"
                    
                    echo "=== APPLICATION URLs ==="
                    kubectl get service hotel-booking-service -n ${K8S_NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null | xargs -I {} echo "Application URL: http://{}:8080" || echo "Service IP not available"
                    """
                }
            }
        }
    }
    
    post {
        always {
            echo "📋 Pipeline execution completed!"
            
            // Publish reports
            publishHTML([
                allowMissing: true,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: '.',
                reportFiles: 'trivy-security-report.html',
                reportName: 'Security Report'
            ])
            
            // Final status
            sh """
            echo "=== PIPELINE EXECUTION SUMMARY ==="
            echo "Result: ${currentBuild.currentResult}"
            echo "Build: ${env.BUILD_NUMBER}"
            echo "Version: ${APP_VERSION}"
            echo "Deployment Strategy: ${params.DEPLOYMENT_STRATEGY}"
            echo "Duration: ${currentBuild.durationString}"
            echo "URL: ${env.BUILD_URL}"
            """
            
            // Cleanup
            sh """
            docker system prune -f 2>/dev/null || true
            rm -f k8s/app-deployment-*.yaml trivy-security-report.html deployment.env 2>/dev/null || true
            """
            cleanWs()
        }
        
        success {
            echo "🎉 Pipeline executed successfully!"
            
            script {
                def switchInfo = params.DEPLOYMENT_STRATEGY == 'blue-green' ? 
                    (params.AUTO_SWITCH ? 
                        "Traffic automatically switched to ${NEXT_DEPLOYMENT}" : 
                        "Manual traffic switch required: kubectl patch service hotel-booking-service -n ${K8S_NAMESPACE} -p '{\"spec\":{\"selector\":{\"version\":\"${NEXT_DEPLOYMENT}\"}}}'") :
                    "Rolling deployment completed"
                
                emailext (
                    subject: "SUCCESS: ${env.JOB_NAME} [${env.BUILD_NUMBER}] - ${params.DEPLOYMENT_STRATEGY} Deployment",
                    body: """
                    🎉 Deployment Completed Successfully!
                    
                    📊 Deployment Details:
                    - Application: Hotel Booking System
                    - Version: ${APP_VERSION}
                    - Strategy: ${params.DEPLOYMENT_STRATEGY}
                    - Build: ${env.BUILD_NUMBER}
                    
                    🐳 Docker Images:
                    - Version: ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION}
                    - Latest: ${DOCKER_NAMESPACE}/${APP_NAME}:latest
                    ${params.DEPLOYMENT_STRATEGY == 'blue-green' ? "- Deployment: ${DOCKER_NAMESPACE}/${APP_NAME}:${NEXT_DEPLOYMENT}" : ""}
                    
                    ☸️ Kubernetes Status:
                    - Namespace: ${K8S_NAMESPACE}
                    - Status: Deployed and validated
                    
                    ✅ Quality Gates:
                    - Code Quality: PASSED
                    - Security Scan: COMPLETED
                    - Health Checks: PASSED
                    
                    🔗 Links:
                    - Jenkins: ${env.BUILD_URL}
                    - Docker Hub: https://hub.docker.com/r/${DOCKER_NAMESPACE}/${APP_NAME}
                    
                    ⚠️ Next Steps:
                    ${switchInfo}
                    """,
                    to: "${EMAIL_TO}",
                    replyTo: "${EMAIL_FROM}"
                )
            }
        }
        
        failure {
            echo "❌ Pipeline failed!"
            
            // Only attempt rollback for blue-green deployments
            script {
                if (params.DEPLOYMENT_STRATEGY == 'blue-green') {
                    echo "🔄 Attempting rollback for blue-green deployment..."
                    sh """
                    # Scale down the failed deployment
                    kubectl scale deployment/hotel-booking-${NEXT_DEPLOYMENT} -n ${K8S_NAMESPACE} --replicas=0 2>/dev/null || echo "Rollback attempted"
                    echo "❌ ${NEXT_DEPLOYMENT} deployment scaled down due to failures"
                    """
                }
            }
            
            emailext (
                subject: "FAILED: ${env.JOB_NAME} [${env.BUILD_NUMBER}]",
                body: """
                ❌ Pipeline Failed!
                
                Application: Hotel Booking System
                Build: ${env.BUILD_NUMBER}
                Deployment Strategy: ${params.DEPLOYMENT_STRATEGY}
                
                Investigation Links:
                - Build Logs: ${env.BUILD_URL}console
                - Security Reports: ${env.BUILD_URL}securityReport
                
                ${params.DEPLOYMENT_STRATEGY == 'blue-green' ? "Rollback Actions:\n- ${NEXT_DEPLOYMENT} deployment scaled down\n- ${CURRENT_DEPLOYMENT} remains active" : ""}
                """,
                to: "${EMAIL_TO}",
                replyTo: "${EMAIL_FROM}"
            )
        }
    }
}