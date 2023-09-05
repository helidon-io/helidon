#!/bin/bash

#
# Copyright (c) 2023 Oracle and/or its affiliates.
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

source ./config.sh
source ./generated-config.sh
source ./utils.sh

# Cleanup
rm -rf ./server ./client
mkdir -p server client

CDIR=$(pwd)

# Rotate server cert and key
cd ${CDIR}/server
genCertAndCSR server
rotateCert server $SERVER_CERT_OCID
prepareKeyToUpload server
rotateKeyInVault server $SERVER_KEY_OCID

# Rotate client cert and key
cd ${CDIR}/client
genCertAndCSR client
rotateCert client $CLIENT_CERT_OCID
prepareKeyToUpload client
rotateKeyInVault client $CLIENT_KEY_OCID

echo "ALL done!"
