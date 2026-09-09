<!--@frontmatter
description: "Helidon MicroProfile security"
-->
# Security

To add security, such as protecting resource methods with authentication, to a
MicroProfile application, add the Helidon security integration dependency to
your project.

## Maven Coordinates

To enable Security, add the following dependency to your project’s `pom.xml`
(see [Managing Dependencies](../../dependency-management.md)).

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.microprofile</groupId>
  <artifactId>helidon-microprofile-security</artifactId>
</dependency>
```

### Securing a JAX-RS Resource

For JAX-RS resources, declare security by adding annotations to a resource class
or method.

Protected resource method:

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

Jersey can obtain JAX-RS routing annotations from a resource interface or an
overridden superclass method, but Jakarta security annotations follow the
Jakarta Annotations inheritance rules independently. Security annotations on an
implemented interface do not contribute to a resource method, and annotations
on an overridden method are ignored. Security annotations on a JAX-RS
application class are not inherited as resource class or method access rules,
but Helidon supplies them to application-level annotation analyzers. An analyzer
response can therefore initialize security for each resource. For example, the
built-in role analyzer uses application-level `@RolesAllowed` to require
authentication without applying the listed roles as the resource method's
authorization rule. A concrete override uses security from its own class and
method; an unchanged inherited superclass method uses security from its
declaring class and method.

For Helidon security annotations and annotation analyzers that opt in through
`AnnotationAnalyzer.supportsExpandedEndpointAnalysis()`, Helidon extends the
ordinary resource class-and-method sequence when the JAX-RS resource definition
and concrete handler differ:

1. The selected resource definition type and the type that declares Jersey's
   selected definition method, followed by that method unless it is declared
   by the implementation. Related class levels are ordered from superclass to
   subclass.
2. The type that declares the actual handling method and the concrete handling
   class, followed by the handling method when it differs from the definition
   method or when the implementation-declared method is also reported as the
   definition method.

Selected types that do not own the member remain available to Helidon
annotations and custom analyzers. Unrelated interfaces, other intermediate
implementation types, and overridden declarations other than Jersey's selected
definition and handling methods do not participate. If the resource definition
and handler are the same class and method, Helidon retains the original single
class-and-method security level.

Java-inherited annotations on a concrete handler are applied before the selected
definition method. When a concrete handler does not own the inherited handling
method, its directly declared Jakarta security annotations and Helidon
`@Authenticated` and `@Authorized` annotations are excluded so they cannot
override the inherited member security. Other directly declared handler
annotations retain their later handling-class position, and the concrete
handling method remains last.

Each security annotation or provider applies its own merge rules to these
levels. Among `@Authenticated` or `@Authorized` annotations, a value later in
the sequence replaces an earlier value. `@Audited` on any level enables
auditing; for each audit metadata field, the first non-default value is retained
and a later value fills it only while it remains at its default. Metadata-based
annotation analyzers that opt in receive the levels in the same order and define
their own conflict behavior. Analyzers that do not opt in retain the application,
selected resource class, and selected resource method sequence. Deprecated
reflection-based analyzers receive complete reflective annotation views and
cannot observe metadata filtering.

Security in Helidon MicroProfile is built on top of Jersey’s and can be
enabled/disabled using the property `security.jersey.enabled=[true|false]`.

### Protecting Helidon endpoints

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

#### Configuring endpoint protection

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

If you need to use a properties file, such as `microprofile-config.properties`,
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
