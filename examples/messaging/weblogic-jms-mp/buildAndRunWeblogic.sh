#!/bin/bash -e
#
# Copyright (c) 2022 Oracle and/or its affiliates.
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

cd ./weblogic

# Attempt Oracle container registry login.
# You need to accept the licence agreement for Weblogic Server at https://container-registry.oracle.com/
# Search for weblogic and accept the Oracle Standard Terms and Restrictions
docker login container-registry.oracle.com

docker build -t wls-admin .

docker run --rm -d \
  -p 7001:7001 \
  -p 7002:7002 \
  --name wls-admin \
  --hostname wls-admin \
  wls-admin

printf "Waiting for WLS to start ."
while true;
do
  if docker logs wls-admin | grep -q "Server state changed to RUNNING"; then
    break;
  fi
  printf "."
  sleep 5
done
printf " [READY]\n"

echo Deploying example JMS queues
docker exec wls-admin \
/bin/bash \
/u01/oracle/wlserver/common/bin/wlst.sh \
/u01/oracle/setupTestJMSQueue.py;

echo Example JMS queues deployed!
echo Console avaiable at http://localhost:7001/console with admin/Welcome1
echo 'Stop Weblogic server with "docker stop wls-admin"'