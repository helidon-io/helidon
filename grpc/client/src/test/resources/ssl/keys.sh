#!/bin/bash
#
# Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

# ------------------------------------------------------------------------
# This file was used to generate the keys and certs used for SSL testing.
# ------------------------------------------------------------------------

# Generate CA key:
openssl genrsa -des3 -passout pass:1111 -out ca.key 2048

# Generate CA certificate:
openssl req -passin pass:1111 -new -x509 -days 99999 -key ca.key -out ca.pem -subj "/CN=localhost"

# Generate server key:
openssl genpkey -out serverKey.pem -algorithm RSA -pkeyopt rsa_keygen_bits:2048

# Generate server signing request
openssl req -passin pass:1111 -new -key serverKey.pem -out server.csr -subj "/CN=localhost"

# Self-signed server certificate:
openssl x509 -req -passin pass:1111 -days 99999 -in server.csr -CA ca.pem -CAkey ca.key -set_serial 01 -out serverCert.pem

# Generate client key
openssl genpkey -out clientKey.pem -algorithm RSA -pkeyopt rsa_keygen_bits:2048

# Generate client signing request:
openssl req -passin pass:1111 -new -key clientKey.pem -out client.csr -subj "/CN=test"

# Self-signed client certificate:
openssl x509 -passin pass:1111 -req -days 99999 -in client.csr -CA ca.pem -CAkey ca.key -set_serial 01  -out clientCert.pem

rm ca.key
rm server.csr
rm rm client.csr

