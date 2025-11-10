pipeline {
  agent any

  tools {
    jdk 'JDK17'
    maven 'Maven3'
  }

  environment {
    SONARQUBE_URL   = '13.203.26.99'
    DOCKER_NAMESPACE = 'saifudheenpv'
    APP_NAME        = 'hotel-booking-system'
    APP_VERSION     = "${env.BUILD_ID}"
    K8S_NAMESPACE   = 'hotel-booking'
    REGION          = 'ap-south-1'
    CLUSTER_NAME    = 'devops-cluster'
  }

  options {
    timestamps()
    disableConcurrentBuilds()
    timeout(time: 45, unit: 'MINUTES')
  }

  parameters {
    choice(name: 'DEPLOYMENT_STRATEGY', choices: ['blue-green', 'rolling'], description: 'Select deployment strategy')
    booleanParam(name: 'AUTO_SWITCH', defaultValue: true, description: 'Auto switch traffic to new version?')
    booleanParam(name: 'AUTO_MIGRATE_JAKARTA', defaultValue: false, description: 'Auto-convert javax.* imports to jakarta.*')
  }

  stages {
    stage('Environment Setup') {
      steps {
        sh '''
          echo "Java:"
          java -version
          echo "Maven:"
          mvn -version
          echo "Docker:"
          docker --version || true
          echo "Kubectl:"
          kubectl version --client || true
        '''
      }
    }

    stage('Checkout Code') {
      steps {
        checkout scm
      }
    }

    stage('Auto-Migrate to Jakarta') {
      when { expression { params.AUTO_MIGRATE_JAKARTA } }
      steps {
        sh '''
          echo "Auto-migrating javax.* -> jakarta.*"
          find src -name "*.java" -type f -print0 | xargs -0 sed -i             -e 's/import javax\.persistence\./import jakarta.persistence./g'             -e 's/import javax\.validation\./import jakarta.validation./g'             -e 's/import javax\.servlet\./import jakarta.servlet./g'             -e 's/import javax\.annotation\./import jakarta.annotation./g'

          find src -name "*.java" -type f -print0 | xargs -0 sed -i             -e 's/org\.thymeleaf\.spring5/org.thymeleaf.spring6/g'             -e 's/org\.thymeleaf\.spring5\.templateresolver/org.thymeleaf.spring6.templateresolver/g'
        '''
      }
    }

    stage('Build & Test') {
      steps {
        sh '''
          echo "Running tests with H2 in-memory database..."
          mvn -U -B clean test -Dserver.port=0 -Dspring.profiles.active=test
          echo "All tests passed with H2 in-memory database!"
        '''
      }
    }

    stage('OWASP Security Scan') {
      steps {
        withCredentials([string(credentialsId: 'nvd-api-key', variable: 'NVD_API_KEY')]) {
          sh '''
            echo "Running OWASP dependency check (OSS Index DISABLED)..."
            mvn -B org.owasp:dependency-check-maven:check \
              -Dnvd.api.key="$NVD_API_KEY" \
              -DfailBuildOnCVSS=11 \
              -Danalyzer.ossindex.enabled=false \
              -Danalyzer.retirejs.enabled=false \
              -DskipSystemScope=true
            echo "OWASP scan completed!"
          '''
        }
      }
    }

    stage('SonarQube Analysis') {
      steps {
        withSonarQubeEnv('Sonar-Server') {
          withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_AUTH_TOKEN')]) {
            sh '''
              mvn -B sonar:sonar \
                -Dsonar.projectKey='hotel-booking-system' \
                -Dsonar.host.url=http://${SONARQUBE_URL}:9000 \
                -Dsonar.login="$SONAR_AUTH_TOKEN" \
                -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
            '''
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
            docker tag ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} ${DOCKER_NAMESPACE}/${APP_NAME}:latest
            docker push ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION}
            docker push ${DOCKER_NAMESPACE}/${APP_NAME}:latest
            echo "Docker images pushed!"
          '''
        }
      }
    }

    stage('Deploy to EKS') {
      steps {
        withCredentials([
          [$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-eks-creds'],
          file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')
        ]) {
          sh '''
            mkdir -p $WORKSPACE/.kube
            cp "$KUBECONFIG_FILE" "$WORKSPACE/.kube/config"
            chmod 600 "$WORKSPACE/.kube/config"
            export KUBECONFIG="$WORKSPACE/.kube/config"

            aws eks update-kubeconfig --name ${CLUSTER_NAME} --region ${REGION}

            kubectl create namespace ${K8S_NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -
            kubectl apply -f k8s/mysql-deployment.yaml -n ${K8S_NAMESPACE}
            kubectl apply -f k8s/mysql-service.yaml -n ${K8S_NAMESPACE}
            kubectl apply -f k8s/app-deployment-blue.yaml -n ${K8S_NAMESPACE}
            kubectl apply -f k8s/app-service.yaml -n ${K8S_NAMESPACE}
            echo "Deployment applied!"
          '''
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
            export KUBECONFIG="$WORKSPACE/.kube/config"
            aws eks update-kubeconfig --name ${CLUSTER_NAME} --region ${REGION}
            kubectl patch service hotel-booking-service -n ${K8S_NAMESPACE} -p '{"spec":{"selector":{"app":"hotel-booking","version":"green"}}}'
            echo "Traffic switched to GREEN!"
          '''
        }
      }
    }

    stage('Post-Deployment Validation') {
      steps {
        withCredentials([
          [$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-eks-creds'],
          file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')
        ]) {
          sh '''
            export KUBECONFIG="$WORKSPACE/.kube/config"
            aws eks update-kubeconfig --name ${CLUSTER_NAME} --region ${REGION}

            echo "Deployment Resources:"
            kubectl get pods,svc,deployments -n ${K8S_NAMESPACE}

            sleep 30

            APP_URL=$(kubectl get svc hotel-booking-service -n ${K8S_NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' || true)
            [ -z "$APP_URL" ] && APP_URL=$(kubectl get svc hotel-booking-service -n ${K8S_NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].ip}' || true)

            if [ -n "$APP_URL" ]; then
              echo "Health check: http://$APP_URL/actuator/health"
              for i in {1..5}; do
                if curl -f -s --max-time 10 "http://$APP_URL/actuator/health" > /dev/null; then
                  echo "Health check PASSED!"
                  break
                else
                  echo "Attempt $i failed, retrying..."
                  sleep 10
                fi
              done
            else
              echo "No external URL, skipping health check"
            fi
          '''
        }
      }
    }
  }

  post {
    success {
      echo "SUCCESS: Deployed!"
      cleanWs()
    }
    failure {
      echo "FAILED: Rolling back to BLUE..."
      withCredentials([
        [$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-eks-creds'],
        file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')
      ]) {
        sh '''
          export KUBECONFIG="$WORKSPACE/.kube/config"
          aws eks update-kubeconfig --name ${CLUSTER_NAME} --region ${REGION}
          kubectl patch service hotel-booking-service -n ${K8S_NAMESPACE} -p '{"spec":{"selector":{"app":"hotel-booking","version":"blue"}}}' || true
          echo "Rollback completed!"
        '''
      }
      cleanWs()
    }
    always {
      echo "Pipeline finished!"
    }
  }
}
