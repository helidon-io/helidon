#!/bin/bash -ex
#
# Copyright (c) 2021 Oracle and/or its affiliates.
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

# Path to this script
[ -h "${0}" ] && readonly SCRIPT_PATH="$(readlink "${0}")" || readonly SCRIPT_PATH="${0}"

# Load pipeline environment setup and define WS_DIR
. $(dirname -- "${SCRIPT_PATH}")/includes/pipeline-env.sh "${SCRIPT_PATH}" '../..'

# Setup error handling using default settings (defined in includes/error_handlers.sh)
error_trap_setup

REQUIRED_ENV_VARS=(
    "OCI_TEST_API_PRIVATE_KEY" \
    "OCI_TEST_USER_OCID" \
    "OCI_TEST_TENANCY_OCID" \
)
for ((i=0;i<${#REQUIRED_ENV_VARS[@]};i++))
{
    require_env "${REQUIRED_ENV_VARS[${i}]}"
}

printf "\nINFO: generating oci config\n"
fingerprints(){
    openssl rsa -pubout -outform DER -in ${1} 2> /dev/null | \
        openssl md5 -c | awk '{print $2}'
}
mkdir -p ~/.oci
cat <<EOF > ~/.oci/config
[DEFAULT]
user=${OCI_TEST_USER_OCID}
fingerprint=$(fingerprints ${OCI_TEST_API_PRIVATE_KEY})
key_file=${OCI_TEST_API_PRIVATE_KEY}
tenancy=${OCI_TEST_TENANCY_OCID}
region=us-ashburn-1
EOF

chmod go-rw ~/.oci/config
chmod go-rw ${OCI_API_PRIVATE_KEY}

cat ~/.oci/config

sleep 10m

mvn ${MAVEN_ARGS} --version