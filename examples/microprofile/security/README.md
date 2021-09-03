
# Helidon MP Multiple Applications with Security

Example MicroProfile application. This has two JAX-RS applications
sharing a common resource accessed through different context roots.

The resource has multiple endpoints, protected with different
levels of security.

## Build and run

```bash
mvn package
java -jar target/helidon-examples-microprofile-mp1_1-security.jar
```

## Endpoints

Open either of the following in a browser. Once the page loads
click on the links to try and load endpoints restricted to 
admin roles or user roles. See the example's `application.yaml`
for the list of user, passwords and roles.

|Endpoint    |Description      |
|:-----------|:----------------|
|`static/helloworld`|Public page with no security|
|`other/helloworld`|Same page from second application|

