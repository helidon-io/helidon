# io.helidon.security.providers.jwt.JwtProvider

## Description

JWT authentication provider

## Configuration options

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



## Usages

- [`security.providers.jwt`](io.helidon.security.spi.SecurityProvider.md#jwt)
- [`server.features.security.security.providers.jwt`](io.helidon.security.spi.SecurityProvider.md#jwt)

---

See the [manifest](manifest.md) for all available types.
