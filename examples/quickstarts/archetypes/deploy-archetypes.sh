#!/bin/bash -e
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

trap 'echo "ERROR: Error occurred at ${BASH_SOURCE}:${LINENO} command: ${BASH_COMMAND}"' ERR
set -eo pipefail

# Path to this script
if [ -h "${0}" ] ; then
  readonly SCRIPT_PATH="$(readlink "$0")"
else
  readonly SCRIPT_PATH="${0}"
fi

# Current directory
readonly MY_DIR=$(cd $(dirname -- "${SCRIPT_PATH}") ; pwd -P)

readonly MVN_VERSION=$(mvn \
    -q \
    -f ${MY_DIR}/../pom.xml \
    -Dexec.executable="echo" \
    -Dexec.args="\${project.version}" \
    --non-recursive \
    org.codehaus.mojo:exec-maven-plugin:1.3.1:exec)

if [ -n "${STAGING_REPO_ID}" ] ; then
    readonly MAVEN_REPO_URL="https://oss.sonatype.org/service/local/staging/deployByRepositoryId/${STAGING_REPO_ID}/"
else
    readonly MAVEN_REPO_URL="https://oss.sonatype.org/service/local/staging/deploy/maven2/"
fi

readonly MAVEN_DEPLOY_ARGS="org.apache.maven.plugins:maven-deploy-plugin:2.8.2:deploy -DaltDeploymentRepository=ossrh::default::${MAVEN_REPO_URL}"
readonly MAVEN_ARGS="${MAVEN_ARGS} clean verify -DskipTests gpg:sign ${MAVEN_DEPLOY_ARGS}"

${MY_DIR}/create-archetypes.sh \
    --version="${MVN_VERSION}" \
    --maven-args="${MAVEN_ARGS}"
