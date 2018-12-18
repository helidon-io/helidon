#!/bin/bash

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
