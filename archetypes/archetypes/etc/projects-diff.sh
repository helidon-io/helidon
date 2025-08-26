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

DESCRIPTION: Archetype test project diff utility

USAGE:

$(basename "${0}") [OPTIONS] CMD

  OPTIONS:

  --actual=DIR
      Set the actual directory

  --orig=FILE
      Set the orig directory

  --index=INDEX
      Set the index of the project to diff

  --help
        Prints the usage and exits.

  CMD:

    diff_csv
        Diff projects.csv files

    diff_project
        Diff a single project

    diff_projects
        Diff projects
EOF
}

# parse command line args
ARGS=( )
while (( ${#} > 0 )); do
  case ${1} in
  "--actual="*)
    DIR_ACTUAL=${1#*=}
    shift
    ;;
  "--orig="*)
    DIR_ORIG=${1#*=}
    shift
    ;;
  "--index="*)
    PROJECT_INDEX=${1#*=}
    shift
    ;;
  "--help")
    usage
    exit 0
    ;;
  "diff_csv"|"diff_project"|"diff_projects")
    COMMAND="${1}"
    shift
    ;;
  "")
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

check_reqs() {
  if [ -z "${DIR_ACTUAL}" ]; then
    echo "ERROR: --actual required" >&2
    exit 1
  fi
  if [ -z "${DIR_ORIG}" ]; then
    echo "ERROR: --orig required" >&2
    exit 1
  fi
  if [ ! -e "${DIR_ACTUAL}/projects.csv" ]; then
    echo "ERROR: ${DIR_ACTUAL}/projects.csv not found" >&2
    exit 1
  fi
  if [ ! -e "${DIR_ORIG}/projects.csv" ]; then
    echo "ERROR: ${DIR_ORIG}/projects.csv not found" >&2
    exit 1
  fi
}

case ${COMMAND} in
"diff_csv"|"diff_projects")
  check_reqs
  ;;
"diff_project")
  check_reqs
  if [ -z "${PROJECT_INDEX}" ]; then
    echo "ERROR: --index required" >&2
    exit 1
  fi
  ;;
"")
  echo "ERROR: no command provided" >&2
  exit 1
  ;;
*)
  printf "ERROR: unknown command '%s'\n" "${COMMAND}" >&2
  exit 1
  ;;
esac

# arg1: csv file
sort_csv() {
  gawk '{
    for (i=1; i <= NF; i++) {
      split($i, a, "=")
      keys[i]=a[1]
      data[a[1]]=a[2]
    }
    asort(keys)
    for (i in keys) {
      printf("%s=%s ", keys[i], data[keys[i]])
    }
    printf("\n")
    delete keys
    delete data
  }' "${1}"
}

match_indexes() {
  local orig_sorted actual_sorted
  orig_sorted=$(mktemp)
  actual_sorted=$(mktemp)

  sort_csv "${DIR_ACTUAL}/projects.csv" > "${actual_sorted}"
  sort_csv "${DIR_ORIG}/projects.csv" | nl -n ln -b a -s ':' -w 1 > "${orig_sorted}"

  local actual_ln orig_ln orig_line
  actual_ln=1
  while read -r actual_line ; do
    orig_line=$(grep -E "^[0-9]+:${actual_line}\W*\$" "${orig_sorted}" || true)
    if [ -n "${orig_line}" ] ; then
      orig_ln=${orig_line%%:*}
      echo "${actual_ln}:${orig_ln}"
    fi
    actual_ln=$((actual_ln+1))
  done < "${actual_sorted}"
}

# arg1: dir
# arg2: index
project_dir() {
  local dir index
  dir="${1}"
  index="${2}"
  if [ "${index}" -eq 1 ] ; then
    echo "${dir}/myproject"
  else
    echo "${dir}/myproject-${index}"
  fi
}

# arg1: index_actual
# arg2: index_orig
diff_project0() {
  local dir_actual dir_orig basename_actual basename_orig file_actual count_actual count_orig tmp_out
  index_actual="${1}"
  index_orig="${2}"
  dir_actual="$(project_dir "${DIR_ACTUAL}" "${index_actual}")"
  dir_orig="$(project_dir "${DIR_ORIG}" "${index_orig}")"
  basename_actual="$(basename "${dir_actual}")"
  basename_orig="$(basename "${dir_orig}")"
  tmp_out=$(mktemp)

  count_actual="$(find "${dir_actual}" -type f | grep -cv "build.log")"
  count_orig="$(find "${dir_orig}" -type f | grep -cv "build.log")"
  if [ "${count_actual}" != "${count_orig}" ] ; then
    printf "File counts:\n"
    printf "%s actual: %s\n" "-" "${count_actual}"
    printf "%s orig: %s\n" "-" "${count_orig}"
    printf "\n"
  fi

  # checking modified
  while read -r file ; do
    file_actual="${dir_actual}/${file/${basename_orig}/${basename_actual}}"
    if [ -e "${file_actual}" ] ; then
      # ignore artifactId differences
      # ignore dates
      diff -u -I "myproject(-[0-9]+)?" \
        -I "[A-Z]{1}[a-z]{2} [A-Z]{1}[a-z]{2} [0-9]{1,2} [0-9]{2}:[0-9]{2}:[0-9]{2} [A-Z]{3} [0-9]{4}" \
        "${file_actual}" "${dir_orig}/${file}" || true
    fi
  done < <(find "${dir_orig}" -type f | cut -b $((${#dir_orig}+2))-) > "${tmp_out}"

  # only output if there are diffs
  if [ "$(wc -l "${tmp_out}" | awk '{print $1}')" -gt 0 ] ; then
    printf "Diffs:\n"
    printf "%sdiff\n" '```'
    cat "${tmp_out}"
    printf "%s\n" '```'
    printf "\n"
  fi

  # checking added
  while read -r file ; do
    # ignore build.log
    # normalize orig and actual paths
    if [ "${file}" != "build.log" ] && \
       [ ! -e "${dir_actual}/${file/${basename_orig}/${basename_actual}}" ] && \
       [ ! -e "${dir_actual}/${file/myproject(-[0-9]+)?//}" ]; then
      echo "${file} - ${basename_orig} - ${basename_actual}"
    fi
  done < <(find "${dir_actual}" -type f | cut -b $((${#dir_actual}+2))-) > "${tmp_out}"

  # only output if there are added files
  if [ "$(wc -l "${tmp_out}" | awk '{print $1}')" -gt 0 ] ; then
    printf "Added files:\n"
    while read -r line ; do
      printf "%s %s\n" "-" "${line}"
    done < "${tmp_out}"
    printf "\n"
  fi

  # checking removed
  while read -r file ; do
    # ignore build.log
    # normalize orig and actual paths
    if [ "${file}" != "build.log" ] && \
       [ ! -e "${dir_actual}/${file/${basename_orig}/${basename_actual}}" ] && \
       [ ! -e "${dir_actual}/${file/myproject(-[0-9]+)?//}" ]; then
      echo "${file} - ${basename_orig} - ${basename_actual}"
    fi
  done < <(find "${dir_orig}" -type f | cut -b $((${#dir_orig}+2))-) > "${tmp_out}"

  # only output if there are removed files
  if [ "$(wc -l "${tmp_out}" | awk '{print $1}')" -gt 0 ] ; then
    printf "Removed files:\n"
    while read -r line ; do
      printf "%s %s\n" "-" "${line}"
    done < "${tmp_out}"
    printf "\n"
  fi
}

# arg1: index_actual
# arg2: index_orig
# arg3: tmp_out
printf_diff() {
  local index_actual index_orig dir_actual dir_orig tmp_out variation
  index_actual="${1}"
  index_orig="${2}"
  dir_actual="$(project_dir "${DIR_ACTUAL}" "${index_actual}")"
  dir_orig="$(project_dir "${DIR_ORIG}" "${index_orig}")"
  tmp_out="${3}"

  # get exact variation as an array
  variation=()
  IFS=' ' read -r -a variation < <(sed -n "${index_actual}p" "${DIR_ACTUAL}/projects.csv")

  printf "Directories:\n"
  printf "%s actual %s\n" "-" "${dir_actual}"
  printf "%s orig: %s\n" "-" "${dir_orig}"
  printf "\n"

  printf "Command:\n"
  printf "%sshell\n" '```'
  printf "helidon init --batch %s\n" "\\"
  for ((i = 0 ; i < ${#variation[*]} ; i++ )); do
    printf "    %s%s" "-D" "${variation[${i}]}"
    if [ $((i+1)) -lt ${#variation[*]} ] ; then
      printf " %s" "\\"
    fi
    printf "\n"
  done
  printf "%s\n" '```'
  printf "\n"

  cat "${tmp_out}"
}

diff_csv() {
  local orig_sorted actual_sorted
  orig_sorted=$(mktemp)
  actual_sorted=$(mktemp)

  sort_csv "${DIR_ACTUAL}/projects.csv" > "${actual_sorted}"
  sort_csv "${DIR_ORIG}/projects.csv" > "${orig_sorted}"

  # diff added
  while read -r line ; do
    grep -E "^${line}\W*\$" "${orig_sorted}" > /dev/null 2>&1 || echo "ADDED: ${line}"
  done < "${actual_sorted}"

  # diff removed
  while read -r line ; do
    grep -E "^${line}\W*\$" "${actual_sorted}" > /dev/null 2>&1 || echo "REMOVED: ${line}"
  done < "${orig_sorted}"
}

diff_project() {
  local index_actual index_orig tmp_out

  while read -r line ; do
    index_actual="${line%%:*}"
    index_orig="${line##*:}"

    if [ "${index_actual}" -eq "${PROJECT_INDEX}" ]; then
      tmp_out=$(mktemp)
      diff_project0 "${index_actual}" "${index_orig}" > "${tmp_out}"
      break
    fi
  done < <(match_indexes)

  if [ -z "${tmp_out}" ]; then
    echo "Index not found: ${PROJECT_INDEX}" >&2
  elif [ "$(wc -l "${tmp_out}" | awk '{print $1}')" -gt 0 ]; then
    printf_diff "${index_actual}" "${index_orig}" "${tmp_out}"
  else
    echo "OK"
  fi
}

diff_projects() {
  local index_orig index_actual tmp_out count
  tmp_out=$(mktemp)
  count=0

  while read -r line ; do
    index_actual="${line%%:*}"
    index_orig="${line##*:}"

    diff_project0 "${index_actual}" "${index_orig}" > "${tmp_out}"
    if [ "$(wc -l "${tmp_out}" | awk '{print $1}')" -gt 0 ] ; then
      count=$((count+1))
      printf "## Diff %d\n" "${count}"
      printf "\n"
      printf_diff "${index_actual}" "${index_orig}" "${tmp_out}"
    fi
  done < <(match_indexes)

  if [ "${count}" -eq 0 ] ; then
    echo "OK"
  fi
}

${COMMAND}
