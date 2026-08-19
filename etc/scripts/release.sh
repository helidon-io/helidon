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
        The version to use with update_version.

  --help
        Prints the usage and exits.

  CMD:

    update_version
        Update the version in the workspace

    get_version
        Get the current version

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
    "update_version"|"get_version"|"release_build"|"create_tag")
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

if [ "${COMMAND}" = "update_version" ] && [ -z "${VERSION}" ] ; then
    echo "ERROR: version required" >&2
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

# Get the current project version from the root pom.
current_version() {
    awk 'BEGIN {FS="[<>]"} ; /<version>/ {print $3; exit 0}' "${WS_DIR}/pom.xml"
}

# Find matching Git-tracked files.
# arg1: pattern
# arg2: include pattern
search() {
    set +o pipefail
    grep "${1}" -Er . --include "${2}" | cut -d ':' -f 1 | xargs git ls-files | sort | uniq
}

replace() {
    local pattern value replace include
    while (( ${#} > 0 )); do
        case ${1} in
        "--pattern="*)
            pattern=${1#*=}
            shift
            ;;
        "--include="*)
            include=${1#*=}
            shift
            ;;
        "--replace="*)
            replace=${1#*=}
            shift
            ;;
        "--value="*)
            value=${1#*=}
            shift
            ;;
        *)
            echo "Unsupported argument: ${1}" >&2
            return 1
            ;;
        esac
    done

    if [ -z "${replace}" ] && [ -n "${value}" ] ; then
        replace=${pattern/\.\*/${value}}
    fi

    for file in $(search "${pattern}" "${include}"); do
        echo "Updating ${file}"
        sed -e s@"${pattern}"@"${replace}"@g "${file}" > "${file}.tmp"
        mv "${file}.tmp" "${file}"
    done
}

update_version(){
    local version project_version

    if [ "${#}" -gt 0 ]; then
        version=${1}
    else
        version=${VERSION}
    fi
    if [ -z "${version}" ] ; then
        echo "ERROR: version required" >&2
        usage >&2
        exit 1
    fi

    project_version=$(current_version)

    replace \
        --pattern="<version>${project_version}</version>" \
        --replace="<version>${version}</version>" \
        --include="pom.xml"

    replace \
        --pattern="<helidon.version>.*</helidon.version>" \
        --value="${version}" \
        --include="pom.xml"

    replace \
        --pattern="helidonversion = .*" \
        --replace="helidonversion = '${version}'" \
        --include="build.gradle"
}

prepare_release(){
    readonly FULL_VERSION="${1}"
    export FULL_VERSION

    printf "\n%s: FULL_VERSION=%s\n\n" "$(basename "${0}")" "${FULL_VERSION}"

    # Do the release work in a branch
    local GIT_BRANCH="release/${FULL_VERSION}"
    git branch -D "${GIT_BRANCH}" > /dev/null 2>&1 || true
    git checkout -b "${GIT_BRANCH}"

    # Invoke update_version
    update_version "${FULL_VERSION}"

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
    local version
    version=$(current_version)
    version=${version%-SNAPSHOT}

    prepare_release "${version}"
    push_release_tag

    printf "version=%s\ntag=refs/tags/%s\n" "${FULL_VERSION}" "${FULL_VERSION}" >&6
}

release_build(){
    local version
    version=$(current_version)
    version=${version%-SNAPSHOT}

    prepare_release "${version}"

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

get_version(){
    printf "version=%s\n" "$(current_version)" >&6
}

# Invoke command
${COMMAND}
