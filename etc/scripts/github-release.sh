#!/bin/bash
#
# Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

# Path to this script
[ -h "${0}" ] && readonly SCRIPT_PATH="$(readlink "${0}")" || readonly SCRIPT_PATH="${0}"

# Load error handling functions and define error handling
. $(dirname -- "${SCRIPT_PATH}")/includes/error_handlers.sh

# Setup error handling using default settings (defined in includes/error_handlers.sh)
error_trap_setup

usage(){
  cat <<EOF

DESCRIPTION: Create a Github Release

USAGE:

$(basename ${0}) --changelog=PATH --version=x.y.z

  --changelog=PATH
        Path to the changelog file.

  --version=x.y.z
        The version to create the Github release for.

  --help
        Prints the usage and exits.

ENVIRONMENT:

  GITHUB_API_KEY:
        Github API key used to authenticate

EOF
}

# parse command line args
ARGS=( "${@}" )
for ((i=0;i<${#ARGS[@]};i++))
{
  ARG=${ARGS[${i}]}
  case ${ARG} in
  "--changelog="*)
    readonly CHANGELOG=${ARG#*=}
    ;;
  "--version="*)
    readonly VERSION=${ARG#*=}
    ;;
  "--help")
    usage
    exit 0
    ;;
  *)
    if [ -z "${CHANGELOG}" ] || [ ! -f "${CHANGELOG}" ]; then
      echo "ERROR: invalid changelog: ${CHANGELOG}."
      exit 1
    elif [ -z "${version}" ]; then
      echo "ERROR: no version provided"
      exit 1
    fi
    ;;
  esac
}

if [ -z "${GITHUB_API_KEY}" ] ; then
    echo "ERROR: GITHUB_API_KEY is not set"
    exit 1
fi

readonly GITHUB_API_URL="https://api.github.com"
readonly MEDIA_TYPE="application/vnd.github.v3+json"
readonly GITHUB_OWNER="helidon-io"
readonly REPO="helidon"

BODY=$(cat ${CHANGELOG} | awk '{printf "%s\\n", $0;}' | sed -e 's/"/\\"/g')
PAYLOAD="{
  \"tag_name\": \"${VERSION}\",
  \"name\": \"${VERSION}\",
  \"body\": \"${BODY}\",
  \"draft\": false,
  \"prerelease\": false
}"

curl \
  -vvv \
  -w "%{http_code}" \
  -u "${GITHUB_OWNER}:${GITHUB_API_KEY}" \
  -H "Accept: ${MEDIA_TYPE}" \
  -X POST \
  -d "${PAYLOAD}" \
  ${GITHUB_API_URL}/repos/${GITHUB_OWNER}/${REPO}/releases

