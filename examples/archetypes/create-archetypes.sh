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
# Create archetypes from the quickstart examples. Usage:
#
# create-archetypes.sh [path-to-local-maven-repo]
#
# path-to-local-maven-repo is an optional path to a local
# maven repository to use. This is used, for example, by
# build pipelines that use a non-standard place for the
# local maven repository.
#
# This script uses the archetype.properties file located in the
# same directory as the script as input.
#
# The script does this for each quickstart example:
#
#  1 A clean build of the example
#  2 Generates the archetype for the example. The generated
#    archetype project goes in target/generated-sources/archetype
#  3 Build's the generated archetype project
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
"

TEMP_PROP_FILE=$(mktemp -t archetype.properties.XXXXXX)

EXTRA_MAVEN_OPTS=
if [ ! -z "${1}" ]; then
    if [ ! -d "${1}" ]; then
        echo "No such maven repo directory ${1}"
        exit 1
    fi
    EXTRA_MAVEN_OPTS="-Dmaven.repo.local=${1}"
    echo "Using local repository ${1}"
fi

# Make sure the parent and bom poms are built
mvn -B -N  ${EXTRA_MAVEN_OPTS} -f ${MY_DIR}/../../pom.xml clean install
mvn -B -N  ${EXTRA_MAVEN_OPTS} -f ${MY_DIR}/../../bom/pom.xml clean install

for _ex in ${EXAMPLES}; do

  printf "\n========== ${_ex} ==========\n"
  printf "Clean and sanity build of ${_ex}\n\n"
  mvn -B ${EXTRA_MAVEN_OPTS} -f ${EXAMPLE_DIR}/${_ex}/pom.xml clean package

  echo "    ===== Generating archetype.properties file ${TEMP_PROP_FILE}"
  cat ${MY_DIR}/archetype.properties | sed "s:\${archetypeArtifactId}:${_ex}:" \
       > "${TEMP_PROP_FILE}"
  cat ${TEMP_PROP_FILE}

  echo "    ===== Generating archetype for ${_ex}"
  mvn -B ${EXTRA_MAVEN_OPTS} -f ${EXAMPLE_DIR}/${_ex}/pom.xml archetype:create-from-project -Darchetype.artifactId=${ex}  -Darchetype.properties="${TEMP_PROP_FILE}"

  # Edit the pom to add more info
  ARCHETYPE_DIR=${EXAMPLE_DIR}/${_ex}/target/generated-sources/archetype
  mv ${ARCHETYPE_DIR}/pom.xml ${ARCHETYPE_DIR}/pom.xml.old
  head -11 ${ARCHETYPE_DIR}/pom.xml.old > ${ARCHETYPE_DIR}/pom.xml
  cat >> ${ARCHETYPE_DIR}/pom.xml << EOF
  <description>Helidon Archetype</description>

  <url>https://helidon.io</url>

  <organization>
      <name>Oracle Corporation</name>
      <url>http://www.oracle.com/</url>
  </organization>

  <licenses>
      <license>
          <name>Apache 2.0</name>
          <url>https://www.apache.org/licenses/LICENSE-2.0</url>
      </license>
  </licenses>

  <developers>
      <developer>
          <name>Tomas Langer</name>
          <email>tomas.langer@oracle.com</email>
          <organization>Oracle Corporation</organization>
      </developer>
      <developer>
          <name>Tim Quinn</name>
          <email>tim.quinn@oracle.com</email>
          <organization>Oracle Corporation</organization>
      </developer>
      <developer>
          <name>Romain Grecourt</name>
          <email>romain.grecourt@oracle.com</email>
          <organization>Oracle Corporation</organization>
      </developer>
      <developer>
          <name>Laird Jarrett Nelson</name>
          <email>laird.nelson@oracle.com</email>
          <organization>Oracle Corporation</organization>
      </developer>
      <developer>
          <name>Santiago Pericas-Geertsen</name>
          <email>santiago.pericasgeertsen@oracle.com</email>
          <organization>Oracle Corporation</organization>
      </developer>
  </developers>

  <scm>
      <developerConnection>scm:git:git@github.com:oracle/helidon.git</developerConnection>
      <connection>scm:git:git@github.com:oracle/helidon.git</connection>
      <tag>HEAD</tag>
      <url>https://github.com/oracle/helidon</url>
  </scm>
EOF
  tail -20 ${ARCHETYPE_DIR}/pom.xml.old >> ${ARCHETYPE_DIR}/pom.xml
  rm -f ${ARCHETYPE_DIR}/pom.xml.old

  printf "\nBuilding archetype for ${_ex}\n\n"
  mvn -B ${EXTRA_MAVEN_OPTS} -f ${ARCHETYPE_DIR}/pom.xml install

done

/bin/rm ${TEMP_PROP_FILE}

echo "Done!"
