/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
      stages {
        stage('test-vault') {
          agent {
            kubernetes {
              inheritFrom 'k8s-slave'
              yamlFile 'etc/pods/vault.yaml'
              yamlMergeStrategy merge()
            }
          }
          steps {
            sh './etc/scripts/test-integ-vault.sh'
            archiveArtifacts artifacts: "**/target/surefire-reports/*.txt"
            junit testResults: '**/target/surefire-reports/*.xml'
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
