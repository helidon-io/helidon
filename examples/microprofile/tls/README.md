# Helidon MP TLS Example

This examples shows how to configure server TLS using Helidon MP.

Note: This example uses self-signed server certificate!

## Build and run

```shell
mvn package
java -jar target/helidon-examples-microprofile-tls.jar
```
## Exercise the application
```shell
curl -k -X GET https://localhost:8080
```
