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
#

if [ -d "./cr" ];
then
    echo "=== Starting directly from CRaC checkpoint ==="
else
	  echo "=== Creating CRaC checkpoint ==="
	  echo "Checking CRIU compatibility(don't forget --privileged):"
	  $JAVA_HOME/lib/criu check
	  set +e
    $JAVA_HOME/bin/java -XX:CRaCCheckpointTo=cr -jar ./*.jar
    set -e
fi
exec $JAVA_HOME/bin/java -XX:CRaCRestoreFrom=cr

