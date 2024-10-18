#!/bin/bash
#
# Copyright (c) 2024 Oracle and/or its affiliates.
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

DESCRIPTION: Nexus utility

USAGE:

$(basename "${0}") [OPTIONS] --directory=DIR CMD

  --dir=DIR
      Set the staging directory to use.

  --description=DESCRIPTION
        Set the staging repository description to use.
        %{version} can be used to subsitute the release version.

  --help
        Prints the usage and exits.

  CMD:

    deploy_release
        Deploy /staging to a release repository

    deploy_snapshots
        Deploy /staging directory to a snapshots repository
EOF
}

# parse command line args
ARGS=( )
while (( ${#} > 0 )); do
  case ${1} in
  "--dir="*)
    STAGING_DIR=${1#*=}
    shift
    ;;
  "--description="*)
    DESCRIPTION=${1#*=}
    shift
    ;;
  "--help")
    usage
    exit 0
    ;;
  "deploy_release"|"deploy_snapshots")
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

# copy stdout as fd 6 and redirect stdout to stderr
# this allows us to use fd 6 for returning data
exec 6>&1 1>&2

case ${COMMAND} in
"deploy_release")
  if [ -z "${DESCRIPTION}" ] ; then
    echo "ERROR: description required" >&2
    usage
    exit 1
  fi
  ;;
"deploy_snapshots")
  # no-op
  ;;
"")
  echo "ERROR: no command provided" >&2
  usage
  exit 1
  ;;
*)
  echo "ERROR: unknown command ${COMMAND}" >&2
  usage
  exit 1
  ;;
esac

if [ -z "${STAGING_DIR}" ] ; then
  echo "ERROR: --staging-dir is required" >&2
  usage
  exit 1
fi

if [ -z "${NEXUS_USER}" ] ; then
  echo "ERROR: NEXUS_USER environment is not set" >&2
  usage
  exit 1
fi
if [ -z "${NEXUS_PASSWORD}" ] ; then
  echo "ERROR: NEXUS_PASSWORD environment is not set" >&2
  usage
  exit 1
fi

if [ ! -d "${STAGING_DIR}" ] ; then
  echo "ERROR: Invalid staging directory: ${STAGING_DIR}" >&2
  exit 1
fi

readonly STAGING_PROFILE="6026dab46eed94"
readonly NEXUS_URL="https://oss.sonatype.org"

nexus_start() {
  printf "\nCreating staging repository...\n" >&2

  local tmpfile statusfile
  tmpfile=$(mktemp)
  statusfile=$(mktemp)

  # create the staging repository
  curl -s \
    -u "${NEXUS_USER}:${NEXUS_PASSWORD}" \
    -w "%{stderr}%{http_code} %{url_effective}\n%{stdout}%{http_code}" \
    -H 'Content-Type: application/json' \
    -d "{\"data\": {\"description\": \"${1}\"}}" \
    -o "${tmpfile}" \
    "${NEXUS_URL}/service/local/staging/profiles/${STAGING_PROFILE}/start" > "${statusfile}"

  # handle errors
  if [ "$(cat "${statusfile}")" != "201" ] ; then
    jq -r '.errors[].msg' < "${tmpfile}" >&2
    return 1
  fi

  # return the repository id
  jq -r '.data.stagedRepositoryId' < "${tmpfile}"
}

# arg1: repo id
nexus_finish() {
  printf "\nClosing staging repository...\n" >&2

  local tmpfile statusfile
  tmpfile=$(mktemp)
  statusfile=$(mktemp)

  # close the staging repository
  curl -s \
    -u "${NEXUS_USER}:${NEXUS_PASSWORD}" \
    -w "%{stderr}%{http_code} %{url_effective}\n%{stdout}%{http_code}" \
    -H 'Content-Type: application/json' \
    -d "{\"data\": {\"stagedRepositoryId\": \"${1}\"}}" \
    -o "${tmpfile}" \
    "${NEXUS_URL}/service/local/staging/profiles/${STAGING_PROFILE}/finish" > "${statusfile}"

  # handle errors
  if [ "$(cat "${statusfile}")" != "201" ] ; then
    jq -r '.errors[].msg' < "${tmpfile}" >&2
    return 1
  fi

  # wait for completion
  while true ; do

    # fetch repo details
    curl -s \
      -u "${NEXUS_USER}:${NEXUS_PASSWORD}" \
      -w "%{stderr}%{http_code} %{url_effective}\n%{stdout}%{http_code}" \
      -H 'Content-Type: application/json' \
      -H 'Accept: application/json' \
      -o "${tmpfile}" \
      "${NEXUS_URL}/service/local/staging/repository/${1}" > "${statusfile}"

    if [ "$(cat "${statusfile}")" != "200" ] ; then
      jq -r '.errors[].msg' < "${tmpfile}" >&2
      return 1
    elif [ "$(jq -r '.transitioning' < "${tmpfile}")" = "false" ] ; then
      break
    else
      sleep 5
    fi
  done

  # check status
  if [ "$(jq -r '.type' < "${tmpfile}")" == "open" ] ; then

    printf "\nERROR: Staging repository not closed!\n" >&2

    # Get failed rules
    curl -s \
      -u "${NEXUS_USER}:${NEXUS_PASSWORD}" \
      -w "%{stderr}%{http_code} %{url_effective}\n" \
      -H 'Content-Type: application/json' \
      -H 'Accept: application/json' \
      -o "${tmpfile}" \
      "${NEXUS_URL}/service/local/staging/repository/${1}/activity"

    # Print errors
    jq -r '.[] |
      select(.name == "close").events[] |
      select(.name == "ruleFailed").properties[] |
      select(.name == "failureMessage").value' "${tmpfile}" >&2

    return 1
  fi
}

# arg1: base URL
# arg2: staging directory
nexus_upload() {
  printf "\nUploading artifacts...\n" >&2

  local tmpfile
  tmpfile=$(mktemp)

  # Generate a curl config file for all files to deploy
  # Use -T <file> --url <url> for each file
  while read -r i ; do
      echo "-T ${2}/${i}" >> "${tmpfile}"
      echo "--url ${1}/${i}" >> "${tmpfile}"
  done < <(find "${2}" -type f | cut -c $((${#2} + 2))-)

  # Upload
  curl -s \
    --user "${NEXUS_USER}:${NEXUS_PASSWORD}" \
    --write-out "%{stderr}%{http_code} %{url_effective}\n" \
    --config "${tmpfile}" \
    --parallel \
    --parallel-max 10 \
    --retry 3
}

find_version() {
  local versions version

  # List the "version" directories
  versions=$(while read -r v ; do
   dirname "${v}" | xargs basename
  done < <(find "${STAGING_DIR}" -type f -name "*.pom" -print) | sort | uniq)

  # Enforce one version per staging directory
  for v in ${versions} ; do
    if [ -n "${version}" ] ; then
      echo "ERROR: staging directory contains more than one version: ${versions}" >&2
      return 1
    fi
    version="${v}"
  done

  if [ -z "${version}" ] ; then
    echo "ERROR: version not found" >&2
    return 1
  fi
  echo "${version}"
}

deploy_release(){
  local version repo_id
  version=$(find_version)

  # Make sure version does NOT end in -SNAPSHOT
  if [[ "${v}" = *-SNAPSHOT ]]; then
    echo "ERROR: Version ${version} is a SNAPSHOT version" >&2
    exit 1
  fi

  repo_id="$(nexus_start "${DESCRIPTION/\%{version\}/${version}}")"
  nexus_upload "${NEXUS_URL}/service/local/staging/deployByRepositoryId/${repo_id}" "${STAGING_DIR}"
  nexus_finish "${repo_id}"
}

deploy_snapshots() {
  local version
  version=$(find_version)

  # Make sure version ends in -SNAPSHOT
  if [[ "${version}" != *-SNAPSHOT ]]; then
    echo "ERROR: Version ${version} is NOT a SNAPSHOT version" >&2
    exit 1
  fi

  nexus_upload "${NEXUS_URL}/content/repositories/snapshots" "${STAGING_DIR}"
}

${COMMAND}
