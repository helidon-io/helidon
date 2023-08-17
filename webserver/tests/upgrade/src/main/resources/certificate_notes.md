To generate a new certificate:

```shell
# Create the private key and csr request
openssl req -newkey rsa:2048 -nodes -keyout key.pem -out certificate.csr -config ./cert-config.txt
# Generate a self-signed certificate
openssl x509 -req -in certificate.csr -signkey key.pem -out certificate.pem -days 99999 -sha256 -extfile cert-config.ext
# Convert to pkcs12 keystore (set a password using a prompt)
openssl pkcs12 -inkey key.pem -in certificate.pem -export -out server.p12
```
