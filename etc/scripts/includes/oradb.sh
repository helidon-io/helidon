#
# Copyright (c) 2021 Oracle and/or its affiliates.
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

# Oracle database setup (shell script)
# Arguments: $1 - docker image

echo 'Using Oracle database'

readonly DB_HOST='127.0.0.1'
readonly DB_PORT='1521'
readonly DB_NAME='pokemon'
readonly DB_USER='test'
readonly DB_PASSWORD='Us3r_P4ssw0rd'
readonly DBA_USER='system'
readonly DBA_PASSWORD='r00t_p4ssw0rd'
readonly DB_URL="jdbc:oracle:thin:@${DB_HOST}:${DB_PORT}/${DB_NAME}"

readonly TEST_CONFIG='oradb.yaml'

if [ -z "${1}" ]; then
    readonly DOCKER_ENV="-e DB_SID=HELIDON -e DB_PDB=${DB_NAME}"
    readonly DOCKER_IMG='store/oracle/database-enterprise:12.2.0.1'
else
    readonly DOCKER_ENV="-e ORACLE_SID=HELIDON -e ORACLE_PDB=${DB_NAME} -e ORACLE_PWD=${DBA_PASSWORD} -e ORACLE_EDITION=standard"
    readonly DOCKER_IMG="${1}"
fi

readonly DB_PROFILE='oradb'

echo " - Database URL: ${DB_URL}"
