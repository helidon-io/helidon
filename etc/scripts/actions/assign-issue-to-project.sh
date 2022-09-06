#!/bin/bash -l
#
# Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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

# Assign an issue to a column on a project board
#
# usage: assign-issue-to-board <repository-full-name> <issue-number> <project-name> <column-name>
#
# assign-issue-to-board oracle/helidon 1234 Backlog Triage
#
# GITHUB_API_KEY must be set to an API key
#
#

set -e  # Immediately exit if any command has a non-zero exit status
set -u  # Immediately exit if an uninitialized variable is referenced

readonly REPOSITORY_FULL_NAME="$1"
readonly ISSUE_NUMBER="$2"
readonly PROJECT_NAME="$3"
readonly COLUMN_NAME="$4"

if [ -z "${REPOSITORY_FULL_NAME}" -o -z "${ISSUE_NUMBER}" -o -z "${PROJECT_NAME}" -o -z "${COLUMN_NAME}" ]; then
    echo "usage: $0 <owner-name> <repository-name> <issue-number> <project-name> <column-name>"
    exit 1
fi

#echo "DEBUG: $1 $2 $3 $4"

readonly OWNER_NAME=$(echo ${REPOSITORY_FULL_NAME} | cut -d/ -f1)
readonly REPOSITORY_NAME=$(echo ${REPOSITORY_FULL_NAME} | cut -d/ -f2)

readonly GITHUB_API="https://api.github.com"

# Verify issue number is valid
readonly GET_ISSUE_URL="${GITHUB_API}/repos/${REPOSITORY_FULL_NAME}/issues/${ISSUE_NUMBER}"
HTTP_CODE=$(curl -o /dev/null -X GET -u "${OWNER_NAME}:${GITHUB_API_KEY}" --retry 3 \
            -s -w "%{http_code}" \
            -H 'Accept: application/vnd.github.inertia-preview+json' \
            "$GET_ISSUE_URL")

if [ "${HTTP_CODE}" == "404" ]; then
    echo "Could not find issue number ${ISSUE_NUMBER} in ${REPOSITORY_FULL_NAME}"
    exit 1
fi

# Get issue's ID
readonly ISSUE=$(curl -s -X GET -u "${OWNER_NAME}:${GITHUB_API_KEY}" --retry 3 \
            -H 'Accept: application/vnd.github.inertia-preview+json' \
            "$GET_ISSUE_URL")
readonly ISSUEID=$(echo "$ISSUE" | jq -r ".id")

# Get list of all projects. Assume there are less than 100!
readonly LIST_PROJECTS_URL="${GITHUB_API}/repos/${REPOSITORY_FULL_NAME}/projects?per_page=100"
readonly PROJECTS=$(curl -s -X GET -u "${OWNER_NAME}:${GITHUB_API_KEY}" --retry 3 \
            -H 'Accept: application/vnd.github.inertia-preview+json' \
            "$LIST_PROJECTS_URL")

if [ -z "${PROJECTS}" ]; then
    echo "Found no projects in ${REPOSITORY_FULL_NAME}"
    exit 1
fi

# Extract projectid for the given project name
readonly PROJECTID=$(echo "$PROJECTS" | jq -r ".[] | select(.name == \"$PROJECT_NAME\").id")
if [ -z "${PROJECTID}" ]; then
    echo "Could not find project ${PROJECT_NAME} in ${REPOSITORY_FULL_NAME}" 
    exit 1
fi

# Get list of columns on the project
readonly LIST_COLUMNS_URL="${GITHUB_API}/projects/${PROJECTID}/columns"
readonly COLUMNS=$(curl -s -X GET -u "${OWNER_NAME}:${GITHUB_API_KEY}" --retry 3 \
            -H 'Accept: application/vnd.github.inertia-preview+json' \
            "$LIST_COLUMNS_URL")


# Extract columnid for the given column
readonly COLUMNID=$(echo "$COLUMNS" | jq -r ".[] | select(.name == \"$COLUMN_NAME\").id")
if [ -z "${COLUMNID}" ]; then
    echo "Could not find column named ${COLUMN_NAME} in project ${PROJECT_NAME} in ${REPOSITORY_FULL_NAME}"
    exit 1
fi

echo "Assigning issue ${ISSUE_NUMBER}:${ISSUEID} to column ${COLUMN_NAME}:${COLUMNID} in project ${PROJECT_NAME}:${PROJECTID} in ${REPOSITORY_FULL_NAME}"


# Add issue to project board column
HTTP_CODE=$(curl -s -X POST -u "${OWNER_NAME}:${GITHUB_API_KEY}" --retry 3 \
     -o /dev/null \
     -w "%{http_code}" \
     -H 'Accept: application/vnd.github.inertia-preview+json' \
     -d "{\"content_type\": \"Issue\", \"content_id\": $ISSUEID}" \
     "${GITHUB_API}/projects/columns/$COLUMNID/cards")

if [ "${HTTP_CODE}" == "422" ]; then
    echo "Issue ${ISSUE_NUMBER}:${ISSUEID} already exists in ${COLUMN_NAME}:${COLUMNID} in project ${PROJECT_NAME}:${PROJECTID} in ${REPOSITORY_FULL_NAME}"
    exit 0
fi

if [ "${HTTP_CODE}" == "200" -o "${HTTP_CODE}" == 201 -o "${HTTP_CODE}" == 204 ]; then
    exit 0
fi

echo "Error adding ${ISSUE_NUMBER}:${ISSUEID} to ${COLUMN_NAME}:${COLUMNID} in project ${PROJECT_NAME}:${PROJECTID} in ${REPOSITORY_FULL_NAME}: ${HTTP_CODE}"
exit ${HTTP_CODE}

