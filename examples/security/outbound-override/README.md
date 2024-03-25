
# Helidon Security Outbound Override Example

Example that propagates identity, and on one endpoint explicitly
sets the username and password.

## Build and run

```shell
mvn package
java -jar target/helidon-examples-security-outbound-override.jar
```

Try the endpoints:

```shell
curl -u "jack:changeit" http://localhost:8080/propagate
curl -u "jack:changeit" http://localhost:8080/override
curl -u "jill:changeit" http://localhost:8080/propagate
curl -u "jill:changeit" http://localhost:8080/override
```
