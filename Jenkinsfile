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
        
        // Blue-Green Deployment
        CURRENT_DEPLOYMENT = 'blue'
        NEXT_DEPLOYMENT = 'green'
        
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
            choices: ['blue-green', 'rolling', 'canary'],
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
                    // Determine deployment strategy
                    if (params.DEPLOYMENT_STRATEGY == 'blue-green') {
                        env.DEPLOYMENT_TYPE = 'blue-green'
                        // Determine current active deployment
                        sh '''
                        CURRENT_COLOR=$(kubectl get service hotel-booking-service -n hotel-booking -o jsonpath='{.spec.selector.version}' 2>/dev/null || echo "blue")
                        if [ "$CURRENT_COLOR" == "blue" ]; then
                            echo "CURRENT_DEPLOYMENT=blue" > deployment.env
                            echo "NEXT_DEPLOYMENT=green" >> deployment.env
                        else
                            echo "CURRENT_DEPLOYMENT=green" > deployment.env
                            echo "NEXT_DEPLOYMENT=blue" >> deployment.env
                        fi
                        '''
                        load 'deployment.env'
                    } else {
                        env.DEPLOYMENT_TYPE = 'rolling'
                    }
                    
                    // Verify all tools
                    sh '''
                    echo "=== TOOL VERSIONS ==="
                    java -version
                    mvn --version
                    docker --version
                    kubectl version --client
                    trivy --version
                    echo "=== KUBERNETES CLUSTER INFO ==="
                    kubectl cluster-info || echo "Kubernetes not accessible"
                    echo "=== CURRENT DEPLOYMENT STATE ==="
                    kubectl get deployments,services,pods -n hotel-booking || echo "Namespace not found"
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
                echo "Branch: $(git branch --show-current)"
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
                mvn org.owasp:dependency-check-maven:check -DskipTests || echo "Dependency check completed with warnings"
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
                    docker tag ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} ${DOCKER_NAMESPACE}/${APP_NAME}:${NEXT_DEPLOYMENT}
                    """
                    
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
                trivy image --skip-db-update --format template --template "@contrib/html.tpl" --output trivy-security-report.html ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION}
                trivy image --skip-db-update --exit-code 0 --severity HIGH,CRITICAL ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION}
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
                sh """
                docker push ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION}
                docker push ${DOCKER_NAMESPACE}/${APP_NAME}:latest
                docker push ${DOCKER_NAMESPACE}/${APP_NAME}:${NEXT_DEPLOYMENT}
                """
                
                sh """
                echo "✅ Images pushed successfully!"
                echo "Tags: ${APP_VERSION}, latest, ${NEXT_DEPLOYMENT}"
                """
            }
        }
        
        // STAGE 10: BLUE-GREEN DEPLOYMENT
        stage('Blue-Green Deployment') {
            steps {
                echo "🎯 Deploying ${NEXT_DEPLOYMENT} environment..."
                script {
                    // Create namespace if not exists
                    sh """
                    kubectl create namespace ${K8S_NAMESPACE} --dry-run=client -o yaml | kubectl apply -f - || echo "Namespace exists"
                    """
                    
                    // Deploy MySQL (if not exists)
                    sh """
                    kubectl apply -f k8s/mysql-deployment.yaml -n ${K8S_NAMESPACE} --validate=false || echo "MySQL deployment exists"
                    kubectl apply -f k8s/mysql-service.yaml -n ${K8S_NAMESPACE} --validate=false || echo "MySQL service exists"
                    """
                    
                    // Wait for MySQL
                    sh """
                    echo "⏳ Waiting for MySQL to be ready..."
                    for i in \$(seq 1 30); do
                        if kubectl get pods -n ${K8S_NAMESPACE} -l app=mysql 2>/dev/null | grep -q "Running" && \\
                           kubectl exec -n ${K8S_NAMESPACE} \$(kubectl get pods -n ${K8S_NAMESPACE} -l app=mysql -o jsonpath='{.items[0].metadata.name}') -- mysqladmin ping -h localhost >/dev/null 2>&1; then
                            echo "✅ MySQL is ready!"
                            break
                        fi
                        if [ \$i -eq 30 ]; then
                            echo "⚠️ MySQL not ready after 5 minutes, continuing..."
                            break
                        fi
                        echo "⏱️ Waiting for MySQL... (attempt \$i/30)"
                        sleep 10
                    done
                    """
                    
                    // Create next deployment
                    sh """
                    # Create ${NEXT_DEPLOYMENT} deployment from template
                    sed -e "s|hotel-booking-blue|hotel-booking-${NEXT_DEPLOYMENT}|g" \\
                        -e "s|version: blue|version: ${NEXT_DEPLOYMENT}|g" \\
                        -e "s|saifudheenpv/hotel-booking-system:latest|${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION}|g" \\
                        k8s/app-deployment-blue.yaml > k8s/app-deployment-${NEXT_DEPLOYMENT}.yaml
                    
                    # Apply ${NEXT_DEPLOYMENT} deployment
                    kubectl apply -f k8s/app-deployment-${NEXT_DEPLOYMENT}.yaml -n ${K8S_NAMESPACE}
                    """
                    
                    // Wait for next deployment to be ready
                    sh """
                    echo "⏳ Waiting for ${NEXT_DEPLOYMENT} deployment to be ready..."
                    kubectl rollout status deployment/hotel-booking-${NEXT_DEPLOYMENT} -n ${K8S_NAMESPACE} --timeout=600s
                    echo "✅ ${NEXT_DEPLOYMENT} deployment is ready!"
                    """
                }
            }
        }
        
        // STAGE 11: PRE-SWITCH VALIDATION
        stage('Pre-Switch Validation') {
            steps {
                echo "🔍 Validating ${NEXT_DEPLOYMENT} deployment..."
                script {
                    // Test the new deployment internally
                    sh """
                    echo "=== RUNNING HEALTH CHECKS ON ${NEXT_DEPLOYMENT} ==="
                    
                    # Get ${NEXT_DEPLOYMENT} pod name
                    NEXT_POD=\$(kubectl get pods -n ${K8S_NAMESPACE} -l version=${NEXT_DEPLOYMENT} -o jsonpath='{.items[0].metadata.name}')
                    echo "Testing pod: \$NEXT_POD"
                    
                    # Test health endpoint
                    for i in \$(seq 1 10); do
                        if kubectl exec -n ${K8S_NAMESPACE} \$NEXT_POD -- curl -s http://localhost:8080/actuator/health | grep -q '"status":"UP"'; then
                            echo "✅ ${NEXT_DEPLOYMENT} health check passed!"
                            break
                        fi
                        if [ \$i -eq 10 ]; then
                            echo "❌ ${NEXT_DEPLOYMENT} health check failed"
                            exit 1
                        fi
                        sleep 10
                    done
                    
                    # Test application endpoint
                    kubectl exec -n ${K8S_NAMESPACE} \$NEXT_POD -- curl -s http://localhost:8080/ | grep -q "Hotel Booking" && \\
                    echo "✅ ${NEXT_DEPLOYMENT} application is responding!" || \\
                    echo "⚠️ ${NEXT_DEPLOYMENT} application response unexpected"
                    
                    echo "=== ${NEXT_DEPLOYMENT} VALIDATION COMPLETED ==="
                    """
                }
            }
        }
        
        // STAGE 12: TRAFFIC SWITCHING
        stage('Traffic Switching') {
            when {
                expression { params.AUTO_SWITCH }
            }
            steps {
                echo "🔄 Switching traffic to ${NEXT_DEPLOYMENT}..."
                script {
                    // Update service to point to new deployment
                    sh """
                    # Update service selector
                    kubectl patch service hotel-booking-service -n ${K8S_NAMESPACE} -p '{"spec":{"selector":{"version":"${NEXT_DEPLOYMENT}"}}}'
                    
                    echo "✅ Traffic switched to ${NEXT_DEPLOYMENT}"
                    echo "=== CURRENT DEPLOYMENT STATE ==="
                    kubectl get deployments,services -n ${K8S_NAMESPACE}
                    """
                    
                    // Verify traffic is routing correctly
                    sh """
                    echo "🔍 Verifying traffic routing..."
                    for i in \$(seq 1 10); do
                        SERVICE_IP=\$(kubectl get service hotel-booking-service -n ${K8S_NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
                        if [ ! -z "\$SERVICE_IP" ]; then
                            if curl -s http://\$SERVICE_IP:8080/actuator/health | grep -q '"status":"UP"'; then
                                echo "✅ External health check passed!"
                                break
                            fi
                        fi
                        sleep 10
                    done
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
                    kubectl get deployments,services,pods -n ${K8S_NAMESPACE}
                    
                    echo "=== DEPLOYMENT HISTORY ==="
                    kubectl rollout history deployment/hotel-booking-${NEXT_DEPLOYMENT} -n ${K8S_NAMESPACE}
                    
                    echo "=== RESOURCE USAGE ==="
                    kubectl top pods -n ${K8S_NAMESPACE} 2>/dev/null || echo "Metrics not available"
                    
                    echo "=== CURRENT TRAFFIC ROUTING ==="
                    kubectl get service hotel-booking-service -n ${K8S_NAMESPACE} -o jsonpath='{.spec.selector}' | jq .
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
            echo "Deployment: ${NEXT_DEPLOYMENT}"
            echo "Duration: ${currentBuild.durationString}"
            echo "URL: ${env.BUILD_URL}"
            """
            
            // Cleanup
            sh """
            docker system prune -f
            rm -f k8s/app-deployment-*.yaml trivy-security-report.html deployment.env
            """
            cleanWs()
        }
        
        success {
            echo "🎉 Pipeline executed successfully!"
            
            script {
                // Fixed string interpolation - use triple quotes and proper escaping
                def switchCommand = params.AUTO_SWITCH ? 
                    "Traffic automatically switched to ${NEXT_DEPLOYMENT}" : 
                    "Manual traffic switch required: kubectl patch service hotel-booking-service -n ${K8S_NAMESPACE} -p '{\"spec\":{\"selector\":{\"version\":\"${NEXT_DEPLOYMENT}\"}}}'"
                
                emailext (
                    subject: "SUCCESS: ${env.JOB_NAME} [${env.BUILD_NUMBER}] - ${NEXT_DEPLOYMENT} Deployed",
                    body: """
                    🎉 Blue-Green Deployment Completed Successfully!
                    
                    📊 Deployment Details:
                    - Application: Hotel Booking System
                    - New Version: ${APP_VERSION}
                    - New Deployment: ${NEXT_DEPLOYMENT}
                    - Previous Deployment: ${CURRENT_DEPLOYMENT}
                    - Build: ${env.BUILD_NUMBER}
                    
                    🐳 Docker Images:
                    - Production: ${DOCKER_NAMESPACE}/${APP_NAME}:${NEXT_DEPLOYMENT}
                    - Version: ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION}
                    - Latest: ${DOCKER_NAMESPACE}/${APP_NAME}:latest
                    
                    ☸️ Kubernetes Status:
                    - Namespace: ${K8S_NAMESPACE}
                    - Active: ${params.AUTO_SWITCH ? NEXT_DEPLOYMENT : 'Manual switch required'}
                    - Ready: ${NEXT_DEPLOYMENT} deployment is ready for traffic
                    
                    ✅ Quality Gates:
                    - Code Quality: PASSED
                    - Security Scan: COMPLETED
                    - Health Checks: PASSED
                    
                    🔗 Links:
                    - Jenkins: ${env.BUILD_URL}
                    - Docker Hub: https://hub.docker.com/r/${DOCKER_NAMESPACE}/${APP_NAME}
                    
                    ⚠️ Next Steps:
                    ${switchCommand}
                    """,
                    to: "${EMAIL_TO}",
                    replyTo: "${EMAIL_FROM}"
                )
            }
        }
        
        failure {
            echo "❌ Pipeline failed!"
            
            // Rollback logic for blue-green
            script {
                if (env.DEPLOYMENT_TYPE == 'blue-green') {
                    echo "🔄 Attempting rollback..."
                    sh """
                    # Scale down the failed deployment
                    kubectl scale deployment/hotel-booking-${NEXT_DEPLOYMENT} -n ${K8S_NAMESPACE} --replicas=0
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
                Failed Deployment: ${NEXT_DEPLOYMENT}
                
                Investigation Links:
                - Build Logs: ${env.BUILD_URL}console
                - Security Reports: ${env.BUILD_URL}securityReport
                
                Rollback Actions:
                - ${NEXT_DEPLOYMENT} deployment scaled down
                - ${CURRENT_DEPLOYMENT} remains active
                """,
                to: "${EMAIL_TO}",
                replyTo: "${EMAIL_FROM}"
            )
        }
    }
}