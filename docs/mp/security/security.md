# Adding Security

To add security, such as protecting resource methods with authentication, to a MicroProfile application, add the Helidon security integration dependency to your project.

## Maven Coordinates

To enable Security, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

```xml
<dependency>
    <groupId>io.helidon.microprofile</groupId>
    <artifactId>helidon-microprofile-security</artifactId>
</dependency>
```

### Securing a JAX-RS Resource

For JAX-RS resources, declare security by adding annotations to a resource class or method.

*Protected resource method*

```java
@GET
@io.helidon.security.annotations.Authenticated
@io.helidon.security.annotations.Authorized
// you can also use io.helidon.security.abac.role.RoleValidator.Roles
@RolesAllowed("admin")
public String adminResource(@Context io.helidon.security.SecurityContext securityContext) {
    return "you are " + securityContext.userName();
}
```

Security in Helidon MicroProfile is built on top of Jersey’s and can be enabled/disabled using the property `security.jersey.enabled=[true|false]`.

### Protecting Helidon endpoints

There are several endpoints provided by Helidon services, such as:

- Health endpoint (`/health`)
- Metrics endpoint (`/metrics`)
- OpenAPI endpoint (`/openapi`)
- Configured static content (can use any path configured)

These endpoints are all implemented using Helidon WebServer and as such can be protected only through Security integration with WebServer.

The following section describes configuration of such protection using configuration files, in this case using a `yaml` file, as it provides a tree structure.

#### Configuring endpoint protection

The configuration is usually placed under `security.web-server` (this can be customized in Helidon SE).

The following shows an example we will explain in detail:

*application.yaml*

```yaml
security:
  providers:
    - abac: 
    - provider-key: 
  web-server:
    defaults:
      authenticate: true 
    paths:
      - path: "/metrics/*" 
        roles-allowed: "admin"
      - path: "/health/*" 
        roles-allowed: "monitor"
      - path: "/openapi/*" 
        abac:
          scopes: ["openapi"]
      - path: "/static/*" 
        roles-allowed: ["user", "monitor"]
```

- Attribute based access control provider that checks roles and scopes
- The provider(s) used in your application, such as `oidc`
- Default configuration for paths configured below in `paths` section
- Protection of `/metrics` and all nested paths with `admin` role required
- Protection of `/health` and all nested paths with `monitor` role required
- Protection of `/openapi` and all nested paths with `openapi` scope required
- Protection of static content configured on `/static` path with either `user` or `monitor` role required

If you need to use a properties file, such as `microprofile-config.properties`, you can convert the file by using index based numbers for arrays, such as:

*microprofile-config.properties*

```properties
security.providers.0.abac=
security.providers.1.provider-key.optional=false
security.web-server.defaults.authenticate=true
security.web-server.paths.0.path=/metrics/*
security.web-server.paths.0.roles-allowed=admin
security.web-server.paths.3.path=/static/*
security.web-server.paths.3.roles-allowed=user,monitor
```
