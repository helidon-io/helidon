# io.helidon.security.providers.oidc.OidcProvider

## Description

Open ID Connect security provider

## Configuration options


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
<code>force-<wbr>https-<wbr>redirects</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
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
<td>
<code>Cross<wbr>Origin<wbr>Config</code>
</td>
<td>
</td>
<td>Assign cross-origin resource sharing settings</td>
</tr>
<tr>
<td>
<code>cookie-<wbr>encryption-<wbr>refresh-<wbr>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to encrypt refresh token cookie created by this microservice</td>
</tr>
<tr>
<td>
<code>query-<wbr>id-token-<wbr>param-<wbr>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>id_<wbr>token</code>
</td>
<td>Name of a query parameter that contains the JWT id token when parameter is used</td>
</tr>
<tr>
<td>
<code>header-<wbr>use</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to expect JWT in a header field</td>
</tr>
<tr>
<td>
<a id="header-token"></a>
<a href="io.helidon.security.util.TokenHandler.md">
<code>header-<wbr>token</code>
</a>
</td>
<td>
<code>Token<wbr>Handler</code>
</td>
<td>
</td>
<td>A <code>Token<wbr>Handler</code> to process header containing a JWT</td>
</tr>
<tr>
<td>
<code>cookie-<wbr>name-<wbr>state</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>JSESSIONID_<wbr>3</code>
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
<td>
<code>List&lt;<wbr>Outbound<wbr>Target&gt;</code>
</td>
<td>
</td>
<td>Add a new target configuration</td>
</tr>
<tr>
<td>
<code>propagate</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether to propagate identity</td>
</tr>
<tr>
<td>
<a id="client-credentials-config"></a>
<a href="io.helidon.security.providers.oidc.common.ClientCredentialsConfig.md">
<code>client-<wbr>credentials-<wbr>config</code>
</a>
</td>
<td>
<code>Client<wbr>Credentials<wbr>Config</code>
</td>
<td>
</td>
<td>Set the configuration related to the client credentials flow</td>
</tr>
<tr>
<td>
<code>cookie-<wbr>name-<wbr>refresh-<wbr>token</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>JSESSIONID_<wbr>3</code>
</td>
<td>The name of the cookie to use for the refresh token</td>
</tr>
<tr>
<td>
<code>query-<wbr>param-<wbr>tenant-<wbr>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>h_<wbr>tenant</code>
</td>
<td>Name of a query parameter that contains the tenant name when the parameter is used</td>
</tr>
<tr>
<td>
<code>query-<wbr>param-<wbr>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>access<wbr>Token</code>
</td>
<td>Name of a query parameter that contains the JWT access token when parameter is used</td>
</tr>
<tr>
<td>
<a id="pkce-challenge-method"></a>
<a href="io.helidon.security.providers.oidc.common.PkceChallengeMethod.md">
<code>pkce-<wbr>challenge-<wbr>method</code>
</a>
</td>
<td>
<code>Pkce<wbr>Challenge<wbr>Method</code>
</td>
<td>
<code>S256</code>
</td>
<td>Proof Key Code Exchange (PKCE) challenge creation method</td>
</tr>
<tr>
<td>
<code>optional</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether authentication is required</td>
</tr>
<tr>
<td>
<code>cookie-<wbr>domain</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Domain the cookie is valid for</td>
</tr>
<tr>
<td>
<code>frontend-<wbr>uri</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Full URI of this application that is visible from user browser</td>
</tr>
<tr>
<td>
<a id="cookie-same-site"></a>
<a href="io.helidon.http.SetCookie.SameSite.md">
<code>cookie-<wbr>same-<wbr>site</code>
</a>
</td>
<td>
<code>Same<wbr>Site</code>
</td>
<td>
<code>LAX</code>
</td>
<td>When using cookie, used to set the SameSite cookie value</td>
</tr>
<tr>
<td>
<code>cookie-<wbr>encryption-<wbr>id-enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
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
<td>
<code>Web<wbr>Client</code>
</td>
<td>
</td>
<td>WebClient configuration used for outbound requests to the identity server. This configuration sets the values to the OIDC WebClient default configuration</td>
</tr>
<tr>
<td>
<code>cookie-<wbr>http-<wbr>only</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>When using cookie, if set to true, the HttpOnly attribute will be configured</td>
</tr>
<tr>
<td>
<code>cookie-<wbr>encryption-<wbr>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether to encrypt token cookie created by this microservice</td>
</tr>
<tr>
<td>
<code>pkce-<wbr>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether this provider should support PKCE</td>
</tr>
<tr>
<td>
<code>proxy-<wbr>port</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>80</code>
</td>
<td>Proxy port</td>
</tr>
<tr>
<td>
<code>cookie-<wbr>encryption-<wbr>tenant-<wbr>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to encrypt tenant name cookie created by this microservice</td>
</tr>
<tr>
<td>
<code>use-<wbr>jwt-<wbr>groups</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Claim <code>groups</code> from JWT will be used to automatically add  groups to current subject (may be used with <code>jakarta.<wbr>annotation.<wbr>security.<wbr>Roles<wbr>Allowed</code> annotation)</td>
</tr>
<tr>
<td>
<code>token-<wbr>signature-<wbr>validation</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether access token signature check should be enabled</td>
</tr>
<tr>
<td>
<code>cookie-<wbr>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>JSESSIONID</code>
</td>
<td>Name of the cookie to use</td>
</tr>
<tr>
<td>
<code>cookie-<wbr>use</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to use cookie to store JWT between requests</td>
</tr>
<tr>
<td>
<a id="outbound-type"></a>
<a href="io.helidon.security.providers.oidc.common.OidcOutboundType.md">
<code>outbound-<wbr>type</code>
</a>
</td>
<td>
<code>Oidc<wbr>Outbound<wbr>Type</code>
</td>
<td>
<code>USER_<wbr>JWT</code>
</td>
<td>Type of the OIDC outbound</td>
</tr>
<tr>
<td>
<code>redirect</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>By default, the client should redirect to the identity server for the user to log in</td>
</tr>
<tr>
<td>
<code>redirect-<wbr>uri</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>&#8288;/&#8288;oidc/<wbr>redirect</code>
</td>
<td>URI to register web server component on, used by the OIDC server to redirect authorization requests to after a user logs in or approves scopes</td>
</tr>
<tr>
<td>
<code>cookie-<wbr>name-<wbr>id-token</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>JSESSIONID_<wbr>2</code>
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
<td>
<code>Tenant<wbr>Config</code>
</td>
<td>
</td>
<td>Configurations of the tenants</td>
</tr>
<tr>
<td>
<code>cookie-<wbr>max-<wbr>age-<wbr>seconds</code>
</td>
<td>
<code>Long</code>
</td>
<td>
</td>
<td>When using cookie, used to set MaxAge attribute of the cookie, defining how long the cookie is valid</td>
</tr>
<tr>
<td>
<code>cookie-<wbr>encryption-<wbr>password</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>Master password for encryption/decryption of cookies</td>
</tr>
<tr>
<td>
<code>cookie-<wbr>encryption-<wbr>state-<wbr>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to encrypt state cookie created by this microservice</td>
</tr>
<tr>
<td>
<code>cookie-<wbr>path</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>/</code>
</td>
<td>Path the cookie is valid for</td>
</tr>
<tr>
<td>
<code>query-<wbr>param-<wbr>use</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether to use a query parameter to send JWT token from application to this server</td>
</tr>
<tr>
<td>
<code>cookie-<wbr>name-<wbr>tenant</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>HELIDON_<wbr>TENANT</code>
</td>
<td>The name of the cookie to use for the tenant name</td>
</tr>
<tr>
<td>
<code>cookie-<wbr>secure</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>When using cookie, if set to true, the Secure attribute will be configured</td>
</tr>
<tr>
<td>
<code>cookie-<wbr>encryption-<wbr>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Name of the encryption configuration available through <code>Security#<wbr>encrypt(<wbr>String,<wbr> byte[])</code> and <code>Security#<wbr>decrypt(<wbr>String,<wbr> String)</code></td>
</tr>
<tr>
<td>
<code>id-<wbr>token-<wbr>signature-<wbr>validation</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether id token signature check should be enabled</td>
</tr>
<tr>
<td>
<code>max-<wbr>redirects</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>5</code>
</td>
<td>Configure maximal number of redirects when redirecting to an OIDC provider within a single authentication attempt</td>
</tr>
<tr>
<td>
<code>access-<wbr>token-<wbr>ip-check</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to check if current IP address matches the one access token was issued for</td>
</tr>
<tr>
<td>
<code>redirect-<wbr>attempt-<wbr>param</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>h_<wbr>ra</code>
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
<code>relative-<wbr>uris</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Can be set to <code>true</code> to force the use of relative URIs in all requests, regardless of the presence or absence of proxies or no-proxy lists</td>
</tr>
<tr>
<td>
<code>proxy-<wbr>host</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Proxy host to use</td>
</tr>
<tr>
<td>
<code>proxy-<wbr>protocol</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>http</code>
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
