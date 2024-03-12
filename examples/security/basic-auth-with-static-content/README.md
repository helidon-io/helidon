# Web Server Integration and Basic Authentication

This example demonstrates integration of Web Server
based application with Security component and Basic authentication (from HttpAuthProvider), including
protection of a static resource.

## Contents

There are two examples with exactly the same behavior:
1. BasicExampleMain - shows how to programmatically secure application
2. BasicExampleConfigMain - shows how to secure application with configuration
    1. see src/main/resources/application.yaml for configuration

## Build and run

```shell
mvn package
java -jar target//helidon-examples-security-webserver-basic-uath.jar
```

Try the application:

The application starts on a random port, the following assumes it is `56551`
```shell
export PORT=37667
curl http://localhost:${PORT}/public
curl -u "jill:password" http://localhost:${PORT}/noRoles
curl -u "john:password" http://localhost:${PORT}/user
curl -u "jack:password" http://localhost:${PORT}/admin
curl -v -u "john:password" http://localhost:${PORT}/deny
curl -u "jack:password" http://localhost:${PORT}/noAuthn
```
