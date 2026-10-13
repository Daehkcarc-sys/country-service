pipeline {
    agent any
    tools {
        maven 'M2_HOME'
    }
    triggers {
        githubPush()
    }
    stages {
        stage('Checkout code') {
            steps {
                git branch: 'main', url: 'https://github.com/Daehkcarc-sys/country-service.git'
            }
        }
        stage('Compile code') {
            steps {
                sh 'mvn compile'
            }
        }
        stage('Test code') {
            steps {
                sh 'mvn test'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
                }
            }
        }
        stage('Package code') {
            steps {
                sh 'mvn package -DskipTests'
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
}
