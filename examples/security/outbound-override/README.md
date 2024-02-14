
# Helidon Security Outbound Override Example

Example that propagates identity, and on one endpoint explicitly
sets the username and password.

## Build and run

```shell
mvn package
java -jar target/helidon-examples-security-outbound-override.jar
```

Try the endpoints (port is random, shall be replaced accordingly):
```shell
export PORT=35973
curl -u "jack:password" http://localhost:${PORT}/propagate
curl -u "jack:password" http://localhost:${PORT}/override
curl -u "jill:anotherPassword" http://localhost:${PORT}/propagate
curl -u "jill:anotherPassword" http://localhost:${PORT}/override
```
