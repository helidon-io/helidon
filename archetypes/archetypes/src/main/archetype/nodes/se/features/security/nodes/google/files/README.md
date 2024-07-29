## Try Google login

There is a static web page in src/main/resources/WEB with a page to login to Google.

This example requires a Google client id to run.
Update the following files with your client id (it should support http://localhost:8080):
1. src/main/resources/application.yaml - set security.properties.google-client-id or override it in a file in ~/helidon/examples.yaml
2. src/main/resources/WEB/index.html - update the meta tag in header with name "google-signin-client_id"
3. src/main/java/io/helidon/security/examples/google/GoogleMain.java - update the client id in builder of provider
