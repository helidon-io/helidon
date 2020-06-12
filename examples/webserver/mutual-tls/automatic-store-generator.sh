#!/bin/bash -e
#
# Copyright (c) 2020 Oracle and/or its affiliates.
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

# Windows Mingwin fix for path resolving
#export MSYS_NO_PATHCONV=1

NAME=""
TYPE=PKCS12
SINGLE=true

createCertificatesAndStores() {
	mkdir out
  echo 'Generating new key stores...'
  keytool -genkeypair -keyalg RSA -keysize 2048 -alias root-ca -dname "CN=$NAME-CA" -validity 21650 -keystore ca.jks -storepass password -keypass password -deststoretype pkcs12 -ext KeyUsage=digitalSignature,keyEncipherment,keyCertSign -ext ExtendedKeyUsage=serverAuth,clientAuth -ext BasicConstraints=ca:true,PathLen:3
  keytool -genkeypair -keyalg RSA -keysize 2048 -alias server -dname "CN=localhost" -validity 21650 -keystore server.jks -storepass password -keypass password -deststoretype pkcs12
  keytool -genkeypair -keyalg RSA -keysize 2048 -alias client -dname "C=CZ,CN=$NAME-client,OU=Prague,O=Oracle" -validity 21650 -keystore client.jks -storepass password -keypass password -deststoretype pkcs12
  echo 'Obtaining client and server certificates...'
  keytool -exportcert -keystore client.jks -storepass password -alias client -rfc -file client.cer
  keytool -exportcert -keystore server.jks -storepass password -alias server -rfc -file server.cer
  echo 'Generating CSR for client and server...'
  keytool -certreq -keystore server.jks -alias server -keypass password -storepass password -keyalg rsa -file server.csr
  keytool -certreq -keystore client.jks -alias client -keypass password -storepass password -keyalg rsa -file client.csr
  echo 'Obtaining CA pem and key...'
  keytool -importkeystore -srckeystore ca.jks -destkeystore ca.p12 -srcstoretype jks -deststoretype pkcs12 -srcstorepass password -deststorepass password
  openssl pkcs12 -in ca.p12 -out ca.key -nocerts -passin pass:password -passout pass:password
  openssl pkcs12 -in ca.p12 -out ca.pem -nokeys -passin pass:password -passout pass:password
  echo 'Signing client and server certificates...'
  openssl x509 -req -in client.csr -CA ca.pem -CAkey ca.key -CAcreateserial -out client-signed.cer -days 21650 -passin pass:password
  openssl x509 -req -in server.csr -CA ca.pem -CAkey ca.key -CAcreateserial -out server-signed.cer -sha256 -days 21650  -passin pass:password
  echo 'Replacing server and client certificates with the signed ones...'
  keytool -importkeystore -srckeystore client.jks -destkeystore client.p12 -srcstoretype jks -deststoretype pkcs12 -srcstorepass password -deststorepass password
  openssl pkcs12 -in client.p12 -nodes -out client-private.key -nocerts -passin pass:password
  openssl pkcs12 -export -in client-signed.cer -inkey client-private.key -out client-signed.p12 -name client -passout pass:password
  keytool -delete -alias client -keystore client.jks -storepass password
  keytool -importkeystore -srckeystore client-signed.p12 -srcstoretype PKCS12 -destkeystore client.jks -srcstorepass password -deststorepass password
  keytool -importkeystore -srckeystore server.jks -destkeystore server.p12 -srcstoretype jks -deststoretype pkcs12 -srcstorepass password -deststorepass password
  openssl pkcs12 -in server.p12 -nodes -out server-private.key -nocerts -passin pass:password
  openssl pkcs12 -export -in server-signed.cer -inkey server-private.key -out server-signed.p12 -name server -passout pass:password
  keytool -delete -alias server -keystore server.jks -storepass password
  keytool -importkeystore -srckeystore server-signed.p12 -srcstoretype PKCS12 -destkeystore server.jks -srcstorepass password -deststorepass password
	
	echo "Importing CA cert to the client and server stores..."
	if [ "$SINGLE" = true ] ; then
		keytool -v -trustcacerts -keystore client.jks -importcert -file ca.pem -alias root-ca -storepass password -noprompt
		keytool -v -trustcacerts -keystore server.jks -importcert -file ca.pem -alias root-ca -storepass password -noprompt
	else 
		keytool -v -trustcacerts -keystore client-truststore.jks -importcert -file ca.pem -alias root-ca -storepass password -noprompt
		keytool -v -trustcacerts -keystore server-truststore.jks -importcert -file ca.pem -alias root-ca -storepass password -noprompt
	fi
	
	echo "Changing aliases to 1..."
	keytool -changealias -alias server -destalias 1 -keypass password -keystore server.jks -storepass password
	keytool -changealias -alias client -destalias 1 -keypass password -keystore client.jks -storepass password

	echo "Generating requested type of stores..."
	if [ "$TYPE" = PKCS12 ] || [ "$TYPE" = P12 ] ; then
		keytool -importkeystore -srckeystore client.jks -destkeystore out/client.p12 -srcstoretype JKS -deststoretype PKCS12 -srcstorepass password -deststorepass password
		keytool -importkeystore -srckeystore server.jks -destkeystore out/server.p12 -srcstoretype JKS -deststoretype PKCS12 -srcstorepass password -deststorepass password
		if [ "$SINGLE" = false ] ; then
			keytool -importkeystore -srckeystore server-truststore.jks -destkeystore out/server-truststore.p12 -srcstoretype JKS -deststoretype PKCS12 -srcstorepass password -deststorepass password
			keytool -importkeystore -srckeystore client-truststore.jks -destkeystore out/client-truststore.p12 -srcstoretype JKS -deststoretype PKCS12 -srcstorepass password -deststorepass password
		fi
	else 
		mv client.jks out/client.jks
		mv server.jks out/server.jks
		if [ "$SINGLE" = false ] ; then
			mv client-truststore.jks out/client-truststore.jks
			mv server-truststore.jks out/server-truststore.jks
		fi
	fi
}

removeAllPreviouslyCreatedStores() {
    echo 'Removing all of previously created items...'

    rm -fv ca.key
    rm -fv ca.jks
    rm -fv ca.p12
    rm -fv ca.pem
    rm -fv ca.srl
    rm -fv server.jks
    rm -fv server.cer
    rm -fv server.csr
    rm -fv server.p12
    rm -fv server-private.key
    rm -fv server-signed.cer
    rm -fv server-signed.p12
    rm -fv server-truststore.jks
    rm -fv client.cer
    rm -fv client.csr
    rm -fv client.p12
    rm -fv client-private.key
    rm -fv client-signed.cer
    rm -fv client-signed.p12
    rm -fv client.jks
    rm -fv client-truststore.jks
	  rm -rf out

    echo 'Clean up finished'
}

while [ "$1" != "" ]; do
    case $1 in
        -n | --name )           shift
                                NAME=$1
                                ;;
        -t | --type )           shift
                                TYPE=$1
                                ;;
        -s | --single )    		shift
								SINGLE=$1
                                ;;
        -h | --help )           echo "Some cool help"
                                exit
                                ;;
        * )                     echo "ERROR: Invalid parameter" $1
                                exit 1
    esac
    shift
done
if [ -z "$NAME" ]; then
    echo "ERROR: Please specify the name of Organization/Application by parameter -n | --name"
	exit 1
else
	echo "Generating certs for Organization/Application "$NAME
fi
case $TYPE in
        JKS | P12 | PKCS12 )
			echo "Output file will be of type" $TYPE
            ;;
        *)
			echo 'ERROR: Invalid output type' $TYPE 
			echo 'Only JKS | P12 | PKCS12 supported'
			return 1
esac
case $SINGLE in
        true )
			echo "Truststore and private key will be in single file"
            ;;
		false ) 
			echo "Truststore and private key will be in separate files"
			;;
        *)
			echo "ERROR: Only value true/false valid in single parameter! Current " $SINGLE
			exit 1
esac

removeAllPreviouslyCreatedStores
createCertificatesAndStores
