To generate a new certificate:

```shell
# Create the private key and csr request
openssl req \
    -newkey rsa:2048 \
    -nodes \
    -keyout src/test/resources/key.pem \
    -out src/test/resources/certificate.csr \
    -config src/test/resources/cert-config.txt

# Generate a self-signed certificate
openssl x509 \
    -req \
    -in src/test/resources/certificate.csr \
    -signkey src/test/resources/key.pem \
    -out src/test/resources/certificate.pem \
    -days 99999 \
    -sha256 \
    -extfile src/test/resources/cert-config.ext

# Convert to pkcs12 keystore (set a password using a prompt)
openssl pkcs12 \
    -inkey src/test/resources/key.pem \
    -in src/test/resources/certificate.pem \
    -export \
    -out src/test/resources/server.p12

# Create the client truststore
keytool -import \
    -file src/test/resources/certificate.pem \
    -storetype PKCS12 \
    -alias 1 \
    -keystore src/test/resources/client.p12
```
