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

FOLDER=./
ALIAS='1'
PASSWORD=${1:-'password'}

function clean(){
    FILE=$1
    echo "=============================> Cleaning $FILE"
    rm -f ${FILE}
}

function printKeystore(){
    KEYSTORE=$1
    echo "=============================> Print ${KEYSTORE}"
    keytool -list -keystore ${FOLDER}/${KEYSTORE} -storepass ${PASSWORD} -v
}

function createKeystore(){
    KEYSTORE=$1
    DNAME=$2
    keytool -keystore ${FOLDER}/${KEYSTORE} -genkey -storetype PKCS12 -keyalg RSA -alias ${ALIAS} -storepass ${PASSWORD} -dname "${DNAME}" -ext "SAN=dns:localhost,ip:127.0.0.1" -ext "KU=digitalSignature,nonRepudiation,keyEncipherment,dataEncipherment,keyAgreement,keyCertSign" -validity 36500
    keytool -list -rfc -keystore ${FOLDER}/${KEYSTORE} -alias ${ALIAS} -storepass ${PASSWORD} > ${FOLDER}/privateKey.cert
    echo "=============================> ${FOLDER}/${KEYSTORE} created"
}

function createTruststore(){
    TRUSTSTORE=$1
    keytool -import -noprompt -file ${FOLDER}/privateKey.cert -storetype PKCS12 -alias ${ALIAS} -keystore ${FOLDER}/${TRUSTSTORE} -storepass ${PASSWORD}
    echo "=============================> ${FOLDER}/${TRUSTSTORE} created"
}

clean server.p12
clean client.p12

createKeystore 'server.p12' 'CN=helidon-test-certificate, OU=Helidon, O=Oracle, L=Santa Clara, ST=California, C=US, emailAddress=info@helidon.io'
createTruststore 'client.p12'

printKeystore 'server.p12'
printKeystore 'client.p12'
