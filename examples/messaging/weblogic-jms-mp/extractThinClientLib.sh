#!/bin/bash -e
#
# Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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


# Copy wlthint3client.jar from docker container
docker cp wls-admin:/u01/oracle/wlserver/server/lib/wlthint3client.jar ./weblogic/wlthint3client.jar
# Copy DemoTrust.jks from docker container(needed if you want to try t3s protocol)
docker cp wls-admin:/u01/oracle/wlserver/server/lib/DemoTrust.jks ./weblogic/DemoTrust.jks