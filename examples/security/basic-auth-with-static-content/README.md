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
java -jar target/helidon-examples-security-webserver-basic-auth.jar
```

Try the application:

The application starts at the `8080` port
```shell
curl http://localhost:8080/public
curl -u "jill:changeit" http://localhost:8080/noRoles
curl -u "john:changeit" http://localhost:8080/user
curl -u "jack:changeit" http://localhost:8080/admin
curl -v -u "john:changeit" http://localhost:8080/deny
curl -u "jack:changeit" http://localhost:8080/noAuthn
```
