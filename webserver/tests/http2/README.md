To generate a new certificate:

```bash
cat <<EOF > cert-config.txt
[ req ]
prompt = no
default_md = sha256
distinguished_name = dn

[ dn ]
C=US
ST=California
L=Santa Clara
O=Oracle
OU=Helidon
emailAddress=info@helidon.io
CN = helidon-test-certificate
EOF

cat <<EOF > cert-config.ext
authorityKeyIdentifier = keyid:always,issuer:always
keyUsage               = digitalSignature, nonRepudiation, keyEncipherment, dataEncipherment, keyAgreement, keyCertSign
subjectAltName = @alt_names

[ alt_names ]
DNS = localhost
IP.1 = 127.0.0.1
IP.2 = 0:0:0:0:0:0:0:1
EOF

# Create the private key and csr request
openssl req \
    -newkey rsa:2048 \
    -nodes \
    -keyout ./key.pem \
    -out ./certificate.csr \
    -config ./cert-config.txt

# Generate a self-signed certificate
openssl x509 \
    -req \
    -in ./certificate.csr \
    -signkey ./key.pem \
    -out ./certificate.pem \
    -days 99999 \
    -sha256 \
    -extfile ./cert-config.ext

# Convert to pkcs12 keystore
openssl pkcs12 \
    -passin pass:password \
    -passout pass:password \
    -inkey ./key.pem \
    -in ./certificate.pem \
    -export \
    -out ./server.p12

# Create the client truststore
keytool -import \
    -srcstorepass password \
    -deststorepass password \
    -file ./certificate.pem \
    -storetype PKCS12 \
    -alias 1 \
    -keystore ./client.p12 \
    -noprompt
```