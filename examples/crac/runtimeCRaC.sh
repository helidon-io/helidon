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

if [ ! -d "$CR_DIR" ];
then
	  echo "==== Creating CRaC checkpoint ===="
	  echo "=== Checking CRIU compatibility(don't forget --privileged) ==="
	  $JAVA_HOME/lib/criu check

	  echo "=== Checking glibc version ==="
	  # glibc version higher than 2.34.9000-29 are known to have problems with rseq
    # on some kernels, workaround GLIBC_TUNABLES=glibc.pthread.rseq=0
    ldd --version | grep ldd
    # Workaround for https://github.com/checkpoint-restore/criu/issues/1696
    # see https://github.com/checkpoint-restore/criu/pull/1706
    export GLIBC_TUNABLES=glibc.pthread.rseq=0

    echo "=== Pre-starting Helidon MP app ==="
	  set +e
    $JAVA_HOME/bin/java -XX:CRaCCheckpointTo=/crac-checkpoint/cr -jar ./*.jar
    set -e

    echo "=== CRaC checkpoint created, checking log dump for errors ==="
    cat $CR_DIR/dump*.log | grep "Warn\|Err\|succ"
else
echo "==== Starting directly from CRaC checkpoint ===="
exec $JAVA_HOME/bin/java -XX:CRaCRestoreFrom=$CR_DIR
fi


