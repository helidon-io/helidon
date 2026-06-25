<!--@frontmatter
description: "Helidon Security providers"
-->
# Security Providers

## Implemented Security Providers

Helidon provides the following security providers for endpoint protection:

| Provider                                       | Type           | Outbound supported | Description                                                       |
|------------------------------------------------|----------------|--------------------|-------------------------------------------------------------------|
| [OIDC Provider][oidc-provider]                 | Authentication | ✅                  | Open ID Connect supporting JWT, Scopes, Groups and OIDC code flow |
| [HTTP Basic Authentication][http-basic-authe]  | Authentication | ✅                  | HTTP Basic Authentication support                                 |
| [HTTP Digest Authentication][http-digest-auth] | Authentication | 🚫                 | **Deprecated!** HTTP Digest Authentication support                |
| [Header Assertion][header-assertion]           | Authentication | ✅                  | Asserting a user based on a header value                          |
| [HTTP Signatures][http-signatures]             | Authentication | ✅                  | Protecting service to service communication through signatures    |
| [IDCS Roles][idcs-roles]                       | Role Mapping   | 🚫                 | Retrieves roles from IDCS provider for authenticated user         |
| [ABAC Authorization][abac-authorizati]         | Authorization  | 🚫                 | Attribute based access control authorization policies             |

The following providers are no longer evolved:

| Provider                     | Type           | Outbound supported | Description                                                               |
|------------------------------|----------------|--------------------|---------------------------------------------------------------------------|
| [Google Login][google-login] | Authentication | ✅                  | **Deprecated!** Authenticates a token from request against Google servers |
| [JWT Provider][jwt-provider] | Authentication | ✅                  | JWT tokens passed from frontend                                           |

## OIDC Provider

Open ID Connect security provider.

### Maven Coordinates

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.microprofile</groupId>
  <artifactId>helidon-microprofile-oidc</artifactId>
</dependency>
```

### Configuration options

<!--@include ../../config/io.helidon.security.providers.oidc.OidcProvider.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][io-helidon-secur].
<!--/include-->

### Configuration Example

```yaml [application.yaml]
security:
  providers:
  - oidc:
      client-id: "client-id-of-this-service"
      client-secret: "${CLEAR=changeit}"
      identity-uri: "https://your-tenant.identity-server.com"
      frontend-uri: "http://my-service:8080"
      audience: "http://my-service"
      outbound:
        - name: "internal-services"
          hosts: ["*.example.org"]
          outbound-token:
            header: "X-Internal-Auth"
```

### Example

See the [example][example] on GitHub.

### How does it work?

At Helidon startup, if OIDC provider is configured, the following will happen:

1.  `client-id`, `client-secret`, and `identityUri` are validated - these must
    provide values
2.  Unless all resources are configured as local resources, the provider
    attempts to contact the `oidc-metadata.resource` endpoint to retrieve all
    endpoints

At runtime, depending on configuration...

If a request comes without a token or with insufficient scopes:

1.  If `redirect` is set to `true` (default), request is redirected to the
    authorization endpoint of the identity server. If set to false, `401` is
    returned
2.  User authenticates against the identity server
3.  The identity server redirects back to Helidon service with a code
4.  Helidon service contacts the identity server’s token endpoint, to exchange
    the code for a JWT
5.  The JWT is stored in a cookie (if cookie support is enabled, which it is by
    default)
6.  Helidon service redirects to original endpoint (on itself)

Redirect attempts are counted to prevent infinite login redirects. By default,
Helidon stores the count in the `redirect-attempt-param` query parameter. Set
`redirect-attempt-counter-strategy` to `COOKIE` to store the counter in a small
cookie instead. Set it to `NONE` to disable redirect attempt counting and
`max-redirects` loop protection. The `redirect-attempt-param` value is used as
the cookie name prefix when the `COOKIE` strategy is used; the full cookie name
also includes a tenant and original URI hash.

Helidon obtains a token from request (from cookie, header, or query parameter):

1.  Token is parsed as a singed JWT
2.  We validate the JWT signature either against local JWK or against the
    identity server’s introspection endpoint depending on configuration
3.  We validate the issuer and audience of the token if it matches the
    configured values
4.  A subject is created from the JWT, including scopes from the token
5.  We validate that we have sufficient scopes to proceed, and return `403` if
    not
6.  Handling is returned to security to process other security providers

### Multi Tenancy

The OIDC provider also supports multi tenancy. To enable this feature, it is
required to do several steps.

1.  To enable the default multi-tenant support, add the `multi-tenant: true`
    option to the OIDC provider configuration
2.  Specify the desired way to provide the tenant name. This step is done over
    adding the `tenant-id-style` configuration option. For more information, see
    the table below
3.  Add the tenants section to the OIDC provider configuration

```yaml [application.yaml]
tenants:
   - name: "example-tenant"
     # ... tenant configuration options
```

There are four ways to provide the required tenant information to Helidon by
default.

Possible <code>tenant-id-style</code> configuration options:
<table class="tableblock frame-all grid-all stretch">
<thead>
<tr>
<th>key</th>
<th>description</th>
<th>additional config options</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>host-header</code></td>
<td>Tenant configuration will be selected based on your host present in the <code>Host</code> header value.</td>
<td> </td>
</tr>
<tr>
<td><code>domain</code></td>
<td>Similar to the <code>host-header</code> style, but now the tenant name is identified just as a part of the host name. By default, it selects the third domain level.</p>
<p>Example: Host header value from inbound request is <code>my.helidon.com</code> → domain level 3 is <code>my</code>, domain level 2 is <code>helidon</code> and domain level 1 is <code>com</code>.</td>
<td><code>tenant-id-domain-level: &lt;domain level&gt;</code></td>
</tr>
<tr>
<td><code>token-handler</code></td>
<td>The tenant name information is expected to be provided through the configured custom header value.</td>
<td><code>tenant-id-handler:
  header: &quot;my-custom-header&quot;</code></td>
</tr>
<tr>
<td><code>none</code></td>
<td>No tenant name finding is used. Default tenant name <code>@default</code> is used instead.</td>
<td></td>
</tr>
</tbody>
</table>

You can also implement a custom way of discovering the tenant name and tenant
configuration. The custom tenant name discovery from request can be done by
implementing SPI:

`io.helidon.security.providers.oidc.common.spi.TenantIdProvider`

and the custom tenant configuration discovery can be provided by implementing
SPI:

`io.helidon.security.providers.oidc.common.spi.TenantConfigProvider`

#### Configuration options

<!--@include ../../config/io.helidon.security.providers.oidc.common.TenantConfig.md#configuration-options delim=--- offset=3 collapseTables=10 -->
See [Configuration options][io-helidon-secur-2].
<!--/include-->

#### How does that work?

Multi-tenant support requires to obtain tenant name from the incoming request.
OIDC configuration is selected based on the received tenant name. The way this
tenant name has to be provided is configured via `tenant-id-style`
configuration. See [How to enable tenants](#multi-tenancy) for more information.
After matching tenant configuration with the received name, the rest of the OIDC
flow if exactly the same as in [How does OIDC work](#how-does-it-work).

Base OIDC configuration is treated as a default tenant, which is used, if no
tenant name is provided. This default tenant is having `@default` name
specified.

It is also important to note, that each tenant configuration is based on the
default tenant configuration (base OIDC configuration), and therefore its
configuration do not need to change all the properties, if they do not differ
from the base OIDC configuration.

### CORS Settings

CORS is (now) a single component configured either through config (key `cors`),
or programmatically via `io.helidon.webserver.cors.CorsFeature`. To add proper
CORS setup for the OIDC endpoint, use one of these. Component specific CORS
setup will be removed from Helidon.

## HTTP Basic Authentication Provider

HTTP Basic authentication support

### Maven Coordinates

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.security.providers</groupId>
  <artifactId>helidon-security-providers-http-auth</artifactId>
</dependency>
```

### Configuration options

<!--@include ../../config/io.helidon.security.providers.httpauth.HttpBasicAuthProvider.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][io-helidon-secur-3].
<!--/include-->

### Configuration Example

```yaml [application.yaml]
security:
  providers:
  - http-basic-auth:
      realm: "helidon"
      users:
      - login: "john"
        password: "${CLEAR=changeit}"
        roles: ["admin"]
      - login: "jack"
        password: "changeit"
        roles: ["user", "admin"]
      outbound:
        - name: "internal-services"
          hosts: ["*.example.org"]
          # Propagates current user's identity or identity from request property
          outbound-token:
            header: "X-Internal-Auth"
        - name: "partner-service"
          hosts: ["*.partner.org"]
          # Uses this username and password
          username: "partner-user-1"
          password: "${CLEAR=changeit}"
```

### Example

See the [example][example-2] on GitHub.

### How does it work?

See [RFC 7617][rfc7617].

**Authentication of request**

When a request is received without the `Authorization: basic ....` header, a
challenge is returned to provide such authentication.

When a request is received with the `Authorization: basic ....` header, the
username and password is validated against configured users (and users obtained
from custom service if any provided).

Subject is created based on the username and roles provided by the user store.

**Identity propagation**

When identity propagation is configured, there are several options for
identifying username and password to propagate:

1.  We propagate the current username and password (inbound request must be
    authenticated using basic authentication).
2.  We use username and password from an explicitly configured property (See
    `EndpointConfig.PROPERTY_OUTBOUND_ID` and
    `EndpointConfig.PROPERTY_OUTBOUND_SECRET`)
3.  We use username and password associated with an outbound target (see example
    configuration above)

Identity is propagated only if:

1.  There is an outbound target configured for the endpoint
2.  Or there is an explicitly configured username/password for the current
    request (through request property)

**Custom user store**

Java service loader service
`io.helidon.security.providers.httpauth.spi.UserStoreService` can be implemented
to provide users to the provider, such as when validated against an internal
database or LDAP server. The user store is defined so you never need the clear
text password of the user.

*Warning on security of HTTP Basic Authentication (or lack thereof)*

Basic authentication uses base64 encoded username and password and passes it
over the network. Base64 is only encoding, not encryption - so anybody that gets
hold of the header value can learn the actual username and password of the user.
This is a security risk and an attack vector that everybody should be aware of
before using HTTP Basic Authentication. We recommend using this approach only
for testing and demo purposes.

## HTTP Digest Authentication Provider

> [!WARNING]
> This provider is deprecated and will be removed in a future version of Helidon
> without replacement. It is kept in Helidon 4 for backward compatibility only,
> relies on obsolete MD5 hash, and should not be used in production.

### Maven Coordinates

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.security.providers</groupId>
  <artifactId>helidon-security-providers-http-auth</artifactId>
</dependency>
```

### Configuration options

<!--@include ../../config/io.helidon.security.providers.httpauth.HttpDigestAuthProvider.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][io-helidon-secur-4].
<!--/include-->

### Configuration Example

```yaml [application.yaml]
security:
  providers:
  - http-digest-auth:
      realm: "helidon"
      server-secret: "${CLEAR=service-wide-secret-not-known-outside}"
      users:
      - login: "john"
        password: "${CLEAR=changeit}"
        roles: ["admin"]
      - login: "jack"
        password: "changeit"
        roles: ["user", "admin"]
```

### How does it work?

See https://tools.ietf.org/html/rfc7616.

**Authentication of request**

When a request is received without the `Authorization: digest ....` header, a
challenge is returned to provide such authentication using `WWW-Authenticate`
header.

When a request is received with the `Authorization: digest ....` header, the
request is validated against configured users (and users obtained from custom
service if any provided).

Subject is created based on the username and roles provided by the user store.

**Custom user store**

Java service loader service
`io.helidon.security.providers.httpauth.spi.UserStoreService` can be implemented
to provide users to the provider, such as when validated against an internal
database or LDAP server. The user store is defined so you never need the clear
text password of the user.

*Note on security of HTTP Digest Authentication*

This authentication scheme is obsolete and should only be used for local testing
or short-lived compatibility work.

## Header Authentication Provider

Asserts user or service identity based on a value of a header.

### Maven Coordinates

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.security.providers</groupId>
  <artifactId>helidon-security-providers-header</artifactId>
</dependency>
```

### Configuration options

<!--@include ../../config/io.helidon.security.providers.header.HeaderAtnProvider.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][io-helidon-secur-5].
<!--/include-->

### Configuration Example

```yaml [application.yaml]
security:
  providers:
    header-atn:
      atn-token:
        header: "X-AUTH-USER"
      outbound:
        - name: "internal-services"
          hosts: ["*.example.org"]
          # propagates the current user or service id using the same header as authentication
        - name: "partner-service"
          hosts: ["*.partner.org"]
          # propagates an explicit username in a custom header
          username: "service-27"
          outbound-token:
            header: "X-Service-Auth"
```

### How does it work?

This provider inspects a specified request header and extracts the
username/service name from it and asserts it as current subject’s principal.

This can be used when we use perimeter authentication (e.g. there is a gateway
that takes care of authentication and propagates the user in a header).

**Identity propagation**

Identity is propagated only if an outbound target matches the target service.

The following options exist when propagating identity: 1. We propagate the
current username using the configured header 2. We use username associated with
an outbound target (see example configuration above)

**Caution**

When using this provider, you must be sure the header cannot be explicitly
configured by a user or another service. All requests should go through a
gateway that removes this header from inbound traffic, and only configures it
for authenticated users/services. Another option is to use this with fully
trusted parties (such as services within a single company, on a single protected
network not accessible to any users), and of course for testing and demo
purposes.

## HTTP Signatures Provider

Support for HTTP Signatures.

### Maven Coordinates

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.security.providers</groupId>
  <artifactId>helidon-security-providers-http-sign</artifactId>
</dependency>
```

### Configuration options

<!--@include ../../config/io.helidon.security.providers.httpsign.HttpSignProvider.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][io-helidon-secur-6].
<!--/include-->

### Configuration Example

<!--@mdc ::code-collapse -->
```yaml [application.yaml]
security:
  providers:
    - http-signatures:
        inbound:
          keys:
            - key-id: "service1-hmac"
              principal-name: "Service1 - HMAC signature"
              hmac.secret: "${CLEAR=changeit}"
            - key-id: "service1-rsa"
              principal-name: "Service1 - RSA signature"
              public-key:
                keystore:
                  resource.path: "src/main/resources/keystore.p12"
                  passphrase: "changeit"
                  cert.alias: "service_cert"
        outbound:
          - name: "service2-hmac"
            hosts: ["localhost"]
            paths: ["/service2"]
            signature:
              key-id: "service1-hmac"
              hmac.secret: "${CLEAR=changeit}"
          - name: "service2-rsa"
            hosts: ["localhost"]
            paths: ["/service2-rsa.*"]
            signature:
              key-id: "service1-rsa"
              private-key:
                keystore:
                  resource.path: "src/main/resources/keystore.p12"
                  passphrase: "changeit"
                  key.alias: "myPrivateKey"
```
<!--@mdc :: -->

### Example

See the [example][example-3] on GitHub.

### Signature basics

- standard: based on https://tools.ietf.org/html/draft-cavage-http-signatures-03
- key-id: an arbitrary string used to locate signature configuration - when a
  request is received the provider locates validation configuration based on
  this id (e.g. HMAC shared secret or RSA public key). Commonly used meanings
  are: key fingerprint (RSA); API Key

### How does it work?

**Inbound Signatures** We act as a server and another party is calling us with a
signed HTTP request. We validate the signature and assume identity of the
caller.

**Outbound Signatures** We act as a client and we sign our outgoing requests. If
there is a matching `outbound` target specified in configuration, its
configuration will be applied for signing the outgoing request, otherwise there
is no signature added

## IDCS Role Mapper

A role mapper to retrieve roles from Oracle IDCS.

### Maven Coordinates

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.security.providers</groupId>
  <artifactId>helidon-security-providers-idcs-mapper</artifactId>
</dependency>
```

### Single-tenant

#### Configuration options

<!--@include ../../config/io.helidon.security.providers.idcs.mapper.IdcsRoleMapperProvider.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][io-helidon-secur-7].
<!--/include-->

### Multi-tenant

#### Configuration options

<!--@include ../../config/io.helidon.security.providers.idcs.mapper.IdcsMtRoleMapperProvider.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][io-helidon-secur-8].
<!--/include-->

### Configuration Example

```yaml [application.yaml]
security:
  providers:
    - idcs-role-mapper:
        multitenant: false
        oidc-config:
            client-id: "client-id"
            client-secret: "changeit"
            identity-uri: "IDCS identity server address"
```
### Example

See the [example][example-4] on GitHub.

### How does it work?

The provider asks the IDCS server to provide list of roles for the currently
authenticated user. The result is cached for a certain period of time (see
`cache-config` above).

## ABAC Provider

Attribute based access control authorization provider.

### Maven Coordinates

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.security.providers</groupId>
  <artifactId>helidon-security-providers-abac</artifactId>
</dependency>
```

### Configuration options

<!--@include ../../config/io.helidon.security.providers.abac.AbacProvider.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][io-helidon-secur-9].
<!--/include-->

### Configuration Example

See the [example][example-5] on GitHub.

```yaml [application.yaml]
security:
  providers:
    - abac:
```

### Example

See the [example][example-5] on GitHub.

### How does it work?

ABAC uses available validators and validates them against attributes of the
authenticated user.

Combinations of `fail-on-unvalidated` and `fail-if-none-validated`:

1.  `true` & `true`: Will fail if any attribute is not validated and if any has
    failed validation
2.  `false` & `true`: Will fail if there is one or more attributes present and
    NONE of them is validated or if any has failed validation, Will NOT fail if
    there is at least one validated attribute and any number of not validated
    attributes (and NONE failed)
3.  `false` & `false`: Will fail if there is any attribute that failed
    validation, Will NOT fail if there are no failed validation or if there are
    NONE validated

Any attribute of the following objects can be used:

- environment (such as time of request) - e.g. env.time.year
- subject (user) - e.g. subject.principal.id
- subject (service) - e.g. service.principal.id
- object (must be explicitly invoked by developer in code, as object cannot be
  automatically added to security context) - e.g. object.owner

This provider checks that all defined ABAC validators are validated. If there is
a definition for a validator that is not checked, the request is denied
(depending on configuration as mentioned above).

ABAC provider also allows an object to be used in authorization process, such as
when evaluating if an object’s owner is the current user. The following example
uses the Expression language validator to demonstrate the point in a JAX-RS
resource:

Example of using an object

```java
@Authenticated
@Path("/abac")
public class AbacResource {
    @GET
    @Authorized(explicit = true)
    @PolicyStatement("${env.time.year >= 2017 && object.owner == subject.principal.id}")
    public Response process(@Context SecurityContext context) {
        // probably looked up from a database
        SomeResource res = new SomeResource("user");
        AuthorizationResponse atzResponse = context.authorize(res);

        if (atzResponse.isPermitted()) {
            //do the update
            return Response.ok().entity("fine, sir").build();
        } else {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(atzResponse.description().orElse("Access not granted"))
                    .build();
        }
    }
}
```

**The following validators are implemented:**

- [Roles](#role-validator)
- [Scopes](#scope-validator)
- [EL Policy][el-policy]

### Role Validator

Checks whether user/service is in either of the required role(s).

Configuration Key: `role-validator`

Annotations: `@RolesAllowed`, `@RoleValidator.Roles`

Configuration example for `WebServer`

```yaml [application.yaml]
security:
  web-server.paths:
    - path: "/user/*"
      roles-allowed: ["user"]
```

JAX-RS example

```java
@RolesAllowed("user")
@RoleValidator.Roles(value = "service_role", subjectType = SubjectType.SERVICE)
@Authenticated
@Path("/abac")
public class AbacResource {
}
```

**JAX-RS sub-resource locators**

When using sub-resource locators in JAX-RS, the roles allowed are collected from
each "level" of execution: - Application class annotations - Resource class
annotations + resource method annotations - Sub-resource class annotations +
sub-resource method annotations - Sub-resource class annotations + sub-resource
method annotations (for every sub-resource on the path)

The `RolesAllowed` or `Roles` annotation to be used is the last one in the path
as defined above.

*Example 1:* There is a `RolesAllowed("admin")` defined on a sub-resource
locator resource class. In this case the required role is `admin`.

*Example 2:* There is a `RolesAllowed("admin")` defined on a sub-resource
locator resource class and a `RolesAllowed("user")` defined on the method of the
sub-resource that provides the response. In this case the required role is
`user`.

### Scope Validator

Checks whether user has all the required scopes.

Configuration Key: `scope-validator`

Annotations: `@Scope`

Configuration example for `WebServer`

```yaml [application.yaml]
security:
  web-server.paths:
    - path: "/user/*"
      abac.scopes:
        ["calendar_read", "calendar_edit"]
```

JAX-RS example

```java
@Scope("calendar_read")
@Scope("calendar_edit")
@Authenticated
@Path("/abac")
public class AbacResource {
}
```

### Expression Language Policy Validator

Policy executor using Java EE policy expression language (EL)

Configuration Key: `policy-javax-el`

Annotations: `@PolicyStatement`

Example of a policy statement: `${env.time.year >= 2017}`

Configuration example for `WebServer`

```yaml [application.yaml]
security:
  web-server.paths:
    - path: "/user/*"
      policy:
        statement: "hasScopes('calendar_read','calendar_edit') AND timeOfDayBetween('8:15', '17:30')"
```

JAX-RS example

```java
@PolicyStatement("${env.time.year >= 2017}")
@Authenticated
@Path("/abac")
public class AbacResource {
}
```

Configuration example for `JAX-RS` over the configuration

```yaml [application.yaml]
server:
  features:
    security:
      endpoints:
        - path: "/somePath"
          config:
            abac.policy-validator.statement: "\\${env.time.year >= 2017}"
```

## Google Login Provider

Authenticates a token from request against Google identity provider

> [!WARNING]
> This provider is deprecated and will be removed in a future version of
> Helidon. Please use our OpenID Connect security provider instead.

### Maven Coordinates

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.security.providers</groupId>
  <artifactId>helidon-security-providers-google-login</artifactId>
</dependency>
```

### Configuration options

<!--@include ../../config/io.helidon.security.providers.google.login.GoogleTokenProvider.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][io-helidon-secur-10].
<!--/include-->

### Example

See the [example][example-6] on GitHub.

Configuration example

```yaml [application.yaml]
security:
  providers:
    - provider:
        client-id: "Google client id"
```

### How does it work?

We expect to receive a token (with sufficient scopes) from the inbound request,
such as when using the Google login button on a page. The page has access to the
token in JavaScript and can send it to backend with every request in a header
field (`Authorization` with `bearer ` prefix is assumed by default).

Once we receive the token in Helidon, we parse it and:

1.  Validate if it timed out locally
2.  Return a cached response (see `EvictableCache` with default values)
3.  Otherwise, verify using Google API - `GoogleIdTokenVerifier`

We build a subject from the Google token with the following attributes filled
(if in token):

- userId
- email
- name
- emailVerified
- locale
- family_name
- given_name
- picture (URL)

**Outbound security** The token will be propagated to outbound calls if an
outbound target exists that matches the invoked endpoint (see `outbound`
configuration above).

## JWT Provider

JWT token authentication and outbound security provider.

### Maven Coordinates

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.security.providers</groupId>
  <artifactId>helidon-security-providers-jwt</artifactId>
</dependency>
```

### Configuration options

<!--@include ../../config/io.helidon.security.providers.jwt.JwtProvider.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][io-helidon-secur-11].
<!--/include-->

### Configuration Example

```yaml [application.yaml]
security:
  providers:
    - provider:
        atn-token:
          jwk.resource.resource-path: "verifying-jwk.json"
          jwt-issuer: "http://trusted.issuer"
          jwt-audience: "http://my.service"
        sign-token:
          jwk.resource.resource-path: "signing-jwk.json"
          jwt-issuer: "http://my.server/identity"
          outbound:
          - name: "propagate-token"
            hosts: ["*.internal.org"]
          - name: "generate-token"
            hosts: ["1.partner-service"]
            jwk-kid: "partner-1"
            jwt-kid: "helidon"
            jwt-audience: "http://1.partner-service"
```

### Example

See the [example][example-2] on GitHub.

### How does it work?

JSON Web Token (JWT) provider has support for authentication and outbound
security.

Authentication is based on validating the token (signature, valid before etc.)
and on asserting the subject of the JWT subject claim.

For outbound, we support either token propagation (e.g. the token from request
is propagated further) or support for generating a brand new token based on
configuration of this provider.

[oidc-provider]: #oidc-provider
[http-basic-authe]: #http-basic-authentication-provider
[http-digest-auth]: #http-digest-authentication-provider
[header-assertion]: #header-authentication-provider
[http-signatures]: #http-signatures-provider
[idcs-roles]: #idcs-role-mapper
[abac-authorizati]: #abac-provider
[google-login]: #google-login-provider
[jwt-provider]: #jwt-provider
[example]: https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/security/idcs-login
[example-2]: https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/security/outbound-override
[example-3]: https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/security/webserver-signatures
[example-4]: https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/security/idcs-login/
[example-5]: https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/security/attribute-based-access-control
[el-policy]: #expression-language-policy-validator
[example-6]: https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/security/google-login
[io-helidon-secur]: ../../config/io.helidon.security.providers.oidc.OidcProvider.md#configuration-options
[io-helidon-secur-2]: ../../config/io.helidon.security.providers.oidc.common.TenantConfig.md#configuration-options
[io-helidon-secur-3]: ../../config/io.helidon.security.providers.httpauth.HttpBasicAuthProvider.md#configuration-options
[io-helidon-secur-4]: ../../config/io.helidon.security.providers.httpauth.HttpDigestAuthProvider.md#configuration-options
[io-helidon-secur-5]: ../../config/io.helidon.security.providers.header.HeaderAtnProvider.md#configuration-options
[io-helidon-secur-6]: ../../config/io.helidon.security.providers.httpsign.HttpSignProvider.md#configuration-options
[io-helidon-secur-7]: ../../config/io.helidon.security.providers.idcs.mapper.IdcsRoleMapperProvider.md#configuration-options
[io-helidon-secur-8]: ../../config/io.helidon.security.providers.idcs.mapper.IdcsMtRoleMapperProvider.md#configuration-options
[io-helidon-secur-9]: ../../config/io.helidon.security.providers.abac.AbacProvider.md#configuration-options
[io-helidon-secur-10]: ../../config/io.helidon.security.providers.google.login.GoogleTokenProvider.md#configuration-options
[io-helidon-secur-11]: ../../config/io.helidon.security.providers.jwt.JwtProvider.md#configuration-options
[rfc7617]: https://tools.ietf.org/html/rfc7617
