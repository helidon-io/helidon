# Integration with Google login button

This example demonstrates Integration with Google login button on a web page.

## Contents

There are two examples with exactly the same behavior
1. builder - shows how to programmatically secure application
2. config - shows how to secure application with configuration
    1. see src/main/resources/application.conf
    
There is a static web page in src/main/resources/WEB with a page to login to Google.

This example requires a Google client id to run. 
Update the following files with your client id (it should support http://localhost:8080):
1. src/main/resources/application.yaml - set security.properties.google-client-id or override it in a file in ~/helidon/examples.yaml
2. src/main/resources/WEB/index.html - update the meta tag in header with name "google-signin-client_id"
3. src/main/java/io/helidon/security/examples/google/GoogleBuilderMain.java - update the client id in builder of provider

## Build and run

```bash
mvn package
java -jar target/helidon-examples-security-google-login.jar
```
