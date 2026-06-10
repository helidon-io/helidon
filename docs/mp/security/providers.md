# Security Providers

## Implemented Security Providers

Helidon provides the following security providers for endpoint protection:

| Provider | Type | Outbound supported | Description |
|----|----|----|----|
| [OIDC Provider][oidc-provider] | Authentication | ✅ | Open ID Connect supporting JWT, Scopes, Groups and OIDC code flow |
| [HTTP Basic Authentication][http-basic-authe] | Authentication | ✅ | HTTP Basic Authentication support |
| [HTTP Digest Authentication][http-digest-auth] | Authentication | 🚫 | **Deprecated!** HTTP Digest Authentication support |
| [Header Assertion][header-assertion] | Authentication | ✅ | Asserting a user based on a header value |
| [HTTP Signatures][http-signatures] | Authentication | ✅ | Protecting service to service communication through signatures |
| [IDCS Roles][idcs-roles] | Role Mapping | 🚫 | Retrieves roles from IDCS provider for authenticated user |
| [ABAC Authorization][abac-authorizati] | Authorization | 🚫 | Attribute based access control authorization policies |

The following providers are no longer evolved:

| Provider | Type | Outbound supported | Description |
|----|----|----|----|
| [Google Login][google-login] | Authentication | ✅ | **Deprecated!** Authenticates a token from request against Google servers |
| [JWT Provider][jwt-provider] | Authentication | ✅ | JWT tokens passed from frontend |

### OIDC Provider

Open ID Connect security provider.

#### Setup

Maven dependency

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.microprofile</groupId>
  <artifactId>helidon-microprofile-oidc</artifactId>
</dependency>
```

#### Configuration options

<!--@include ../../config/io.helidon.security.providers.oidc.OidcProvider.md#configuration-options offset=2 -->
<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>force-https-redirects</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Force HTTPS for redirects to identity provider</td>
</tr>
<tr>
<td>
<a id="cors"></a>
<a href="io.helidon.cors.CrossOriginConfig.md">
<code>cors</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="CrossOriginConfig">CrossOriginConfig</code>
</td>
<td class="cm-default-cell">
</td>
<td>Assign cross-origin resource sharing settings</td>
</tr>
<tr>
<td>
<code>cookie-encryption-refresh-enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to encrypt refresh token cookie created by this microservice</td>
</tr>
<tr>
<td>
<code>query-id-token-param-name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">id_token</code>
</td>
<td>Name of a query parameter that contains the JWT id token when parameter is used</td>
</tr>
<tr>
<td>
<code>header-use</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to expect JWT in a header field</td>
</tr>
<tr>
<td>
<a id="header-token"></a>
<a href="io.helidon.security.util.TokenHandler.md">
<code>header-token</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="TokenHandler">TokenHandler</code>
</td>
<td class="cm-default-cell">
</td>
<td>A <code>TokenHandler</code> to process header containing a JWT</td>
</tr>
<tr>
<td>
<code>cookie-name-state</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value" title="JSESSIONID_3">JSESSIONID_3</code>
</td>
<td>The name of the cookie to use for the state storage</td>
</tr>
<tr>
<td>
<a id="outbound"></a>
<a href="io.helidon.security.providers.common.OutboundTarget.md">
<code>outbound</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;OutboundTarget&gt;">List&lt;OutboundTarget&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Add a new target configuration</td>
</tr>
<tr>
<td>
<code>propagate</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether to propagate identity</td>
</tr>
<tr>
<td>
<a id="client-credentials-config"></a>
<a href="io.helidon.security.providers.oidc.common.ClientCredentialsConfig.md">
<code>client-credentials-config</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="ClientCredentialsConfig">ClientCredentialsConfig</code>
</td>
<td class="cm-default-cell">
</td>
<td>Set the configuration related to the client credentials flow</td>
</tr>
<tr>
<td>
<code>cookie-name-refresh-token</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value" title="JSESSIONID_3">JSESSIONID_3</code>
</td>
<td>The name of the cookie to use for the refresh token</td>
</tr>
<tr>
<td>
<code>query-param-tenant-name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">h_tenant</code>
</td>
<td>Name of a query parameter that contains the tenant name when the parameter is used</td>
</tr>
<tr>
<td>
<code>query-param-name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value" title="accessToken">accessToken</code>
</td>
<td>Name of a query parameter that contains the JWT access token when parameter is used</td>
</tr>
<tr>
<td>
<a id="pkce-challenge-method"></a>
<a href="io.helidon.security.providers.oidc.common.PkceChallengeMethod.md">
<code>pkce-challenge-method</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="PkceChallengeMethod">PkceChallengeMethod</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">S256</code>
</td>
<td>Proof Key Code Exchange (PKCE) challenge creation method</td>
</tr>
<tr>
<td>
<code>optional</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether authentication is required</td>
</tr>
<tr>
<td>
<code>cookie-domain</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Domain the cookie is valid for</td>
</tr>
<tr>
<td>
<code>frontend-uri</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Full URI of this application that is visible from user browser</td>
</tr>
<tr>
<td>
<a id="cookie-same-site"></a>
<a href="io.helidon.http.SetCookie.SameSite.md">
<code>cookie-same-site</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">SameSite</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">LAX</code>
</td>
<td>When using cookie, used to set the SameSite cookie value</td>
</tr>
<tr>
<td>
<code>cookie-encryption-id-enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to encrypt id token cookie created by this microservice</td>
</tr>
<tr>
<td>
<a id="webclient"></a>
<a href="io.helidon.webclient.api.WebClient.md">
<code>webclient</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">WebClient</code>
</td>
<td class="cm-default-cell">
</td>
<td>WebClient configuration used for outbound requests to the identity server. This configuration sets the values to the OIDC WebClient default configuration</td>
</tr>
<tr>
<td>
<code>cookie-http-only</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>When using cookie, if set to true, the HttpOnly attribute will be configured</td>
</tr>
<tr>
<td>
<code>cookie-encryption-enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether to encrypt token cookie created by this microservice</td>
</tr>
<tr>
<td>
<code>pkce-enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether this provider should support PKCE</td>
</tr>
<tr>
<td>
<code>proxy-port</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">80</code>
</td>
<td>Proxy port</td>
</tr>
<tr>
<td>
<code>cookie-encryption-tenant-enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to encrypt tenant name cookie created by this microservice</td>
</tr>
<tr>
<td>
<code>use-jwt-groups</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Claim <code>groups</code> from JWT will be used to automatically add  groups to current subject (may be used with <code>jakarta.annotation.security.RolesAllowed</code> annotation)</td>
</tr>
<tr>
<td>
<code>token-signature-validation</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether access token signature check should be enabled</td>
</tr>
<tr>
<td>
<code>cookie-name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">JSESSIONID</code>
</td>
<td>Name of the cookie to use</td>
</tr>
<tr>
<td>
<code>cookie-use</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to use cookie to store JWT between requests</td>
</tr>
<tr>
<td>
<a id="outbound-type"></a>
<a href="io.helidon.security.providers.oidc.common.OidcOutboundType.md">
<code>outbound-type</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="OidcOutboundType">OidcOutboundType</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">USER_JWT</code>
</td>
<td>Type of the OIDC outbound</td>
</tr>
<tr>
<td>
<code>redirect</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>By default, the client should redirect to the identity server for the user to log in</td>
</tr>
<tr>
<td>
<code>redirect-uri</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value" title="/oidc/redirect">/oidc/redirect</code>
</td>
<td>URI to register web server component on, used by the OIDC server to redirect authorization requests to after a user logs in or approves scopes</td>
</tr>
<tr>
<td>
<code>cookie-name-id-token</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value" title="JSESSIONID_2">JSESSIONID_2</code>
</td>
<td>Name of the cookie to use for id token</td>
</tr>
<tr>
<td>
<a id="tenants"></a>
<a href="io.helidon.security.providers.oidc.common.TenantConfig.md">
<code>tenants</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="TenantConfig">TenantConfig</code>
</td>
<td class="cm-default-cell">
</td>
<td>Configurations of the tenants</td>
</tr>
<tr>
<td>
<code>cookie-max-age-seconds</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td class="cm-default-cell">
</td>
<td>When using cookie, used to set MaxAge attribute of the cookie, defining how long the cookie is valid</td>
</tr>
<tr>
<td>
<code>cookie-encryption-password</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Master password for encryption/decryption of cookies</td>
</tr>
<tr>
<td>
<code>cookie-encryption-state-enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to encrypt state cookie created by this microservice</td>
</tr>
<tr>
<td>
<code>cookie-path</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">/</code>
</td>
<td>Path the cookie is valid for</td>
</tr>
<tr>
<td>
<code>query-param-use</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether to use a query parameter to send JWT token from application to this server</td>
</tr>
<tr>
<td>
<code>cookie-name-tenant</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value" title="HELIDON_TENANT">HELIDON_TENANT</code>
</td>
<td>The name of the cookie to use for the tenant name</td>
</tr>
<tr>
<td>
<code>cookie-secure</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>When using cookie, if set to true, the Secure attribute will be configured</td>
</tr>
<tr>
<td>
<code>cookie-encryption-name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Name of the encryption configuration available through <code>Security#encrypt(String, byte[])</code> and <code>Security#decrypt(String, String)</code></td>
</tr>
<tr>
<td>
<code>id-token-signature-validation</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether id token signature check should be enabled</td>
</tr>
<tr>
<td>
<code>max-redirects</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">5</code>
</td>
<td>Configure maximal number of redirects when redirecting to an OIDC provider within a single authentication attempt</td>
</tr>
<tr>
<td>
<code>access-token-ip-check</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to check if current IP address matches the one access token was issued for</td>
</tr>
<tr>
<td>
<code>redirect-attempt-param</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">h_ra</code>
</td>
<td>Configure the parameter used to store the number of attempts in redirect</td>
</tr>
</tbody>
</table>


### Deprecated Options


<table class="cm-table">
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>relative-uris</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Can be set to <code>true</code> to force the use of relative URIs in all requests, regardless of the presence or absence of proxies or no-proxy lists</td>
</tr>
<tr>
<td>
<code>proxy-host</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Proxy host to use</td>
</tr>
<tr>
<td>
<code>proxy-protocol</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">http</code>
</td>
<td>Proxy protocol to use when proxy is used</td>
</tr>
</tbody>
</table>
<!--/include-->


### Example code

See the [example][example] on GitHub.

Configuration example

```yaml
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

1.  `client-id`, `client-secret`, and `identityUri` are validated - these must provide values
2.  Unless all resources are configured as local resources, the provider attempts to contact the `oidc-metadata.resource` endpoint to retrieve all endpoints

At runtime, depending on configuration…​

If a request comes without a token or with insufficient scopes:

1.  If `redirect` is set to `true` (default), request is redirected to the authorization endpoint of the identity server. If set to false, `401` is returned
2.  User authenticates against the identity server
3.  The identity server redirects back to Helidon service with a code
4.  Helidon service contacts the identity server’s token endpoint, to exchange the code for a JWT
5.  The JWT is stored in a cookie (if cookie support is enabled, which it is by default)
6.  Helidon service redirects to original endpoint (on itself)

Redirect attempts are counted to prevent infinite login redirects. By default, Helidon stores the count in the `redirect-attempt-param` query parameter. Set `redirect-attempt-counter-strategy` to `COOKIE` to store the counter in a small cookie instead. Set it to `NONE` to disable redirect attempt counting and `max-redirects` loop protection. The `redirect-attempt-param` value is used as the cookie name prefix when the `COOKIE` strategy is used; the full cookie name also includes a tenant and original URI hash.

Helidon obtains a token from request (from cookie, header, or query parameter):

1.  Token is parsed as a singed JWT
2.  We validate the JWT signature either against local JWK or against the identity server’s introspection endpoint depending on configuration
3.  We validate the issuer and audience of the token if it matches the configured values
4.  A subject is created from the JWT, including scopes from the token
5.  We validate that we have sufficient scopes to proceed, and return `403` if not
6.  Handling is returned to security to process other security providers

### Multiple tenants

The OIDC provider also supports multiple tenants. To enable this feature, it is required to do several steps.

1.  To enable the default multi-tenant support, add the `multi-tenant: true` option to the OIDC provider configuration
2.  Specify the desired way to provide the tenant name. This step is done over adding the `tenant-id-style` configuration option. For more information, see the table below
3.  Add the tenants section to the OIDC provider configuration

```yaml
tenants:
   - name: "example-tenant"
     # ... tenant configuration options
```

There are four ways to provide the required tenant information to Helidon by default.

<table class="tableblock frame-all grid-all stretch">
<caption>Table 1. Possible <code>tenant-id-style</code> configuration options</caption>
<colgroup>
<col style="width: 22%" />
<col style="width: 44%" />
<col style="width: 33%" />
</colgroup>
<thead>
<tr>
<th class="tableblock halign-left valign-top">key</th>
<th class="tableblock halign-left valign-top">description</th>
<th class="tableblock halign-left valign-top">additional config options</th>
</tr>
</thead>
<tbody>
<tr>
<td class="tableblock halign-left valign-top"><p><code>host-header</code></p></td>
<td class="tableblock halign-left valign-top"><p>Tenant configuration will be selected based on your host present in the <code>Host</code> header value.</p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><code>domain</code></p></td>
<td class="tableblock halign-left valign-top"><p>Similar to the <code>host-header</code> style, but now the tenant name is identified just as a part of the host name. By default, it selects the third domain level.</p>
<p>Example: Host header value from inbound request is <code>my.helidon.com</code> → domain level 3 is <code>my</code>, domain level 2 is <code>helidon</code> and domain level 1 is <code>com</code>.</p></td>
<td class="tableblock halign-left valign-top"><pre class="highlight"><code>tenant-id-domain-level: &lt;domain level&gt;</code></pre></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><code>token-handler</code></p></td>
<td class="tableblock halign-left valign-top"><p>The tenant name information is expected to be provided through the configured custom header value.</p></td>
<td class="tableblock halign-left valign-top"><pre class="highlight"><code>tenant-id-handler:
  header: &quot;my-custom-header&quot;</code></pre></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><code>none</code></p></td>
<td class="tableblock halign-left valign-top"><p>No tenant name finding is used. Default tenant name <code>@default</code> is used instead.</p></td>
<td class="tableblock halign-left valign-top"></td>
</tr>
</tbody>
</table>

You can also implement a custom way of discovering the tenant name and tenant configuration. The custom tenant name discovery from request can be done by implementing SPI:

`io.helidon.security.providers.oidc.common.spi.TenantIdProvider`

and the custom tenant configuration discovery can be provided by implementing SPI:

`io.helidon.security.providers.oidc.common.spi.TenantConfigProvider`

#### Available tenant config options

##### Configuration options

<!--@include ../../config/io.helidon.security.providers.oidc.common.TenantConfig.md#configuration-options offset=3 -->
<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>audience</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Audience of issued tokens</td>
</tr>
<tr>
<td>
<code>authorization-endpoint-uri</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">URI</code>
</td>
<td class="cm-default-cell">
</td>
<td>URI of an authorization endpoint used to redirect users to for logging-in</td>
</tr>
<tr>
<td>
<code>base-scopes</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">openid</code>
</td>
<td>Configure base scopes</td>
</tr>
<tr>
<td>
<code>check-audience</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Configure audience claim check</td>
</tr>
<tr>
<td>
<code>client-id</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Client ID as generated by OIDC server</td>
</tr>
<tr>
<td>
<code>client-secret</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Client secret as generated by OIDC server</td>
</tr>
<tr>
<td>
<code>client-timeout-millis</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">30000</code>
</td>
<td>Timeout of calls using web client</td>
</tr>
<tr>
<td>
<a id="decryption-keys"></a>
<a href="io.helidon.security.providers.idcsRoleMapper.oidcConfig.tenants.DecryptionKeysConfig.md">
<code>decryption-keys</code>
</a>
</td>
<td class="cm-type-cell">
</td>
<td class="cm-default-cell">
</td>
<td>Configuration for decryption-keys</td>
</tr>
<tr>
<td>
<code>identity-uri</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">URI</code>
</td>
<td class="cm-default-cell">
</td>
<td>URI of the identity server, base used to retrieve OIDC metadata</td>
</tr>
<tr>
<td>
<code>introspect-endpoint-uri</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">URI</code>
</td>
<td class="cm-default-cell">
</td>
<td>Endpoint to use to validate JWT</td>
</tr>
<tr>
<td>
<code>issuer</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Issuer of issued tokens</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Name of the tenant</td>
</tr>
<tr>
<td>
<a id="oidc-metadata"></a>
<a href="io.helidon.security.providers.idcsRoleMapper.oidcConfig.tenants.OidcMetadataConfig.md">
<code>oidc-metadata</code>
</a>
</td>
<td class="cm-type-cell">
</td>
<td class="cm-default-cell">
</td>
<td>Configuration for oidc-metadata</td>
</tr>
<tr>
<td>
<code>oidc-metadata-well-known</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>If set to true, metadata will be loaded from default (well known) location, unless it is explicitly defined using oidc-metadata-resource</td>
</tr>
<tr>
<td>
<code>optional-audience</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Allow audience claim to be optional</td>
</tr>
<tr>
<td>
<code>scope-audience</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Audience of the scope required by this application</td>
</tr>
<tr>
<td>
<code>server-type</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">@default</code>
</td>
<td>Configure one of the supported types of identity servers</td>
</tr>
<tr>
<td>
<a id="sign-jwk"></a>
<a href="io.helidon.security.providers.idcsRoleMapper.oidcConfig.tenants.SignJwkConfig.md">
<code>sign-jwk</code>
</a>
</td>
<td class="cm-type-cell">
</td>
<td class="cm-default-cell">
</td>
<td>Configuration for sign-jwk</td>
</tr>
<tr>
<td>
<a id="token-endpoint-auth"></a>
<a href="io.helidon.security.providers.oidc.common.OidcConfig.ClientAuthentication.md">
<code>token-endpoint-auth</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="ClientAuthentication">ClientAuthentication</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value" title="CLIENT_SECRET_BASIC">CLIENT_SECRET_BASIC</code>
</td>
<td>Type of authentication to use when invoking the token endpoint</td>
</tr>
<tr>
<td>
<code>token-endpoint-uri</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">URI</code>
</td>
<td class="cm-default-cell">
</td>
<td>URI of a token endpoint used to obtain a JWT based on the authentication code</td>
</tr>
<tr>
<td>
<code>validate-jwt-with-jwk</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Use JWK (a set of keys to validate signatures of JWT) to validate tokens</td>
</tr>
</tbody>
</table>
<!--/include-->


#### How does that work?

Multi-tenant support requires to obtain tenant name from the incoming request. OIDC configuration is selected based on the received tenant name. The way this tenant name has to be provided is configured via `tenant-id-style` configuration. See [How to enable tenants](#multiple-tenants) for more information. After matching tenant configuration with the received name, the rest of the OIDC flow if exactly the same as in [How does OIDC work](#how-does-it-work).

Base OIDC configuration is treated as a default tenant, which is used, if no tenant name is provided. This default tenant is having `@default` name specified.

It is also important to note, that each tenant configuration is based on the default tenant configuration (base OIDC configuration), and therefore its configuration do not need to change all the properties, if they do not differ from the base OIDC configuration.

## CORS Settings

CORS is (now) a single component configured either through config (key `cors`), or programmatically via `io.helidon.webserver.cors.CorsFeature`. To add proper CORS setup for the OIDC endpoint, use one of these. Component specific CORS setup will be removed from Helidon.

### HTTP Basic Authentication Provider

HTTP Basic authentication support

#### Setup

Maven dependency

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.security.providers</groupId>
  <artifactId>helidon-security-providers-http-auth</artifactId>
</dependency>
```

#### Overview

#### Configuration options

<!--@include ../../config/io.helidon.security.providers.httpauth.HttpBasicAuthProvider.md#configuration-options offset=2 -->
<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<a id="outbound"></a>
<a href="io.helidon.security.providers.common.OutboundTarget.md">
<code>outbound</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;OutboundTarget&gt;">List&lt;OutboundTarget&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Add a new outbound target to configure identity propagation or explicit username/password</td>
</tr>
<tr>
<td>
<code>optional</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether authentication is required</td>
</tr>
<tr>
<td>
<code>realm</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">helidon</code>
</td>
<td>Set the realm to use when challenging users</td>
</tr>
<tr>
<td>
<a id="principal-type"></a>
<a href="io.helidon.security.SubjectType.md">
<code>principal-type</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="SubjectType">SubjectType</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">USER</code>
</td>
<td>Principal type this provider extracts (and also propagates)</td>
</tr>
<tr>
<td>
<a id="users"></a>
<a href="io.helidon.security.providers.httpauth.ConfigUserStore.ConfigUser.md">
<code>users</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;ConfigUser&gt;">List&lt;ConfigUser&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Set user store to validate users</td>
</tr>
</tbody>
</table>
<!--/include-->


#### Example code

See the [example][example-2] on GitHub.

Configuration example

```yaml
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

#### How does it work?

See <a href="https://tools.ietf.org/html/rfc7617" class="bare">https://tools.ietf.org/html/rfc7617</a>.

**Authentication of request**

When a request is received without the `Authorization: basic …​.` header, a challenge is returned to provide such authentication.

When a request is received with the `Authorization: basic …​.` header, the username and password is validated against configured users (and users obtained from custom service if any provided).

Subject is created based on the username and roles provided by the user store.

**Identity propagation**

When identity propagation is configured, there are several options for identifying username and password to propagate:

1.  We propagate the current username and password (inbound request must be authenticated using basic authentication).
2.  We use username and password from an explicitly configured property (See `EndpointConfig.PROPERTY_OUTBOUND_ID` and `EndpointConfig.PROPERTY_OUTBOUND_SECRET`)
3.  We use username and password associated with an outbound target (see example configuration above)

Identity is propagated only if:

1.  There is an outbound target configured for the endpoint
2.  Or there is an explicitly configured username/password for the current request (through request property)

**Custom user store**

Java service loader service `io.helidon.security.providers.httpauth.spi.UserStoreService` can be implemented to provide users to the provider, such as when validated against an internal database or LDAP server. The user store is defined so you never need the clear text password of the user.

*Warning on security of HTTP Basic Authentication (or lack thereof)*

Basic authentication uses base64 encoded username and password and passes it over the network. Base64 is only encoding, not encryption - so anybody that gets hold of the header value can learn the actual username and password of the user. This is a security risk and an attack vector that everybody should be aware of before using HTTP Basic Authentication. We recommend using this approach only for testing and demo purposes.

### HTTP Digest Authentication Provider

~~HTTP Digest authentication support~~

This provider is deprecated and will be removed in a future version of Helidon without replacement. It is kept in Helidon 4 for backward compatibility only, relies on obsolete MD5 hash, and should not be used in production.

#### Setup

Maven dependency

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.security.providers</groupId>
  <artifactId>helidon-security-providers-http-auth</artifactId>
</dependency>
```

#### Overview

#### Configuration options

<!--@include ../../config/io.helidon.security.providers.httpauth.HttpDigestAuthProvider.md#configuration-options offset=2 -->
<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<a id="qop"></a>
<a href="io.helidon.security.providers.httpauth.HttpDigest.Qop.md">
<code>qop</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Qop</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">NONE</code>
</td>
<td>Only `AUTH` supported. If left empty, uses the legacy approach (older RFC version). `AUTH-INT` is not supported</td>
</tr>
<tr>
<td>
<code>server-secret</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>The nonce is encrypted using this secret - to make sure the nonce we get back was generated by us and to make sure we can safely time-out nonce values</td>
</tr>
<tr>
<td>
<code>optional</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether authentication is required</td>
</tr>
<tr>
<td>
<code>realm</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">Helidon</code>
</td>
<td>Set the realm to use when challenging users</td>
</tr>
<tr>
<td>
<code>nonce-timeout-millis</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">86400000</code>
</td>
<td>How long will the nonce value be valid. When timed-out, browser will re-request username/password</td>
</tr>
<tr>
<td>
<a id="principal-type"></a>
<a href="io.helidon.security.SubjectType.md">
<code>principal-type</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="SubjectType">SubjectType</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">USER</code>
</td>
<td>Principal type this provider extracts (and also propagates)</td>
</tr>
<tr>
<td>
<a id="users"></a>
<a href="io.helidon.security.providers.httpauth.ConfigUserStore.ConfigUser.md">
<code>users</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;ConfigUser&gt;">List&lt;ConfigUser&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Set user store to obtain passwords and roles based on logins</td>
</tr>
<tr>
<td>
<a id="algorithm"></a>
<a href="io.helidon.security.providers.httpauth.HttpDigest.Algorithm.md">
<code>algorithm</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Algorithm</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">MD5</code>
</td>
<td>Digest algorithm to use</td>
</tr>
</tbody>
</table>
<!--/include-->


#### Example code

Configuration example

```yaml
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

#### How does it work?

See <a href="https://tools.ietf.org/html/rfc7616" class="bare">https://tools.ietf.org/html/rfc7616</a>.

**Authentication of request**

When a request is received without the `Authorization: digest …​.` header, a challenge is returned to provide such authentication using `WWW-Authenticate` header.

When a request is received with the `Authorization: digest …​.` header, the request is validated against configured users (and users obtained from custom service if any provided).

Subject is created based on the username and roles provided by the user store.

**Custom user store**

Java service loader service `io.helidon.security.providers.httpauth.spi.UserStoreService` can be implemented to provide users to the provider, such as when validated against an internal database or LDAP server. The user store is defined so you never need the clear text password of the user.

*Note on security of HTTP Digest Authentication*

This authentication scheme is obsolete and should only be used for local testing or short-lived compatibility work.

### Header Authentication Provider

Asserts user or service identity based on a value of a header.

#### Setup

Maven dependency

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.security.providers</groupId>
  <artifactId>helidon-security-providers-header</artifactId>
</dependency>
```

#### Overview

#### Configuration options

<!--@include ../../config/io.helidon.security.providers.header.HeaderAtnProvider.md#configuration-options offset=2 -->
<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<a id="atn-token"></a>
<a href="io.helidon.security.util.TokenHandler.md">
<code>atn-token</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="TokenHandler">TokenHandler</code>
</td>
<td class="cm-default-cell">
</td>
<td>Token handler to extract username from request</td>
</tr>
<tr>
<td>
<code>authenticate</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to authenticate requests</td>
</tr>
<tr>
<td>
<a id="outbound"></a>
<a href="io.helidon.security.providers.common.OutboundTarget.md">
<code>outbound</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;OutboundTarget&gt;">List&lt;OutboundTarget&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Configure outbound target for identity propagation</td>
</tr>
<tr>
<td>
<code>propagate</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether to propagate identity</td>
</tr>
<tr>
<td>
<code>optional</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether authentication is required</td>
</tr>
<tr>
<td>
<a id="outbound-token"></a>
<a href="io.helidon.security.util.TokenHandler.md">
<code>outbound-token</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="TokenHandler">TokenHandler</code>
</td>
<td class="cm-default-cell">
</td>
<td>Token handler to create outbound headers to propagate identity</td>
</tr>
<tr>
<td>
<a id="principal-type"></a>
<a href="io.helidon.security.SubjectType.md">
<code>principal-type</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="SubjectType">SubjectType</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">USER</code>
</td>
<td>Principal type this provider extracts (and also propagates)</td>
</tr>
</tbody>
</table>
<!--/include-->


#### Example code

Configuration example

```yaml
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

This provider inspects a specified request header and extracts the username/service name from it and asserts it as current subject’s principal.

This can be used when we use perimeter authentication (e.g. there is a gateway that takes care of authentication and propagates the user in a header).

**Identity propagation**

Identity is propagated only if an outbound target matches the target service.

The following options exist when propagating identity: 1. We propagate the current username using the configured header 2. We use username associated with an outbound target (see example configuration above)

**Caution**

When using this provider, you must be sure the header cannot be explicitly configured by a user or another service. All requests should go through a gateway that removes this header from inbound traffic, and only configures it for authenticated users/services. Another option is to use this with fully trusted parties (such as services within a single company, on a single protected network not accessible to any users), and of course for testing and demo purposes.

### HTTP Signatures Provider

Support for HTTP Signatures.

#### Setup

Maven dependency

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.security.providers</groupId>
  <artifactId>helidon-security-providers-http-sign</artifactId>
</dependency>
```

#### Overview

#### Configuration options

<!--@include ../../config/io.helidon.security.providers.httpsign.HttpSignProvider.md#configuration-options offset=2 -->
<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<a id="headers"></a>
<a href="io.helidon.security.providers.httpsign.HttpSignHeader.md">
<code>headers</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;HttpSignHeader&gt;">List&lt;HttpSignHeader&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Add a header that is validated on inbound requests</td>
</tr>
<tr>
<td>
<a id="outbound"></a>
<a href="io.helidon.security.providers.common.OutboundConfig.md">
<code>outbound</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="OutboundConfig">OutboundConfig</code>
</td>
<td class="cm-default-cell">
</td>
<td>Add outbound targets to this builder</td>
</tr>
<tr>
<td>
<a id="inbound-keys"></a>
<a href="io.helidon.security.providers.httpsign.InboundClientDefinition.md">
<code>inbound.keys</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;InboundClientDefinition&gt;">List&lt;InboundClientDefinition&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Add inbound configuration</td>
</tr>
<tr>
<td>
<code>backward-compatible-eol</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Enable support for Helidon versions before 3.0.0 (exclusive)</td>
</tr>
<tr>
<td>
<code>optional</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Set whether the signature is optional</td>
</tr>
<tr>
<td>
<code>realm</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">helidon</code>
</td>
<td>Realm to use for challenging inbound requests that do not have "Authorization" header in case header is <code>HttpSignHeader#AUTHORIZATION</code> and singatures are not optional</td>
</tr>
<tr>
<td>
<a id="sign-headers"></a>
<a href="io.helidon.security.providers.httpsign.SignedHeadersConfig.HeadersConfig.md">
<code>sign-headers</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;HeadersConfig&gt;">List&lt;HeadersConfig&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Override the default inbound required headers (e.g</td>
</tr>
</tbody>
</table>
<!--/include-->


#### Example code

See the [example][example-3] on GitHub.

Configuration example

<!--@mdc ::code-collapse -->
```yaml
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

#### Signature basics

- standard: based on <a href="https://tools.ietf.org/html/draft-cavage-http-signatures-03" class="bare">https://tools.ietf.org/html/draft-cavage-http-signatures-03</a>
- key-id: an arbitrary string used to locate signature configuration - when a request is received the provider locates validation configuration based on this id (e.g. HMAC shared secret or RSA public key). Commonly used meanings are: key fingerprint (RSA); API Key

#### How does it work?

**Inbound Signatures** We act as a server and another party is calling us with a signed HTTP request. We validate the signature and assume identity of the caller.

**Outbound Signatures** We act as a client and we sign our outgoing requests. If there is a matching `outbound` target specified in configuration, its configuration will be applied for signing the outgoing request, otherwise there is no signature added

### IDCS Role Mapper

A role mapper to retrieve roles from Oracle IDCS.

#### Setup

Maven dependency

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.security.providers</groupId>
  <artifactId>helidon-security-providers-idcs-mapper</artifactId>
</dependency>
```

#### Single-tenant IDCS Role Mapper

#### Configuration options

<!--@include ../../config/io.helidon.security.providers.idcs.mapper.IdcsRoleMapperProvider.md#configuration-options offset=2 -->
<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<a id="cache-config"></a>
<a href="io.helidon.security.providers.common.EvictableCache.md">
<code>cache-config</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="EvictableCache">EvictableCache</code>
</td>
<td class="cm-default-cell">
</td>
<td>Use explicit <code>io.helidon.security.providers.common.EvictableCache</code> for role caching</td>
</tr>
<tr>
<td>
<code>default-idcs-subject-type</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">user</code>
</td>
<td>Configure subject type to use when requesting roles from IDCS</td>
</tr>
<tr>
<td>
<a id="oidc-config"></a>
<a href="io.helidon.security.providers.oidc.common.OidcConfig.md">
<code>oidc-config</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">OidcConfig</code>
</td>
<td class="cm-default-cell">
</td>
<td>Use explicit <code>io.helidon.security.providers.oidc.common.OidcConfig</code> instance, e.g</td>
</tr>
<tr>
<td>
<a id="subject-types"></a>
<a href="io.helidon.security.SubjectType.md">
<code>subject-types</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;SubjectType&gt;">List&lt;SubjectType&gt;</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">USER</code>
</td>
<td>Add a supported subject type</td>
</tr>
</tbody>
</table>
<!--/include-->


#### Multi-tenant IDCS Role Mapper

#### Configuration options

<!--@include ../../config/io.helidon.security.providers.idcs.mapper.IdcsMtRoleMapperProvider.md#configuration-options offset=2 -->
<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<a id="cache-config"></a>
<a href="io.helidon.security.providers.common.EvictableCache.md">
<code>cache-config</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="EvictableCache">EvictableCache</code>
</td>
<td class="cm-default-cell">
</td>
<td>Use explicit <code>io.helidon.security.providers.common.EvictableCache</code> for role caching</td>
</tr>
<tr>
<td>
<code>default-idcs-subject-type</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">user</code>
</td>
<td>Configure subject type to use when requesting roles from IDCS</td>
</tr>
<tr>
<td>
<a id="idcs-app-name-handler"></a>
<a href="io.helidon.security.util.TokenHandler.md">
<code>idcs-app-name-handler</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="TokenHandler">TokenHandler</code>
</td>
<td class="cm-default-cell">
</td>
<td>Configure token handler for IDCS Application name</td>
</tr>
<tr>
<td>
<a id="oidc-config"></a>
<a href="io.helidon.security.providers.oidc.common.OidcConfig.md">
<code>oidc-config</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">OidcConfig</code>
</td>
<td class="cm-default-cell">
</td>
<td>Use explicit <code>io.helidon.security.providers.oidc.common.OidcConfig</code> instance, e.g</td>
</tr>
<tr>
<td>
<a id="subject-types"></a>
<a href="io.helidon.security.SubjectType.md">
<code>subject-types</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;SubjectType&gt;">List&lt;SubjectType&gt;</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">USER</code>
</td>
<td>Add a supported subject type</td>
</tr>
<tr>
<td>
<a id="idcs-tenant-handler"></a>
<a href="io.helidon.security.util.TokenHandler.md">
<code>idcs-tenant-handler</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="TokenHandler">TokenHandler</code>
</td>
<td class="cm-default-cell">
</td>
<td>Configure token handler for IDCS Tenant ID</td>
</tr>
</tbody>
</table>
<!--/include-->


#### Example code

See the [example][example-4] on GitHub.

Configuration example

```yaml
security:
  providers:
    - idcs-role-mapper:
        multitenant: false
        oidc-config:
            client-id: "client-id"
            client-secret: "changeit"
            identity-uri: "IDCS identity server address"
```

#### How does it work?

The provider asks the IDCS server to provide list of roles for the currently authenticated user. The result is cached for a certain period of time (see `cache-config` above).

### ABAC Provider

Attribute based access control authorization provider.

#### Setup

Maven dependency

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.security.providers</groupId>
  <artifactId>helidon-security-providers-abac</artifactId>
</dependency>
```

#### Overview

#### Configuration options

<!--@include ../../config/io.helidon.security.providers.abac.AbacProvider.md#configuration-options offset=2 -->
<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>fail-if-none-validated</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to fail if NONE of the attributes is validated</td>
</tr>
<tr>
<td>
<code>fail-on-unvalidated</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to fail if any attribute is left unvalidated</td>
</tr>
</tbody>
</table>
<!--/include-->


#### Example code

See the [example][example-5] on GitHub.

Configuration example

```yaml
security:
  providers:
    - abac:
```

#### Configuration options

The following table shows all configuration options of the provider and their default values

| key | default value | description |
|----|----|----|
| `fail-on-unvalidated` | `true` | "Unvalidated" means: an attribute is defined, but there is no validator available for it |
| `fail-if-none-validated` | `true` | "None validated" means: there was not a single attribute that was validated |

#### How does it work?

ABAC uses available validators and validates them against attributes of the authenticated user.

Combinations of `fail-on-unvalidated` and `fail-if-none-validated`:

1.  `true` & `true`: Will fail if any attribute is not validated and if any has failed validation
2.  `false` & `true`: Will fail if there is one or more attributes present and NONE of them is validated or if any has failed validation, Will NOT fail if there is at least one validated attribute and any number of not validated attributes (and NONE failed)
3.  `false` & `false`: Will fail if there is any attribute that failed validation, Will NOT fail if there are no failed validation or if there are NONE validated

Any attribute of the following objects can be used:

- environment (such as time of request) - e.g. env.time.year
- subject (user) - e.g. subject.principal.id
- subject (service) - e.g. service.principal.id
- object (must be explicitly invoked by developer in code, as object cannot be automatically added to security context) - e.g. object.owner

This provider checks that all defined ABAC validators are validated. If there is a definition for a validator that is not checked, the request is denied (depending on configuration as mentioned above).

ABAC provider also allows an object to be used in authorization process, such as when evaluating if an object’s owner is the current user. The following example uses the Expression language validator to demonstrate the point in a JAX-RS resource:

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

#### Role Validator

Checks whether user/service is in either of the required role(s).

Configuration Key: `role-validator`

Annotations: `@RolesAllowed`, `@RoleValidator.Roles`

Configuration example for `WebServer`

```yaml
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

##### Interaction with JAX-RS sub-resource locators

When using sub-resource locators in JAX-RS, the roles allowed are collected from each "level" of execution: - Application class annotations - Resource class annotations + resource method annotations - Sub-resource class annotations + sub-resource method annotations - Sub-resource class annotations + sub-resource method annotations (for every sub-resource on the path)

The `RolesAllowed` or `Roles` annotation to be used is the last one in the path as defined above.

*Example 1:* There is a `RolesAllowed("admin")` defined on a sub-resource locator resource class. In this case the required role is `admin`.

*Example 2:* There is a `RolesAllowed("admin")` defined on a sub-resource locator resource class and a `RolesAllowed("user")` defined on the method of the sub-resource that provides the response. In this case the required role is `user`.

#### Scope Validator

Checks whether user has all the required scopes.

Configuration Key: `scope-validator`

Annotations: `@Scope`

Configuration example for `WebServer`

```yaml
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

#### Expression Language Policy Validator

Policy executor using Java EE policy expression language (EL)

Configuration Key: `policy-javax-el`

Annotations: `@PolicyStatement`

Example of a policy statement: `${env.time.year >= 2017}`

Configuration example for `WebServer`

```yaml
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

```yaml
server:
  features:
    security:
      endpoints:
        - path: "/somePath"
          config:
            abac.policy-validator.statement: "\\${env.time.year >= 2017}"
```

### Google Login Provider

Authenticates a token from request against Google identity provider

This provider is deprecated and will be removed in a future version of Helidon. Please use our OpenID Connect security provider instead.

#### Setup

Maven dependency

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.security.providers</groupId>
  <artifactId>helidon-security-providers-google-login</artifactId>
</dependency>
```

#### Overview

#### Configuration options

<!--@include ../../config/io.helidon.security.providers.google.login.GoogleTokenProvider.md#configuration-options offset=2 -->
<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>proxy-port</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">80</code>
</td>
<td>Set proxy port when talking to Google</td>
</tr>
<tr>
<td>
<a id="outbound"></a>
<a href="io.helidon.security.providers.common.OutboundConfig.md">
<code>outbound</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="OutboundConfig">OutboundConfig</code>
</td>
<td class="cm-default-cell">
</td>
<td>Outbound configuration - a set of outbound targets that will have the token propagated</td>
</tr>
<tr>
<td>
<code>proxy-host</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Set proxy host when talking to Google</td>
</tr>
<tr>
<td>
<code>optional</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>If set to true, this provider will return <code>io.helidon.security.SecurityResponse.SecurityStatus#ABSTAIN</code> instead of failing in case of invalid request</td>
</tr>
<tr>
<td>
<code>realm</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">helidon</code>
</td>
<td>Set the authentication realm to build challenge, defaults to "helidon"</td>
</tr>
<tr>
<td>
<code>client-id</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Google application client id, to validate that the token was generated by Google for us</td>
</tr>
<tr>
<td>
<a id="token"></a>
<a href="io.helidon.security.util.TokenHandler.md">
<code>token</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="TokenHandler">TokenHandler</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value" title="&#x60;Authorization&#x60; header with &#x60;bearer&#x60; prefix">&#x60;Authorization&#x60; header with &#x60;bearer&#x60; prefix</code>
</td>
<td>Token provider to extract Google access token from request, defaults to "Authorization" header with a "bearer " prefix</td>
</tr>
</tbody>
</table>
<!--/include-->


#### Example code

See the [example][example-6] on GitHub.

Configuration example

```yaml
security:
  providers:
    - provider:
        client-id: "Google client id"
```

#### How does it work?

We expect to receive a token (with sufficient scopes) from the inbound request, such as when using the Google login button on a page. The page has access to the token in javascript and can send it to backend with every request in a header field (`Authorization` with \`bearer \` prefix is assumed by default).

Once we receive the token in Helidon, we parse it and:

1.  Validate if it timed out locally
2.  Return a cached response (see `EvictableCache` with default values)
3.  Otherwise verify using Google API - `GoogleIdTokenVerifier`

We build a subject from the Google token with the following attributes filled (if in token):

- userId
- email
- name
- emailVerified
- locale
- family_name
- given_name
- picture (URL)

**Outbound security** The token will be propagated to outbound calls if an outbound target exists that matches the invoked endpoint (see `outbound` configuration above).

### JWT Provider

JWT token authentication and outbound security provider.

#### Setup

Maven dependency

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.security.providers</groupId>
  <artifactId>helidon-security-providers-jwt</artifactId>
</dependency>
```

#### Overview

#### Configuration options

<!--@include ../../config/io.helidon.security.providers.jwt.JwtProvider.md#configuration-options offset=2 -->
<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>allow-impersonation</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether to allow impersonation by explicitly overriding username from outbound requests using <code>io.helidon.security.EndpointConfig#PROPERTY_OUTBOUND_ID</code> property</td>
</tr>
<tr>
<td>
<code>allow-unsigned</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Configure support for unsigned JWT</td>
</tr>
<tr>
<td>
<a id="atn-token"></a>
<a href="io.helidon.security.providers.jwt.AtnTokenConfig.md">
<code>atn-token</code>
</a>
</td>
<td class="cm-type-cell">
</td>
<td class="cm-default-cell">
</td>
<td>Configuration for atn-token</td>
</tr>
<tr>
<td>
<code>authenticate</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to authenticate requests</td>
</tr>
<tr>
<td>
<code>optional</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether authentication is required</td>
</tr>
<tr>
<td>
<a id="principal-type"></a>
<a href="io.helidon.security.SubjectType.md">
<code>principal-type</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="SubjectType">SubjectType</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">USER</code>
</td>
<td>Principal type this provider extracts (and also propagates)</td>
</tr>
<tr>
<td>
<code>propagate</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to propagate identity</td>
</tr>
<tr>
<td>
<a id="sign-token"></a>
<a href="io.helidon.security.providers.common.OutboundConfig.md">
<code>sign-token</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="OutboundConfig">OutboundConfig</code>
</td>
<td class="cm-default-cell">
</td>
<td>Configuration of outbound rules</td>
</tr>
<tr>
<td>
<code>use-jwt-groups</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Claim <code>groups</code> from JWT will be used to automatically add  groups to current subject (may be used with <code>jakarta.annotation.security.RolesAllowed</code> annotation)</td>
</tr>
</tbody>
</table>
<!--/include-->


#### Example code

See the [example][example-2] on GitHub.

Configuration example

```yaml
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

#### How does it work?

JSON Web Token (JWT) provider has support for authentication and outbound security.

Authentication is based on validating the token (signature, valid before etc.) and on asserting the subject of the JWT subject claim.

For outbound, we support either token propagation (e.g. the token from request is propagated further) or support for generating a brand new token based on configuration of this provider.

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
