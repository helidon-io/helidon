# io.helidon.security.providers.jwt.JwtProvider

## Description

JWT authentication provider

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
<code>allow-<wbr>impersonation</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether to allow impersonation by explicitly overriding username from outbound requests using <code>io.<wbr>helidon.<wbr>security.<wbr>Endpoint<wbr>Config#<wbr>PROPERTY_<wbr>OUTBOUND_<wbr>ID</code> property</td>
</tr>
<tr>
<td>
<code>allow-<wbr>unsigned</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Configure support for unsigned JWT</td>
</tr>
<tr>
<td>
<a id="atn-token"></a>
<a href="io.helidon.security.providers.jwt.AtnTokenConfig.md">
<code>atn-<wbr>token</code>
</a>
</td>
<td>
</td>
<td>
</td>
<td>Configuration for atn-token</td>
</tr>
<tr>
<td>
<code>authenticate</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to authenticate requests</td>
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
<a id="principal-type"></a>
<a href="io.helidon.security.SubjectType.md">
<code>principal-<wbr>type</code>
</a>
</td>
<td>
<code>Subject<wbr>Type</code>
</td>
<td>
<code>USER</code>
</td>
<td>Principal type this provider extracts (and also propagates)</td>
</tr>
<tr>
<td>
<code>propagate</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to propagate identity</td>
</tr>
<tr>
<td>
<a id="sign-token"></a>
<a href="io.helidon.security.providers.common.OutboundConfig.md">
<code>sign-<wbr>token</code>
</a>
</td>
<td>
<code>Outbound<wbr>Config</code>
</td>
<td>
</td>
<td>Configuration of outbound rules</td>
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
</tbody>
</table>



## Usages

- [`security.providers.jwt`](io.helidon.security.spi.SecurityProvider.md#jwt)
- [`server.features.security.security.providers.jwt`](io.helidon.security.spi.SecurityProvider.md#jwt)

---

See the [manifest](manifest.md) for all available types.
