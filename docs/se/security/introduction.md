# Security Introduction

## Overview

Helidon Security provides authentication, authorization, and auditing for your Helidon application. It includes the following features:

1.  Authentication - support for authenticating incoming requests, creating a security Subject with Principal and Grants. Principal represents current user/service. Grant may represent a Role, Scope etc. Responsibility to create Principals and Grants lies with AuthenticationProvider SPI. The following Principals are expected and supported by default:
    1.  UserPrincipal - the party is an end-user (e.g. a person) - there can be zero to one user principals in a subject
    2.  ServicePrincipal - the party is a service (e.g. a computer program) - there can be zero to one service principals in a subject
2.  Authorization - support for authorizing incoming requests. Out-of-the-box the security module supports ABAC and RBAC (Attribute based access control and Role based access control). RBAC is handled through RolesAllowed annotation (for integrations that support injection).
3.  Outbound security - support for propagating identity or (in general) securing outbound requests. Modification of a request to include outbound security is responsibility of OutboundSecurityProvider SPI
4.  Audit - security module audits most important events through its own API (e.g. Authentication events, Authorization events, outbound security events). A default AuditProvider is provided as well, logging to Java util logging (JUL) logger called "AUDIT" (may be overridden through configuration). AuditProvider SPI may be implemented to support other auditing options.

Each feature is implemented with the help of "[Security Providers](providers.md)".

Security module is quite HTTP centric (as most common use cases are related to HTTP REST), though it is not HTTP specific (the security module may be used to secure even other transports, such as JMS, Kafka messages etc. if an appropriate integration module is developed, as all APIs can be mapped to a non-HTTP protocol). Nevertheless, there may be security providers that only make sense with HTTP (such as HTTP digest authentication).

## Maven Coordinates

To enable Security, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

``` xml
<dependency>
    <groupId>io.helidon.security</groupId>
    <artifactId>helidon-security</artifactId>
</dependency>
```

## Usage

To integrate with a container, or to use Security standalone, we must create an instance of security. In general, Security supports three approaches

- a fluent-API builder pattern - you configure everything "by hand"
- a configuration based pattern - you configure everything in a configuration file
- hybrid - you load a builder from configuration and update it in a program

Once a security instance is built, it can be used to initialize an [integration with a container](containers-integration.md), or to use security from a program directly:

*Security direct usage*

``` java
SecurityContext context = security.contextBuilder(UUID.randomUUID().toString()) 
        .env(SecurityEnvironment.builder()
                     .method("get")
                     .path("/test")
                     .transport("http")
                     .header("Authorization", "Bearer abcdefgh")
                     .build())
        .build();

AuthenticationResponse response = context.atnClientBuilder().submit(); 
if (response.status().isSuccess()) {
    System.out.println(response.user());
    System.out.println(response.service());
} else {
    System.out.println("Authentication failed: " + response.description());
}
```

- Create a security context
- Use the context to authenticate a request

### Builder Pattern

*Security through a builder*

``` java
Security security = Security.builder()
        .addProvider(HttpBasicAuthProvider.builder()) 
        .build();
```

- Create a provider instance based on the provider documentation

### Configuration Pattern

See [Secure config](tools.md) for details about encrypting passwords in configuration files.

*Security from configuration*

``` java
Security security = Security.create(config); 
```

- Uses `io.helidon.Config`

As mentioned above, security features are implemented through providers, which are configured under key `security.providers`. Each element of the list is one security provider. The key of the provider must match its config key (as documented in [Security Providers](providers.md) for each supported provider).

A key `enabled` can be used for each provider to provide fine control of which providers are enabled/disabled, for example to support different setup in testing and in production environments.

*Security from configuration - application.yaml*

``` yaml
# Uses config encryption filter to encrypt passwords
security:
  providers:
  - abac:
  - http-basic-auth:
      realm: "helidon"
      users:
      - login: "jack"
        password: "${CLEAR=password}"
        roles: ["user", "admin"]
      - login: "jill"
        password: "${CLEAR=password}"
        roles: ["user"]
```

#### Overriding Configuration

When a configuration needs to be overridden, we may have problems with the list type of the `providers` configuration. To simplify overrides using properties, you can explicitly set up a type of provider using a `type` key.

Example:

``` properties
security.providers.1.type=header-atn
security.providers.1.header-atn.authenticate=false
```

Would explicitly override the second provider (`http-basic-auth` in example above) with `header-atn` provider. Note that the `type` and the key of the provider must match.

### Hybrid Pattern (Builder with Configuration)

*Security from configuration and builder*

``` java
Security security1 = Security.builder(config) 
        .addProvider(HttpBasicAuthProvider.builder())
        .build();

Security security2 = Security.builder() 
        .addProvider(HttpBasicAuthProvider.builder())
        .config(config)
        .build();
```

- Uses io.helidon.Config
- Or reverse order
