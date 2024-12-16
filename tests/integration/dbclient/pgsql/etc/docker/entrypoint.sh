#!/usr/bin/env bash
#
# Copyright (c) 2024 Oracle and/or its affiliates.
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
set -Eeo pipefail

PGDATA="${PGDATA:-/var/lib/pgsql/data}"

# initialize the data directory
initdb \
    --username="${POSTGRES_USER:-test}" \
    --pwfile=<(printf "%s" "${POSTGRES_PASSWORD:-test}") \
    -D "${PGDATA}"

# update config file
# - listen on all network interfaces
# - turn off logging collector to log to the console
sed -e "s/^#listen_addresses = 'localhost'/listen_addresses = '*'/g" \
    -e "s/logging_collector = on/logging_collector = off/g" \
    -e "s/^#session_replication_role = 'origin'/session_replication_role = '${POSTGRES_SRR:-origin}'/g" \
    -i "${PGDATA}/postgresql.conf"

# enabling trust for all connections
printf 'host all all all %s\n' "$(postgres -C password_encryption)" >> "${PGDATA}/pg_hba.conf"

# temporary start to create the database
pg_ctl -D "${PGDATA}" -w start
psql \
    --username "${POSTGRES_USER:-test}" \
    --no-password \
    --dbname postgres \
    --set db="${POSTGRES_DB:-test}" <<-'EOSQL'
    create database :"db";
    set session_replication_role = replica;
EOSQL
pg_ctl -D "${PGDATA}" -m fast -w stop

exec "${@}"
