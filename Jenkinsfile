pipeline {
    agent any

    options {
        skipDefaultCheckout()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '3'))
        timeout(time: 45, unit: 'MINUTES')
    }

    environment {
        XDG_RUNTIME_DIR = '/run/jenkins'
    }

    stages {
        stage('Checkout') {
            steps {
                deleteDir()
                checkout scm
                sh 'git log -1 --format="Building %h %s"'
            }
        }

        stage('Build and test') {
            steps {
                sh '''
                    set -eu
                    # CI JARs use placeholders in the two datasource profiles.
                    for profile in dev prod; do
                        config="backend/src/main/resources/application-$profile.yml"
                        awk '
                            /^  [^[:space:]]/ { datasource = ($0 ~ /^  datasource:[[:space:]]*$/) }
                            datasource && /^    url:/ {
                                print "    url: jdbc:mysql://127.0.0.1:1/ci_unused"
                                url++; next
                            }
                            datasource && /^    username:/ {
                                print "    username: ci_unused"
                                user++; next
                            }
                            datasource && /^    password:/ {
                                print "    password: ci_unused"
                                password++; next
                            }
                            { print }
                            END { if (url != 1 || user != 1 || password != 1) exit 1 }
                        ' "$config" > "$config.ci"
                        mv "$config.ci" "$config"
                    done

                    mkdir -p "$HOME/.m2/repository"
                    settings_file="$WORKSPACE/deploy/maven-public-settings.xml"
                    test -f "$settings_file"
                    podman --cgroup-manager=cgroupfs run --rm --pull=never \\
                        --network slirp4netns --userns keep-id \\
                        -e MAVEN_OPTS=-Xmx640m -e MAVEN_CONFIG=/cache \\
                        -v "$WORKSPACE:/build:rw" \\
                        -v "$HOME/.m2/repository:/cache:rw" \\
                        -v "$settings_file:/ci-maven-settings.xml:ro" \\
                        -w /build/backend localhost/shoe-ci-maven:java17 \\
                        mvn -B -ntp -Pprod -s /ci-maven-settings.xml \\
                        -Dmaven.repo.local=/cache package
                '''
            }
            post {
                always {
                    junit testResults: 'backend/target/surefire-reports/TEST-*.xml',
                          allowEmptyResults: true
                }
                success {
                    archiveArtifacts artifacts: 'backend/target/shoe-factory-backend-*.jar',
                                     fingerprint: true
                }
            }
        }
    }
}
