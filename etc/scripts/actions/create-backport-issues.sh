#!/bin/bash -l
#
# Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

# Create backport for issue and assign them the same priority, same assignee, same labels
# If original issue does not have a version label, add it
#
# usage: issue-backport.sh <repository-full-name> <issue-number> <helidon-version>
#
# this will create an issue for other Helidon versions (currently supports 2.x, 3.x & 4.x)
#
# GITHUB_API_KEY must be set to an API key
#

set -e # Immediately exit if any command has a non-zero exit status
set -u # Immediately exit if an uninitialized variable is referenced

function join_by {
  local IFS="$1"
  shift
  echo "$*"
}

readonly REPOSITORY_FULL_NAME="$1"
readonly ISSUE_NUMBER="$2"
readonly HELIDON_VERSION="$3"

if [ -z "${REPOSITORY_FULL_NAME}" -o -z "${ISSUE_NUMBER}" -o -z "${HELIDON_VERSION}" -o $# -le 3 ]; then
  echo "usage: $0 <repository-name> <issue-number> <helidon-version> <helidon-versions-to-port-to...>"
  exit 1
fi

readonly OWNER_NAME=$(echo ${REPOSITORY_FULL_NAME} | cut -d/ -f1)
readonly REPOSITORY_NAME=$(echo ${REPOSITORY_FULL_NAME} | cut -d/ -f2)

readonly GITHUB_API="https://api.github.com"

# Verify issue number is valid
readonly GET_ISSUE_URL="${GITHUB_API}/repos/${REPOSITORY_FULL_NAME}/issues/${ISSUE_NUMBER}"
HTTP_CODE=$(curl -o /dev/null -X GET \
  -H "Authorization: Bearer ${GITHUB_API_KEY}" \
  --retry 3 \
  -s -w "%{http_code}" \
  -H 'Accept: application/vnd.github.inertia-preview+json' \
  "$GET_ISSUE_URL")

if [ "${HTTP_CODE}" == "404" ]; then
  echo "Could not find issue number ${ISSUE_NUMBER} in ${REPOSITORY_FULL_NAME}"
  exit 1
fi

# Get issue information
readonly ISSUE=$(curl -s -X GET \
  -H "Authorization: Bearer ${GITHUB_API_KEY}" \
  --retry 3 \
  -H 'Accept: application/vnd.github.inertia-preview+json' \
  "$GET_ISSUE_URL")

# Get issue information
issue_title=$(echo "$ISSUE" | jq -r ".title")
readonly ISSUE_ASSIGNEE=$(echo "$ISSUE" | jq -r ".assignee.login")
readonly ISSUE_LABELS=$(echo "$ISSUE" | jq -r ".labels") # JSON Array

# Create an issue for each version that is not used
readonly VERSIONS=("2.x" "3.x" "4.x")

############################################################
# If original issue does not have a version label, add it
############################################################
version_labels=()
for row in $(echo "${ISSUE_LABELS}" | jq -r '.[] | @base64'); do
  label=$(echo ${row} | base64 --decode)
  label_text=$(echo $label | jq -r ".name")

  if [[ " ${VERSIONS[*]} " =~ " ${label_text} " ]]; then
    version_labels+=("\"${label_text}\"")
  fi
done

if [ ${#version_labels[@]} -eq 0 ]; then
  HTTP_CODE=$(curl \
    -o /dev/null \
    -w "%{http_code}" \
    -s \
    -X POST \
    --retry 3 \
    -H "Authorization: Bearer ${GITHUB_API_KEY}" \
    -H "Accept: application/vnd.github+json" \
    https://api.github.com/repos/${OWNER_NAME}/${REPOSITORY_NAME}/issues/${ISSUE_NUMBER}/labels \
    -d "{\"labels\":[\"${HELIDON_VERSION}\"]}")

  if [ "${HTTP_CODE}" != "200" ]; then
    echo "Failed to add ${HELIDON_VERSION} label to issue ${ISSUE_NUMBER}"
    exit 1
  fi
fi

# Replace all instances of " with ' in the Issue Title to avoid JSON parsing issue
issue_title=$(sed "s/\"/'/g" <<< "$issue_title")

############################################################
# For each version the caller specified add a porting issue.
############################################################
version_targets=()
next_version_to_check=2
for is_version_selected in "${@:4}"; do
  version=${next_version_to_check}.x
  next_version_to_check=$((next_version_to_check+1))
  if [ "$version" != "$HELIDON_VERSION" -a "$is_version_selected" = "true" ]; then
    # Create issue for other indicated versions and add the same labels and assignee
    new_issue_title="[$version] ${issue_title}"
    new_issue_text="Backport of #${ISSUE_NUMBER} for Helidon ${version}"

    # by default, add label for the version we are backporting into, and for backport itself
    labels_to_add=("\"$version\"" "\"backport\"")

    # then add all labels from the issue that are not version labels
    for row in $(echo "${ISSUE_LABELS}" | jq -r '.[] | @base64'); do
      label=$(echo ${row} | base64 --decode)
      label_text=$(echo $label | jq -r ".name")

      if [[ ! " ${VERSIONS[*]} " =~ " ${label_text} " ]]; then
        labels_to_add+=("\"${label_text}\"")
      fi
    done

    # prepare the labels text
    labels_text=$(join_by , "${labels_to_add[@]}")

    # create request JSON (if original issue does not have an assignee, do not add it to new issue)
    if [ "${ISSUE_ASSIGNEE}" == "null" ]; then
      new_issue_json="{\"title\":\"$new_issue_title\",\"body\":\"$new_issue_text\",\"labels\":[$labels_text]}"
    else
      new_issue_json="{\"title\":\"$new_issue_title\",\"body\":\"$new_issue_text\",\"assignees\":[\"$ISSUE_ASSIGNEE\"],\"labels\":[$labels_text]}"
    fi

    # create the issue using Github API
    new_issue=$(curl \
      -s \
      -X POST \
      --retry 3 \
      -H "Authorization: Bearer ${GITHUB_API_KEY}" \
      -H "Accept: application/vnd.github+json" \
      https://api.github.com/repos/${OWNER_NAME}/${REPOSITORY_NAME}/issues \
      -d "$new_issue_json")

    new_issue_number=$(echo $new_issue | jq -r ".number")
    new_issue_url=$(echo $new_issue | jq -r ".html_url")

    # Print out the Github API Server response if unable to parse the issue number. Also display the
    # json payload that was sent so it can be inspected for problems if such issue occur.
    if [ "${new_issue_number}" == "null" ]; then
      echo "Encountered an error while attempting to create an issue: $new_issue"
      echo "Json payload: $new_issue_json"
    else
      echo "Created issue for version ${version}, issue number: ${new_issue_number}, url: ${new_issue_url}"
    fi
  fi
done
