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

DESCRIPTION: Upload staged artifacts to the Central Portal

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

    upload_release
        Upload staging directory to a release repository

    upload_snapshot
        Uploading staging directory to a snapshots repository
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
  "upload_release"|"upload_snapshot")
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
"upload_release")
  if [ -z "${DESCRIPTION}" ] ; then
    echo "ERROR: description required" >&2
    usage
    exit 1
  fi
  ;;
"upload_snapshot")
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

if [ -z "${CENTRAL_USER}" ] ; then
  echo "ERROR: CENTRAL_USER environment is not set" >&2
  usage
  exit 1
fi
if [ -z "${CENTRAL_PASSWORD}" ] ; then
  echo "ERROR: CENTRAL_PASSWORD environment is not set" >&2
  usage
  exit 1
fi

if [ ! -d "${STAGING_DIR}" ] ; then
  echo "ERROR: Invalid staging directory: ${STAGING_DIR}" >&2
  exit 1
fi

# Central Portal URL for releases
#readonly CENTRAL_URL="http://localhost:8080/api/v1/"
readonly CENTRAL_URL="https://central.sonatype.com/api/v1"
# Central SNAPSHOT URL
readonly SNAPSHOT_URL="https://central.sonatype.com/repository/maven-snapshots"

BEARER=$(printf "%s:%s" "${CENTRAL_USER}" "${CENTRAL_PASSWORD}" | base64)

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

upload_snapshot() {
  echo "Uploading SNAPSHOT..." >&2
  local version
  version=$(find_version)

  # Make sure version ends in -SNAPSHOT
  if [[ "${version}" != *-SNAPSHOT ]]; then
    echo "ERROR: Version ${version} is NOT a SNAPSHOT version" >&2
    exit 1
  fi

  nexus_upload "${SNAPSHOT_URL}" "${STAGING_DIR}"
}

upload_release() {
  echo "Uploading release..." >&2
  local version
  version=$(find_version)

  # Make sure version does NOT end in -SNAPSHOT
  if [[ "${v}" = *-SNAPSHOT ]]; then
    echo "ERROR: Version ${version} is a SNAPSHOT version" >&2
    exit 1
  fi

  deployment_id="$(central_upload "${CENTRAL_URL}" "${STAGING_DIR}")"
  central_finish "${deployment_id}"
}

# Upload contents of the staging directory to central portal
# arg1: base URL of upload portal
# arg2: staging directory
# prints deployment ID
central_upload() {
  local version
  version=$(find_version)

  printf "Uploading artifacts...\n" >&2
  readonly UPLOAD_BUNDLE=io-helidon-artifacts-${version}.zip
  rm -f "${UPLOAD_BUNDLE}"
  printf "Creating artifact bundle %s...\n" "${UPLOAD_BUNDLE}" >&2
  (cd "${2}"; zip -ryq "../${UPLOAD_BUNDLE}" .)

  local responseFile statusFile
  responseFile=$(mktemp)
  statusFile=$(mktemp)

  printf "Uploading %s to %s...\n" "${UPLOAD_BUNDLE}" "${1}" >&2
  # Upload bundle in one shot
  # publishingType of USER_MANAGED acts like "staging". Artifacts are uploaded and verified but not published.
  curl --request POST \
    --retry 2 \
    --header "Authorization: Bearer ${BEARER}" \
    --write-out "%{stderr}%{http_code} %{url_effective}\n%{stdout}%{http_code}" \
    --form bundle=@"${UPLOAD_BUNDLE}" \
    -o  "${responseFile}" \
    "${1}/publisher/upload?name=io-helidon-${version}&publishingType=USER_MANAGED" > "${statusFile}"

  # handle errors
  if [ "$(cat "${statusFile}")" != "201" ] ; then
    printf "[ERROR] %s\n" "$(cat "${responseFile}")" >&2
    exit 1
  fi

  # If success then output deployment ID
  cat "${responseFile}"
}

# Poll deployment status until operation is complete.
# arg1: deployment ID
central_finish() {
  printf "\n\nVerifying upload status of %s...\n\n" "$1" >&2

  while true; do
    local deploymentState
    deploymentState=$(central_get_deployment_state "$1")
    printf "%s...\n" "${deploymentState}" >&2
    case ${deploymentState} in
    "PENDING")
      ;;
    "VALIDATING")
      ;;
    "VALIDATED")
      printf "Done. Bits are uploaded." >&2
      exit
      ;;
    "PUBLISHING")
      ;;
    "PUBLISHED")
      printf "!!!! Oh No! Artifacts have been published!!!! That should not have happened." >&2
      exit
      ;;
    "FAILED")
      exit
      ;;
    esac
    sleep 10
  done
}

# Gets the status of a deployment from central portal
# arg1: deployment ID
# Prints deployment state for the given ID:
# PENDING, VALIDATING, VALIDATED, PUBLISHING, PUBLISHED, FAILED
# Should never be PUBLISHING or PUBLISHED since our publishingType is USER_MANAGED
central_get_deployment_state() {
  curl --request POST \
    -s \
    --retry 3 \
    --header "Authorization: Bearer ${BEARER}" \
    "${CENTRAL_URL}/publisher/status?id=${1}" \
    | jq -r ".deploymentState"
}

# Upload to a nexus repository. This is used to support SNAPSHOT releases
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
    --user "${CENTRAL_USER}:${CENTRAL_PASSWORD}" \
    --write-out "%{stderr}%{http_code} %{url_effective} t_pretrans=%{time_pretransfer}s t_tot=%{time_total}s %{speed_upload}B/s\n" \
    --config "${tmpfile}" \
    --parallel \
    --parallel-max 10 \
    --retry 3
}

${COMMAND}
