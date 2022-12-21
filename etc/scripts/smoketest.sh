#!/bin/bash
#
# Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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

# Smoke test a Helidon release. This assumes:
# 1. The release has a source tag in the Helidon GitHub repo
# 2. The bits are in either the OSS Sonatype Staging Repo or Maven Central
# 3. You have a profile defined as "ossrh-staging" that configures
#    https://oss.sonatype.org/content/groups/staging/ as a repository
#    See bottom of RELEASE.md for details

# Path to this script
[ -h "${0}" ] && readonly SCRIPT_PATH="$(readlink "${0}")" || readonly SCRIPT_PATH="${0}"

# Load error handling functions and define error handling
. $(dirname -- "${SCRIPT_PATH}")/includes/error_handlers.sh

# Local error handler
smoketest_on_error(){
    on_error
    echo "===== Log file: ${OUTPUTFILE} ====="
    # In case there is a process left running
}

# Setup error handling using local error handler (defined in includes/error_handlers.sh)
error_trap_setup 'smoketest_on_error'

usage(){
    cat <<EOF

DESCRIPTION: Helidon Smoke Test Script

USAGE:

$(basename ${0}) [ --staged ] [ --giturl=URL ] [ --clean ] [--help ] --version=V  CMD

  --staged
        Use the OSS Sonatype Staging repository at
        https://oss.sonatype.org/content/groups/staging/
        If not specified assumes release is in Maven Central

  --version=x.y.z
        Test the specified version of Helidon

  --giturl=URL
        URL to git repository with release tag. If you don't provide
        this it will attempt to use origin of the repo this script
        is being run from

  --clean
        Remove the specified version of Helidon from the local
        Maven repository cache before running tests. This is to
        ensure artifacts are downloaded from remote repository.

  --help
        Prints the usage and exits.

  CMD:

    full
        Runs full smoke test against released version:
        1. archetypes
        2. functional tests in workspace
        3. builds examples

    quick
        1. archetypes


  Example:
    smoketest.sh --staged --clean --version=1.4.1 full
EOF
}

STAGED_PROFILE=""
CLEAN_MVN_REPO=""

# parse command line args
ARGS=( "${@}" )
for ((i=0;i<${#ARGS[@]};i++))
{
    ARG=${ARGS[${i}]}
    case ${ARG} in
    "--staged")
        readonly STAGED_PROFILE="-Possrh-staging"
        ;;
    "--clean")
        readonly CLEAN_MVN_REPO="true"
        ;;
    "--version="*)
        VERSION=${ARG#*=}
        ;;
    "--giturl="*)
        GIT_URL=${ARG#*=}
        ;;
    "--help")
        usage
        exit 0
        ;;
    *)
        if [ "${ARG}" = "full" ] || [ "${ARG}" = "quick" ] ; then
            readonly COMMAND="${ARG}"
        else
            echo "ERROR: unknown argument: ${ARG}"
            exit 1
        fi
        ;;
    esac
}

if [ -z "${COMMAND}" ] ; then
    echo "ERROR: no command provided"
    exit 1
fi

if [ -z "${VERSION}" ] ; then
    echo "ERROR: no version provided. Please use --version option to specify a version"
    exit 1
fi

readonly MAVEN_ARGS=""

readonly SCRIPT_DIR=$(dirname ${SCRIPT_PATH})

readonly DATESTAMP=$(date +%Y-%m-%d-%H-%M-%S)
mkdir -p /var/tmp/helidon-smoke
readonly SCRATCH=$(mktemp -d /var/tmp/helidon-smoke/${VERSION}-${DATESTAMP}.XXXX)

if [ -z "${GIT_URL}" ] ; then
    cd ${SCRIPT_DIR}
    GIT_URL=$(git remote get-url origin)
fi

if [ -z "${GIT_URL}" ] ; then
    echo "ERROR: can't determine URL of git repository. Pleas use --giturl option"
    exit 1
fi

set -u

full(){
    echo "===== Full Test ====="
    cd ${SCRATCH}
    quick
    cd ${SCRATCH}

    if [[ "${VERSION}" =~ .*SNAPSHOT ]]; then
        echo "WARNING! SNAPSHOT version. Skipping tag checkout"
    else
        echo "===== Cloning Workspace ${GIT_URL} ====="
        git clone ${GIT_URL}
        cd ${SCRATCH}/helidon
        echo "===== Checking out tags/${VERSION} ====="
        git checkout tags/${VERSION}
    fi

    echo "===== Building examples ====="
    cd ${SCRATCH}/helidon/examples
    # XXX we exclude todo-app frontend due to the issues with npm behind firewall
    mvn ${MAVEN_ARGS} clean install -pl '!todo-app/frontend' ${STAGED_PROFILE}
    cd ${SCRATCH}

    echo "===== Building test support ====="
    cd ${SCRATCH}/helidon/microprofile/tests/
    mvn -N ${MAVEN_ARGS} clean install ${STAGED_PROFILE}
    cd ${SCRATCH}/helidon/microprofile/tests/junit5
    mvn ${MAVEN_ARGS} clean install ${STAGED_PROFILE}
    cd ${SCRATCH}/helidon/microprofile/tests/junit5-tests
    mvn ${MAVEN_ARGS} clean install ${STAGED_PROFILE}

    echo "===== Running tests ====="
    cd ${SCRATCH}/helidon/tests
    mvn ${MAVEN_ARGS} clean install ${STAGED_PROFILE}

    # Primes dependencies for native-image builds
    cd ${SCRATCH}/helidon/tests/integration/native-image
    mvn ${MAVEN_ARGS} clean install ${STAGED_PROFILE}

    echo "===== Running native image tests ====="
    if [ -z "${GRAALVM_HOME}" ]; then
        echo "WARNING! GRAALVM_HOME is not set. Skipping native image tests"
    else
        echo "GRAALVM_HOME=${GRAALVM_HOME}"
        readonly native_image_tests="se-1 mp-1 mp-2 mp-3"
        for native_test in ${native_image_tests}; do
            cd ${SCRATCH}/helidon/tests/integration/native-image/${native_test}
            mvn ${MAVEN_ARGS} clean package -Pnative-image ${STAGED_PROFILE}
        done

        # Run this one because it has no pre-reqs and self-tests
        cd ${SCRATCH}/helidon/tests/integration/native-image/mp-1
        target/helidon-tests-native-image-mp-1
    fi

}

waituntilready() {
    # Give app a chance to start --retry will retry until it is up
    # --retry-connrefused requires curl 7.51.0 or newer
    sleep 6
    #curl -s --retry-connrefused --retry 3 -X GET http://localhost:8080/health/live
    curl -s --retry 3 -X GET http://localhost:8080/health/live
    echo
}

testGET() {
    echo "GET $1"
    http_code=`curl -s -o /dev/null -w "%{http_code}" -X GET $1`
    if [ ${http_code} -ne "200" ]; then
        echo "ERROR: Bad HTTP code. Expected 200 got ${http_code}. GET $1"
        kill ${PID}
        return 1
    fi
    return 0
}

#
# $1 = archetype name: "quickstart-se"
buildAndTestArchetype(){
    archetype_name=$1
    archetype_pkg=`echo ${archetype_name} | tr "\-" "\."`

    echo "===== Testing Archetype ${archetype_name} ====="

    mvn ${MAVEN_ARGS} -U archetype:generate -DinteractiveMode=false \
        -DarchetypeGroupId=io.helidon.archetypes \
        -DarchetypeArtifactId=helidon-${archetype_name} \
        -DarchetypeVersion=${VERSION} \
        -DgroupId=io.helidon.examples \
        -DartifactId=helidon-${archetype_name} \
        -Dpackage=io.helidon.examples.${archetype_pkg} \
        ${STAGED_PROFILE}


    echo "===== ${archetype_name}: building jar ====="
    mvn ${MAVEN_ARGS} -f helidon-${archetype_name}/pom.xml ${STAGED_PROFILE} clean package

    echo "===== Running and pinging ${archetype_name} app using jar ====="
    java -jar helidon-${archetype_name}/target/helidon-${archetype_name}.jar &
    PID=$!
    testApp ${archetype_name}
    kill ${PID}

    echo "===== ${archetype_name}: building jlink image ====="
    mvn ${MAVEN_ARGS} -f helidon-${archetype_name}/pom.xml ${STAGED_PROFILE} -Pjlink-image package -DskipTests

    echo "===== Running and pinging ${archetype_name} app using jlink image ====="
    helidon-${archetype_name}/target/helidon-${archetype_name}-jri/bin/start &
    PID=$!
    testApp ${archetype_name}
    kill ${PID}
    sleep 1
}

testApp(){
    # Wait for app to come up
    waituntilready

    # Hit some endpoints
    if [ "${archetype_name}" = "quickstart-se" -o  "${archetype_name}" = "quickstart-mp" ]; then
        testGET http://localhost:8080/greet
        testGET http://localhost:8080/greet/Joe
        testGET http://localhost:8080/health
        testGET http://localhost:8080/metrics
    fi

    if [ "${archetype_name}" = "bare-se" -o  "${archetype_name}" = "bare-mp" ]; then
        testGET http://localhost:8080/simple-greet
    fi

    if [ "${archetype_name}" = "database-se" -o  "${archetype_name}" = "database-mp" ]; then
        testGET http://localhost:8080/pokemon
        testGET http://localhost:8080/health
    fi
}

quick(){
    readonly archetypes="
      quickstart-se \
      quickstart-mp \
      bare-se \
      bare-mp \
      database-se \
      database-mp \
      "

    echo "===== Quick Test ====="
    cd ${SCRATCH}

    echo "===== Testing Archetypes ====="

    for a in ${archetypes}; do
        buildAndTestArchetype $a
    done
}

cd ${SCRATCH}

readonly OUTPUTFILE=${SCRATCH}/helidon-smoketest-log.txt
readonly LOCAL_MVN_REPO=$(mvn ${MAVEN_ARGS} help:evaluate -Dexpression=settings.localRepository | grep -v '\[INFO\]')

echo "===== Running in ${SCRATCH} ====="
echo "===== Log file: ${OUTPUTFILE} ====="

if [ ! -z "${CLEAN_MVN_REPO}" -a -d "${LOCAL_MVN_REPO}" ]; then
    echo "===== Cleaning release from local maven repository ${LOCAL_MVN_REPO}  ====="
    find ${LOCAL_MVN_REPO}/io/helidon -depth  -name ${VERSION} -type d -exec rm -rf {} \;
fi

# Invoke command
${COMMAND} | tee $OUTPUTFILE

echo "===== Log file: ${OUTPUTFILE} ====="
