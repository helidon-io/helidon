# Web Server Integration and Digest Authentication

This example demonstrates Integration of WebServer
based application with Security component and Digest authentication (from HttpAuthProvider).

## Contents

There are two examples with exactly the same behavior:
1. DigestExampleMain - shows how to programmatically secure application
2. DigestExampleConfigMain - shows how to secure application with configuration
    1. see src/main/resources/application.conf for configuration

## Build and run

With JDK8+
```bash
mvn package
java -jar target/helidon-examples-security-webserver-digest-auth.jar
```
