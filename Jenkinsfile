pipeline {
    agent none
    stages {
        stage('Pipeline-init') {
            agent any
            steps {
                script {
                    STAGE = ""
                    STATUS = ""
                    CHANGED = "NO"
                    CHANGES = ""
                    SUBJECT = ""
                    COLOR = ""
                }
            }
        }
        stage('Hadolint-lint') {
            agent {
                docker {
                    image "lukasmartinelli/hadolint"
                    args "-u root"
                }
            }
            steps {
                script { CHANGED = "NO" }
                sh 'hadolint Dockerfile || true'
            }
            post {
                success { script { STATUS = "SUCCESS" } }
                failure { script { STATUS = "FAILURE" } }
                changed { script { CHANGED = "YES" } }
                always { script { SUBJECT = "Build #${env.BUILD_NUMBER} of ${env.JOB_NAME} at 'hadolint-lint'" } }
            }
        }
        stage('Dockerlint-lint') {
            agent {
                docker {
                    image "redcoolbeans/dockerlint"
                }
            }
            steps {
                script { CHANGED = "NO" }
                sh 'dockerlint -f Dockerfile || true'
            }
            post {
                success { script { STATUS = "SUCCESS" } }
                failure { script { STATUS = "FAILURE" } }
                changed { script { CHANGED = "YES" } }
                always { script { SUBJECT = "Build #${env.BUILD_NUMBER} of ${env.JOB_NAME} at 'dockerlint-lint'" } }
            }
        }
        stage('Dockerfile-atomic-lint') {
            agent {
                docker {
                    image "projectatomic/dockerfile-lint"
                    args "-u root"
                }
            }
            steps {
                script { CHANGED = "NO" }
                sh 'dockerfile_lint -f Dockerfile || true'
            }
            post {
                success { script { STATUS = "SUCCESS" } }
                failure { script { STATUS = "FAILURE" } }
                changed { script { CHANGED = "YES" } }
                always { script { SUBJECT = "Build #${env.BUILD_NUMBER} of ${env.JOB_NAME} at 'dockerfile-lint'" } }
            }
        }
        stage('Build container') {
            agent any
            steps {
                script { CHANGED = "NO" }
                sh "rm -rf test/integration/*"
                sh "docker build -t liatrio/ldop-jenkins:${env.BRANCH_NAME} ."
                sh "docker push liatrio/ldop-jenkins:${env.BRANCH_NAME}"
                script {
                    if ( env.BRANCH_NAME == 'master' ) {
                        containerVersion = getVersionFromContainer("liatrio/ldop-jenkins:${env.BRANCH_NAME}")
                        failIfVersionExists("liatrio","ldop-jenkins",containerVersion)
                        sh "docker build -t liatrio/ldop-jenkins:${containerVersion} ."
                    }
                }
            }
            post {
                success { script { STATUS = "SUCCESS" } }
                failure { script { STATUS = "FAILURE" } }
                changed { script { CHANGED = "YES" } }
                always { script { SUBJECT = "Build #${env.BUILD_NUMBER} of ${env.JOB_NAME} at 'ldop-jenkins-build'" } }
            }
        }
        stage('Integration-test') {
            agent {
              docker {
                image "hashicorp/terraform:full"
                args "-u root"
              }
            }
            steps {
                git branch: 'master', url: 'https://github.com/liatrio/ldop-docker-compose'
                withCredentials([[
                    $class: "AmazonWebServicesCredentialsBinding",
                    credentialsId: "Jenkins AWS Creds",
                    accessKeyVariable: "AWS_ACCESS_KEY_ID",
                    secretKeyVariable: "AWS_SECRET_ACCESS_KEY"]]) {
                    sh "echo \$(pwd) > result"
                    script {
                        DIR = readFile('result').trim()
                        CHANGED = "NO"
                    }
                    testSuite("ldop-jenkins", "${env.BRANCH_NAME}", DIR)
                    sh "export TF_VAR_branch_name=\"${env.BRANCH_NAME}\""
                    sh '''sed -i 's/timeout 30m/timeout -t 1800/g' test/integration/run-integration-test.sh &&
                          bash test/validation/validation.sh &&
                          cd test/integration/ &&
                          terraform init &&
                          bash run-integration-test.sh'''
                }
            }
            post {
                success { script { STATUS = "SUCCESS" } }
                failure { script { STATUS = "FAILURE" } }
                changed { script { CHANGED = "YES" } }
                always { script { SUBJECT = "Build #${env.BUILD_NUMBER} of ${env.JOB_NAME} at 'ldop-integration-testing'" } }
            }
        }
        stage('Push to dockerhub') {
            agent any
            steps {
                script { CHANGED = "NO" }
                sh "docker tag liatrio/ldop-jenkins:${env.BRANCH_NAME} liatrio/ldop-jenkins:latest"
                sh "docker push liatrio/ldop-jenkins:latest"
                script {
                    if ( env.BRANCH_NAME == 'master' )
                        sh "docker push liatrio/ldop-jenkins:${containerVersion}"
                }
            }
            post {
                success { script { STATUS = "SUCCESS" } }
                failure { script { STATUS = "FAILURE" } }
                changed { script { CHANGED = "YES" } }
                always { script { SUBJECT = "Build #${env.BUILD_NUMBER} of ${env.JOB_NAME} at 'Push to dockerhub'" } }
            }
        }
    }
    post {
        always {
            script {
                if (CHANGED == "YES") {
                    if (STATUS == "SUCCESS") {
                        COLOR = "3AA552"
                    } else {
                        COLOR = "CF1318"
                    }

                    RESULT = formatSlackOutput(SUBJECT, env.JOB_URL, currentBuild.changeSets, STATUS)

                    slackSend (channel: "#ldop", color: "#${COLOR}", message: "${RESULT}")
                }
            }
        }
    }
}
