/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

pipeline {
  agent {
    label "linux"
  }
  options {
    parallelsAlwaysFailFast()
  }
  environment {
    NPM_CONFIG_REGISTRY = credentials('npm-registry')
  }
  stages {
    stage('default') {
      parallel {
        stage('build'){
          steps {
            script {
              try {
                sh './etc/scripts/build.sh'
              } finally {
                archiveArtifacts artifacts: "**/target/surefire-reports/*.txt, **/target/failsafe-reports/*.txt"
                junit testResults: '**/target/surefire-reports/*.xml,**/target/failsafe-reports/*.xml'
              }
            }
          }
        }
        stage('copyright'){
          steps {
            sh './etc/scripts/copyright.sh'
          }
        }
        stage('checkstyle'){
          steps {
            sh './etc/scripts/checkstyle.sh'
          }
        }
        stage('integration-tests') {
          stages {
            stage('test-mysql') {
              agent {
                kubernetes {
                  inheritFrom 'k8s-slave'
                  yamlFile 'etc/pods/mysql.yaml'
                  yamlMergeStrategy merge()
                }
              }
              steps {
                sh './etc/scripts/test-integ-mysql.sh'
                archiveArtifacts artifacts: "tests/integration/**/target/failsafe-reports/*.txt"
                junit testResults: 'tests/integration/**/target/failsafe-reports/*.xml'
              }
            }
            stage('test-pgsql') {
              agent {
                kubernetes {
                  inheritFrom 'k8s-slave'
                  yamlFile 'etc/pods/pgsql.yaml'
                  yamlMergeStrategy merge()
                }
              }
              steps {
                sh './etc/scripts/test-integ-pgsql.sh'
                archiveArtifacts artifacts: "tests/integration/**/target/failsafe-reports/*.txt"
                junit testResults: 'tests/integration/**/target/failsafe-reports/*.xml'
              }
            }
          }
        }
      }
    }
    stage('release') {
      when {
        branch '**/release-*'
      }
      environment {
        GITHUB_SSH_KEY = credentials('helidonrobot-github-ssh-private-key')
        MAVEN_SETTINGS_FILE = credentials('helidonrobot-maven-settings-ossrh')
        GPG_PUBLIC_KEY = credentials('helidon-gpg-public-key')
        GPG_PRIVATE_KEY = credentials('helidon-gpg-private-key')
        GPG_PASSPHRASE = credentials('helidon-gpg-passphrase')
      }
      steps {
        sh './etc/scripts/release.sh release_build'
      }
    }
  }
}
