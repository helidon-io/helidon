To generate a new certificate:

```shell
# Create the private key and csr request
openssl req \
    -newkey rsa:2048 \
    -nodes \
    -keyout src/main/resources/key.pem \
    -out src/main/resources/certificate.csr \
    -config src/main/resources/cert-config.txt

# Generate a self-signed certificate
openssl x509 \
    -req \
    -in src/main/resources/certificate.csr \
    -signkey src/main/resources/key.pem \
    -out src/main/resources/certificate.pem \
    -days 99999 \
    -sha256 \
    -extfile src/main/resources/cert-config.ext

# Convert to pkcs12 keystore (set a password using a prompt)
openssl pkcs12 \
    -inkey src/main/resources/key.pem \
    -in src/main/resources/certificate.pem \
    -export \
    -out src/main/resources/server.p12

# Create the client truststore
keytool -import \
    -file src/main/resources/certificate.pem \
    -storetype PKCS12 \
    -alias 1 \
    -keystore src/main/resources/client.p12
```
