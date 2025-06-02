#!/bin/bash
#
# Copyright (c) 2025 Oracle and/or its affiliates.
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
      "${CODE}" "${BASH_SOURCE[0]}" "${LINENO}" "${BASH_COMMAND}" >&2
}
trap on_error ERR

usage(){
    cat <<EOF

DESCRIPTION: Create a Maven settings.xml file that includes central staging repo so we can use it for testing.

USAGE:

$(basename "${0}") --dir=D

  --dir=path
        Directory to place settings.xml file in. Defaults to ${HOME}/.m2
EOF
}

# parse command line args
ARGS=( "${@}" )
for ((i=0;i<${#ARGS[@]};i++))
{
  ARG=${ARGS[${i}]}
  case ${ARG} in
  "--dir="*)
    DESTINATION_DIRECTORY=${ARG#*=}
    ;;
  *)
    echo "ERROR: unknown argument: ${ARG}"
    exit 1
    ;;
  esac
}

if [ -z "${CENTRAL_USER}" ] ; then
    echo "ERROR: environment variable CENTRAL_USER is required." >&2
    usage
    exit 1
fi

if [ -z "${CENTRAL_PASSWORD}" ] ; then
    echo "ERROR: environment variable CENTRAL_PASSWORD is required." >&2
    usage
    exit 1
fi

if [ -z "${DESTINATION_DIRECTORY}" ] ; then
    DESTINATION_DIRECTORY="${HOME}/.m2"
    mkdir -p "${DESTINATION_DIRECTORY}"
fi

if [ ! -d "${DESTINATION_DIRECTORY}" ] ; then
    echo "ERROR: destination directory ${DESTINATION_DIRECTORY} does not exist or is not a directory" >&2
    usage
    exit 1
fi

readonly DESTINATION_DIRECTORY

if [ -e "${DESTINATION_DIRECTORY}/settings.xml" ]; then
    echo "WARNING: ${DESTINATION_DIRECTORY}/settings.xml already exists. Copying to ${DESTINATION_DIRECTORY}/settings.xml.bk" >&2
    cp  "${DESTINATION_DIRECTORY}/settings.xml" "${DESTINATION_DIRECTORY}/settings.xml.bk"
fi

# Create credential needed to access Maven Central Publishing portal
BEARER=$(printf "%s:%s" "${CENTRAL_USER}" "${CENTRAL_PASSWORD}" | base64)
readonly BEARER

maven_settings() {
 cat <<EOF
 <settings>
   <servers>
     <server>
       <id>central.manual.testing</id>
       <configuration>
         <httpHeaders>
           <property>
             <name>Authorization</name>
             <value>Bearer ${BEARER}</value>
           </property>
         </httpHeaders>
       </configuration>
     </server>
   </servers>
   <profiles>
       <profile>
         <id>central.manual.testing</id>
         <repositories>
           <repository>
             <id>central.manual.testing</id>
             <name>Central Testing repository</name>
             <url>https://central.sonatype.com/api/v1/publisher/deployments/download</url>
           </repository>
         </repositories>
         <pluginRepositories>
             <pluginRepository>
                 <id>central.manual.testing</id>
                 <name>Central Testing repository</name>
                 <url>https://central.sonatype.com/api/v1/publisher/deployments/download</url>
             </pluginRepository>
         </pluginRepositories>
       </profile>
   </profiles>
</settings>
EOF
}

setup_central_settings() {
  echo "INFO: creating settings.xml in ${DESTINATION_DIRECTORY}"
  maven_settings > "${DESTINATION_DIRECTORY}/settings.xml"
}

setup_central_settings
