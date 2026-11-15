pipeline {
    agent { label 'shoe-factory-docker' }

    options {
        disableConcurrentBuilds()
    }

    parameters {
        booleanParam(name: 'DEPLOY_BACKEND', defaultValue: false, description: '构建成功后部署 Java 后端')
    }

    stages {
        stage('Build backend') {
            steps {
                script {
                    def commit = sh(script: 'git rev-parse --short=12 HEAD', returnStdout: true).trim()
                    env.BACKEND_IMAGE = "shoe-factory-backend:${commit}-${env.BUILD_NUMBER}"
                }
                sh '''
                    set -eu
                    set -- --tag "$BACKEND_IMAGE"
                    if [ -n "${MAVEN_SETTINGS_FILE:-}" ]; then
                        set -- "$@" --secret "id=maven_settings,src=$MAVEN_SETTINGS_FILE"
                    fi
                    docker build "$@" backend
                '''
            }
        }

        stage('Deploy backend') {
            when {
                expression { params.DEPLOY_BACKEND }
            }
            steps {
                sh '''
                    set -eu
                    : "${BACKEND_ENV_FILE:?Set BACKEND_ENV_FILE on the deployment agent}"
                    : "${DEPLOY_ROOT:?Set DEPLOY_ROOT on the deployment agent}"
                    docker compose -f deploy/compose.backend.yml up -d --no-deps --no-build --wait --wait-timeout 150 backend
                '''
            }
        }
    }
}
