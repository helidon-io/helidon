#!/bin/bash
#
# Copyright (c) 2018,2020 Oracle and/or its affiliates. All rights reserved.
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

set -o pipefail || true  # trace ERR through pipes
set -o errtrace || true # trace ERR through commands and functions
set -o errexit || true  # exit the script if any statement returns a non-true return value

on_error(){
    CODE="${?}" && \
    set +x && \
    printf "[ERROR] Error(code=%s) occurred at %s:%s command: %s\n" \
        "${CODE}" "${BASH_SOURCE}" "${LINENO}" "${BASH_COMMAND}"
}
trap on_error ERR

# Path to this script
if [ -h "${0}" ] ; then
  readonly SCRIPT_PATH="$(readlink "${0}")"
else
  readonly SCRIPT_PATH="${0}"
fi

# Path to the root of the workspace
readonly WS_DIR=$(cd $(dirname -- "${SCRIPT_PATH}") ; cd ../.. ; pwd -P)

source ${WS_DIR}/etc/scripts/pipeline-env.sh

if [ "${WERCKER}" = "true" -o "${GITLAB}" = "true" ] ; then
  apt-get update && apt-get -y install graphviz
fi

inject_credentials

echo "========="
mvn --version
echo "========="

VERSION_PLUGIN_JAVADOC="3.2.0-SNAPSHOT"

# Our aggregated javadoc are built as part of the site lifecycle.
# We require enhancements that are in maven-javadoc-plugin 3.2.0.
# It isn't released yet. So we build a SNAPSHOT version ourselves.
# Ick, I know. But we expect it to be released real-soon-now, so this
# is a temporary hack.
build_javadoc_plugin(){
    readonly JAVADOC_GIT_URL="https://github.com/apache/maven-javadoc-plugin"
    readonly JAVADOC_PLUGIN_DIR="${WS_DIR}/../maven-javadoc-plugin"

    mkdir -p ${JAVADOC_PLUGIN_DIR}
    git clone ${JAVADOC_GIT_URL} ${JAVADOC_PLUGIN_DIR}
    mvn -f ${JAVADOC_PLUGIN_DIR}/pom.xml clean install -DskipTests=true
}

mvn -f ${WS_DIR}/pom.xml \
    clean install -e \
    -B \
    -Dversion.plugin.javadoc=${VERSION_PLUGIN_JAVADOC} \
    -Pexamples,archetypes,spotbugs,javadoc,sources,tck,tests,pipeline

examples/quickstarts/archetypes/test-archetypes.sh

# Build site and agregated javadocs
build_javadoc_plugin
mvn site
