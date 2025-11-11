pipeline {
  agent any

  tools {
    jdk 'JDK17'
    maven 'Maven3'
  }

  environment {
    SONARQUBE_URL     = '13.203.26.99'
    DOCKER_NAMESPACE  = 'saifudheenpv'
    APP_NAME          = 'hotel-booking-system'
    APP_VERSION       = "${env.BUILD_ID}"
    K8S_NAMESPACE     = 'hotel-booking'
    REGION            = 'ap-south-1'
    CLUSTER_NAME      = 'devops-cluster'
    EXTERNAL_IP       = 'NOT-READY'
  }

  options {
    timestamps()
    disableConcurrentBuilds()
    timeout(time: 40, unit: 'MINUTES')
    buildDiscarder(logRotator(numToKeepStr: '15'))
  }

  parameters {
    choice(name: 'DEPLOYMENT_STRATEGY', choices: ['blue-green', 'rolling'], description: 'Deployment Strategy')
    booleanParam(name: 'AUTO_SWITCH', defaultValue: true, description: 'Auto switch traffic?')
    booleanParam(name: 'AUTO_MIGRATE_JAKARTA', defaultValue: false, description: 'Migrate javax → jakarta')
  }

  stages {
    stage('Environment Setup') {
      steps {
        sh '''
          echo "Java: $(java -version 2>&1 | head -1)"
          echo "Maven: $(mvn -version | head -1)"
          echo "Docker: $(docker --version)"
          echo "Kubectl: $(kubectl version --client --short 2>/dev/null || kubectl version --client)"
        '''
      }
    }

    stage('Checkout Code') {
      steps { checkout scm }
    }

    stage('Auto-Migrate to Jakarta') {
      when { expression { params.AUTO_MIGRATE_JAKARTA } }
      steps {
        sh 'find src -name "*.java" -type f -print0 | xargs -0 sed -i "s/import javax\\.persistence\\./import jakarta.persistence./g"'
      }
    }

    stage('Build & Test') {
      steps {
        sh 'mvn -U -B clean test -Dspring.profiles.active=test'
      }
    }

    stage('OWASP Security Scan') {
      steps {
        withCredentials([string(credentialsId: 'nvd-api-key', variable: 'NVD_API_KEY')]) {
          sh '''
            mvn -B org.owasp:dependency-check-maven:check \
              -Dnvd.api.key="$NVD_API_KEY" \
              -DfailBuildOnCVSS=11 \
              -DossindexAnalyzer.enabled=false
          '''
        }
      }
    }

    stage('SonarQube Analysis') {
      steps {
        withSonarQubeEnv('Sonar-Server') {
          withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_AUTH_TOKEN')]) {
            sh 'mvn -B sonar:sonar -Dsonar.projectKey=hotel-booking-system -Dsonar.host.url=http://${SONARQUBE_URL}:9000 -Dsonar.login="$SONAR_AUTH_TOKEN"'
          }
        }
      }
    }

    stage('Docker Build & Push') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'docker-token', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
          sh '''
            echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
            docker build -t ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} .
            docker push ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION}
            echo "Pushed v${APP_VERSION}"
          '''
        }
      }
    }

    stage('Trivy Scan') {
      steps {
        sh 'trivy image --exit-code 0 --severity HIGH,CRITICAL ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION}'
      }
    }

    stage('Deploy to EKS') {
      steps {
        withCredentials([
          [$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-eks-creds'],
          file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')
        ]) {
          sh '''
            mkdir -p .kube && cp "$KUBECONFIG_FILE" .kube/config && chmod 600 .kube/config
            export KUBECONFIG=.kube/config
            aws eks update-kubeconfig --name ${CLUSTER_NAME} --region ${REGION}

            kubectl apply -f k8s/mysql-deployment.yaml -n ${K8S_NAMESPACE}
            kubectl apply -f k8s/mysql-service.yaml -n ${K8S_NAMESPACE}

            export APP_VERSION=${APP_VERSION}
            envsubst < k8s/app-deployment-blue.yaml | kubectl apply -f - -n ${K8S_NAMESPACE}
            envsubst < k8s/app-deployment-green.yaml | kubectl apply -f - -n ${K8S_NAMESPACE}
            kubectl apply -f k8s/app-service.yaml -n ${K8S_NAMESPACE}

            echo "Service Status:"
            kubectl get svc hotel-booking-service -n hotel-booking -o wide
          '''

          script {
            def ip = sh(script: 'kubectl get svc hotel-booking-service -n hotel-booking --no-headers | awk "{print \$4}"', returnStdout: true).trim()
            env.EXTERNAL_IP = ip.contains('elb.amazonaws.com') ? ip : 'PENDING'
          }
        }
      }
    }

    stage('Blue-Green Switch') {
      when { expression { params.DEPLOYMENT_STRATEGY == 'blue-green' && params.AUTO_SWITCH } }
      steps {
        withCredentials([
          [$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-eks-creds'],
          file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')
        ]) {
          sh '''
            export KUBECONFIG=.kube/config
            aws eks update-kubeconfig --name ${CLUSTER_NAME} --region ${REGION}
            kubectl wait --for=condition=ready pod -l app=hotel-booking,version=green -n ${K8S_NAMESPACE} --timeout=300s
            kubectl patch service hotel-booking-service -n ${K8S_NAMESPACE} -p '{"spec":{"selector":{"version":"green"}}}'
            kubectl scale deployment hotel-booking-blue --replicas=0 -n ${K8S_NAMESPACE} || true
            echo "Traffic switched to GREEN v${APP_VERSION}"
          '''
        }
      }
    }
  }

  post {
    success {
      echo "SUCCESS: Deployed v${APP_VERSION}!"
      emailext(
        to: 'mesaifudheenpv@gmail.com',
        subject: "LIVE: Luxstay Hotel v${APP_VERSION}",
        body: """
        <h2>Your App is LIVE!</h2>
        <b>Version:</b> v${APP_VERSION}<br>
        <b>URL:</b> <a href="http://${env.EXTERNAL_IP}">http://${env.EXTERNAL_IP}</a><br><br>
        Jenkins: <a href="${env.BUILD_URL}">${env.BUILD_URL}</a>
        """,
        mimeType: 'text/html'
      )
    }
    failure {
      emailext(
        to: 'mesaifudheenpv@gmail.com',
        subject: "FAILED: Hotel Booking v${APP_VERSION}",
        body: "Build Failed!<br>Check: <a href='${env.BUILD_URL}console'>Console</a>",
        mimeType: 'text/html'
      )
    }
    always {
      cleanWs()
    }
  }
}
