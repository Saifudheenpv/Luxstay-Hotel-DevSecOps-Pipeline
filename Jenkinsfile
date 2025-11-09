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
            defaultValue: true,
            description: 'Automatically switch traffic after deployment'
        )
    }

    stages {

        /* 🔧 ENVIRONMENT SETUP */
        stage('Environment Setup') {
            steps {
                script {
                    echo "🔧 Setting up build environment..."
                    env.CURRENT_DEPLOYMENT = 'blue'
                    env.NEXT_DEPLOYMENT = (env.CURRENT_DEPLOYMENT == 'blue') ? 'green' : 'blue'
                    env.DEPLOYMENT_TYPE = params.DEPLOYMENT_STRATEGY

                    sh '''
                    echo "=== TOOL VERSIONS ==="
                    java -version
                    mvn --version
                    docker --version
                    kubectl version --client || echo "kubectl not configured"
                    trivy --version || echo "trivy not installed"
                    df -h
                    '''
                }
            }
        }

        /* 🔐 GIT CHECKOUT */
        stage('Secure GitHub Checkout') {
            steps {
                echo "🔐 Checking out code from GitHub..."
                checkout scm
                sh '''
                echo "Repository: $(git config --get remote.origin.url)"
                echo "Branch: $(git rev-parse --abbrev-ref HEAD)"
                echo "Commit: $(git rev-parse HEAD)"
                '''
            }
        }

        /* 🧪 DEPENDENCY SECURITY */
        stage('Dependency Security Scan') {
            when { expression { params.RUN_SECURITY_SCAN } }
            steps {
                echo "🔍 Running OWASP dependency scan..."
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

        /* ⚙️ COMPILE & TEST */
        stage('Compile & Test') {
            parallel {
                stage('Compile') {
                    steps {
                        echo "🔨 Compiling Java project..."
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

        /* 📊 SONARQUBE */
        stage('SonarQube Analysis') {
            steps {
                echo "📊 Running SonarQube analysis..."
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

        /* 🚦 QUALITY GATE */
        stage('Quality Gate') {
            steps {
                echo "🚦 Waiting for SonarQube Quality Gate..."
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
                echo "✅ Quality Gate passed!"
            }
        }

        /* 🐳 BUILD DOCKER */
        stage('Package & Docker Build') {
            steps {
                script {
                    echo "📦 Building Docker image..."
                    sh 'mvn clean package -DskipTests'
                    archiveArtifacts 'target/*.jar'

                    withCredentials([usernamePassword(credentialsId: 'docker-token', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                        sh 'echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin'
                    }

                    sh """
                    docker build -t ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} .
                    docker tag ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} ${DOCKER_NAMESPACE}/${APP_NAME}:latest
                    docker tag ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} ${DOCKER_NAMESPACE}/${APP_NAME}:${NEXT_DEPLOYMENT}
                    """
                }
            }
        }

        /* 🔒 TRIVY SCAN */
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

        /* 📤 PUSH TO DOCKER HUB */
        stage('Docker Push') {
            steps {
                script {
                    echo "📤 Pushing Docker images to Docker Hub..."
                    sh """
                    docker push ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION}
                    docker push ${DOCKER_NAMESPACE}/${APP_NAME}:latest
                    docker push ${DOCKER_NAMESPACE}/${APP_NAME}:${NEXT_DEPLOYMENT}
                    """
                }
            }
        }

        /* ☸️ DEPLOY TO EKS */
        stage('Kubernetes Deployment') {
            steps {
                withCredentials([
                    [$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-eks-creds'],
                    file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')
                ]) {
                    script {
                        echo "🔐 Setting up and authenticating to EKS..."
                        sh '''
                        mkdir -p $WORKSPACE/.kube
                        cp $KUBECONFIG_FILE $WORKSPACE/.kube/config
                        chmod 600 $WORKSPACE/.kube/config
                        export KUBECONFIG=$WORKSPACE/.kube/config
                        aws sts get-caller-identity

                        echo "🎯 Deploying MySQL + App resources..."
                        kubectl create namespace hotel-booking --dry-run=client -o yaml | kubectl apply -f - || true
                        kubectl apply -f k8s/mysql-deployment.yaml -n hotel-booking --validate=false
                        kubectl apply -f k8s/mysql-service.yaml -n hotel-booking --validate=false

                        echo "🚀 Deploying application resources..."
                        kubectl apply -f k8s/app-deployment-blue.yaml -n hotel-booking --validate=false
                        kubectl apply -f k8s/app-service.yaml -n hotel-booking --validate=false
                        '''
                    }
                }
            }
        }

        /* 🔁 AUTO SWITCH BLUE-GREEN */
        stage('Blue-Green Switch') {
            when { expression { params.AUTO_SWITCH && params.DEPLOYMENT_STRATEGY == 'blue-green' } }
            steps {
                script {
                    echo "🔁 Automatically switching traffic to ${NEXT_DEPLOYMENT}..."
                    sh '''
                    kubectl patch service hotel-booking-service -n hotel-booking \
                      -p "{\"spec\":{\"selector\":{\"app\":\"hotel-booking\",\"version\":\"${NEXT_DEPLOYMENT}\"}}}"
                    '''
                }
            }
        }

        /* 🔍 VALIDATION */
        stage('Post-Deployment Validation') {
            steps {
                withCredentials([
                    [$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-eks-creds'],
                    file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')
                ]) {
                    script {
                        echo "🔍 Validating Kubernetes resources..."
                        retry(3) {
                            sh '''
                            export KUBECONFIG=$WORKSPACE/.kube/config
                            echo "✅ Nodes:"; kubectl get nodes
                            echo "✅ Pods:"; kubectl get pods -n hotel-booking
                            echo "✅ Services:"; kubectl get svc -n hotel-booking
                            '''
                        }
                    }
                }
            }
        }
    }

    /* 🏁 POST STAGES */
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
                echo "❌ Deployment failed! Rolling back..."
                sh '''
                kubectl patch service hotel-booking-service -n hotel-booking \
                  -p '{"spec":{"selector":{"app":"hotel-booking","version":"blue"}}}' || true
                '''
            }
        }
    }
}
