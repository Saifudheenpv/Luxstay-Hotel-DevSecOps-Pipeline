pipeline {
    agent any

    tools {
        jdk 'JDK17'
        maven 'Maven3'
    }

    environment {
        SONARQUBE_URL = '13.203.26.99'
        JENKINS_URL = '13.203.25.43'
        DOCKER_REGISTRY = "docker.io"
        DOCKER_NAMESPACE = "saifudheenpv"

        APP_NAME = 'hotel-booking-system'
        APP_VERSION = "${env.BUILD_ID}"
        K8S_NAMESPACE = 'hotel-booking'

        MAVEN_OPTS = '-Xmx1024m -Djava.security.egd=file:/dev/./urandom'

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
        stage('Environment Setup') {
            steps {
                script {
                    echo "🔧 Setting up build environment..."
                    env.CURRENT_DEPLOYMENT = 'blue'
                    env.NEXT_DEPLOYMENT = 'green'
                    env.DEPLOYMENT_TYPE = params.DEPLOYMENT_STRATEGY

                    if (params.DEPLOYMENT_STRATEGY == 'blue-green') {
                        try {
                            def currentColor = sh(
                                script: """
                                    kubectl get service hotel-booking-service -n hotel-booking \
                                    -o jsonpath='{.spec.selector.version}' 2>/dev/null || echo "blue"
                                """,
                                returnStdout: true
                            ).trim()

                            if (currentColor == 'blue') {
                                env.CURRENT_DEPLOYMENT = 'blue'
                                env.NEXT_DEPLOYMENT = 'green'
                            } else {
                                env.CURRENT_DEPLOYMENT = 'green'
                                env.NEXT_DEPLOYMENT = 'blue'
                            }
                        } catch (Exception e) {
                            echo "⚠️ Could not detect current deployment, using default (blue)"
                        }
                    }

                    sh '''
                    echo "=== TOOL VERSIONS ==="
                    java -version
                    mvn --version
                    docker --version
                    kubectl version --client || echo "kubectl not configured"
                    trivy --version
                    df -h
                    '''
                }
            }
        }

        stage('Secure GitHub Checkout') {
            steps {
                echo "🔐 Checking out source from GitHub..."
                checkout scm
                sh '''
                echo "Repository: $(git config --get remote.origin.url)"
                echo "Branch: $(git rev-parse --abbrev-ref HEAD)"
                echo "Commit: $(git rev-parse HEAD)"
                '''
            }
        }

        stage('Dependency Security Scan') {
            when { expression { params.RUN_SECURITY_SCAN } }
            steps {
                echo "🔍 Running dependency scan..."
                sh 'mvn org.owasp:dependency-check-maven:check -DskipTests || true'
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

        stage('Compile & Test') {
            parallel {
                stage('Compile') {
                    steps {
                        echo "🔨 Compiling source..."
                        sh 'mvn clean compile -DskipTests'
                    }
                }
                stage('Unit Tests') {
                    steps {
                        echo "🧪 Running unit tests..."
                        sh 'mvn test'
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

        stage('SonarQube Analysis') {
            steps {
                echo "📊 Running SonarQube code analysis..."
                withSonarQubeEnv('Sonar-Server') {
                    withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                        sh """
                        mvn sonar:sonar \
                          -Dsonar.projectKey=hotel-booking-system \
                          -Dsonar.projectName='Hotel Booking System' \
                          -Dsonar.host.url=http://${SONARQUBE_URL}:9000 \
                          -Dsonar.login=$SONAR_TOKEN \
                          -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                          -Dsonar.java.binaries=target/classes \
                          -Dsonar.sourceEncoding=UTF-8
                        """
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                echo "🚦 Waiting for SonarQube Quality Gate..."
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
                echo "✅ Quality Gate passed!"
            }
        }

        stage('Package & Docker Build') {
            steps {
                script {
                    echo "📦 Building Docker image..."
                    sh 'mvn package -DskipTests'
                    archiveArtifacts 'target/*.jar'

                    withCredentials([usernamePassword(credentialsId: 'docker-token', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                        sh 'echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin'
                    }

                    sh """
                    docker build -t ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} .
                    docker tag ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} ${DOCKER_NAMESPACE}/${APP_NAME}:latest
                    """

                    if (params.DEPLOYMENT_STRATEGY == 'blue-green') {
                        sh "docker tag ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} ${DOCKER_NAMESPACE}/${APP_NAME}:${NEXT_DEPLOYMENT}"
                    }
                }
            }
        }

        stage('Container Security Scan') {
            when { expression { params.RUN_SECURITY_SCAN } }
            steps {
                echo "🔒 Scanning Docker image with Trivy..."
                sh """
                trivy image --skip-db-update --format template --template "@contrib/html.tpl" \
                    --output trivy-security-report.html ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} || true
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

        stage('Docker Push') {
            steps {
                script {
                    echo "📤 Pushing Docker images..."
                    sh """
                    docker push ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION}
                    docker push ${DOCKER_NAMESPACE}/${APP_NAME}:latest
                    """
                    if (params.DEPLOYMENT_STRATEGY == 'blue-green') {
                        sh "docker push ${DOCKER_NAMESPACE}/${APP_NAME}:${NEXT_DEPLOYMENT}"
                    }
                }
            }
        }

        stage('Kubernetes Deployment') {
            steps {
                script {
                    echo "🎯 Deploying to Kubernetes..."
                    sh """
                    kubectl create namespace ${K8S_NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -
                    kubectl apply -f k8s/mysql-secret.yaml -n ${K8S_NAMESPACE} || true
                    kubectl apply -f k8s/mysql-config.yaml -n ${K8S_NAMESPACE} || true
                    kubectl apply -f k8s/mysql-deployment.yaml -n ${K8S_NAMESPACE} || true
                    kubectl apply -f k8s/mysql-service.yaml -n ${K8S_NAMESPACE} || true
                    """

                    if (params.DEPLOYMENT_STRATEGY == 'blue-green') {
                        echo "🚀 Deploying new version to ${NEXT_DEPLOYMENT}..."
                        sh """
                        sed -e "s|hotel-booking-blue|hotel-booking-${NEXT_DEPLOYMENT}|g" \
                            -e "s|version: blue|version: ${NEXT_DEPLOYMENT}|g" \
                            -e "s|saifudheenpv/hotel-booking-system:latest|${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION}|g" \
                            k8s/app-deployment-blue.yaml > k8s/app-deployment-${NEXT_DEPLOYMENT}.yaml
                        kubectl apply -f k8s/app-deployment-${NEXT_DEPLOYMENT}.yaml -n ${K8S_NAMESPACE}
                        kubectl rollout status deployment/hotel-booking-${NEXT_DEPLOYMENT} -n ${K8S_NAMESPACE} --timeout=600s
                        """
                    } else {
                        echo "🚀 Rolling update deployment..."
                        sh """
                        kubectl set image deployment/hotel-booking-blue hotel-booking=${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} -n ${K8S_NAMESPACE} || \
                        kubectl apply -f k8s/app-deployment-blue.yaml -n ${K8S_NAMESPACE}
                        kubectl rollout status deployment/hotel-booking-blue -n ${K8S_NAMESPACE} --timeout=600s
                        """
                    }

                    sh "kubectl apply -f k8s/app-service.yaml -n ${K8S_NAMESPACE}"
                }
            }
        }

        stage('Post-Deployment Validation') {
            steps {
                script {
                    echo "🔍 Validating deployment..."
                    sh """
                    kubectl get pods -n ${K8S_NAMESPACE}
                    kubectl get services -n ${K8S_NAMESPACE}
                    """
                }
            }
        }
    }

    post {
        always {
            echo "📋 Pipeline execution completed!"
            cleanWs()
        }

        success {
            echo "🎉 SUCCESS: Build and deployment completed successfully!"
        }

        failure {
            script {
                echo "❌ Pipeline failed!"
                if (params.DEPLOYMENT_STRATEGY == 'blue-green') {
                    sh """
                    kubectl scale deployment/hotel-booking-${NEXT_DEPLOYMENT} -n ${K8S_NAMESPACE} --replicas=0 || true
                    echo "Rolled back failed deployment ${NEXT_DEPLOYMENT}"
                    """
                }
            }
        }
    }
}
