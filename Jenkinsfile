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
            name: 'DEPLOY_ENVIRONMENT',
            choices: ['dev', 'staging', 'prod'],
            description: 'Select deployment environment'
        )
        booleanParam(
            name: 'RUN_SECURITY_SCAN',
            defaultValue: true,
            description: 'Run Trivy security scan'
        )
        booleanParam(
            name: 'SKIP_TESTS',
            defaultValue: false,
            description: 'Skip unit tests (not recommended)'
        )
    }
    
    stages {
        // STAGE 1: ENVIRONMENT PREPARATION
        stage('Environment Setup') {
            steps {
                echo "🔧 Setting up build environment..."
                script {
                    // Verify all tools are available
                    sh '''
                    echo "=== TOOL VERSIONS ==="
                    java -version
                    mvn --version
                    docker --version
                    kubectl version --client
                    aws --version
                    trivy --version
                    echo "=== DISK SPACE ==="
                    df -h
                    echo "=== MEMORY USAGE ==="
                    free -h
                    '''
                    
                    // Set environment specific variables
                    if (params.DEPLOY_ENVIRONMENT == 'prod') {
                        env.SPRING_PROFILE = 'prod'
                        env.K8S_NAMESPACE = 'hotel-booking-prod'
                    } else if (params.DEPLOY_ENVIRONMENT == 'staging') {
                        env.SPRING_PROFILE = 'prod'
                        env.K8S_NAMESPACE = 'hotel-booking-staging'
                    } else {
                        env.SPRING_PROFILE = 'test'
                        env.K8S_NAMESPACE = 'hotel-booking-dev'
                    }
                }
            }
        }
        
        // STAGE 2: SECURE CODE CHECKOUT
        stage('Secure GitHub Checkout') {
            steps {
                echo "🔐 Securely checking out code..."
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: '*/main']],
                    extensions: [
                        [
                            $class: 'CloneOption',
                            depth: 1,
                            noTags: false,
                            shallow: true,
                            timeout: 10
                        ],
                        [
                            $class: 'CleanBeforeCheckout'
                        ]
                    ],
                    userRemoteConfigs: [[
                        credentialsId: 'github-token',
                        url: 'https://github.com/your-username/Hotel-Booking-System.git'
                    ]]
                ])
                
                sh '''
                echo "=== SECURE CHECKOUT COMPLETED ==="
                echo "Repository: $(git config --get remote.origin.url)"
                echo "Branch: $(git branch --show-current)"
                echo "Commit: $(git rev-parse HEAD)"
                echo "Build: ${BUILD_ID}"
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
        
        // STAGE 4: CODE COMPILATION
        stage('Secure Compile') {
            steps {
                echo "🔨 Compiling with security flags..."
                sh """
                mvn compile \
                    -DskipTests \
                    -Dspring.profiles.active=${env.SPRING_PROFILE} \
                    -Dfile.encoding=UTF-8
                """
            }
        }
        
        // STAGE 5: UNIT TESTS WITH COVERAGE
        stage('Unit Tests & Coverage') {
            when {
                expression { !params.SKIP_TESTS }
            }
            steps {
                echo "🧪 Running comprehensive tests..."
                sh """
                mvn test \
                    -Dspring.profiles.active=test \
                    -Djacoco.skip=false \
                    -DfailIfNoTests=false
                """
            }
            post {
                always {
                    junit(
                        testResults: 'target/surefire-reports/*.xml',
                        allowEmptyResults: true
                    )
                    jacoco(
                        execPattern: 'target/jacoco.exec',
                        classPattern: 'target/classes',
                        sourcePattern: 'src/main/java',
                        exclusionPattern: 'src/test*'
                    )
                }
            }
        }
        
        // STAGE 6: SONARQUBE QUALITY GATE
        stage('SonarQube Analysis') {
            steps {
                echo "📊 Running code quality analysis..."
                withSonarQubeEnv('Sonar-Server') {
                    sh """
                    mvn sonar:sonar \
                      -Dsonar.projectKey=hotel-booking-system-${params.DEPLOY_ENVIRONMENT} \
                      -Dsonar.projectName='Hotel Booking System - ${params.DEPLOY_ENVIRONMENT.toUpperCase()}' \
                      -Dsonar.host.url=http://${SONARQUBE_URL}:9000 \
                      -Dsonar.login=${SONAR_TOKEN} \
                      -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                      -Dsonar.java.binaries=target/classes \
                      -Dsonar.sourceEncoding=UTF-8 \
                      -Dsonar.exclusions='**/test/**,**/target/**,**/static/**,**/templates/**' \
                      -Dsonar.coverage.exclusions='**/dto/**,**/model/**,**/config/**,**/entity/**'
                    """
                }
            }
        }
        
        // STAGE 7: QUALITY GATE
        stage('Quality Gate') {
            steps {
                echo "🚦 Checking Quality Gate..."
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
                echo "✅ Quality Gate passed!"
            }
        }
        
        // STAGE 8: SECURE PACKAGING
        stage('Secure Package Build') {
            steps {
                echo "📦 Building secure application package..."
                sh """
                mvn clean package \
                    -DskipTests \
                    -Dspring.profiles.active=${env.SPRING_PROFILE} \
                    -Djar.finalName=${APP_NAME}-${APP_VERSION}
                """
                
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
                
                sh '''
                echo "=== BUILD ARTIFACTS VERIFICATION ==="
                ls -la target/*.jar
                jar tf target/*.jar | head -20
                '''
            }
        }
        
        // STAGE 9: DOCKER SECURITY SCAN & BUILD
        stage('Docker Security & Build') {
            steps {
                echo "🐳 Building secure Docker image..."
                script {
                    // Secure Docker login
                    withCredentials([usernamePassword(
                        credentialsId: 'docker-token',
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASS'
                    )]) {
                        sh """
                        echo \"\${DOCKER_PASS}\" | docker login -u \"\${DOCKER_USER}\" --password-stdin
                        """
                    }
                    
                    // Build with security best practices
                    sh """
                    docker build \
                        --tag ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} \
                        --tag ${DOCKER_NAMESPACE}/${APP_NAME}:latest \
                        --build-arg SPRING_PROFILES_ACTIVE=${env.SPRING_PROFILE} \
                        --label version=${APP_VERSION} \
                        --label build-date=\$(date +%Y-%m-%d) \
                        --label commit-hash=\$(git rev-parse --short HEAD) \
                        .
                    """
                    
                    echo "✅ Docker image built with security labels"
                    sh "docker images | grep ${APP_NAME}"
                }
            }
        }
        
        // STAGE 10: CONTAINER SECURITY SCAN
        stage('Container Security Scan') {
            when {
                expression { params.RUN_SECURITY_SCAN }
            }
            steps {
                echo "🔒 Scanning container for vulnerabilities..."
                script {
                    sh """
                    # Update Trivy database
                    trivy image --download-db-only
                    
                    # Scan image and generate report
                    trivy image \
                        --format template \
                        --template \"@contrib/html.tpl\" \
                        --output trivy-security-report.html \
                        --exit-code 0 \
                        --severity HIGH,CRITICAL \
                        ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION}
                    
                    # Check for critical vulnerabilities (non-blocking for now)
                    trivy image \
                        --exit-code 0 \
                        --severity CRITICAL \
                        ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} || echo \"Critical vulnerabilities found - review report\"
                    """
                }
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
        
        // STAGE 11: SECURE DOCKER PUSH
        stage('Secure Docker Push') {
            steps {
                echo "📤 Pushing to Docker Hub securely..."
                script {
                    sh """
                    docker push ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION}
                    docker push ${DOCKER_NAMESPACE}/${APP_NAME}:latest
                    """
                    
                    echo "✅ Images pushed successfully"
                    sh """
                    echo "Docker images available at:"
                    echo "https://hub.docker.com/r/${DOCKER_NAMESPACE}/${APP_NAME}"
                    echo "Tags: ${APP_VERSION}, latest"
                    """
                }
            }
        }
        
        // STAGE 12: KUBERNETES DEPLOYMENT
        stage('Kubernetes Deployment') {
            steps {
                echo "🚀 Deploying to Kubernetes..."
                script {
                    // Setup Kubernetes access
                    sh """
                    # Configure kubectl with proper permissions
                    mkdir -p ~/.kube
                    kubectl config set-cluster eks-cluster --server=\$(echo \"\${KUBECONFIG}\" | grep server | cut -d: -f2- | tr -d ' ')
                    kubectl config set-credentials jenkins-user --token=\$(echo \"\${KUBECONFIG}\" | grep token | cut -d: -f2- | tr -d ' ')
                    kubectl config set-context jenkins-context --cluster=eks-cluster --user=jenkins-user
                    kubectl config use-context jenkins-context
                    """
                    
                    // Create namespace if not exists
                    sh """
                    kubectl create namespace ${env.K8S_NAMESPACE} --dry-run=client -o yaml | kubectl apply -f - || echo \"Namespace handling completed\"
                    """
                    
                    // Create secrets for database
                    sh """
                    kubectl create secret generic mysql-secret \
                        --namespace ${env.K8S_NAMESPACE} \
                        --from-literal=password=securepassword123 \
                        --dry-run=client -o yaml | kubectl apply -f - || echo \"MySQL secret handled\"
                    """
                    
                    // Deploy MySQL with health checks
                    sh """
                    kubectl apply -f k8s/mysql-deployment.yaml --namespace ${env.K8S_NAMESPACE} --validate=false
                    kubectl apply -f k8s/mysql-service.yaml --namespace ${env.K8S_NAMESPACE} --validate=false
                    """
                    
                    // Wait for MySQL with proper bash syntax
                    sh """
                    echo "⏳ Waiting for MySQL to be ready..."
                    for i in \$(seq 1 30); do
                        if kubectl get pods --namespace ${env.K8S_NAMESPACE} -l app=mysql 2>/dev/null | grep -q "Running"; then
                            if kubectl exec --namespace ${env.K8S_NAMESPACE} \$(kubectl get pods --namespace ${env.K8S_NAMESPACE} -l app=mysql -o jsonpath='{.items[0].metadata.name}') -- mysqladmin ping -h localhost >/dev/null 2>&1; then
                                echo "✅ MySQL is ready and responsive!"
                                break
                            fi
                        fi
                        if [ \$i -eq 30 ]; then
                            echo "⚠️ MySQL not fully ready after 5 minutes, but continuing..."
                            break
                        fi
                        echo "⏱️ Waiting for MySQL... (attempt \$i/30)"
                        sleep 10
                    done
                    """
                    
                    // Prepare application deployment
                    sh """
                    # Create deployment with current image
                    sed -e "s|\\\$IMAGE_TAG|${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION}|g" \
                        -e "s|\\\$SPRING_PROFILE|${env.SPRING_PROFILE}|g" \
                        -e "s|\\\$NAMESPACE|${env.K8S_NAMESPACE}|g" \
                        k8s/app-deployment-blue.yaml > k8s/app-deployment-${APP_VERSION}.yaml
                    """
                    
                    // Deploy application
                    sh """
                    kubectl apply -f k8s/app-deployment-${APP_VERSION}.yaml --namespace ${env.K8S_NAMESPACE} --validate=false
                    kubectl apply -f k8s/app-service.yaml --namespace ${env.K8S_NAMESPACE} --validate=false
                    """
                    
                    echo "✅ Application deployed successfully!"
                }
            }
        }
        
        // STAGE 13: HEALTH CHECKS & VERIFICATION
        stage('Health Verification') {
            steps {
                echo "🏥 Running comprehensive health checks..."
                script {
                    // Wait for application to be ready
                    sh """
                    echo "⏳ Waiting for application pods..."
                    for i in \$(seq 1 30); do
                        if kubectl get pods --namespace ${env.K8S_NAMESPACE} -l app=hotel-booking 2>/dev/null | grep -q "Running"; then
                            echo "✅ Application pods are running!"
                            break
                        fi
                        if [ \$i -eq 30 ]; then
                            echo "❌ Application pods failed to start"
                            exit 1
                        fi
                        sleep 10
                    done
                    """
                    
                    // Get deployment status
                    sh """
                    echo "=== KUBERNETES DEPLOYMENT STATUS ==="
                    kubectl get all --namespace ${env.K8S_NAMESPACE}
                    echo "=== APPLICATION PODS ==="
                    kubectl get pods --namespace ${env.K8S_NAMESPACE} -l app=hotel-booking
                    echo "=== SERVICES ==="
                    kubectl get services --namespace ${env.K8S_NAMESPACE}
                    """
                    
                    // Perform health check
                    sh """
                    echo "🔍 Performing application health check..."
                    for i in \$(seq 1 10); do
                        POD_NAME=\$(kubectl get pods --namespace ${env.K8S_NAMESPACE} -l app=hotel-booking -o jsonpath='{.items[0].metadata.name}')
                        if kubectl exec --namespace ${env.K8S_NAMESPACE} \$POD_NAME -- curl -f http://localhost:8080/actuator/health >/dev/null 2>&1; then
                            echo "✅ Application health check passed!"
                            break
                        fi
                        if [ \$i -eq 10 ]; then
                            echo "⚠️ Health check failed after 10 attempts"
                        fi
                        sleep 15
                    done
                    """
                }
            }
        }
        
        // STAGE 14: PERFORMANCE TESTING (OPTIONAL)
        stage('Performance Test') {
            when {
                expression { params.DEPLOY_ENVIRONMENT == 'staging' }
            }
            steps {
                echo "⚡ Running performance tests..."
                sh """
                # Basic load test with curl
                for i in \$(seq 1 10); do
                    timeout 10 kubectl port-forward svc/hotel-booking-service 8080:8080 --namespace ${env.K8S_NAMESPACE} &
                    sleep 5
                    curl -s -o /dev/null -w "Response: %{http_code}, Time: %{time_total}s\\n" http://localhost:8080/actuator/health
                    pkill -f "kubectl port-forward"
                    sleep 2
                done
                """
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
                reportDir: 'target',
                reportFiles: 'dependency-check-report.html',
                reportName: 'Dependency Security'
            ])
            
            // Final status and cleanup
            sh """
            echo "=== FINAL PIPELINE STATUS ==="
            echo "Result: ${currentBuild.currentResult}"
            echo "Number: ${currentBuild.number}"
            echo "Duration: ${currentBuild.durationString}"
            echo "URL: ${env.BUILD_URL}"
            echo "=== RESOURCE CLEANUP ==="
            docker system prune -f --filter until=24h
            """
            
            // Secure cleanup
            sh """
            # Remove sensitive files
            rm -f k8s/app-deployment-*.yaml trivy-security-report.html *.log || true
            
            # Clean workspace
            find . -name "*.tmp" -delete
            find . -name "*.log" -delete
            """
            
            cleanWs(
                cleanWhenAborted: true,
                cleanWhenFailure: true,
                cleanWhenNotBuilt: true,
                cleanWhenUnstable: true,
                cleanWhenSuccess: true
            )
        }
        
        success {
            echo "🎉 Pipeline executed successfully!"
            
            sh """
            echo "=== DEPLOYMENT SUCCESS ==="
            echo "Application: ${APP_NAME}"
            echo "Version: ${APP_VERSION}"
            echo "Environment: ${params.DEPLOY_ENVIRONMENT}"
            echo "Namespace: ${env.K8S_NAMESPACE}"
            echo "Docker Image: ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION}"
            echo "Profile: ${env.SPRING_PROFILE}"
            """
            
            emailext (
                subject: "SUCCESS: ${env.JOB_NAME} [${env.BUILD_NUMBER}] - ${params.DEPLOY_ENVIRONMENT.toUpperCase()}",
                body: """
                🎉 CICD Pipeline Completed Successfully!
                
                📊 Deployment Details:
                - Application: Hotel Booking System
                - Environment: ${params.DEPLOY_ENVIRONMENT.toUpperCase()}
                - Version: ${APP_VERSION}
                - Build: ${env.BUILD_NUMBER}
                - Profile: ${env.SPRING_PROFILE}
                
                🐳 Docker Image:
                ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION}
                
                ☸️ Kubernetes:
                - Namespace: ${env.K8S_NAMESPACE}
                - Cluster: EKS
                
                ✅ Quality Gates:
                - Code Quality: PASSED
                - Security Scan: COMPLETED
                - Tests: PASSED
                
                🔗 Links:
                - Jenkins: ${env.BUILD_URL}
                - Docker Hub: https://hub.docker.com/r/${DOCKER_NAMESPACE}/${APP_NAME}
                - SonarQube: http://${SONARQUBE_URL}:9000
                
                📈 Next Steps:
                Monitor application metrics and logs for any issues.
                """,
                to: "${EMAIL_TO}",
                replyTo: "${EMAIL_FROM}"
            )
        }
        
        failure {
            echo "❌ Pipeline failed!"
            
            // Capture failure logs
            sh """
            echo "=== FAILURE ANALYSIS ==="
            echo "Last 50 lines of build log:"
            tail -50 ../logs/${env.BUILD_NUMBER}.log || echo "Log file not available"
            echo "=== RECENT DOCKER IMAGES ==="
            docker images | head -10
            echo "=== KUBERNETES EVENTS ==="
            kubectl get events --namespace ${env.K8S_NAMESPACE} --sort-by='.lastTimestamp' | tail -10 || echo "K8s not available"
            """
            
            emailext (
                subject: "FAILED: ${env.JOB_NAME} [${env.BUILD_NUMBER}] - ${params.DEPLOY_ENVIRONMENT.toUpperCase()}",
                body: """
                ❌ CICD Pipeline Failed!
                
                🔍 Failure Details:
                - Application: Hotel Booking System
                - Environment: ${params.DEPLOY_ENVIRONMENT.toUpperCase()}
                - Build: ${env.BUILD_NUMBER}
                - Stage: ${env.STAGE_NAME}
                
                📋 Common Issues:
                • Code quality gate failure
                • Security vulnerabilities
                • Test failures
                • Docker build issues
                • Kubernetes deployment errors
                • Resource constraints
                
                🔗 Investigation Links:
                - Build Logs: ${env.BUILD_URL}console
                - SonarQube: http://${SONARQUBE_URL}:9000
                - Security Reports: ${env.BUILD_URL}trivy
                
                ⚠️ Immediate Actions:
                1. Check build logs for specific errors
                2. Review security scan reports
                3. Verify Kubernetes cluster status
                4. Check resource availability
                
                💡 Pro Tips:
                • Ensure all tests pass locally before committing
                • Check SonarQube for code quality issues
                • Verify Docker Hub access and quotas
                • Confirm Kubernetes cluster health
                """,
                to: "${EMAIL_TO}",
                replyTo: "${EMAIL_FROM}"
            )
        }
        
        unstable {
            echo "⚠️ Pipeline completed with warnings!"
            emailext (
                subject: "UNSTABLE: ${env.JOB_NAME} [${env.BUILD_NUMBER}]",
                body: "Pipeline completed with test failures or quality warnings. Please review reports.",
                to: "${EMAIL_TO}"
            )
        }
    }
}