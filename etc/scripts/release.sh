#!/bin/bash
#
# Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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

DESCRIPTION: Helidon Release Script

USAGE:

$(basename "${0}") [ --version=V ] CMD

  --version=V
        Override the version to use.

  --help
        Prints the usage and exits.

  CMD:

    update_version
        Update the version in the workspace

    release_build
        Perform a release build
        This will create a local branch, deploy artifacts and push a tag

    create_tag
        Create and push a release tag

EOF
}

# parse command line args
ARGS=( "${@}" )
for ((i=0;i<${#ARGS[@]};i++))
{
    ARG=${ARGS[${i}]}
    case ${ARG} in
    "--version="*)
        VERSION=${ARG#*=}
        ;;
    "--help")
        usage
        exit 0
        ;;
    "update_version"|"release_build"|"create_tag")
        readonly COMMAND="${ARG}"
        ;;
    *)
        echo "ERROR: unknown argument: ${ARG}" >&2
        exit 1
        ;;
    esac
}

if [ -z "${COMMAND}" ] ; then
    echo "ERROR: no command provided" >&2
    usage >&2
    exit 1
fi

# Copy stdout as fd 6 and redirect operational output to stderr. Commands that
# return shell data use fd 6 so their output remains safe to source.
exec 6>&1 1>&2

# Path to this script
if [ -h "${0}" ] ; then
    readonly SCRIPT_PATH="$(readlink "${0}")"
else
    readonly SCRIPT_PATH="${0}"
fi

# Path to the root of the workspace
readonly WS_DIR=$(cd $(dirname -- "${SCRIPT_PATH}") ; cd ../.. ; pwd -P)

source "${WS_DIR}/etc/scripts/pipeline-env.sh"

# Resolve FULL_VERSION
if [ -z "${VERSION+x}" ]; then

    # get maven version
    MVN_VERSION=$(mvn ${MAVEN_ARGS} \
        -q \
        -f ${WS_DIR}/pom.xml \
        -Dexec.executable="echo" \
        -Dexec.args="\${project.version}" \
        --non-recursive \
        org.codehaus.mojo:exec-maven-plugin:1.3.1:exec)

    # strip qualifier
    readonly VERSION="${MVN_VERSION%-*}"
    readonly FULL_VERSION="${VERSION}"
else
    readonly FULL_VERSION="${VERSION}"
fi

export FULL_VERSION
printf "\n%s: FULL_VERSION=%s\n\n" "$(basename ${0})" "${FULL_VERSION}"

update_version(){
    # Update version
    mvn ${MAVEN_ARGS} -f ${WS_DIR}/parent/pom.xml versions:set versions:set-property \
        -DgenerateBackupPoms=false \
        -DnewVersion="${FULL_VERSION}" \
        -Dproperty=helidon.version \
        -DprocessAllModules=true

    # Hack to update helidon.version
    for pom in `egrep "<helidon.version>.*</helidon.version>" -r . --include pom.xml | cut -d ':' -f 1 | sort | uniq `
    do
        cat ${pom} | \
            sed -e s@'<helidon.version>.*</helidon.version>'@"<helidon.version>${FULL_VERSION}</helidon.version>"@g \
            > ${pom}.tmp
        mv ${pom}.tmp ${pom}
    done

    # Hack to update helidon.version in build.gradle files
    for bfile in `egrep "helidonversion = .*" -r . --include build.gradle | cut -d ':' -f 1 | sort | uniq `
    do
        cat ${bfile} | \
            sed -e s@'helidonversion = .*'@"helidonversion = \'${FULL_VERSION}\'"@g \
            > ${bfile}.tmp
        mv ${bfile}.tmp ${bfile}
    done

}

prepare_release(){
    # Do the release work in a branch
    local GIT_BRANCH="release/${FULL_VERSION}"
    git branch -D "${GIT_BRANCH}" > /dev/null 2>&1 || true
    git checkout -b "${GIT_BRANCH}"

    # Invoke update_version
    update_version

    # Update scm/tag entry in the parent pom
    sed -e s@'<tag>[^<]*</tag>'@"<tag>${FULL_VERSION}</tag>"@g \
        "${WS_DIR}/parent/pom.xml" > "${WS_DIR}/parent/pom.xml.tmp"
    mv "${WS_DIR}/parent/pom.xml.tmp" "${WS_DIR}/parent/pom.xml"

    # Git user info
    git config user.email || git config --global user.email "info@helidon.io"
    git config user.name || git config --global user.name "Helidon Robot"

    # Commit version changes
    git commit -a -m "Release ${FULL_VERSION} [ci skip]"
}

push_release_tag(){
    local GIT_REMOTE PUSH_REMOTE

    git tag -f "${FULL_VERSION}"

    PUSH_REMOTE=origin
    if [ "${GITHUB_ACTIONS:-false}" != "true" ]; then
        GIT_REMOTE=$(git config --get remote.origin.url | \
            sed -e "s,https://[^@]*@\([^/]*\)/,git@\1:," \
                -e "s,https://\([^/]*\)/,git@\1:,")
        git remote remove release > /dev/null 2>&1 || true
        git remote add release "${GIT_REMOTE}"
        PUSH_REMOTE=release
    fi

    git push --force "${PUSH_REMOTE}" \
        refs/tags/"${FULL_VERSION}":refs/tags/"${FULL_VERSION}"
}

create_tag(){
    prepare_release
    push_release_tag

    printf "version=%s\ntag=refs/tags/%s\n" "${FULL_VERSION}" "${FULL_VERSION}" >&6
}

release_build(){
    prepare_release

    # Perform deployment
    mvn ${MAVEN_ARGS} clean deploy \
       -Prelease,no-snapshots \
       -DskipTests  \
       -DaltDeploymentRepository=":::file://${PWD}/staging"

    push_release_tag

    "${WS_DIR}/etc/scripts/upload.sh" upload_release \
                --dir="staging" \
                --description="Helidon v%{FULL_VERSION}"
}

# Invoke command
${COMMAND}
