#!/bin/bash
#
# Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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
set -e

echo 'Starting Cassandra database'
/docker-entrypoint.sh "$@" > /var/log/cassandra.log &

echo 'Waiting for database to become available'
COUNT='1'
while test $COUNT -lt '120' && ! timeout 1 bash -c 'cat < /dev/null > /dev/tcp/127.0.0.1/9042' > /dev/null 2>&1 ; do
  if [ "$((COUNT%10))" -eq '0' ]; then
    echo "  ...$COUNT s"
  fi
  sleep 1
  COUNT=$((COUNT+1))
done

echo 'Creating todos table'
cqlsh -e "
    CREATE KEYSPACE backend WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor' : 1};
    CREATE TABLE backend.backend (id ascii, user ascii, message ascii, completed Boolean, created timestamp, PRIMARY KEY (id));
    select * from backend.backend;
" \
  || true

echo 'Opening database log file'
echo '-------------------------'
tail -f /var/log/cassandra.log
