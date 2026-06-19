# WebServer Security

## Maven Coordinates

To integrate [web server](../webserver/webserver.md), add the following
dependency to your project’s pom.xml file:

Maven Dependency:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webserver</groupId>
  <artifactId>helidon-webserver-security</artifactId>
</dependency>
```

## Usage

There are two steps to configure security with WebServer:

1.  Create a security instance and register it with the server.
2.  Protect server routes with optional security features.

Example using builders:

<!--@mdc ::code-callout -->
```java
WebServer.builder()
        .addFeature(SecurityFeature.builder() // <1>
                            .security(security)
                            .defaults(SecurityFeature.authenticate())
                            .build())
        .routing(r -> r
                .get("/service1", SecurityFeature.rolesAllowed("user"), this::processService1Request)) // <2>
        .build();
```
1. Register the security feature in the web server, enforce authentication by
   default
2. Protect this route with authentication (from defaults) and role "user"
<!--@mdc :: -->

<!--@mdc ::code-callout -->
```java
WebServer.builder()
        // This is step 1 - register security instance with web server processing
        // security - instance of security either from config or from a builder
        // securityDefaults - default enforcement for each route that has a security definition
        .addFeature(SecurityFeature.create(builder -> builder.config(config))) // <1>
        .routing(r -> r
                .get("/service1", this::processService1Request)) // <2>
        .build();
```
1. Helper method to load both security and web server security from configuration
2. Security for this route is defined in the configuration
<!--@mdc :: -->

<!--@mdc ::code-callout -->
```yaml
security:
  web-server: # <1>
      defaults:
        # defaults for paths configured in the section below
        authenticate: true
      paths:
        - path: "/service1/*"
          methods: ["get"]
          roles-allowed: ["user"]
          # "authenticate: true" is implicit, as it is configured in defaults above
```
1. Configuration of integration with web server
<!--@mdc :: -->

Note: `defaults` section in configuration is related to paths on WebServer
configured below in `paths` section, it will not apply to any other path on the
webserver.

## Protecting Endpoints

There are several endpoints provided by Helidon services, such as:

- Health endpoint (`/health`)
- Metrics endpoint (`/metrics`)
- OpenAPI endpoint (`/openapi`)
- Configured static content (can use any path configured)

These endpoints are all implemented using Helidon WebServer and as such can be
protected only through Security integration with WebServer.

The following section describes configuration of such protection using
configuration files, in this case using a `yaml` file, as it provides a tree
structure.

### Configuring Endpoint Protection

The configuration is usually placed under `security.web-server` (this can be
customized in Helidon SE).

The following shows an example we will explain in detail:

<!--@mdc ::code-callout -->
```yaml [application.yaml]
security:
  providers:
    - abac: # <1>
    - provider-key: # <2>
  web-server:
    defaults:
      authenticate: true # <3>
    paths:
      - path: "/metrics/*" # <4>
        roles-allowed: "admin"
      - path: "/health/*" # <5>
        roles-allowed: "monitor"
      - path: "/openapi/*" # <6>
        abac:
          scopes: ["openapi"]
      - path: "/static/*" # <7>
        roles-allowed: ["user", "monitor"]
```
1. Attribute based access control provider that checks roles and scopes
2. The provider(s) used in your application, such as `oidc`
3. Default configuration for paths configured below in `paths` section
4. Protection of `/metrics` and all nested paths with `admin` role required
5. Protection of `/health` and all nested paths with `monitor` role required
6. Protection of `/openapi` and all nested paths with `openapi` scope required
7. Protection of static content configured on `/static` path with either `user`
   or `monitor` role required
<!--@mdc :: -->
you can convert the file by using index based numbers for arrays, such as:

```properties [microprofile-config.properties]
security.providers.0.abac=
security.providers.1.provider-key.optional=false
security.web-server.defaults.authenticate=true
security.web-server.paths.0.path=/metrics/*
security.web-server.paths.0.roles-allowed=admin
security.web-server.paths.3.path=/static/*
security.web-server.paths.3.roles-allowed=user,monitor
```

## Reference

- [Helidon WebServer Security Integration][helidon-webserve]

[helidon-webserve]: https://helidon.io/docs/v4/apidocs/io.helidon.webserver.security/module-summary.html
