# io.helidon.security.providers.oidc.OidcProvider

## Description

Open ID Connect security provider.

## Usages

- [`security.providers.oidc`](../config/io_helidon_security_spi_SecurityProvider.md#aab762-oidc)
- [`server.features.security.security.providers.oidc`](../config/io_helidon_security_spi_SecurityProvider.md#aab762-oidc)

## Configuration options

<table class="tableblock frame-all grid-all stretch">
<colgroup>
<col style="width: 20%" />
<col style="width: 20%" />
<col style="width: 20%" />
<col style="width: 20%" />
<col style="width: 20%" />
</colgroup>
<thead>
<tr>
<th class="tableblock halign-left valign-top">Key</th>
<th class="tableblock halign-left valign-top">Kind</th>
<th class="tableblock halign-left valign-top">Type</th>
<th class="tableblock halign-left valign-top">Default Value</th>
<th class="tableblock halign-left valign-top">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a3a883-access-token-ip-check"></span> <code>access-token-ip-check</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>true</code></p></td>
<td class="tableblock halign-left valign-top"><p>Whether to check if current IP address matches the one access token was issued for</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a7e9b5-client-credentials-config"></span> <a href="../config/../config/io_helidon_security_providers_oidc_common_ClientCredentialsConfig.html"><code>client-credentials-config</code></a></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>i.h.s.p.o.c.ClientCredentialsConfig</code></p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
<td class="tableblock halign-left valign-top"><p>Set the configuration related to the client credentials flow</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a9b107-cookie-domain"></span> <code>cookie-domain</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
<td class="tableblock halign-left valign-top"><p>Domain the cookie is valid for</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a593cb-cookie-encryption-enabled"></span> <code>cookie-encryption-enabled</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>false</code></p></td>
<td class="tableblock halign-left valign-top"><p>Whether to encrypt token cookie created by this microservice</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="aaabc9-cookie-encryption-id-enabled"></span> <code>cookie-encryption-id-enabled</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>true</code></p></td>
<td class="tableblock halign-left valign-top"><p>Whether to encrypt id token cookie created by this microservice</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="ab14ce-cookie-encryption-name"></span> <code>cookie-encryption-name</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
<td class="tableblock halign-left valign-top">Name of the encryption configuration available through
Security#encrypt(String, byte[)&lt;/code&gt; and &lt;code&gt;Security#decrypt(String, String)&lt;/code&gt;]</td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="ac0d77-cookie-encryption-password"></span> <code>cookie-encryption-password</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>LIST</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
<td class="tableblock halign-left valign-top"><p>Master password for encryption/decryption of cookies</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="ad8fc7-cookie-encryption-refresh-enabled"></span> <code>cookie-encryption-refresh-enabled</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>true</code></p></td>
<td class="tableblock halign-left valign-top"><p>Whether to encrypt refresh token cookie created by this microservice</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a5da71-cookie-encryption-state-enabled"></span> <code>cookie-encryption-state-enabled</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>true</code></p></td>
<td class="tableblock halign-left valign-top"><p>Whether to encrypt state cookie created by this microservice</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a16963-cookie-encryption-tenant-enabled"></span> <code>cookie-encryption-tenant-enabled</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>true</code></p></td>
<td class="tableblock halign-left valign-top"><p>Whether to encrypt tenant name cookie created by this microservice</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="ad2ea9-cookie-http-only"></span> <code>cookie-http-only</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>true</code></p></td>
<td class="tableblock halign-left valign-top"><p>When using cookie, if set to true, the HttpOnly attribute will be configured</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="ae7d0f-cookie-max-age-seconds"></span> <code>cookie-max-age-seconds</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Long</code></p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
<td class="tableblock halign-left valign-top"><p>When using cookie, used to set MaxAge attribute of the cookie, defining how long the cookie is valid</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a423f0-cookie-name"></span> <code>cookie-name</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>JSESSIONID</code></p></td>
<td class="tableblock halign-left valign-top"><p>Name of the cookie to use</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a47aa0-cookie-name-id-token"></span> <code>cookie-name-id-token</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>JSESSIONID_2</code></p></td>
<td class="tableblock halign-left valign-top"><p>Name of the cookie to use for id token</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="afcd78-cookie-name-refresh-token"></span> <code>cookie-name-refresh-token</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>JSESSIONID_3</code></p></td>
<td class="tableblock halign-left valign-top"><p>The name of the cookie to use for the refresh token</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a3e5f9-cookie-name-state"></span> <code>cookie-name-state</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>JSESSIONID_3</code></p></td>
<td class="tableblock halign-left valign-top"><p>The name of the cookie to use for the state storage</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="afcf69-cookie-name-tenant"></span> <code>cookie-name-tenant</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>HELIDON_TENANT</code></p></td>
<td class="tableblock halign-left valign-top"><p>The name of the cookie to use for the tenant name</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="ae5841-cookie-path"></span> <code>cookie-path</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>/</code></p></td>
<td class="tableblock halign-left valign-top"><p>Path the cookie is valid for</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a0a656-cookie-same-site"></span> <a href="../config/../config/io_helidon_http_SetCookie_SameSite.html"><code>cookie-same-site</code></a></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>i.h.h.S.SameSite</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>LAX</code></p></td>
<td class="tableblock halign-left valign-top"><p>When using cookie, used to set the SameSite cookie value</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a0f553-cookie-secure"></span> <code>cookie-secure</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>false</code></p></td>
<td class="tableblock halign-left valign-top"><p>When using cookie, if set to true, the Secure attribute will be configured</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a70aa3-cookie-use"></span> <code>cookie-use</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>true</code></p></td>
<td class="tableblock halign-left valign-top"><p>Whether to use cookie to store JWT between requests</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="ad1309-cors"></span> <a href="../config/../config/io_helidon_cors_CrossOriginConfig.html"><code>cors</code></a></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>i.h.c.CrossOriginConfig</code></p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
<td class="tableblock halign-left valign-top"><p>Assign cross-origin resource sharing settings</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="afd33a-force-https-redirects"></span> <code>force-https-redirects</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>false</code></p></td>
<td class="tableblock halign-left valign-top"><p>Force HTTPS for redirects to identity provider</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a069b5-frontend-uri"></span> <code>frontend-uri</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
<td class="tableblock halign-left valign-top"><p>Full URI of this application that is visible from user browser</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="abf3fb-header-token"></span> <a href="../config/../config/io_helidon_security_util_TokenHandler.html"><code>header-token</code></a></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>i.h.s.u.TokenHandler</code></p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
<td class="tableblock halign-left valign-top"><p>A <code>TokenHandler</code> to process header containing a JWT</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a866f7-header-use"></span> <code>header-use</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>true</code></p></td>
<td class="tableblock halign-left valign-top"><p>Whether to expect JWT in a header field</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a5a4ae-id-token-signature-validation"></span> <code>id-token-signature-validation</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>true</code></p></td>
<td class="tableblock halign-left valign-top"><p>Whether id token signature check should be enabled</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="ab5728-max-redirects"></span> <code>max-redirects</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Integer</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>5</code></p></td>
<td class="tableblock halign-left valign-top"><p>Configure maximal number of redirects when redirecting to an OIDC provider within a single authentication attempt</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="ab32e8-optional"></span> <code>optional</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>false</code></p></td>
<td class="tableblock halign-left valign-top"><p>Whether authentication is required</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="acf040-outbound"></span> <a href="../config/../config/io_helidon_security_providers_common_OutboundTarget.html"><code>outbound</code></a></p></td>
<td class="tableblock halign-left valign-top"><p><code>LIST</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>i.h.s.p.c.OutboundTarget</code></p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
<td class="tableblock halign-left valign-top"><p>Add a new target configuration</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="aebe14-outbound-type"></span> <a href="../config/../config/io_helidon_security_providers_oidc_common_OidcOutboundType.html"><code>outbound-type</code></a></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>i.h.s.p.o.c.OidcOutboundType</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>USER_JWT</code></p></td>
<td class="tableblock halign-left valign-top"><p>Type of the OIDC outbound</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a6ccfb-pkce-challenge-method"></span> <a href="../config/../config/io_helidon_security_providers_oidc_common_PkceChallengeMethod.html"><code>pkce-challenge-method</code></a></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>i.h.s.p.o.c.PkceChallengeMethod</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>S256</code></p></td>
<td class="tableblock halign-left valign-top"><p>Proof Key Code Exchange (PKCE) challenge creation method</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a4050c-pkce-enabled"></span> <code>pkce-enabled</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>false</code></p></td>
<td class="tableblock halign-left valign-top"><p>Whether this provider should support PKCE</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="ad84e8-propagate"></span> <code>propagate</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>false</code></p></td>
<td class="tableblock halign-left valign-top"><p>Whether to propagate identity</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a03394-proxy-port"></span> <code>proxy-port</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Integer</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>80</code></p></td>
<td class="tableblock halign-left valign-top"><p>Proxy port</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="aa5960-query-id-token-param-name"></span> <code>query-id-token-param-name</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>id_token</code></p></td>
<td class="tableblock halign-left valign-top"><p>Name of a query parameter that contains the JWT id token when parameter is used</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a4e9c0-query-param-name"></span> <code>query-param-name</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>accessToken</code></p></td>
<td class="tableblock halign-left valign-top"><p>Name of a query parameter that contains the JWT access token when parameter is used</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a0b01e-query-param-tenant-name"></span> <code>query-param-tenant-name</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>h_tenant</code></p></td>
<td class="tableblock halign-left valign-top"><p>Name of a query parameter that contains the tenant name when the parameter is used</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a3d4be-query-param-use"></span> <code>query-param-use</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>false</code></p></td>
<td class="tableblock halign-left valign-top"><p>Whether to use a query parameter to send JWT token from application to this server</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="aa4ba9-redirect"></span> <code>redirect</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>false</code></p></td>
<td class="tableblock halign-left valign-top"><p>By default, the client should redirect to the identity server for the user to log in</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="adf51c-redirect-attempt-param"></span> <code>redirect-attempt-param</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>h_ra</code></p></td>
<td class="tableblock halign-left valign-top"><p>Configure the parameter used to store the number of attempts in redirect</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a21f20-redirect-uri"></span> <code>redirect-uri</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>/oidc/redirect</code></p></td>
<td class="tableblock halign-left valign-top"><p>URI to register web server component on, used by the OIDC server to redirect authorization requests to after a user logs in or approves scopes</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="aef163-tenants"></span> <a href="../config/../config/io_helidon_security_providers_oidc_common_TenantConfig.html"><code>tenants</code></a></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>i.h.s.p.o.c.TenantConfig</code></p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
<td class="tableblock halign-left valign-top"><p>Configurations of the tenants</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a5f4ac-token-signature-validation"></span> <code>token-signature-validation</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>true</code></p></td>
<td class="tableblock halign-left valign-top"><p>Whether access token signature check should be enabled</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a50324-use-jwt-groups"></span> <code>use-jwt-groups</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>true</code></p></td>
<td class="tableblock halign-left valign-top"><p>Claim <code>groups</code> from JWT will be used to automatically add groups to current subject (may be used with <code>jakarta.annotation.security.RolesAllowed</code> annotation)</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a85467-webclient"></span> <a href="../config/../config/io_helidon_webclient_api_WebClient.html"><code>webclient</code></a></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>i.h.w.a.WebClient</code></p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
<td class="tableblock halign-left valign-top"><p>WebClient configuration used for outbound requests to the identity server. This configuration sets the values to the OIDC WebClient default configuration</p></td>
</tr>
</tbody>
</table>

### Deprecated Options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="af9976-proxy-host"></span> `proxy-host` | `VALUE` | `String` |   | Proxy host to use |
| <span id="aa965f-proxy-protocol"></span> `proxy-protocol` | `VALUE` | `String` | `http` | Proxy protocol to use when proxy is used |
| <span id="abf0b4-relative-uris"></span> `relative-uris` | `VALUE` | `Boolean` | `false` | Can be set to `true` to force the use of relative URIs in all requests, regardless of the presence or absence of proxies or no-proxy lists |

------------------------------------------------------------------------

See the [manifest](../config/manifest.md) for all available types.
