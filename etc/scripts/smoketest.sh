#!/bin/bash
#
# Copyright (c) 2019, 2025 Oracle and/or its affiliates.
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
    "${CODE}" "${BASH_SOURCE[0]}" "${LINENO}" "${BASH_COMMAND}"
}
trap on_error ERR

usage(){
    cat <<EOF

DESCRIPTION: Helidon Smoke Test

USAGE:

$(basename "${0}") [ OPTIONS ] --version=V

  --staged
        Use the OSS Sonatype Staging repository at
        https://oss.sonatype.org/content/groups/staging/
        If not specified assumes release is in Maven Central

  --version=x.y.z
        Test the specified version of Helidon.

  --archetype=X
        Test the specified archetype.

  --clean
        Use a fresh local Maven repository.

  --help
        Prints the usage and exits.

EOF
}

# parse command line args
ARGS=( "${@}" )
for ((i=0;i<${#ARGS[@]};i++))
{
  ARG=${ARGS[${i}]}
  case ${ARG} in
  "--staged")
    MVN_ARGS="${MVN_ARGS} -Possrh-staging"
    ;;
  "--clean")
    MVN_ARGS="${MVN_ARGS} -Dmaven.repo.local=.m2/repository"
    ;;
  "--version="*)
    VERSION=${ARG#*=}
    ;;
  "--archetype="*)
    ARCHETYPE=${ARG#*=}
    ;;
  "--help")
    usage
    exit 0
    ;;
  *)
    echo "ERROR: unknown argument: ${ARG}"
    exit 1
    ;;
  esac
}
readonly ARGS
readonly ARCHETYPE

if [ -z "${VERSION}" ] ; then
    echo "ERROR: version required" >&2
    usage
    exit 1
fi

PID=""
trap '[ -n "${PID}" ] && kill ${PID} 2> /dev/null || true' 0

maven_proxies() {
  [ -f "${HOME}/.m2/settings.xml" ] && \
  awk -f- "${HOME}/.m2/settings.xml" <<EOF
      BEGIN{
        IN_PROXIES="FALSE"
        FS="[<>]"
      }
      /<proxies>/{
        IN_PROXIES="true"
        next
      }
      /<\/proxies>/{
        IN_PROXIES="false"
      }
      {
        if (IN_PROXIES=="true") {
          print \$0
        }
      }
EOF
}

maven_settings() {
 cat <<EOF
 <settings>
     <proxies>
 $(maven_proxies)
     </proxies>
     <profiles>
         <profile>
             <id>ossrh-staging</id>
             <repositories>
                 <repository>
                     <id>ossrh-staging</id>
                     <name>OSS Sonatype Staging</name>
                     <url>https://oss.sonatype.org/content/groups/staging/</url>
                     <snapshots>
                         <enabled>false</enabled>
                     </snapshots>
                     <releases>
                         <enabled>true</enabled>
                     </releases>
                 </repository>
             </repositories>
             <pluginRepositories>
                 <pluginRepository>
                     <id>ossrh-staging</id>
                     <name>OSS Sonatype Staging</name>
                     <url>https://oss.sonatype.org/content/groups/staging/</url>
                     <snapshots>
                         <enabled>false</enabled>
                     </snapshots>
                     <releases>
                         <enabled>true</enabled>
                     </releases>
                 </pluginRepository>
             </pluginRepositories>
         </profile>
     </profiles>
 </settings>
EOF
}

# arg1: uri
wait_ready() {
  sleep 6
  if ! kill -0 "${PID}" 2> /dev/null ; then
    echo "ERROR: process not alive" >&2
    return 1
  fi

  case ${1} in
  bare-*)
    # no-op
    ;;
  *-mp)
    curl -q -s -f \
      --retry 3 \
       -o /dev/null \
       -w "%{http_code} %{url_effective}\n" \
       http://localhost:8080/health/live
    ;;
  *)
    curl -q -s -f \
      --retry 3 \
       -o /dev/null \
       -w "%{http_code} %{url_effective}\n" \
       http://localhost:8080/observe/health/live
    ;;
  esac
}

# arg1: url
http_get() {
  curl -q -s -f \
    -w "\n%{http_code} %{url_effective}\n" \
    "${1}"
}

# arg1: archetype
test_app(){
  # health & metrics
  case ${1} in
  bare-*)
    # no-op
    ;;
  *-se)
    http_get http://localhost:8080/observe/health
    http_get http://localhost:8080/observe/metrics
    ;;
  *-mp)
    http_get http://localhost:8080/health
    http_get http://localhost:8080/metrics
    ;;
  esac

  # app endpoint
  case ${1} in
  database-*)
    # no-op
    ;;
  bare-se|quickstart-*)
    http_get http://localhost:8080/greet
    http_get http://localhost:8080/greet/Joe
    ;;
  bare-mp)
    http_get http://localhost:8080/simple-greet
    ;;
  esac
}

# arg1: archetype
test_archetype(){
  printf "\n*******************************************"
  printf "\nINFO: %s - Generating project" "${ARCHETYPE}"
  printf "\n*******************************************\n\n"

  # shellcheck disable=SC2086
  mvn ${MVN_ARGS} -U \
    -DinteractiveMode=false \
    -DarchetypeGroupId=io.helidon.archetypes \
    -DarchetypeArtifactId="helidon-${ARCHETYPE}" \
    -DarchetypeVersion="${VERSION}" \
    -DgroupId=io.helidon.smoketest \
    -DartifactId=helidon-"${ARCHETYPE}" \
    -Dpackage=io.helidon.smoketest."${ARCHETYPE/-/.}" \
    archetype:generate

  printf "\n*******************************************"
  printf "\nINFO: %s - Building jar" "${ARCHETYPE}"
  printf "\n*******************************************\n\n"

  # shellcheck disable=SC2086
  mvn ${MVN_ARGS} \
    -f "helidon-${ARCHETYPE}/pom.xml" \
    clean package

  printf "\n*******************************************"
  printf "\nINFO: %s - Running and pinging app using jar image" "${ARCHETYPE}"
  printf "\n*******************************************\n\n"

  java -jar "helidon-${ARCHETYPE}/target/helidon-${ARCHETYPE}.jar" &
  PID=${!}
  wait_ready "${ARCHETYPE}"
  test_app "${ARCHETYPE}"
  kill ${PID}

  printf "\n*******************************************"
  printf "\nINFO: %s - Building jlink image" "${ARCHETYPE}"
  printf "\n*******************************************\n\n"

  # shellcheck disable=SC2086
  mvn ${MVN_ARGS} \
    -f "helidon-${ARCHETYPE}/pom.xml" \
    -DskipTests \
    -Pjlink-image \
    package

  printf "\n*******************************************"
  printf "\nINFO: %s - Running and pinging app using jlink image" "${ARCHETYPE}"
  printf "\n*******************************************\n\n"

  "helidon-${ARCHETYPE}/target/helidon-${ARCHETYPE}-jri/bin/start" &
  PID=${!}
  wait_ready "${ARCHETYPE}"
  test_app "${ARCHETYPE}"
  kill ${PID}
}

WORK_DIR="${TMPDIR:-$(mktemp -d)}/helidon-smoke/${VERSION}-$(date +%Y-%m-%d-%H-%M-%S)"
readonly WORK_DIR

LOG_FILE="${WORK_DIR}/test.log"
readonly LOG_FILE

mkdir -p "${WORK_DIR}"

maven_settings > "${WORK_DIR}/settings.xml"
MVN_ARGS="${MVN_ARGS} -s ${WORK_DIR}/settings.xml"

exec 1>> >(tee  "${LOG_FILE}")
exec 2>> >(tee  "${LOG_FILE}")

cd "${WORK_DIR}"

printf "\n*******************************************"
printf "\nINFO: Directory - %s" "${WORK_DIR}"
printf "\nINFO: Log - %s" "${LOG_FILE}"
printf "\n*******************************************\n\n"

test_archetype "${ARCHETYPE}"

printf "\n*******************************************"
printf "\nINFO: Directory - %s" "${WORK_DIR}"
printf "\nINFO: Log - %s" "${LOG_FILE}"
printf "\n*******************************************\n\n"
