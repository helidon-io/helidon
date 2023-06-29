#
# Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

###############################################################################
# Pipeline environment setup                                                  #
###############################################################################
# Shell variables: WS_DIR
# Arguments: $1 - Script path
#            $2 - cd to Helidon root directory from script path
#
# Atleast WS_DIR or both arguments must be passed.

# WS_DIR variable verification.
if [ -z "${WS_DIR}" ]; then

    if [ -z "${1}" ]; then
        echo "ERROR: Missing required script path, exitting"
        exit 1
    fi

    if [ -z "${2}" ]; then
        echo "ERROR: Missing required cd to Helidon root directory from script path, exitting"
        exit 1
    fi

    readonly WS_DIR=$(cd $(dirname -- "${1}") ; cd ${2} ; pwd -P)

fi

# Multiple definition protection.
if [ -z "${__PIPELINE_ENV_INCLUDED__}" ]; then
    readonly __PIPELINE_ENV_INCLUDED__='true'

    . ${WS_DIR}/etc/scripts/includes/error_handlers.sh

    if [ -z "${GRAALVM_HOME}" ]; then
        export GRAALVM_HOME="/tools/graalvm-ce-java17-22.3.0"
    fi

    require_env() {
        if [ -z "$(eval echo \$${1})" ] ; then
            echo "ERROR: ${1} not set in the environment"
            return 1
        fi
    }

    # Set Graal VM into JAVA_HOME and PATH
    # Modified shell variables: JAVA_HOME - JDK home directory
    #                           PATH      - executables search path
    graalvm() {
        JAVA_HOME=${GRAALVM_HOME}
        PATH="${PATH}:${JAVA_HOME}/bin"
    }

    MAVEN_OPTS="${MAVEN_OPTS} -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn"
    MAVEN_OPTS="${MAVEN_OPTS} -Dorg.slf4j.simpleLogger.showDateTime=true"
    MAVEN_OPTS="${MAVEN_OPTS} -Dorg.slf4j.simpleLogger.dateTimeFormat=HH:mm:ss,SSS"
    # Needed for archetype engine plugin
    MAVEN_OPTS="${MAVEN_OPTS} --add-opens=java.base/java.util=ALL-UNNAMED"
    # Needed for generating site
    MAVEN_OPTS="${MAVEN_OPTS} --add-opens=java.desktop/com.sun.imageio.plugins.png=ALL-UNNAMED"

    MAVEN_ARGS="${MAVEN_ARGS} -B ${MAVEN_HTTP_ARGS}"

    if [ -n "${JENKINS_HOME}" ] ; then
      export JAVA_HOME="/tools/jdk17"
      export PATH="/tools/apache-maven-3.8.6/bin:${JAVA_HOME}/bin:/tools/node-v12/bin:${PATH}"
      if [ -n "${GITHUB_SSH_KEY}" ] ; then
          export GIT_SSH_COMMAND="ssh -i ${GITHUB_SSH_KEY}"
      fi
      if [ -n "${NPM_CONFIG_REGISTRY}" ] ; then
          MAVEN_ARGS="${MAVEN_ARGS} -Dnpm.download.root=${NPM_CONFIG_REGISTRY}/npm/-/"
      fi
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
    fi

    if [ -n "${RELEASE_WORKFLOW}" ] ; then
        if [ -n "${MAVEN_SETTINGS}" ] ; then
            export MAVEN_SETTINGS_FILE="${HOME}/.m2/settings.xml"
            echo "${MAVEN_SETTINGS}" > "${MAVEN_SETTINGS_FILE}"
            MAVEN_ARGS="${MAVEN_ARGS} -s ${MAVEN_SETTINGS_FILE}"
        fi
        if [ -n "${GPG_PUBLIC_KEY}" ] ; then
            tmpfile=$(mktemp /tmp/pub.XXXXXX.key)
            echo "${GPG_PUBLIC_KEY}" > "${tmpfile}"
            gpg --import --no-tty --batch "${tmpfile}"
            rm "$tmpfile"
        fi
        if [ -n "${GPG_PRIVATE_KEY}" ] ; then
            tmpfile=$(mktemp /tmp/pri.XXXXXX.key)
            echo "${GPG_PRIVATE_KEY}" > "${tmpfile}"
            gpg --allow-secret-key-import --import --no-tty --batch "${tmpfile}"
            rm "$tmpfile"
        fi
        if [ -n "${GPG_PASSPHRASE}" ] ; then
            echo "allow-preset-passphrase" >> ~/.gnupg/gpg-agent.conf
            gpg-connect-agent reloadagent /bye
            GPG_KEYGRIP=$(gpg --with-keygrip -K | grep "Keygrip" | head -1 | awk '{print $3}')
            /usr/lib/gnupg/gpg-preset-passphrase --preset "${GPG_KEYGRIP}" <<< "${GPG_PASSPHRASE}"
        fi
    fi
    export MAVEN_ARGS
    export MAVEN_OPTS
else
    echo "WARNING: ${WS_DIR}/etc/scripts/includes/pipeline-env.sh included multiple times."
fi
