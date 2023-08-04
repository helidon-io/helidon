#!/bin/bash -e

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

PRIVATE_KEY_AS_PEM=private-key.pem
VAULT_PUBLIC_WRAPPING_KEY_PATH=vaultWrappingPub.key
PRIVATE_KEY_AS_DER=uploadedKey.der
TEMPORARY_AES_KEY_PATH=tmpAES.key
WRAPPED_TEMPORARY_AES_KEY_FILE=wrappedTmpAES.key
WRAPPED_TARGET_KEY_FILE=wrappedUploadedKey.key
WRAPPED_KEY_MATERIAL_FILE=readyToUpload.der

prepareKeyToUpload() {
  KEYSTORE_FILE=${1}.jks

  # Obtain OCI wrapping key
  oci kms management wrapping-key get \
    --query 'data."public-key"' \
    --raw-output \
    --endpoint ${VAULT_MANAGEMENT_ENDPOINT} \
    >$VAULT_PUBLIC_WRAPPING_KEY_PATH

  # Extract server/client private key
  openssl pkcs12 -in "$KEYSTORE_FILE" \
    -nocerts \
    -passin pass:password -passout pass:password \
    -out $PRIVATE_KEY_AS_PEM

  ## Upload server/client private key to vault
  # Generate a temporary AES key
  openssl rand -out $TEMPORARY_AES_KEY_PATH 32

  # Wrap the temporary AES key with the public wrapping key using RSA-OAEP with SHA-256:
  openssl pkeyutl -encrypt -in $TEMPORARY_AES_KEY_PATH \
    -inkey $VAULT_PUBLIC_WRAPPING_KEY_PATH \
    -pubin -out $WRAPPED_TEMPORARY_AES_KEY_FILE \
    -pkeyopt rsa_padding_mode:oaep \
    -pkeyopt rsa_oaep_md:sha256

  # Generate hexadecimal of the temporary AES key material:
  TEMPORARY_AES_KEY_HEXDUMP=$(hexdump -v -e '/1 "%02x"' <${TEMPORARY_AES_KEY_PATH})

  # If the RSA private key you want to import is in PEM format, convert it to DER:
  openssl pkcs8 -topk8 -nocrypt \
    -inform PEM -outform DER \
    -passin pass:password -passout pass:password \
    -in $PRIVATE_KEY_AS_PEM -out $PRIVATE_KEY_AS_DER

  # Wrap RSA private key with the temporary AES key:
  openssl enc -id-aes256-wrap-pad -iv A65959A6 -K "${TEMPORARY_AES_KEY_HEXDUMP}" -in $PRIVATE_KEY_AS_DER -out $WRAPPED_TARGET_KEY_FILE

  # Create the wrapped key material by concatenating both wrapped keys:
  cat $WRAPPED_TEMPORARY_AES_KEY_FILE $WRAPPED_TARGET_KEY_FILE >$WRAPPED_KEY_MATERIAL_FILE

  KEY_MATERIAL_AS_BASE64=$(base64 -w 0 readyToUpload.der)

  JSON_KEY_MATERIAL="{\"keyMaterial\": \"$KEY_MATERIAL_AS_BASE64\",\"wrappingAlgorithm\": \"RSA_OAEP_AES_SHA256\"}"

  echo $JSON_KEY_MATERIAL >key-material.json
}

test() {
  KEC="Aha!"
  echo $KEC
}

createKeyInVault() {
  TYPE=$1
  KEY_NAME=${2}

  export NEW_KEY_OCID=$(oci kms management key import \
    --compartment-id ${COMPARTMENT_OCID} \
    --display-name ${KEY_NAME}-${TYPE} \
    --key-shape '{"algorithm": "RSA", "length": 256}' \
    --protection-mode SOFTWARE \
    --endpoint ${VAULT_MANAGEMENT_ENDPOINT} \
    --wrapped-import-key file://key-material.json \
    --query 'data.id' \
    --raw-output)

  echo "$NEW_KEY_OCID"
}

rotateKeyInVault() {
  TYPE=$1
  KEY_OCID=${2}

  oci kms management key-version import \
    --key-id $KEY_OCID \
    --endpoint ${VAULT_MANAGEMENT_ENDPOINT} \
    --wrapped-import-key file://key-material.json
}

genCertAndCSR() {
  TYPE=$1

  # Get CA cert
  oci certificates certificate-authority-bundle get --query 'data."certificate-pem"' \
    --raw-output \
    --certificate-authority-id ${CA_OCID} \
    >ca.pem

  # Generating new server key store
  keytool -genkeypair -keyalg RSA -keysize 2048 \
    -alias ${TYPE} \
    -dname "CN=localhost" \
    -validity 60 \
    -keystore ${TYPE}.jks \
    -storepass password -keypass password \
    -deststoretype pkcs12

  # Create CSR
  keytool -certreq -keystore "${TYPE}.jks" \
    -alias ${TYPE} \
    -keypass password \
    -storepass password \
    -validity 60 \
    -keyalg rsa \
    -file ${TYPE}.csr
}

uploadNewCert() {
  TYPE=$1
  CERT_NAME=$2
  ## Create server/client certificate in OCI
  export NEW_CERT_OCID=$(oci certs-mgmt certificate create-certificate-managed-externally-issued-by-internal-ca \
  --compartment-id ${COMPARTMENT_OCID} \
  --issuer-certificate-authority-id ${CA_OCID} \
  --name ${CERT_NAME}-${TYPE} \
  --csr-pem "$(cat ${TYPE}.csr)" \
  --query 'data.id' \
  --raw-output)

  echo "$NEW_CERT_OCID"
}

rotateCert() {
  TYPE=$1
  CERT_OCID=$2

  ## Renew server certificate in OCI
  oci certs-mgmt certificate update-certificate-managed-externally \
    --certificate-id "${CERT_OCID}" \
    --csr-pem "$(cat ${TYPE}.csr)"
}
