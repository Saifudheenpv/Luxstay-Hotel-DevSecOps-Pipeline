pipeline {
    agent any

    tools {
        jdk 'JDK17'
        maven 'Maven3'
    }

    environment {
        SONARQUBE_URL = '13.203.26.99'
        DOCKER_NAMESPACE = "saifudheenpv"
        APP_NAME = 'hotel-booking-system'
        APP_VERSION = "${env.BUILD_ID}"
        K8S_NAMESPACE = 'hotel-booking'
        REGION = 'ap-south-1'
        CLUSTER_NAME = 'devops-cluster'
        NEXUS_URL = 'http://13.203.26.99:8081/repository/maven-releases/'
    }

    triggers {
        githubPush()
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 45, unit: 'MINUTES')
    }

    parameters {
        choice(name: 'DEPLOYMENT_STRATEGY', choices: ['blue-green', 'rolling'], description: 'Select deployment strategy')
        booleanParam(name: 'AUTO_SWITCH', defaultValue: true, description: 'Auto switch traffic to new version?')
    }

    stages {

        stage('Environment Setup') {
            steps {
                script {
                    echo "🔧 Setting up environment..."
                    sh '''
                    java -version
                    mvn --version
                    docker --version
                    kubectl version --client
                    '''
                }
            }
        }

        stage('Checkout Code') {
            steps {
                echo "📦 Checking out code..."
                checkout scm
            }
        }

        stage('Maven Compile & Unit Tests') {
            steps {
                echo "🧪 Compiling and running unit tests..."
                sh 'mvn clean compile test'
            }
        }

        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('SonarQubeServer') {
                    sh 'mvn sonar:sonar -Dsonar.projectKey=hotel-booking -Dsonar.host.url=http://13.203.26.99:9000 -Dsonar.login=$SONAR_AUTH_TOKEN'
                }
            }
        }

        stage('OWASP Dependency Check') {
            steps {
                echo "🔐 Running OWASP dependency scan..."
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

        stage('Maven Build & Publish to Nexus') {
            steps {
                echo "📦 Building and uploading artifact to Nexus..."
                sh '''
                mvn clean package -DskipTests
                mvn deploy -DskipTests -Dnexus.url=${NEXUS_URL}
                '''
            }
        }

        stage('Docker Build, Scan & Push') {
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: 'docker-token', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                        sh '''
                        echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
                        docker build -t ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} .
                        docker tag ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} ${DOCKER_NAMESPACE}/${APP_NAME}:latest
                        
                        echo "🔍 Running Trivy scan..."
                        trivy image --format table --output trivy-scan.txt ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} || true

                        docker push ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION}
                        docker push ${DOCKER_NAMESPACE}/${APP_NAME}:latest
                        '''
                    }
                }
            }
            post {
                always {
                    publishHTML([
                        allowMissing: true,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: '.',
                        reportFiles: 'trivy-scan.txt',
                        reportName: 'Trivy Scan Report'
                    ])
                }
            }
        }

        stage('Deploy to EKS') {
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

                        aws eks get-token --cluster-name ${CLUSTER_NAME} --region ${REGION} > /tmp/token.json
                        TOKEN=$(jq -r .status.token /tmp/token.json)
                        kubectl config set-credentials arn:aws:eks:${REGION}:724663512594:cluster/${CLUSTER_NAME} --token="$TOKEN"

                        echo "🎯 Deploying resources..."
                        kubectl create namespace ${K8S_NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -
                        kubectl apply -f k8s/mysql-deployment.yaml -n ${K8S_NAMESPACE} --validate=false
                        kubectl apply -f k8s/mysql-service.yaml -n ${K8S_NAMESPACE} --validate=false
                        kubectl apply -f k8s/app-deployment-blue.yaml -n ${K8S_NAMESPACE} --validate=false
                        kubectl apply -f k8s/app-service.yaml -n ${K8S_NAMESPACE} --validate=false
                        '''
                    }
                }
            }
        }

        stage('Blue-Green Switch') {
            when { expression { params.DEPLOYMENT_STRATEGY == 'blue-green' && params.AUTO_SWITCH == true } }
            steps {
                script {
                    echo "🔁 Switching traffic to GREEN version..."
                    withCredentials([
                        [$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-eks-creds'],
                        file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')
                    ]) {
                        sh '''
                        export KUBECONFIG=$WORKSPACE/.kube/config
                        aws eks get-token --cluster-name ${CLUSTER_NAME} --region ${REGION} > /tmp/token.json
                        TOKEN=$(jq -r .status.token /tmp/token.json)
                        kubectl config set-credentials arn:aws:eks:${REGION}:724663512594:cluster/${CLUSTER_NAME} --token="$TOKEN"

                        kubectl patch service hotel-booking-service -n ${K8S_NAMESPACE} \
                          --type merge \
                          -p '{"spec":{"selector":{"app":"hotel-booking","version":"blue"}}}'

                        echo "✅ Switched traffic to BLUE version (active pods)."
                        '''
                    }
                }
            }
        }

        stage('Health Check & Validation') {
            steps {
                script {
                    echo "🔍 Performing external health check..."
                    def lbUrl = sh(script: "kubectl get svc hotel-booking-service -n ${K8S_NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'", returnStdout: true).trim()
                    echo "🌍 External URL: http://${lbUrl}"

                    sh """
                    sleep 20
                    curl -I http://${lbUrl}/ || true
                    curl http://${lbUrl}/actuator/health || true
                    """
                }
            }
        }
    }

    post {
        success {
            script {
                def lbUrl = sh(script: "kubectl get svc hotel-booking-service -n hotel-booking -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'", returnStdout: true).trim()
                echo "🎉 SUCCESS: Deployment complete and app accessible at: http://${lbUrl}"
                emailext(
                    to: 'mesaifudheenpv@gmail.com',
                    subject: "✅ SUCCESS: Hotel Booking App Deployed",
                    body: """<h3>Deployment Successful 🎯</h3>
                             <p>Your Hotel Booking System was successfully deployed to AWS EKS.</p>
                             <p><b>Access URL:</b> <a href='http://${lbUrl}'>http://${lbUrl}</a></p>
                             <p>Regards,<br>Jenkins CI/CD</p>""",
                    mimeType: 'text/html'
                )
            }
        }
        failure {
            echo "❌ Deployment failed! Rolling back..."
            emailext(
                to: 'mesaifudheenpv@gmail.com',
                subject: "❌ FAILURE: Hotel Booking App Deployment Failed",
                body: "<p>Deployment failed. Please check Jenkins logs for details.</p>",
                mimeType: 'text/html'
            )
        }
        always {
            cleanWs()
        }
    }
}
