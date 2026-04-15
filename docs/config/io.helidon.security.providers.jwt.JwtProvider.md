# io.helidon.security.providers.jwt.JwtProvider

## Description

JWT authentication provider

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Default Value</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>allow-impersonation</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether to allow impersonation by explicitly overriding username from outbound requests using &lt;code&gt;io.helidon.security.EndpointConfig#PROPERTY_OUTBOUND_ID&lt;/code&gt; property</td>
</tr>
<tr>
<td><code>allow-unsigned</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Configure support for unsigned JWT</td>
</tr>
<tr>
<td><a id="atn-token"></a><a href="io.helidon.security.providers.jwt.AtnTokenConfig.md"><code>atn-token</code></a></td>
<td></td>
<td></td>
<td>Configuration for atn-token</td>
</tr>
<tr>
<td><code>authenticate</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to authenticate requests</td>
</tr>
<tr>
<td><code>optional</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether authentication is required</td>
</tr>
<tr>
<td><a id="principal-type"></a><a href="io.helidon.security.SubjectType.md"><code>principal-type</code></a></td>
<td><code>SubjectType</code></td>
<td><code>USER</code></td>
<td>Principal type this provider extracts (and also propagates)</td>
</tr>
<tr>
<td><code>propagate</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to propagate identity</td>
</tr>
<tr>
<td><a id="sign-token"></a><a href="io.helidon.security.providers.common.OutboundConfig.md"><code>sign-token</code></a></td>
<td><code>OutboundConfig</code></td>
<td></td>
<td>Configuration of outbound rules</td>
</tr>
<tr>
<td><code>use-jwt-groups</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Claim &lt;code&gt;groups&lt;/code&gt; from JWT will be used to automatically add  groups to current subject (may be used with &lt;code&gt;jakarta.annotation.security.RolesAllowed&lt;/code&gt; annotation)</td>
</tr>
</tbody>
</table>


## Usages

- [`security.providers.jwt`](io.helidon.security.spi.SecurityProvider.md#jwt)
- [`server.features.security.security.providers.jwt`](io.helidon.security.spi.SecurityProvider.md#jwt)

---

See the [manifest](manifest.md) for all available types.
