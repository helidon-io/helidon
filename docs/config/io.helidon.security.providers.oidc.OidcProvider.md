# io.helidon.security.providers.oidc.OidcProvider

## Description

Open ID Connect security provider

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }

    .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table>
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


<table>
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


## Usages

- [`security.providers.oidc`](io.helidon.security.spi.SecurityProvider.md#oidc)
- [`server.features.security.security.providers.oidc`](io.helidon.security.spi.SecurityProvider.md#oidc)

---

See the [manifest](manifest.md) for all available types.
