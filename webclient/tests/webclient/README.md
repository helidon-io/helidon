## WebServer tests mTLS 

This readme describes how to generate the PKCS#12 files used by the tests.

### Root CA

```bash
openssl req -new -sha256 -newkey rsa:2048 \
	-keyout ca.key \
	-subj "/CN=Helidon-Test-CA" \
	-out ca.csr \
	-passout pass:password

openssl x509 -req \
	-in ca.csr \
	-signkey ca.key \
	-out ca.crt \
	-days 99999 -sha256 -passin pass:password
```

### Client Certificate

```bash
openssl req -new -sha256 -newkey rsa:2048 \
	-keyout client.key \
	-subj "/CN=Helidon-Test-Client" \
	-out client.csr \
	-extensions v3_req \
	-passout pass:password

openssl x509 -req \
	-in client.csr \
	-CA ca.crt \
	-CAkey ca.key \
	-out client.crt \
	-extfile <(printf "subjectAltName = DNS:localhost, IP:127.0.0.1") \
	-days 99999 -sha256 -passin pass:password

openssl pkcs12 \
	-inkey client.key \
	-in <(cat client.crt ca.crt) \
	-export \
	-out src/test/resources/client.p12 \
	-passin pass:password \
	-passout pass:password
```

### Server Certificate


```bash
openssl req -new -sha256 -newkey rsa:2048 \
	-keyout server.key \
	-subj "/CN=Helidon-Test-Server" \
	-out server.csr \
	-extensions v3_req \
	-passout pass:password

openssl x509 -req \
	-in server.csr \
	-CA ca.crt \
	-CAkey ca.key \
	-out server.crt \
	-extfile <(printf "subjectAltName = DNS:localhost, IP:127.0.0.1") \
	-days 99999 -sha256 -passin pass:password

openssl pkcs12 \
	-inkey server.key \
	-in <(cat server.crt ca.crt) \
	-export \
	-out src/test/resources/server.p12 \
	-passin pass:password \
	-passout pass:password
```

### Secondary Server Certificate

```bash
openssl req -new -sha256 -newkey rsa:2048 \
	-keyout server2.key \
	-subj "/CN=Helidon-Test-Server-Secondary" \
	-out server2.csr \
	-extensions v3_req \
	-passout pass:password

openssl x509 -req \
	-in server2.csr \
	-CA ca.crt \
	-CAkey ca.key \
	-out server2.crt \
	-extfile <(printf "subjectAltName = DNS:localhost, IP:127.0.0.1") \
	-days 99999 -sha256 -passin pass:password

openssl pkcs12 \
	-inkey server2.key \
	-in <(cat server2.crt ca.crt) \
	-export \
	-out src/test/resources/second-valid/server.p12 \
	-passin pass:password \
	-passout pass:password
```

### Trust CA certificates in client keystore

```bash
keytool -import \
    -noprompt \
    -file ca.crt \
    -storetype PKCS12 \
    -alias truststoreCA \
    -keystore src/test/resources/client.p12 \
    -storepass password
```

### Trust CA certificates in server keystore
```bash
keytool -import \
    -noprompt \
    -file ca.crt \
    -storetype PKCS12 \
    -alias truststoreCA \
    -keystore src/test/resources/server.p12 \
    -storepass password

keytool -import \
    -noprompt \
    -file ca.crt \
    -storetype PKCS12 \
    -alias truststoreCA \
    -keystore src/test/resources/second-valid/server.p12 \
    -storepass password
```
