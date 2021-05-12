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

# PostgreSQL database setup (shell script)

echo 'Using PostgreSQL database'

readonly DB_HOST='127.0.0.1'
readonly DB_PORT='5432'
readonly DB_NAME='pokemon'
readonly DB_USER='user'
readonly DB_PASSWORD='p4ssw0rd'
readonly DB_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}"

readonly TEST_CONFIG='pgsql.yaml'

readonly DOCKER_ENV="-e POSTGRES_USER=${DB_USER} -e POSTGRES_DB=${DB_NAME} -e POSTGRES_PASSWORD=${DB_PASSWORD}"
readonly DOCKER_IMG='postgres'

readonly DB_PROFILE='pgsql'

echo " - Database URL: ${DB_URL}"
