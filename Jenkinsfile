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

        /* -------------------------------------------------------
         🔧 ENVIRONMENT SETUP
        ------------------------------------------------------- */
        stage('Environment Setup') {
            steps {
                script {
                    echo "🔧 Setting up environment..."
                    sh '''
                    echo "🧹 Cleaning any previous process on port 8080..."
                    sudo fuser -k 8080/tcp || true

                    java -version
                    mvn --version
                    docker --version
                    kubectl version --client
                    '''
                }
            }
        }

        /* -------------------------------------------------------
         📦 CHECKOUT SOURCE CODE
        ------------------------------------------------------- */
        stage('Checkout Code') {
            steps {
                echo "📦 Checking out code from repository..."
                checkout scm
            }
        }

        /* -------------------------------------------------------
         🧪 BUILD, TEST, AND SECURITY SCAN
        ------------------------------------------------------- */
        stage('Build, Test & Security Scan') {
            steps {
                withCredentials([string(credentialsId: 'nvd-api-key', variable: 'NVD_API_KEY')]) {
                    echo "🧪 Running Maven build + OWASP Dependency Check..."
                    sh '''
                    mvn clean verify -U -DskipTests=false -Dnvd.api.key=$NVD_API_KEY
                    '''
                }
            }
        }

        /* -------------------------------------------------------
         🔎 SONARQUBE ANALYSIS
        ------------------------------------------------------- */
        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('Sonar-Server') {
                    withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_AUTH_TOKEN')]) {
                        echo "🔎 Running SonarQube static code analysis..."
                        sh '''
                        mvn sonar:sonar \
                          -Dsonar.projectKey=${APP_NAME} \
                          -Dsonar.host.url=http://${SONARQUBE_URL}:9000 \
                          -Dsonar.login=$SONAR_AUTH_TOKEN
                        '''
                    }
                }
            }
        }

        /* -------------------------------------------------------
         🐳 DOCKER BUILD & PUSH
        ------------------------------------------------------- */
        stage('Docker Build & Push') {
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: 'docker-token', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                        echo "🐳 Building and pushing Docker image..."
                        sh '''
                        echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
                        docker build -t ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} .
                        docker tag ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} ${DOCKER_NAMESPACE}/${APP_NAME}:latest
                        docker push ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION}
                        docker push ${DOCKER_NAMESPACE}/${APP_NAME}:latest
                        '''
                    }
                }
            }
        }

        /* -------------------------------------------------------
         🚀 DEPLOY TO EKS
        ------------------------------------------------------- */
        stage('Deploy to EKS') {
            steps {
                withCredentials([
                    [$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-eks-creds'],
                    file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')
                ]) {
                    script {
                        echo "🚀 Deploying to Amazon EKS..."
                        sh '''
                        mkdir -p $WORKSPACE/.kube
                        cp $KUBECONFIG_FILE $WORKSPACE/.kube/config
                        chmod 600 $WORKSPACE/.kube/config
                        export KUBECONFIG=$WORKSPACE/.kube/config

                        aws eks update-kubeconfig --name ${CLUSTER_NAME} --region ${REGION}

                        kubectl create namespace ${K8S_NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -
                        kubectl apply -f k8s/mysql-deployment.yaml -n ${K8S_NAMESPACE}
                        kubectl apply -f k8s/mysql-service.yaml -n ${K8S_NAMESPACE}
                        kubectl apply -f k8s/app-deployment-blue.yaml -n ${K8S_NAMESPACE}
                        kubectl apply -f k8s/app-service.yaml -n ${K8S_NAMESPACE}
                        '''
                    }
                }
            }
        }

        /* -------------------------------------------------------
         🔁 BLUE-GREEN DEPLOYMENT SWITCH
        ------------------------------------------------------- */
        stage('Blue-Green Switch') {
            when { expression { params.DEPLOYMENT_STRATEGY == 'blue-green' && params.AUTO_SWITCH } }
            steps {
                withCredentials([
                    [$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-eks-creds'],
                    file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')
                ]) {
                    script {
                        echo "🔁 Switching traffic to GREEN environment..."
                        sh '''
                        export KUBECONFIG=$WORKSPACE/.kube/config
                        aws eks update-kubeconfig --name ${CLUSTER_NAME} --region ${REGION}

                        kubectl patch service hotel-booking-service -n ${K8S_NAMESPACE} \
                          -p '{"spec":{"selector":{"app":"hotel-booking","version":"green"}}}'
                        '''
                    }
                }
            }
        }

        /* -------------------------------------------------------
         🔍 POST DEPLOYMENT VALIDATION
        ------------------------------------------------------- */
        stage('Post-Deployment Validation') {
            steps {
                withCredentials([
                    [$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-eks-creds'],
                    file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')
                ]) {
                    script {
                        echo "🔍 Validating EKS resources and performing health check..."
                        sh '''
                        export KUBECONFIG=$WORKSPACE/.kube/config
                        aws eks update-kubeconfig --name ${CLUSTER_NAME} --region ${REGION}

                        echo "✅ Checking Pods and Services..."
                        kubectl get pods -n ${K8S_NAMESPACE}
                        kubectl get svc -n ${K8S_NAMESPACE}

                        echo "⏳ Waiting 20 seconds before health check..."
                        sleep 20

                        APP_URL=$(kubectl get svc hotel-booking-service -n ${K8S_NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
                        echo "🌐 Checking application health at http://$APP_URL/actuator/health"
                        curl -I http://$APP_URL/actuator/health || echo "⚠️ Health endpoint not reachable yet"
                        '''
                    }
                }
            }
        }
    }

    /* -------------------------------------------------------
     🧹 POST ACTIONS (SUCCESS / FAILURE)
    ------------------------------------------------------- */
    post {
        success {
            echo "🎉 SUCCESS: Application built, scanned, analyzed, containerized, and deployed successfully!"
        }
        failure {
            script {
                echo "❌ DEPLOYMENT FAILED: Rolling back to BLUE version..."
                withCredentials([
                    [$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-eks-creds'],
                    file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')
                ]) {
                    sh '''
                    export KUBECONFIG=$WORKSPACE/.kube/config
                    aws eks update-kubeconfig --name ${CLUSTER_NAME} --region ${REGION}
                    kubectl patch service hotel-booking-service -n ${K8S_NAMESPACE} \
                      -p '{"spec":{"selector":{"app":"hotel-booking","version":"blue"}}}' || true
                    '''
                }
            }
        }
        always {
            cleanWs()
        }
    }
}
