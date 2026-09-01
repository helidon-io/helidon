# io.<wbr>helidon.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>common.<wbr>Oidc<wbr>Config.<wbr>Client<wbr>Authentication

## Description

This type is an enumeration.

## Allowed Values

<table>
<thead>
<tr>
<th>Value</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>CLIENT_<wbr>SECRET_<wbr>BASIC</code></td>
<td>Clients that have received a client_secret value from the Authorization Server authenticate with the Authorization Server in accordance with Section 2.3.1 of OAuth 2.0 [RFC6749] using the HTTP Basic authentication scheme</td>
</tr>
<tr>
<td><code>CLIENT_<wbr>SECRET_<wbr>POST</code></td>
<td>Clients that have received a client_secret value from the Authorization Server, authenticate with the Authorization Server in accordance with Section 2.3.1 of OAuth 2.0 [RFC6749] by including the Client Credentials in the request body</td>
</tr>
<tr>
<td><code>CLIENT_<wbr>SECRET_<wbr>JWT</code></td>
<td>Clients that have received a client_secret value from the Authorization Server create a JWT using an HMAC SHA algorithm, such as HMAC SHA-256</td>
</tr>
<tr>
<td><code>PRIVATE_<wbr>KEY_<wbr>JWT</code></td>
<td>Clients that have registered a public key sign a JWT using that key</td>
</tr>
<tr>
<td><code>CLIENT_<wbr>CERTIFICATE</code></td>
<td>Authentication is done via the client certificate used with MTLS</td>
</tr>
<tr>
<td><code>NONE</code></td>
<td>The Client does not authenticate itself at the Token Endpoint, either because it uses only the Implicit Flow (and so does not use the Token Endpoint) or because it is a Public Client with no Client Secret or other authentication mechanism</td>
</tr>
</tbody>
</table>

## Usages

- <a href="io.helidon.security.providers.oidc.common.TenantConfig.md#token-endpoint-auth"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>tenants.<wbr>token-<wbr>endpoint-<wbr>auth</code></a>
- <a href="io.helidon.security.providers.oidc.common.OidcConfig.md#token-endpoint-auth"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>token-<wbr>endpoint-<wbr>auth</code></a>
- <a href="io.helidon.security.providers.oidc.common.TenantConfig.md#token-endpoint-auth"><code>security.<wbr>providers.<wbr>oidc.<wbr>tenants.<wbr>token-<wbr>endpoint-<wbr>auth</code></a>
- <a href="io.helidon.security.providers.oidc.common.TenantConfig.md#token-endpoint-auth"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>tenants.<wbr>token-<wbr>endpoint-<wbr>auth</code></a>
- <a href="io.helidon.security.providers.oidc.common.OidcConfig.md#token-endpoint-auth"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>token-<wbr>endpoint-<wbr>auth</code></a>
- <a href="io.helidon.security.providers.oidc.common.TenantConfig.md#token-endpoint-auth"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>tenants.<wbr>token-<wbr>endpoint-<wbr>auth</code></a>

---

See the [manifest](manifest.md) for all available types.
