#!/bin/bash
#
# Copyright (c) 2018, 2024 Oracle and/or its affiliates.
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

# Path to this script
if [ -h "${0}" ] ; then
    SCRIPT_PATH="$(readlink "${0}")"
else
    # shellcheck disable=SC155
    SCRIPT_PATH="${0}"
fi
readonly SCRIPT_PATH

# Path to the root of the workspace
# shellcheck disable=SC2046
WS_DIR=$(cd $(dirname -- "${SCRIPT_PATH}") ; cd ../.. ; pwd -P)
readonly WS_DIR

usage(){
    cat <<EOF

DESCRIPTION: Helidon Release Script

USAGE:

$(basename "${0}") [ --build-number=N ] CMD

  --version=V
        Override the version to use.
        This trumps --build-number=N

  --help
        Prints the usage and exits.

  CMD:

    update_version
        Update the version in the workspace

    release_version
        Print the release version

    create_tag
        Create and and push a release tag

    release_build
        Perform a release build

    deploy_snapshot
        Perform a snapshot build and deploy to snapshot repository
EOF
}

# parse command line args
ARGS=( )
while (( ${#} > 0 )); do
    case ${1} in
    "--version="*)
        VERSION=${1#*=}
        shift
        ;;
    "--help")
        usage
        exit 0
        ;;
    "update_version"|"release_version"|"create_tag"|"release_build"|"deploy_snapshot")
        COMMAND="${1}"
        shift
        ;;
    *)
        ARGS+=( "${1}" )
        shift
        ;;
    esac
done
readonly ARGS
readonly COMMAND

if [ -z "${COMMAND+x}" ] ; then
  echo "ERROR: no command provided"
  exit 1
fi

# copy stdout as fd 6 and redirect stdout to stderr
# this allows us to use fd 6 for returning data
exec 6>&1 1>&2

current_version() {
    # shellcheck disable=SC2086
    mvn ${MAVEN_ARGS} -q \
        -f "${WS_DIR}"/file.xml \
        -Dexec.executable="echo" \
        -Dexec.args="\${project.version}" \
        --non-recursive \
        org.codehaus.mojo:exec-maven-plugin:1.3.1:exec
}

release_version() {
    local current_version
    current_version=$(current_version)
    echo "${current_version%-*}"
}

replace() {
    local pattern value include
    pattern="${1}"
    value="${2}"
    include="${3}"
    for file in $(grep "${pattern}" -Er . --include "${include}" | cut -d ':' -f 1 | sort | uniq); do
       echo "Updating ${file}"
       sed -e s@"${pattern}"@"${pattern/\.\*/${value}}"@g \
           < "${file}" \
           > "${file}.tmp"
       mv "${file}.tmp" "${file}"
    done
}

update_version(){
    local version
    version=${1-${VERSION}}
    if [ -z "${version+x}" ] ; then
        echo "ERROR: version required"
        usage
        exit 1
    fi

    # shellcheck disable=SC2086
    mvn ${MAVEN_ARGS} "${ARGS[@]}" \
        -f ${WS_DIR}/parent/file.xml versions:set versions:set-property \
        -DgenerateBackupPoms="false" \
        -DnewVersion="${version}" \
        -Dproperty="helidon.version" \
        -DprocessFromLocalAggregationRoot="false" \
        -DupdateMatchingVersions="false"

    # Hack to update helidon.version
    replace "<helidon.version>.*</helidon.version>" "${version}" "pom.xml"

    # Hack to update helidon.version in build.gradle files
    replace "helidonversion = .*" "${version}" "build.gradle"

    # Hack to update helidon-version in doc files
    replace ":helidon-version: .*" "${version}" "attributes.adoc"

    # Hack to update helidon-version-is-release in doc files
    # We are a released version if we are not a SNAPSHOT version
    local is_released
    if [[ ${1} == *-SNAPSHOT ]]; then
        is_released="false"
    else
        is_released="true"
    fi
    replace ":helidon-version-is-release: .*" "${is_released}" "attributes.adoc"
}

create_tag() {
    local git_branch version

    version=$(release_version)
    git_branch="release/${version}"

    # Use a separate branch
    git branch -D "${git_branch}" > /dev/null 2>&1 || true
    git checkout -b "${git_branch}"

    # Invoke update_version
    update_version "${version}"

    # Git user info
    git config user.email || git config --global user.email "info@helidon.io"
    git config user.name || git config --global user.name "Helidon Robot"

    # Commit version changes
    git commit -a -m "Release ${version}"

    # Create and push a git tag
    git tag -f "${version}"
    git push --force origin refs/tags/"${version}":refs/tags/"${version}"

    echo "tag=refs/tags/${version}" >&6
}

credentials() {
    local tmpfile

    # Bootstrap credentials from environment
    if [ -n "${MAVEN_SETTINGS}" ] ; then
        tmpfile=$(mktemp XXXXXXsettings.xml)
        echo "${MAVEN_SETTINGS}" > "${tmpfile}"
        MAVEN_ARGS="${MAVEN_ARGS} -s ${tmpfile}"
    fi
    if [ -n "${GPG_PRIVATE_KEY}" ] ; then
        tmpfile=$(mktemp XXXXXX.key)
        echo "${GPG_PRIVATE_KEY}" > "${tmpfile}"
        gpg --allow-secret-key-import --import --no-tty --batch "${tmpfile}"
        rm "${tmpfile}"
    fi
    if [ -n "${GPG_PASSPHRASE}" ] ; then
        echo "allow-preset-passphrase" >> ~/.gnupg/gpg-agent.conf
        gpg-connect-agent reloadagent /bye
        GPG_KEYGRIP=$(gpg --with-keygrip -K | grep "Keygrip" | head -1 | awk '{print $3}')
        /usr/lib/gnupg/gpg-preset-passphrase --preset "${GPG_KEYGRIP}" <<< "${GPG_PASSPHRASE}"
    fi
}

release_build(){
    local tmpfile version

    credentials

    # Perform local deployment
    # shellcheck disable=SC2086
    mvn ${MAVEN_ARGS} "${ARGS[@]}" \
        deploy \
        -Prelease \
        -DskipTests \
        -DskipRemoteStaging=true

    # Upload all artifacts to nexus
    version=$(release_version)
    # shellcheck disable=SC2086
    mvn ${MAVEN_ARGS} -N nexus-staging:deploy-staged \
        -DstagingDescription="Helidon v${version}"
}

readonly NEXUS_SNAPSHOT_URL="https://oss.sonatype.org/content/repositories/snapshots/"

deploy_snapshot() {
    local version
    version=$(current_version)

    # Make sure version ends in -SNAPSHOT
    if [[ ${version} != *-SNAPSHOT ]]; then
        echo "Helidon version ${version} is not a SNAPSHOT version. Failing snapshot release."
        exit 1
    fi

    echo "Deploying snapshot build ${version} to ${NEXUS_SNAPSHOT_URL}"

    credentials

    # The nexus-staging-maven-plugin had issues deploying the module
    # helidon-applications because the distributionManagement section is empty.
    # So we deploy using the apache maven-deploy-plugin and altDeploymentRepository
    # property. The deployAtEnd option requires version 3.0.0 of maven-deploy-plugin
    # or newer to work correctly on multi-module systems
    set -x
    mvn "${MAVEN_ARGS}" -e clean deploy \
      -Parchetypes \
      -DskipTests \
      -DaltDeploymentRepository="ossrh::${NEXUS_SNAPSHOT_URL}" \
      -DdeployAtEnd=true \
      -DretryFailedDeploymentCount="10"

    echo "Done. ${MVN_VERSION} deployed to ${NEXUS_SNAPSHOT_URL}"
}

# Invoke command
${COMMAND}
