# Mutual TLS Example

This application demonstrates use of client certificates to 
authenticate HTTP client.

## Build

```shell
mvn package
```

## Run

Run the _config_ variant (default) of the application:

```shell
java -jar target/helidon-examples-webserver-mutual-tls.jar
```

Run the _programmatic_ variant of the application:

```shell
mvn exec:java -Dexec.mainClass=io.helidon.examples.webserver.mtls.ServerBuilderMain
```

## Exercise the application

Using `curl`:

```shell
openssl pkcs12 -in src/main/resources/client.p12 -nodes -legacy -passin pass:password -nokeys -out /tmp/chain.pem
openssl pkcs12 -in src/main/resources/client.p12 -nodes -legacy -passin pass:password -nokeys -cacerts -out /tmp/ca.pem 
openssl pkcs12 -in src/main/resources/client.p12 -nodes -legacy -passin pass:password -nocerts -out /tmp/key.pem
curl --key /tmp/key.pem --cert /tmp/chain.pem --cacert /tmp/ca.pem https://localhost:443 --pass password
```

Using Helidon WebClient setup with configuration:

```shell
mvn exec:java -Dexec.mainClass=io.helidon.examples.webserver.mtls.ClientConfigMain
```

Using Helidon WebClient setup programmatically:

```shell
mvn exec:java -Dexec.mainClass=io.helidon.examples.webserver.mtls.ClientBuilderMain
```
