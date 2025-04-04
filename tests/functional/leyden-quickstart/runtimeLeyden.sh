#!/bin/bash -e

#
# Copyright (c) 2022-2024 Oracle and/or its affiliates.
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

if [ ! -d "$CR_DIR" ];
then
	  echo "==== Creating Leyden checkpoint ===="
    echo "=== Pre-starting Helidon MP app ==="
	  set +e
	  mkdir -p "$CR_DIR"
	  ./warmUp.sh &
    $JAVA_HOME/bin/java -XX:CacheDataStore=$CR_DIR/checkpoint.cds -Xlog:cds=debug:file=$CR_DIR/cds.log -jar ./*.jar
    set -e

    echo "=== Leyden checkpoint created ==="
else
echo "==== Starting directly from Leyden checkpoint ===="
#exec ls -l /helidon
./measure.sh &
exec $JAVA_HOME/bin/java -XX:CacheDataStore=$CR_DIR/checkpoint.cds -jar ./*.jar
#exec $JAVA_HOME/bin/java -jar ./*.jar
fi


