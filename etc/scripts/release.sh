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
    "${CODE}" "${BASH_SOURCE[0]}" "${LINENO}" "${BASH_COMMAND}" >&2
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

$(basename "${0}") --version=V CMD

  --version=V
        The version to use.

  --help
        Prints the usage and exits.

  CMD:

    update_version
        Update the version in the workspace

    get_version
        Get the current version

    create_tag
        Create and and push a release tag
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
  "update_version"|"create_tag"|"get_version")
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

if [ -z "${COMMAND+x}" ] ; then
  echo "ERROR: no command provided"
  exit 1
fi

case ${COMMAND} in
"update_version")
  if [ -z "${VERSION}" ] ; then
    echo "ERROR: version required" >&2
    usage
    exit 1
  fi
  ;;
"create_tag"|"get_version")
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

current_version() {
  awk 'BEGIN {FS="[<>]"} ; /<version>/ {print $3; exit 0}' "${WS_DIR}"/pom.xml
}

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
  local version current_version is_release

  version=${1-${VERSION}}
  if [ -z "${version+x}" ] ; then
    echo "ERROR: version required" >&2
    usage
    exit 1
  fi

  if [[ "${version}" == *-SNAPSHOT ]]; then
    is_release="false"
  else
    is_release="true"
  fi

  # find current version
  current_version=$(current_version)

  # update poms
  replace \
    --pattern="<version>${current_version}</version>" \
    --replace="<version>${version}</version>" \
    --include="pom.xml"

  replace \
    --pattern="<helidon.version>.*</helidon.version>" \
    --value="${version}" \
    --include="pom.xml"

  # update docs
  replace \
    --pattern=":helidon-version: .*" \
    --value="${version}" \
    --include="attributes.adoc"

  replace \
    --pattern=":helidon-version-is-release: .*" \
    --value="${is_release}" \
    --include="attributes.adoc"
}

create_tag() {
  local git_branch version current_version

  current_version=$(current_version)
  version=${current_version%-SNAPSHOT}
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

  echo "version=${version}" >&6
  echo "tag=refs/tags/${version}" >&6
}

get_version() {
  echo "version=$(current_version)" >&6
}

# Invoke command
${COMMAND}
