To generate a server keystore and the client truststore:

```shell
# Create the keystore with a private key and a truststore to trust it
# Typically the server will load the keystore generated as server.p12 and
# the client will load the truststore generated as client.p12
# Default password is 'password', unless you specify other as an argument
./certGen.sh
```
