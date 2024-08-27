#!/bin/bash -l
#
# Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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

readonly REPOSITORY_FULL_NAME="${1}"
readonly ISSUE_NUMBER="${2}"
readonly PROJECT_NAME="${3}"
readonly COLUMN_NAME="${4}"

if [ -z "${REPOSITORY_FULL_NAME}" ] || [ -z "${ISSUE_NUMBER}" ] || [ -z "${PROJECT_NAME}" ] || [ -z "${COLUMN_NAME}" ]; then
    echo "usage: ${0} <owner-name> <repository-name> <issue-number> <project-name> <column-name>"
    exit 1
fi

#echo "DEBUG: $1 $2 $3 $4"

OWNER_NAME=$(echo "${REPOSITORY_FULL_NAME}" | cut -d/ -f1)
GITHUB_API="https://api.github.com"
GET_ISSUE_URL="${GITHUB_API}/repos/${REPOSITORY_FULL_NAME}/issues/${ISSUE_NUMBER}"
readonly OWNER_NAME GITHUB_API GET_ISSUE_URL

# Verify issue number is valid
HTTP_CODE=$(curl -o /dev/null -X GET -u "${OWNER_NAME}:${GITHUB_API_KEY}" --retry 3 \
            -s -w "%{http_code}" \
            -H 'Accept: application/vnd.github.inertia-preview+json' \
            "${GET_ISSUE_URL}")

if [ "${HTTP_CODE}" == "404" ]; then
    echo "Could not find issue number ${ISSUE_NUMBER} in ${REPOSITORY_FULL_NAME}"
    exit 1
fi

# Get issue's ID
ISSUE=$(curl -s -X GET -u "${OWNER_NAME}:${GITHUB_API_KEY}" --retry 3 \
            -H 'Accept: application/vnd.github.inertia-preview+json' \
            "${GET_ISSUE_URL}")
ISSUE_ID=$(echo "${ISSUE}" | jq -r ".id")
readonly ISSUE ISSUE_ID

# Get list of all projects. Assume there are less than 100!
LIST_PROJECTS_URL="${GITHUB_API}/repos/${REPOSITORY_FULL_NAME}/projects?per_page=100"
PROJECTS=$(curl -s -X GET -u "${OWNER_NAME}:${GITHUB_API_KEY}" --retry 3 \
            -H 'Accept: application/vnd.github.inertia-preview+json' \
            "${LIST_PROJECTS_URL}")
readonly  LIST_PROJECTS_URL PROJECTS

if [ -z "${PROJECTS}" ]; then
    echo "Found no projects in ${REPOSITORY_FULL_NAME}"
    exit 1
fi

# Extract projectid for the given project name
PROJECT_ID=$(echo "${PROJECTS}" | jq -r ".[] | select(.name == \"${PROJECT_NAME}\").id")
readonly  PROJECT_ID
if [ -z "${PROJECT_ID}" ]; then
    echo "Could not find project ${PROJECT_NAME} in ${REPOSITORY_FULL_NAME}"
    exit 1
fi

# Get list of columns on the project
LIST_COLUMNS_URL="${GITHUB_API}/projects/${PROJECT_ID}/columns"
COLUMNS=$(curl -s -X GET -u "${OWNER_NAME}:${GITHUB_API_KEY}" --retry 3 \
            -H 'Accept: application/vnd.github.inertia-preview+json' \
            "${LIST_COLUMNS_URL}")
readonly LIST_COLUMNS_URL COLUMNS


# Extract columnid for the given column
COLUMN_ID=$(echo "$COLUMNS" | jq -r ".[] | select(.name == \"${COLUMN_NAME}\").id")
readonly COLUMN_ID
if [ -z "${COLUMN_ID}" ]; then
    echo "Could not find column named ${COLUMN_NAME} in project ${PROJECT_NAME} in ${REPOSITORY_FULL_NAME}"
    exit 1
fi

echo "Assigning issue ${ISSUE_NUMBER}:${ISSUE_ID} to column ${COLUMN_NAME}:${COLUMN_ID} in project ${PROJECT_NAME}:${PROJECT_ID} in ${REPOSITORY_FULL_NAME}"


# Add issue to project board column
HTTP_CODE=$(curl -s -X POST -u "${OWNER_NAME}:${GITHUB_API_KEY}" --retry 3 \
     -o /dev/null \
     -w "%{http_code}" \
     -H 'Accept: application/vnd.github.inertia-preview+json' \
     -d "{\"content_type\": \"Issue\", \"content_id\": ${ISSUE_ID}" \
     "${GITHUB_API}/projects/columns/${COLUMN_ID}/cards")

if [ "${HTTP_CODE}" == "422" ]; then
    echo "Issue ${ISSUE_NUMBER}:${ISSUE_ID} already exists in ${COLUMN_NAME}:${COLUMN_ID} in project ${PROJECT_NAME}:${PROJECT_ID} in ${REPOSITORY_FULL_NAME}"
    exit 0
fi

if [ "${HTTP_CODE}" == "200" ] || [ "${HTTP_CODE}" == 201 ] || [ "${HTTP_CODE}" == 204 ]; then
    exit 0
fi

echo "Error adding ${ISSUE_NUMBER}:${ISSUE_ID} to ${COLUMN_NAME}:${COLUMN_ID} in project ${PROJECT_NAME}:${PROJECT_ID} in ${REPOSITORY_FULL_NAME}: ${HTTP_CODE}"
exit "${HTTP_CODE}"
