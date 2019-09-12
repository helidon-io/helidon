#!/bin/bash
#
# Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# WERCKER=true when running inside a wercker pipeline

inject_credentials(){
  # Add private_key from IDENTITY_FILE
  if [ -n "${IDENTITY_FILE}" ] && [ ! -e ~/.ssh ]; then
    mkdir ~/.ssh/ 2>/dev/null || true
    echo -e "${IDENTITY_FILE}" > ~/.ssh/id_rsa
    chmod og-rwx ~/.ssh/id_rsa
    echo -e "Host *" >> ~/.ssh/config
    echo -e "\tStrictHostKeyChecking no" >> ~/.ssh/config
    echo -e "\tUserKnownHostsFile /dev/null" >> ~/.ssh/config
  fi

 # Add GPG key pair
 if [ -n "${GPG_PUBLIC_KEY}" ] && [ -n "${GPG_PRIVATE_KEY}" ] ; then
    mkdir ~/.gnupg 2>/dev/null || true
    chmod 700 ~/.gnupg
    echo "pinentry-mode loopback" > ~/.gnupg/gpg.conf
    echo -e "${GPG_PUBLIC_KEY}" > ~/.gnupg/helidon_pub.gpg
    gpg --import --no-tty --batch ~/.gnupg/helidon_pub.gpg
    echo -e "${GPG_PRIVATE_KEY}" > ~/.gnupg/helidon_sec.gpg
    gpg --allow-secret-key-import --import --no-tty --batch ~/.gnupg/helidon_sec.gpg
 fi

  # Add docker config from DOCKER_CONFIG_FILE
  if [ -n "${DOCKER_CONFIG_FILE}" ] && [ ! -e ~/.docker ]; then
    mkdir ~/.docker/ 2>/dev/null || true
    printf "${DOCKER_CONFIG_FILE}" > ~/.docker/config.json
    chmod og-rwx ~/.docker/config.json
  fi

  # Add maven settings from MAVEN_SETTINGS_FILE
  if [ -n "${MAVEN_SETTINGS_FILE}" ] ; then
    mkdir ~/.m2/ 2>/dev/null || true
    echo -e "${MAVEN_SETTINGS_FILE}" > ~/.m2/settings.xml
  fi

  # Add maven settings security from MAVEN_SETTINGS_SECURITY_FILE
  # Only if none exist on the system
  if [ -n "${MAVEN_SETTINGS_SECURITY_FILE}" ] && [ ! -e ~/.m2/settings-security.xml ]; then
    mkdir ~/.m2/ 2>/dev/null || true
    echo "${MAVEN_SETTINGS_SECURITY_FILE}" > ~/.m2/settings-security.xml
  fi
}

if [ "${WERCKER}" = "true" ] ; then
    MAVEN_OPTS="-Dmaven.repo.local=${WERCKER_CACHE_DIR}/local_repository"
    MAVEN_OPTS="${MAVEN_OPTS} -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn"
    MAVEN_OPTS="${MAVEN_OPTS} -Dorg.slf4j.simpleLogger.showDateTime=true"
    MAVEN_OPTS="${MAVEN_OPTS} -Dorg.slf4j.simpleLogger.dateTimeFormat=HH:mm:ss,SSS"
    export MAVEN_OPTS
    rm -rf ~/.m2/settings* ~/.gitconfig ~/.ssh ${WERCKER_CACHE_DIR}/local_repository/io/helidon
fi
