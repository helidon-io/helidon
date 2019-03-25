#!/bin/bash
#
# Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

#
# Update the helidon.version property used by the quickstart examples
# and the archetype.version used for the archetypes.
#
# set-version.sh [new-helidon-version]
#
# If new-helidon-version is not passed then the script will look in
# FULL_VERSION
#
set -eo pipefail

# Path to this script
if [ -h "${0}" ] ; then
  SCRIPT_PATH="$(readlink "$0")"
else
  SCRIPT_PATH="${0}"
fi

# Current directory
MY_DIR=$(cd $(dirname -- "${SCRIPT_PATH}") ; pwd -P)

EXAMPLE_DIR="${MY_DIR}/.."

EXAMPLES=" \
  helidon-quickstart-se \
  helidon-quickstart-mp \
  helidon-quickstart-se-graalvm \
"

TEMP_FILE=$(mktemp -t pom.xml.XXXXXX)

newVersion="${FULL_VERSION}"
if [ ! -z "${1}" ]; then
    newVersion="${1}"
fi

# Need a version string
if [ -z "${newVersion}" ]; then
    echo "Must provide the Helidon bom pom version"
    exit 1
fi

# Update helidon.version property in example pom files
for _ex in ${EXAMPLES}; do

  echo "========== ${_ex} =========="
  pomFile="${EXAMPLE_DIR}/${_ex}/pom.xml"
  echo "    Change helidon.version in ${pomFile} to ${newVersion}"

  cat ${pomFile} | sed "s:<helidon.version>.*</helidon.version>:<helidon.version>${newVersion}</helidon.version>:" \
       > "${TEMP_FILE}" && mv "${TEMP_FILE}"  "${pomFile}"

  gradleFile="${EXAMPLE_DIR}/${_ex}/build.gradle"
  echo "    Change helidonversion in ${gradleFile} to ${newVersion}"

  cat ${gradleFile} | sed "s:helidonversion = .*:helidonversion = '${newVersion}':" \
       > "${TEMP_FILE}" && mv "${TEMP_FILE}"  "${gradleFile}"

done

echo "Updating ${MY_DIR}/archetype.properties"
# Update archetype.version in archetype property file
cat "${MY_DIR}/archetype.properties" | sed "s/^archetype.version=.*/archetype.version=${newVersion}/" \
     > "${TEMP_FILE}" && mv "${TEMP_FILE}"  "${MY_DIR}/archetype.properties"

