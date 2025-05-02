#!/usr/bin/env bash
#
# Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
set -Eeom pipefail

mkdir -p /data/db
mkdir -p /var/log/mongodb

# redirect stdout and stderr to log file
: > /var/log/mongodb/mongod.log
exec 2> >(tee -a /var/log/mongodb/mongod.log >&2) > >(tee -a /var/log/mongodb/mongod.log)

# Add argument
args=("${@}")
args+=("--bind_ip_all")

#  start in the background
exec "${args[@]}" &

# wait for started
while true ; do
    grep "mongod startup complete" /var/log/mongodb/mongod.log > /dev/null && break
    sleep 2
done

echo "Initializing database..."
mongosh --host 127.0.0.1 --port 27017 --quiet admin <<-EOJS
    use ${MONGO_DB:-test}
    db.createUser({
        user: "${MONGO_USER:-test}",
        pwd:  "${MONGO_PASSWORD:-test123}",
        roles: [{role: "readWrite", db: "${MONGO_DB:-test}"}]
    })
EOJS

echo "Container ready!"
fg
