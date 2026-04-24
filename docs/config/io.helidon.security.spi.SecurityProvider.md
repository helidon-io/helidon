# io.helidon.security.spi.SecurityProvider

## Description

This type is a provider contract.

## Implementations

<style>
    code {
        white-space: nowrap !important;
    }
</style>



<table>
<thead>
<tr>
<th>Key</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<a href="io.helidon.security.providers.abac.AbacProvider.md">
<code>abac</code>
</a>
</td>
<td>Attribute Based Access Control provider</td>
</tr>
<tr>
<td>
<a href="io.helidon.security.providers.config.vault.ConfigVaultProvider.md">
<code>config-vault</code>
</a>
</td>
<td>Secrets and Encryption provider using just configuration</td>
</tr>
<tr>
<td>
<a href="io.helidon.security.providers.google.login.GoogleTokenProvider.md">
<code>google-login</code>
</a>
</td>
<td>Google Authentication provider</td>
</tr>
<tr>
<td>
<a href="io.helidon.security.providers.header.HeaderAtnProvider.md">
<code>header-atn</code>
</a>
</td>
<td>Security provider that extracts a username (or service name) from a header</td>
</tr>
<tr>
<td>
<a href="io.helidon.security.providers.httpauth.HttpBasicAuthProvider.md">
<code>http-basic-auth</code>
</a>
</td>
<td>HTTP Basic Authentication provider</td>
</tr>
<tr>
<td>
<a href="io.helidon.security.providers.httpauth.HttpDigestAuthProvider.md">
<code>http-digest-auth</code>
</a>
</td>
<td>Http digest authentication security provider</td>
</tr>
<tr>
<td>
<a href="io.helidon.security.providers.idcs.mapper.IdcsMtRoleMapperProvider.md">
<code>idcs-role-mapper</code>
</a>
</td>
<td>Multitenant IDCS role mapping provider</td>
</tr>
<tr>
<td>
<a href="io.helidon.security.providers.idcs.mapper.IdcsRoleMapperProvider.md">
<code>idcs-role-mapper</code>
</a>
</td>
<td>IDCS role mapping provider</td>
</tr>
<tr>
<td>
<a href="io.helidon.security.providers.jwt.JwtProvider.md">
<code>jwt</code>
</a>
</td>
<td>JWT authentication provider</td>
</tr>
<tr>
<td>
<a href="io.helidon.security.providers.oidc.OidcProvider.md">
<code>oidc</code>
</a>
</td>
<td>Open ID Connect security provider</td>
</tr>
</tbody>
</table>



## Usages

- [`security.providers`](io.helidon.security.Security.md#providers)
- [`server.features.security.security.providers`](io.helidon.security.Security.md#providers)

---

See the [manifest](manifest.md) for all available types.
