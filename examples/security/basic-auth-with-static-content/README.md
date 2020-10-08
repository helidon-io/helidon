# Web Server Integration and Basic Authentication

This example demonstrates Integration of WebServer
based application with Security component and Basic authentication (from HttpAuthProvider), including
protection of a static resource.

## Contents

There are two examples with exactly the same behavior:
1. BasicExampleMain - shows how to programmatically secure application
2. BasicExampleConfigMain - shows how to secure application with configuration
    1. see src/main/resources/application.yaml for configuration

## Build and run

```bash
mvn package
java -jar target/helidon-examples-security-webserver-basic-auth.jar
```

Try the application:

The application starts on a random port, the following assumes it is `56551`
```bash
curl http://localhost:56551/public
curl -u "jill:password" http://localhost:56551/noRoles
curl -u "john:password" http://localhost:56551/user
curl -u "jack:password" http://localhost:56551/admin
curl -v -u "john:password" http://localhost:56551/deny
curl -u "jack:password" http://localhost:56551/noAuthn
```
