<!--@frontmatter
description: "Helidon Security providers"
-->
# Security Providers

## Implemented Security Providers

Helidon provides the following security providers for endpoint protection:

| Provider                                      | Type           | Outbound supported | Description                                                       |
|-----------------------------------------------|----------------|--------------------|-------------------------------------------------------------------|
| [OIDC Provider][oidc-provider]                | Authentication | ✅                 | Open ID Connect supporting JWT, Scopes, Groups and OIDC code flow |
| [HTTP Basic Authentication][http-basic-authe] | Authentication | ✅                 | HTTP Basic authentication for local testing and demos             |
| [Header Assertion][header-assertion]          | Authentication | ✅                 | Asserting a user based on a header value                          |
| [HTTP Signatures][http-signatures]            | Authentication | ✅                 | Protecting service to service communication through signatures    |
| [ABAC Authorization][abac-authorizati]        | Authorization  | 🚫                 | Attribute based access control authorization policies             |

The following providers are no longer evolved:

| Provider                     | Type           | Outbound supported | Description                     |
|------------------------------|----------------|--------------------|---------------------------------|
| [JWT Provider][jwt-provider] | Authentication | ✅                 | JWT tokens passed from frontend |

## OIDC Provider

Open ID Connect security provider.

### Maven Coordinates

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.security.providers</groupId>
  <artifactId>helidon-security-providers-oidc</artifactId>
</dependency>
```

### Usage

In Helidon, we need to register the redirection support with routing (in
addition to `SecurityFeature` that integrates with `WebServer`). This is not
required when `redirect` is set to false.

Adding support for OIDC redirects

```java
WebServer.builder()
    .addFeature(SecurityFeature.builder()
        .config(config.get("security"))
        .build())
    .routing(r -> r.addFeature(OidcFeature.create(config)))
    .build();
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

#### Cookie Encryption Secret

Some OIDC cookies are encrypted by default. For production deployments,
configure `cookie-encryption-password` or `cookie-encryption-name` explicitly.
The same secret or named encryption configuration must be available to every
service instance that shares encrypted OIDC cookies.

If encrypted cookies are enabled and neither `cookie-encryption-password` nor
`cookie-encryption-name` is configured, Helidon uses `.helidon-oidc-secret` in
the current working directory as a local fallback secret file. When Helidon
generates that fallback file, it logs a warning. This fallback is intended for a
single service instance.

On POSIX file systems, Helidon creates the file with owner read/write
permissions only and accepts an existing fallback file only when it is a regular
file with owner-only read or read/write permissions. On non-POSIX file systems,
Helidon still rejects symlinks and non-regular existing files, and creates the
fallback file with an exclusive best-effort create operation.

For rolling upgrades from nodes that used the legacy password-based cookie
encryption defaults, set `legacy-cookie-encryption` to `true` while both old and
new nodes are running. This makes upgraded nodes keep writing cookies that older
nodes can decrypt.

After all nodes run the new version, set `legacy-cookie-encryption` to `false`
and set `legacy-cookie-fallback` to `true` for at least one cookie lifetime or
session grace period. After legacy cookies have expired, set both flags to
`false`. These flags are temporary compatibility controls for upgrades, not
steady-state security settings.

These flags only affect password-based OIDC cookie encryption; named Security
encryption configured with `cookie-encryption-name` uses its own encryption
configuration.

For a top-level `server-type=idcs`, the access-token and ID-token cookies are
GZIP-compressed by default when compression reduces their size. Compression is
disabled by default for other server types and can be enabled explicitly with
`cookie-compression-enabled` for the access token and
`cookie-compression-id-enabled` for the ID token. Compression is applied before
encryption when encryption is enabled, and uses a cookie-safe encoded form
otherwise. Password-based legacy cookie encryption disables compression while
`legacy-cookie-encryption` is `true`, even if the compression options are
enabled; named Security encryption does not have this override. During a rolling
upgrade from a version that does not understand compressed token cookies, set
both options to `false` on all nodes before upgrading. New nodes still read
compressed cookies while these settings are disabled, but write the older
uncompressed format. Re-enable compression after all nodes have been upgraded
and the rollback window has closed. Older nodes cannot read compressed cookies.
Rolling back after compression has been re-enabled therefore requires
invalidating affected sessions or waiting for the compressed cookies to expire
and users to authenticate again.

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
<table>
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

#### Available tenant config options

**Configuration options**

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

Base OIDC configuration is treated as a default tenant, which is used if no
tenant name is provided. This default tenant has the name `@default`. An
identified tenant name must match a configured tenant; unknown tenant names are
rejected by default. Set `fallback-to-default-tenant-enabled: true` only when
unknown tenant names should use the default tenant configuration.

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

HTTP Basic authentication support for local testing and demos.

> [!NOTE]
> HTTP Basic authentication is not supported for production use.

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

See https://tools.ietf.org/html/rfc7617.

**Authentication of request**

When a request is received without the `Authorization: basic ...` header, a
challenge is returned to provide such authentication.

When a request is received with the `Authorization: basic ...` header, the
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
before using HTTP Basic Authentication. HTTP Basic authentication is not
supported for production use. We recommend it only for local testing and demo
purposes.


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


#### Configuration Example

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

#### How does it work?

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

By default, inbound validation requires signed `date`, `(request-target)`, and
`host` fields. The `authorization` field must also be signed when it is present,
unless the signature itself is carried in the `Authorization` header. The signed
`Date` value must be within `PT5M` of the server time; configure
`inbound-date-validity` to another duration, or to `PT0S` to disable date
freshness validation. Date freshness rejects stale or far-future signatures; it
does not provide nonce-based replay detection within the accepted time window.
The `(request-target)` field uses the lower-case HTTP method followed by a
space, the request path, and the raw query string from the security environment,
when present. Query parameter order and encoding are significant.

Use `sign-headers` to require additional signed fields such as `digest`,
`content-length`, or `content-type` for selected methods.

If a request carries the signature in the `Authorization` header, that header
value cannot be combined with any other authorization scheme. Use the standalone
`Signature` header when another `Authorization` value must be sent with the same
request.

**Outbound Signatures** We act as a client and we sign our outgoing requests. If
there is a matching `outbound` target specified in configuration, its
configuration will be applied for signing the outgoing request, otherwise there
is no signature added

By default, outbound signing includes `date`, `(request-target)`, and `host`. It
also signs `authorization` when that field is present, unless the signature
itself is carried in the `Authorization` header. The provider adds `date` and
`host` when they are required but missing.


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

```yaml [application.yaml]
security:
  providers:
    - abac:
```

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
uses the Expression language validator to demonstrate the point in an endpoint:

Example of using an object

```java [AbacResource.java]
@Authenticated
@Http.Path("/abac")
public class AbacEndpoint {
    @Http.GET
    @Authorized(explicit = true)
    @PolicyStatement("${env.time.year >= 2017 && object.owner == subject.principal.id}")
    public String process(SecurityContext context) {
        // probably looked up from a database
        SomeResource res = new SomeResource("user");
        AuthorizationResponse atzResponse = context.authorize(res);

        if (atzResponse.isPermitted()) {
            return "fine, sir";
        }
        return atzResponse.description().orElse("Access not granted");
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

Annotation example

```java
@RolesAllowed("user")
@RoleValidator.Roles(value = "service_role", subjectType = SubjectType.SERVICE)
@Authenticated
@Http.Path("/abac")
public class AbacEndpoint {
}
```

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

Annotation example

```java
@Scope("calendar_read")
@Scope("calendar_edit")
@Authenticated
@Http.Path("/abac")
public class AbacEndpoint {
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

Annotation example

```java
@PolicyStatement("${env.time.year >= 2017}")
@Authenticated
@Http.Path("/abac")
public class AbacEndpoint {
}
```

Configuration example for endpoint security over configuration

```yaml [application.yaml]
server:
  features:
    security:
      endpoints:
        - path: "/somePath"
          config:
            abac.policy-validator.statement: "\\${env.time.year >= 2017}"
```


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
is propagated further) or support for generating a brand-new token based on
configuration of this provider.

## Reference

- [Helidon Security Examples][helidon-security]
- [Helidon OIDC Javadoc][helidon-oidc-jav]
- [Helidon HTTP Authentication Javadoc][helidon-http-aut]
- [Helidon Header Authentication Javadoc][helidon-header-a]
- [Helidon HTTP Signature Javadoc][helidon-http-sig]
- [Helidon ABAC Javadoc][helidon-abac-jav]
- [Helidon JWT Javadoc][helidon-jwt-java]

[oidc-provider]: #oidc-provider
[http-basic-authe]: #http-basic-authentication-provider
[header-assertion]: #header-authentication-provider
[http-signatures]: #http-signatures-provider
[abac-authorizati]: #abac-provider
[jwt-provider]: #jwt-provider
[example-2]: https://github.com/helidon-io/helidon-examples/tree/helidon-27.x/examples/security/outbound-override
[example-3]: https://github.com/helidon-io/helidon-examples/tree/helidon-27.x/examples/security/webserver-signatures
[el-policy]: #expression-language-policy-validator
[helidon-security]: https://github.com/helidon-io/helidon-examples/tree/helidon-27.x/examples/security
[helidon-oidc-jav]: https://helidon.io/docs/v27/apidocs/io.helidon.security.providers.oidc/module-summary.html
[helidon-http-aut]: https://helidon.io/docs/v27/apidocs/io.helidon.security.providers.httpauth/module-summary.html
[helidon-header-a]: https://helidon.io/docs/v27/apidocs/io.helidon.security.providers.header/module-summary.html
[helidon-http-sig]: https://helidon.io/docs/v27/apidocs/io.helidon.security.providers.httpsign/module-summary.html
[helidon-abac-jav]: https://helidon.io/docs/v27/apidocs/io.helidon.security.providers.abac/module-summary.html
[helidon-jwt-java]: https://helidon.io/docs/v27/apidocs/io.helidon.security.providers.jwt/module-summary.html
[io-helidon-secur]: ../../config/io.helidon.security.providers.oidc.OidcProvider.md#configuration-options
[io-helidon-secur-2]: ../../config/io.helidon.security.providers.oidc.common.TenantConfig.md#configuration-options
[io-helidon-secur-3]: ../../config/io.helidon.security.providers.httpauth.HttpBasicAuthProvider.md#configuration-options
[io-helidon-secur-5]: ../../config/io.helidon.security.providers.header.HeaderAtnProvider.md#configuration-options
[io-helidon-secur-6]: ../../config/io.helidon.security.providers.httpsign.HttpSignProvider.md#configuration-options
[io-helidon-secur-9]: ../../config/io.helidon.security.providers.abac.AbacProvider.md#configuration-options
[io-helidon-secur-11]: ../../config/io.helidon.security.providers.jwt.JwtProvider.md#configuration-options
