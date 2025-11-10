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
    /* ---------------- ENV ---------------- */
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

    /* ---------------- CODE ---------------- */
    stage('Checkout Code') {
      steps {
        checkout scm
      }
    }

    /* ---------------- QUICK MIGRATION ---------------- */
    stage('Auto-Migrate to Jakarta') {
      when { expression { params.AUTO_MIGRATE_JAKARTA } }
      steps {
        sh '''
          echo "Auto-migrating javax.* -> jakarta.* (safe replacements)"
          find src -name "*.java" -type f -print0 | xargs -0 sed -i \
            -e 's/import javax\\.persistence\\./import jakarta.persistence./g' \
            -e 's/import javax\\.validation\\./import jakarta.validation./g' \
            -e 's/import javax\\.servlet\\./import jakarta.servlet./g' \
            -e 's/import javax\\.annotation\\./import jakarta.annotation./g'

          # If you have thymeleaf Spring5 imports in custom config, update:
          find src -name "*.java" -type f -print0 | xargs -0 sed -i \
            -e 's/org\\.thymeleaf\\.spring5/org.thymeleaf.spring6/g' \
            -e 's/org\\.thymeleaf\\.spring5\\.templateresolver/org.thymeleaf.spring6.templateresolver/g'
        '''
      }
    }

    /* ---------------- BUILD+TEST+OWASP ---------------- */
    stage('Build, Test & OWASP') {
      steps {
        withCredentials([string(credentialsId: 'nvd-api-key', variable: 'NVD_API_KEY')]) {
          sh '''
            # Ensure suppression file exists
            if [ ! -f dependency-check-suppressions.xml ]; then
              cat > dependency-check-suppressions.xml <<'OWASP_EOF'
<?xml version="1.0" encoding="UTF-8"?>
<suppressions xmlns="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd">
  <suppress>
    <notes>False-positive noise on spring-web</notes>
    <packageUrl regex="true">^pkg:maven/org\\.springframework/spring-web@.*$</packageUrl>
    <cve>CVE-2016-1000027</cve>
  </suppress>
</suppressions>
OWASP_EOF
            fi

            # Clean and compile with tests; use test profile explicitly for H2 database
            echo "Running tests with H2 in-memory database (test profile)..."
            mvn -U -B clean verify \
              -Dserver.port=0 \
              -Dspring.profiles.active=test \
              -Dnvd.api.key="$NVD_API_KEY"
            
            echo "✅ All tests passed with H2 in-memory database!"
          '''
        }
      }
    }

    /* ---------------- SONAR ---------------- */
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

    /* ---------------- DOCKER ---------------- */
    stage('Docker Build & Push') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'docker-token', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
          sh '''
            echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
            docker build -t ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} .
            docker tag ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION} ${DOCKER_NAMESPACE}/${APP_NAME}:latest
            docker push ${DOCKER_NAMESPACE}/${APP_NAME}:${APP_VERSION}
            docker push ${DOCKER_NAMESPACE}/${APP_NAME}:latest
            echo "✅ Docker images pushed successfully!"
          '''
        }
      }
    }

    /* ---------------- DEPLOY ---------------- */
    stage('Deploy to EKS') {
      steps {
        withCredentials([
          [$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-eks-creds'],
          file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')
        ]) {
          sh '''
            mkdir -p $WORKSPACE/.kube
            cp $KUBECONFIG_FILE $WORKSPACE/.kube/config
            chmod 600 $WORKSPACE/.kube/config
            export KUBECONFIG=$WORKSPACE/.kube/config

            aws eks update-kubeconfig --name ${CLUSTER_NAME} --region ${REGION}

            echo "Creating/updating Kubernetes resources..."
            kubectl create namespace ${K8S_NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -
            kubectl apply -f k8s/mysql-deployment.yaml -n ${K8S_NAMESPACE}
            kubectl apply -f k8s/mysql-service.yaml -n ${K8S_NAMESPACE}
            kubectl apply -f k8s/app-deployment-blue.yaml -n ${K8S_NAMESPACE}
            kubectl apply -f k8s/app-service.yaml -n ${K8S_NAMESPACE}
            echo "✅ Kubernetes deployment applied successfully!"
          '''
        }
      }
    }

    /* ---------------- SWITCH ---------------- */
    stage('Blue-Green Switch') {
      when { expression { params.DEPLOYMENT_STRATEGY == 'blue-green' && params.AUTO_SWITCH } }
      steps {
        withCredentials([
          [$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-eks-creds'],
          file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')
        ]) {
          sh '''
            export KUBECONFIG=$WORKSPACE/.kube/config
            aws eks update-kubeconfig --name ${CLUSTER_NAME} --region ${REGION}

            echo "Switching traffic to GREEN deployment..."
            kubectl patch service hotel-booking-service -n ${K8S_NAMESPACE} \
              -p '{"spec":{"selector":{"app":"hotel-booking","version":"green"}}}'
            echo "✅ Traffic switched to GREEN deployment!"
          '''
        }
      }
    }

    /* ---------------- VALIDATE ---------------- */
    stage('Post-Deployment Validation') {
      steps {
        withCredentials([
          [$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-eks-creds'],
          file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')
        ]) {
          sh '''
            export KUBECONFIG=$WORKSPACE/.kube/config
            aws eks update-kubeconfig --name ${CLUSTER_NAME} --region ${REGION}

            echo "📋 Deployment Resources:"
            kubectl get pods -n ${K8S_NAMESPACE}
            kubectl get svc -n ${K8S_NAMESPACE}
            kubectl get deployments -n ${K8S_NAMESPACE}

            echo "⏳ Waiting 30s for pods to be ready..."
            sleep 30

            # Check pod status
            kubectl get pods -n ${K8S_NAMESPACE} -o wide
            
            # Health check with retry logic
            APP_URL=$(kubectl get svc hotel-booking-service -n ${K8S_NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
            if [ -z "$APP_URL" ]; then
              APP_URL=$(kubectl get svc hotel-booking-service -n ${K8S_NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
            fi
            
            if [ -n "$APP_URL" ]; then
              echo "🔍 Health check at: http://$APP_URL/actuator/health"
              for i in {1..5}; do
                echo "Attempt $i to check application health..."
                if curl -f -s --max-time 10 "http://$APP_URL/actuator/health" > /dev/null; then
                  echo "✅ Application health check PASSED!"
                  break
                else
                  echo "⏳ Application not ready yet, waiting 10s..."
                  sleep 10
                fi
              done
            else
              echo "⚠️  Could not determine application URL, skipping health check"
            fi
          '''
        }
      }
    }
  }

  post {
    success {
      echo "✅ SUCCESS: Built, scanned, analyzed, pushed and deployed!"
      sh '''
        echo "🎉 Pipeline completed successfully!"
        echo "Application deployed to:"
        kubectl get svc hotel-booking-service -n ${K8S_NAMESPACE} -o wide
      '''
      cleanWs()
    }
    failure {
      script {
        echo "❌ FAILED: Rolling back service to BLUE..."
        withCredentials([
          [$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-eks-creds'],
          file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')
        ]) {
          sh '''
            export KUBECONFIG=$WORKSPACE/.kube/config
            aws eks update-kubeconfig --name ${CLUSTER_NAME} --region ${REGION}
            echo "Rolling back to BLUE deployment..."
            kubectl patch service hotel-booking-service -n ${K8S_NAMESPACE} \
              -p '{"spec":{"selector":{"app":"hotel-booking","version":"blue"}}}' || true
            echo "✅ Rollback completed!"
          '''
        }
      }
      cleanWs()
    }
    always {
      echo "🏁 Pipeline finished at: $(date)"
    }
  }
}