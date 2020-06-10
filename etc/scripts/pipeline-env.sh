#!/bin/bash
#
# Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

require_env() {
    if [ -z "$(eval echo \$${1})" ] ; then
        echo "ERROR: ${1} not set in the environment"
        return 1
    fi
}
if [ -n "${JENKINS_HOME}" ] ; then
    export JAVA_HOME="/tools/jdk11"
    MAVEN_OPTS="${MAVEN_OPTS} -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn"
    MAVEN_OPTS="${MAVEN_OPTS} -Dorg.slf4j.simpleLogger.showDateTime=true"
    MAVEN_OPTS="${MAVEN_OPTS} -Dorg.slf4j.simpleLogger.dateTimeFormat=HH:mm:ss,SSS"
    export MAVEN_OPTS
    export PATH="/tools/apache-maven-3.6.3/bin:${JAVA_HOME}/bin:/tools/node-v12/bin:${PATH}"
    if [ -n "${GITHUB_SSH_KEY}" ] ; then
        export GIT_SSH_COMMAND="ssh -i ${GITHUB_SSH_KEY}"
    fi
    MAVEN_ARGS="${MAVEN_ARGS} -B"
    if [ -n "${MAVEN_SETTINGS_FILE}" ] ; then
        MAVEN_ARGS="${MAVEN_ARGS} -s ${MAVEN_SETTINGS_FILE}"
    fi
    if [ -n "${NPM_CONFIG_REGISTRY}" ] ; then
        MAVEN_ARGS="${MAVEN_ARGS} -Dnpm.download.root=${NPM_CONFIG_REGISTRY}/npm/-/"
    fi
    export MAVEN_ARGS

    if [ -n "${https_proxy}" ] && [[ ! "${https_proxy}" =~ ^http:// ]] ; then
        export https_proxy="http://${https_proxy}"
    fi
    if [ -n "${http_proxy}" ] && [[ ! "${http_proxy}" =~ ^http:// ]] ; then
        export http_proxy="http://${http_proxy}"
    fi
    if [ ! -e "${HOME}/.npmrc" ] ; then
        if [ -n "${NPM_CONFIG_REGISTRY}" ] ; then
            echo "registry = ${NPM_CONFIG_REGISTRY}" >> ${HOME}/.npmrc
        fi
        if [ -n "${https_proxy}" ] ; then
            echo "https-proxy = ${https_proxy}" >> ${HOME}/.npmrc
        fi
        if [ -n "${http_proxy}" ] ; then
            echo "proxy = ${http_proxy}" >> ${HOME}/.npmrc
        fi
        if [ -n "${NO_PROXY}" ] ; then
            echo "noproxy = ${NO_PROXY}" >> ${HOME}/.npmrc
        fi
    fi

    if [ -n "${GPG_PUBLIC_KEY}" ] ; then
        gpg --import --no-tty --batch ${GPG_PUBLIC_KEY}
    fi
    if [ -n "${GPG_PRIVATE_KEY}" ] ; then
        gpg --allow-secret-key-import --import --no-tty --batch ${GPG_PRIVATE_KEY}
    fi
    if [ -n "${GPG_PASSPHRASE}" ] ; then
        echo "allow-preset-passphrase" >> ~/.gnupg/gpg-agent.conf
        gpg-connect-agent reloadagent /bye
        GPG_KEYGRIP=$(gpg --with-keygrip -K | grep "Keygrip" | head -1 | awk '{print $3}')
        /usr/lib/gnupg/gpg-preset-passphrase --preset "${GPG_KEYGRIP}" <<< "${GPG_PASSPHRASE}"
    fi
fi