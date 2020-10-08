# Web Server Integration and Digest Authentication

This example demonstrates Integration of WebServer
based application with Security component and Digest authentication (from HttpAuthProvider).

## Contents

There are two examples with exactly the same behavior:
1. DigestExampleMain - shows how to programmatically secure application
2. DigestExampleConfigMain - shows how to secure application with configuration
    1. see src/main/resources/application.yaml for configuration

## Build and run

```bash
mvn package
java -jar target/helidon-examples-security-webserver-digest-auth.jar
```

Try the application:

The application starts on a random port, the following assumes it is `56551`
```bash
curl http://localhost:56551/public
curl --digest -u "jill:password" http://localhost:56551/noRoles
curl --digest -u "john:password" http://localhost:56551/user
curl --digest -u "jack:password" http://localhost:56551/admin
curl -v --digest -u "john:password" http://localhost:56551/deny
curl --digest -u "jack:password" http://localhost:56551/noAuthn
```
