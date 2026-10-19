pipeline {
    agent any
    tools {
        maven 'M2_HOME'
    }
    triggers {
        githubPush()
    }
    environment {
        NEXUS_URL = 'http://localhost:8083'
        // raw Nexus repository holding the JaCoCo and Surefire reports of each build
        REPORTS_URL = "${NEXUS_URL}/repository/country-service-reports/build-${BUILD_NUMBER}"
    }
    stages {
        stage('Checkout code') {
            steps {
                git branch: 'main', url: 'https://github.com/Daehkcarc-sys/country-service.git'
            }
        }
        stage('Compile, test code, package in war file and store it in maven repo') {
            steps {
                // compile + JUnit tests + JaCoCo report + war + install in local maven repo
                sh 'mvn clean install'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
                }
            }
        }
        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv(installationName: 'MySonarQubeServer', credentialsId: 'sonarqubePWD') {
                    sh 'mvn sonar:sonar -Dsonar.projectKey=country-service -Dsonar.projectName=country-service'
                }
            }
        }
        stage('Quality Gate') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }
        stage('Deploy war to Nexus') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'nexus-creds', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS')]) {
                    sh 'mvn deploy -DskipTests -s ci/settings.xml'
                }
            }
        }
        stage('Store JaCoCo and Surefire reports in Nexus') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'nexus-creds', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS')]) {
                    sh '''
                        cd target
                        # one archive per report type
                        tar -czf jacoco-report.tar.gz -C site jacoco
                        tar -czf surefire-reports.tar.gz surefire-reports
                        curl -sf -u "$NEXUS_USER:$NEXUS_PASS" --upload-file jacoco-report.tar.gz "$REPORTS_URL/jacoco-report.tar.gz"
                        curl -sf -u "$NEXUS_USER:$NEXUS_PASS" --upload-file surefire-reports.tar.gz "$REPORTS_URL/surefire-reports.tar.gz"
                        # browsable copies: open .../jacoco/index.html in Nexus
                        cd site
                        find jacoco -type f | while read f; do
                            curl -sf -u "$NEXUS_USER:$NEXUS_PASS" --upload-file "$f" "$REPORTS_URL/$f"
                        done
                        cd ..
                        for f in surefire-reports/*.xml; do
                            curl -sf -u "$NEXUS_USER:$NEXUS_PASS" --upload-file "$f" "$REPORTS_URL/$f"
                        done
                    '''
                }
            }
        }
        stage('Deploy to Tomcat') {
            steps {
                // Tomcat 10 runs on port 8081 (Jenkins uses 8080)
                sh 'cp target/country-service.war /var/lib/tomcat10/webapps/'
            }
        }
        stage('Check deployment') {
            steps {
                retry(12) {
                    sleep 5
                    sh 'curl -sf http://localhost:8081/country-service/getcountries'
                }
            }
        }
    }
    post {
        success {
            archiveArtifacts artifacts: 'target/country-service.war', fingerprint: true
        }
    }
}
