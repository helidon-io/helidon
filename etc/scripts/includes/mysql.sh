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

# MySQL database setup (shell script)

echo 'Using MySQL database'

readonly DB_HOST='127.0.0.1'
readonly DB_PORT='3306'
readonly DB_NAME='pokemon'
readonly DB_USER='user'
readonly DB_PASSWORD='p4ssw0rd'
readonly DB_ROOT_PASSWORD='r00t_p4ssw0rd'
readonly DB_ARGS='useSSL=false&allowPublicKeyRetrieval=true'
readonly DB_URL="jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?${DB_ARGS}"

readonly TEST_CONFIG='mysql.yaml'

readonly DOCKER_ENV="-e MYSQL_USER=${DB_USER} -e MYSQL_DATABASE=${DB_NAME} -e MYSQL_PASSWORD=${DB_PASSWORD} -e MYSQL_ROOT_PASSWORD=${DB_ROOT_PASSWORD}"
readonly DOCKER_IMG='mysql:8'

readonly DB_PROFILE='mysql'

echo " - Database URL: ${DB_URL}"
