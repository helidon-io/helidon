# Helidon MP IDCS

This example demonstrates integration with IDCS (Oracle identity service, integrated with Open ID Connect provider) where JAX-RS application resources are protected by IDCS.

## Contents

This project contains two samples, one (IdcsApplication.java) and a second (ReactiveService.java). It also contains a static resource. When configured the example exposes multiple HTTP endpoints.

### IDCS Configuration

[This documentation](https://docs.oracle.com/en/cloud/paas/identity-cloud/uaids/oracle-identity-cloud-service.html#GUID-BC4769EE-258A-4B53-AED5-6BA9888C8275) describes basics of IDCS as well as how you can get IDCS instance.

1. [Log in to the IDCS console](https://docs.oracle.com/en/cloud/paas/identity-cloud/uaids/how-access-oracle-identity-cloud-service.html) and create a new application of type "confidential app"
2. Within  **Resources**
    1. Create two resources called `first_scope` and `second_scope`
    2. Primary Audience = `http://localhost:7987/"`   (ensure there is a trailing /)
3. Within **Client Configuration**
    1. Register a client
    2. Allowed Grant Types = Client Credentials,JWT Assertion, Refresh Token, Authorization Code
    3. Check "Allow non-HTTPS URLs"
    4. Set Redirect URL to `http://localhost:7987/oidc/redirect`
    5. Client Type = Confidential
    6. Add all Scopes defined in the resources section
    7. Set allowed operations to `Introspect`
    8. Set Post Logout Redirect URL to `http://localhost:7987/loggedout`

Ensure you save and *activate* the application

### Application Configuration

Edit application.yaml based on your IDCS Configuration

1. idcs-uri  : Base URL of your idcs instance, usually something like https://idcs-<longnumber>.identity.oraclecloud.com
2. idcs-client-id  : This is obtained from your IDCS application in the IDCS console
3. idcs-client-secret   : This is obtained from your IDCS application in the IDCS console
4. frontend-uri : This is the base URL of your application when run, e.g. `http://localhost:7987`
5. proxy-host   : Your proxy server if needed
6. scope-audience : This is the scope audience which MUST match the primary audience in the IDCS resource, recommendation is not to have a trailing slash (/)

## Build and run

```bash
mvn package
java -jar target/helidon-examples-microprofile-security-idcs.jar
```

Try the endpoints:

| Endpoint            | Description                                                            |
|:--------------------|:-----------------------------------------------------------------------|
| `rest/login`        | Login                                                                  |
| `rest/scopes`       | Full security with scopes and roles (see IdcsResource.java)            |
| `rest/nima`         | Protected nima service (see application.yaml - security.web-server)    |
| `web/resource.html` | Protected static resource (see application.yaml - security.web-server) |
