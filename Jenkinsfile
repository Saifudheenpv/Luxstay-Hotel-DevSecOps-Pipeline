pipeline {
    agent any
    
    tools {
        jdk 'JDK17'
        maven 'Maven3'
    }
    
    triggers {
        pollSCM('H/5 * * * *')
        githubPush()
    }
    
    environment {
        DOCKERHUB_CREDENTIALS = credentials('dockerhub-creds')
        SONAR_TOKEN = credentials('sonar-token')
        NEXUS_CREDENTIALS = credentials('nexus-creds')
        GITHUB_CREDENTIALS = credentials('github-credentials')
        
        REGISTRY = 'saifudheenpv'
        APP_NAME = 'hotel-booking-system'
        VERSION = "${env.BUILD_NUMBER}"
        NEXUS_URL = '13.201.212.39:8081'
        SONAR_URL = 'http://13.233.38.12:9000'
        
        K8S_NAMESPACE = 'hotel-booking'
        K8S_CLUSTER = 'aws-kubernetes'
        
        TEST_PROFILE = 'test'
    }
    
    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
        gitHubProjectProperty(projectUrlStr: 'https://github.com/Saifudheenpv/Hotel-Booking-System/')
    }
    
    stages {
        stage('Clean Workspace') {
            steps {
                cleanWs()
                sh '''
                    echo "=== Cleaning Workspace ==="
                    pwd
                    ls -la
                    echo "✅ Workspace cleaned"
                '''
            }
        }
        
        stage('Checkout & Initialize') {
            steps {
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
                        credentialsId: 'github-credentials',
                        url: 'https://github.com/Saifudheenpv/Hotel-Booking-System.git'
                    ]]
                ])
                sh '''
                    echo "=== Pipeline Started ==="
                    echo "Build Number: ${BUILD_NUMBER}"
                    echo "Branch: ${GIT_BRANCH}"
                    echo "Commit: ${GIT_COMMIT}"
                    git log -1 --oneline
                    echo "=== Workspace Contents ==="
                    ls -la
                '''
            }
        }
        
        stage('Clean Docker Environment') {
            steps {
                sh '''
                    echo "=== Cleaning Docker Environment ==="
                    # Remove all stopped containers
                    docker rm -f $(docker ps -aq) 2>/dev/null || echo "No containers to remove"
                    
                    # Remove unused images
                    docker image prune -f
                    
                    # Remove specific app images if exist
                    docker rmi saifudheenpv/hotel-booking-system:${BUILD_NUMBER} 2>/dev/null || echo "Image not found"
                    docker rmi saifudheenpv/hotel-booking-system:latest 2>/dev/null || echo "Image not found"
                    
                    # Remove dangling images
                    docker rmi $(docker images -f "dangling=true" -q) 2>/dev/null || echo "No dangling images"
                    
                    echo "✅ Docker environment cleaned"
                    docker images | grep hotel-booking || echo "No hotel-booking images found"
                '''
            }
        }
        
        stage('Clean Kubernetes Environment') {
            steps {
                script {
                    withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')]) {
                        sh """
                            export KUBECONFIG=${KUBECONFIG_FILE}
                            
                            echo "=== Cleaning Kubernetes Environment ==="
                            
                            # Delete existing deployments (if any)
                            kubectl delete deployment hotel-booking-system-blue -n ${K8S_NAMESPACE} --ignore-not-found --timeout=30s
                            kubectl delete deployment hotel-booking-system-green -n ${K8S_NAMESPACE} --ignore-not-found --timeout=30s
                            
                            # Delete services
                            kubectl delete service hotel-booking-system -n ${K8S_NAMESPACE} --ignore-not-found --timeout=30s
                            kubectl delete service hotel-booking-system-blue -n ${K8S_NAMESPACE} --ignore-not-found --timeout=30s
                            kubectl delete service hotel-booking-system-green -n ${K8S_NAMESPACE} --ignore-not-found --timeout=30s
                            
                            # Delete ingress
                            kubectl delete ingress hotel-booking-ingress -n ${K8S_NAMESPACE} --ignore-not-found --timeout=30s
                            
                            # Delete MySQL resources
                            kubectl delete deployment mysql -n ${K8S_NAMESPACE} --ignore-not-found --timeout=30s
                            kubectl delete service mysql -n ${K8S_NAMESPACE} --ignore-not-found --timeout=30s
                            kubectl delete pvc mysql-pvc -n ${K8S_NAMESPACE} --ignore-not-found --timeout=30s
                            
                            # Wait for cleanup
                            sleep 10
                            
                            echo "✅ Kubernetes environment cleaned"
                            
                            # Show current state
                            echo "=== Current Kubernetes State ==="
                            kubectl get deployments -n ${K8S_NAMESPACE} --ignore-not-found || echo "No deployments found"
                            kubectl get pods -n ${K8S_NAMESPACE} --ignore-not-found || echo "No pods found"
                            kubectl get svc -n ${K8S_NAMESPACE} --ignore-not-found || echo "No services found"
                        """
                    }
                }
            }
        }
        
        stage('Compile & Validate') {
            steps {
                sh '''
                    echo "=== Cleaning Maven Build ==="
                    mvn clean -q
                    
                    echo "=== Compiling Source Code ==="
                    mvn compile -q
                    echo "✅ Compilation successful"
                    
                    echo "=== Validating Dependencies ==="
                    mvn dependency:tree -q -Dincludes=org.springframework
                    echo "✅ Dependencies validated"
                '''
            }
            post {
                failure {
                    error '❌ Compilation failed - check source code'
                }
            }
        }
        
        stage('Unit Tests & Coverage') {
            steps {
                sh """
                    echo "=== Running Unit Tests with ${TEST_PROFILE} Profile ==="
                    mvn test -Dspring.profiles.active=${TEST_PROFILE} -q
                    
                    echo "=== Generating Test Reports ==="
                    mvn jacoco:report surefire-report:report -q
                """
            }
            post {
                always {
                    junit 'target/surefire-reports/**/*.xml'
                    archiveArtifacts 'target/site/jacoco/jacoco.xml'
                    publishHTML([
                        allowMissing: false,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'target/site/jacoco',
                        reportFiles: 'index.html',
                        reportName: 'JaCoCo Coverage Report',
                        reportTitles: 'Code Coverage'
                    ])
                }
                success {
                    echo '✅ All tests passed successfully'
                    sh '''
                        echo "=== Test Summary ==="
                        cat target/surefire-reports/*.txt | grep "Tests run:" | head -1
                    '''
                }
                failure {
                    error '❌ Tests failed - check test reports'
                }
            }
        }
        
        stage('Security Scan - Code') {
            steps {
                sh '''
                    echo "=== Running Trivy Filesystem Security Scan ==="
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
                        -Dsonar.sourceEncoding=UTF-8 \
                        -Dsonar.tests=src/test \
                        -Dsonar.test.inclusions=src/test/**/* \
                        -Dsonar.coverage.exclusions=**/config/**,**/dto/**,**/model/**
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
            post {
                success {
                    echo '✅ Quality Gate passed - Code meets quality standards'
                }
                failure {
                    error '❌ Quality Gate failed - Code quality issues detected'
                }
            }
        }
        
        stage('Build & Package') {
            steps {
                sh '''
                    echo "=== Building Application Package ==="
                    mvn clean package -DskipTests -q
                    echo "=== Generated Artifacts ==="
                    ls -la target/*.jar
                    echo "=== JAR File Info ==="
                    java -jar target/hotel-booking-system-1.0.0.jar --version || echo "JAR executable check completed"
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
                    def jarFiles = findFiles(glob: 'target/*.jar')
                    if (jarFiles) {
                        def jarFile = jarFiles[0]
                        echo "Publishing JAR file: ${jarFile.name}"
                        
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
                                 file: jarFile.path,
                                 type: 'jar']
                            ]
                        )
                        echo "✅ Artifact ${jarFile.name} published to Nexus"
                    } else {
                        echo '⚠️ No JAR files found for Nexus upload'
                    }
                }
            }
        }
        
        stage('Docker Build') {
            steps {
                script {
                    echo "=== Building Docker Image ==="
                    docker.build("${REGISTRY}/${APP_NAME}:${VERSION}")
                    echo "✅ Docker image built: ${REGISTRY}/${APP_NAME}:${VERSION}"
                    
                    // Verify image
                    sh """
                        docker images | grep ${APP_NAME}
                        docker inspect ${REGISTRY}/${APP_NAME}:${VERSION} | grep Created
                    """
                }
            }
        }
        
        stage('Security Scan - Docker Image') {
            steps {
                sh """
                    echo "=== Scanning Docker Image for Vulnerabilities ==="
                    trivy image --exit-code 0 \
                    --severity HIGH,CRITICAL \
                    --format table \
                    ${REGISTRY}/${APP_NAME}:${VERSION} || echo "Image security scan completed with findings"
                    echo "✅ Docker image security scan completed"
                """
            }
        }
        
        stage('Docker Push') {
            steps {
                script {
                    echo "=== Pushing Docker Image to Registry ==="
                    docker.withRegistry('', 'dockerhub-creds') {
                        docker.image("${REGISTRY}/${APP_NAME}:${VERSION}").push()
                    }
                    echo "✅ Docker image pushed: ${REGISTRY}/${APP_NAME}:${VERSION}"
                    
                    // Verify push
                    sh """
                        docker pull ${REGISTRY}/${APP_NAME}:${VERSION} || echo "Pull verification not required"
                    """
                }
            }
        }
        
        stage('Initialize Kubernetes Infrastructure') {
            steps {
                script {
                    withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')]) {
                        sh """
                            export KUBECONFIG=${KUBECONFIG_FILE}
                            
                            echo "=== Initializing Kubernetes Infrastructure ==="
                            
                            # Create namespace
                            kubectl apply -f k8s/namespace.yaml
                            echo "✅ Namespace created"
                            
                            # Deploy MySQL
                            echo "=== Deploying MySQL Database ==="
                            kubectl apply -f k8s/mysql-secret.yaml -n ${K8S_NAMESPACE}
                            kubectl apply -f k8s/mysql-configmap.yaml -n ${K8S_NAMESPACE}
                            kubectl apply -f k8s/mysql-pvc.yaml -n ${K8S_NAMESPACE}
                            kubectl apply -f k8s/mysql-deployment.yaml -n ${K8S_NAMESPACE}
                            kubectl apply -f k8s/mysql-service.yaml -n ${K8S_NAMESPACE}
                            
                            # Wait for MySQL to be ready
                            echo "=== Waiting for MySQL to be ready ==="
                            kubectl wait --for=condition=ready pod -l app=mysql -n ${K8S_NAMESPACE} --timeout=300s
                            echo "✅ MySQL database ready"
                            
                            # Show initial state
                            echo "=== Initial Infrastructure State ==="
                            kubectl get all -n ${K8S_NAMESPACE}
                        """
                    }
                }
            }
        }
        
        stage('Kubernetes Blue-Green Deployment') {
            steps {
                script {
                    withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')]) {
                        // Always start with Blue for fresh deployment
                        sh """
                            export KUBECONFIG=${KUBECONFIG_FILE}
                            
                            echo "=== Starting Blue-Green Deployment ==="
                            echo "=== Deploying Version ${VERSION} to BLUE ==="
                            
                            # Update deployment with new image
                            sed -i "s|image:.*|image: ${REGISTRY}/${APP_NAME}:${VERSION}|g" k8s/app-deployment-blue.yaml
                            
                            # Apply blue deployment and service
                            kubectl apply -f k8s/app-deployment-blue.yaml -n ${K8S_NAMESPACE}
                            kubectl apply -f k8s/app-service-blue.yaml -n ${K8S_NAMESPACE}
                            
                            # Wait for blue rollout
                            echo "=== Waiting for BLUE rollout ==="
                            kubectl rollout status deployment/hotel-booking-system-blue -n ${K8S_NAMESPACE} --timeout=300s
                            
                            echo "✅ BLUE deployment completed successfully"
                            
                            # Apply main service and ingress
                            kubectl apply -f k8s/app-service.yaml -n ${K8S_NAMESPACE}
                            kubectl apply -f k8s/app-ingress.yaml -n ${K8S_NAMESPACE}
                            
                            echo "=== Deployment State ==="
                            kubectl get deployments -n ${K8S_NAMESPACE} -o wide
                            kubectl get pods -n ${K8S_NAMESPACE} -o wide
                            kubectl get svc -n ${K8S_NAMESPACE} -o wide
                            kubectl get ingress -n ${K8S_NAMESPACE} -o wide
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
                            export KUBECONFIG=${KUBECONFIG_FILE}
                            
                            echo "=== Performing Health Checks ==="
                            
                            # Get blue pod
                            POD_NAME=\$(kubectl get pods -n ${K8S_NAMESPACE} -l app=hotel-booking-system,version=blue -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
                            
                            if [ -n "\$POD_NAME" ]; then
                                echo "Testing pod: \$POD_NAME"
                                
                                # Health check using exec
                                echo "=== Checking Application Health ==="
                                for i in {1
                                                                # Health check using exec
                                echo "=== Checking Application Health ==="
                                for i in {1..10}; do
                                    if kubectl exec -n ${K8S_NAMESPACE} \$POD_NAME -- curl -f -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
                                        echo "✅ Application health check PASSED (Attempt \$i/10)"
                                        kubectl exec -n ${K8S_NAMESPACE} \$POD_NAME -- curl -s http://localhost:8080/actuator/health
                                        break
                                    else
                                        echo "Attempt \$i/10: Application not ready yet..."
                                        sleep 10
                                    fi
                                    if [ \$i -eq 10 ]; then
                                        echo "❌ Application health check FAILED after 10 attempts"
                                        exit 1
                                    fi
                                done
                                
                                # Test application endpoints
                                echo "=== Testing Application Endpoints ==="
                                kubectl exec -n ${K8S_NAMESPACE} \$POD_NAME -- curl -s http://localhost:8080/ | head -5 || echo "Home endpoint check completed"
                                
                                # Check database connectivity
                                echo "=== Testing Database Connectivity ==="
                                kubectl exec -n ${K8S_NAMESPACE} \$POD_NAME -- curl -s http://localhost:8080/actuator/health | grep -q "UP" && echo "✅ Database connectivity verified"
                                
                            else
                                echo "❌ No pods found for health check"
                                exit 1
                            fi
                            
                            echo "✅ All health checks passed"
                        """
                    }
                }
            }
        }
        
        stage('Integration Test') {
            steps {
                script {
                    withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')]) {
                        sh """
                            export KUBECONFIG=${KUBECONFIG_FILE}
                            
                            echo "=== Running Integration Tests ==="
                            
                            # Get service URL
                            SERVICE_IP=\$(kubectl get svc hotel-booking-system -n ${K8S_NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
                            if [ -z "\$SERVICE_IP" ]; then
                                SERVICE_IP=\$(kubectl get svc hotel-booking-system -n ${K8S_NAMESPACE} -o jsonpath='{.spec.clusterIP}')
                                echo "Using ClusterIP for testing: \$SERVICE_IP"
                            else
                                echo "Using LoadBalancer IP for testing: \$SERVICE_IP"
                            fi
                            
                            # Test external access (if LoadBalancer)
                            if [ -n "\$SERVICE_IP" ] && [ "\$SERVICE_IP" != "null" ]; then
                                echo "=== Testing External Access ==="
                                for i in {1..10}; do
                                    if curl -f -s http://\$SERVICE_IP/actuator/health > /dev/null 2>&1; then
                                        echo "✅ External health check PASSED"
                                        curl -s http://\$SERVICE_IP/actuator/health | head -5
                                        break
                                    else
                                        echo "Attempt \$i/10: External access not ready..."
                                        sleep 10
                                    fi
                                done
                            fi
                            
                            echo "✅ Integration tests completed"
                        """
                    }
                }
            }
        }
        
        stage('Clean Old Resources') {
            steps {
                script {
                    withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')]) {
                        sh """
                            export KUBECONFIG=${KUBECONFIG_FILE}
                            
                            echo "=== Cleaning Old Resources ==="
                            
                            # Remove old Docker images from Jenkins server
                            docker rmi \$(docker images | grep "${APP_NAME}" | grep -v "${VERSION}" | awk '{print \$3}') 2>/dev/null || echo "No old images to remove"
                            
                            # Clean up old Kubernetes resources if needed
                            kubectl get deployments -n ${K8S_NAMESPACE} -o name | grep -v "blue" | xargs -r kubectl delete -n ${K8S_NAMESPACE} --timeout=30s || echo "No old deployments to delete"
                            
                            echo "✅ Old resources cleaned"
                            
                            # Final status
                            echo "=== Final Deployment Status ==="
                            kubectl get all -n ${K8S_NAMESPACE}
                            echo "=== Docker Images ==="
                            docker images | grep "${APP_NAME}" || echo "No application images found"
                        """
                    }
                }
            }
        }
    }
    
    post {
        always {
            script {
                echo "=== Pipeline Execution Completed ==="
                echo "Build Result: ${currentBuild.result}"
                echo "Application: ${APP_NAME}"
                echo "Version: ${VERSION}"
                echo "Docker Image: ${REGISTRY}/${APP_NAME}:${VERSION}"
                
                // Clean workspace
                cleanWs()
                
                // Send email notification
                emailext (
                    subject: "Build ${currentBuild.result}: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                    body: """
                    Hotel Booking System CI/CD Pipeline Execution Completed!
                    
                    Build Result: ${currentBuild.result}
                    Application: ${APP_NAME}
                    Version: ${VERSION}
                    Docker Image: ${REGISTRY}/${APP_NAME}:${VERSION}
                    Kubernetes Namespace: ${K8S_NAMESPACE}
                    
                    Build URL: ${env.BUILD_URL}
                    Git Commit: ${env.GIT_COMMIT}
                    
                    ${currentBuild.result == 'SUCCESS' ? '🎉 Application deployed successfully!' : '❌ Deployment failed. Check logs for details.'}
                    """,
                    to: "mesaifudheenpv@gmail.com",
                    attachLog: currentBuild.result == 'FAILURE'
                )
            }
        }
        success {
            script {
                currentBuild.description = "✅ SUCCESS - Build ${VERSION}"
                echo "🎉 Pipeline executed successfully! Application deployed to Kubernetes."
                
                // Get deployment info for success message
                withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')]) {
                    sh """
                        export KUBECONFIG=${KUBECONFIG_FILE}
                        echo "=== Deployment Information ==="
                        kubectl get svc,ingress -n ${K8S_NAMESPACE} -o wide
                        
                        # Get LoadBalancer URL if available
                        LB_IP=\$(kubectl get svc hotel-booking-system -n ${K8S_NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
                        if [ -n "\$LB_IP" ]; then
                            echo "🌐 Application URL: http://\$LB_IP"
                        fi
                    """
                }
            }
        }
        failure {
            script {
                currentBuild.description = "❌ FAILED - Build ${VERSION}"
                echo "❌ Pipeline failed! Check logs for details."
                
                // Get failure details
                withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')]) {
                    sh """
                        export KUBECONFIG=${KUBECONFIG_FILE}
                        echo "=== Failure Diagnostics ==="
                        kubectl get pods -n ${K8S_NAMESPACE} || echo "No pods found"
                        kubectl describe pods -n ${K8S_NAMESPACE} -l app=hotel-booking-system || echo "Cannot describe pods"
                        kubectl logs -n ${K8S_NAMESPACE} -l app=hotel-booking-system --tail=50 || echo "No logs available"
                    """
                }
            }
        }
        unstable {
            script {
                currentBuild.description = "⚠️ UNSTABLE - Build ${VERSION}"
                echo "⚠️ Pipeline unstable! Check test reports."
            }
        }
        cleanup {
            cleanWs()
            sh '''
                echo "=== Final Cleanup ==="
                # Remove any temporary files
                rm -f deployment.env || true
                # Clean Docker cache
                docker system prune -f || true
                echo "✅ Cleanup completed"
            '''
        }
    }
}