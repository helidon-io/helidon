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
source ./utils.sh

# Cleanup
rm -rf ./server ./client
mkdir -p server client

CDIR=$(pwd)

# Rotate server cert and key
cd ${CDIR}/server
genCertAndCSR server
NEW_SERVER_CERT_OCID=$(uploadNewCert server $DISPLAY_NAME_PREFIX)
prepareKeyToUpload server
NEW_SERVER_KEY_OCID=$(createKeyInVault server $DISPLAY_NAME_PREFIX)

# Rotate client cert and key
cd ${CDIR}/client
genCertAndCSR client
NEW_CLIENT_CERT_OCID=$(uploadNewCert client $DISPLAY_NAME_PREFIX)
prepareKeyToUpload client
NEW_CLIENT_KEY_OCID=$(createKeyInVault client $DISPLAY_NAME_PREFIX)

echo "======= ALL done! ======="
echo "Newly created OCI resources:"
echo "Server certificate OCID: $NEW_SERVER_CERT_OCID"
echo "Server private key OCID: $NEW_SERVER_KEY_OCID"
echo "Client certificate OCID: $NEW_CLIENT_CERT_OCID"
echo "Client private key OCID: $NEW_CLIENT_KEY_OCID"
echo "Saving to gen-config.sh"
tee ${CDIR}/generated-config.sh << EOF
#!/bin/bash
## Content of this file gets rewritten by createKeys.sh
export SERVER_CERT_OCID=$NEW_SERVER_CERT_OCID
export SERVER_KEY_OCID=$NEW_SERVER_KEY_OCID

export CLIENT_CERT_OCID=$NEW_CLIENT_CERT_OCID
export CLIENT_KEY_OCID=$NEW_CLIENT_KEY_OCID
EOF
