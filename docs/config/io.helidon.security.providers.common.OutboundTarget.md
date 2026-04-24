# io.helidon.security.providers.common.OutboundTarget

## Description

Configuration of outbound target

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
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>hosts</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td>Add supported host for this target</td>
</tr>
<tr>
<td>
<code>methods</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td>Add supported method for this target</td>
</tr>
<tr>
<td>
<code>paths</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td>Add supported paths for this target</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Configure the name of this outbound target</td>
</tr>
<tr>
<td>
<code>transport</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td>Add supported transports for this target</td>
</tr>
</tbody>
</table>



## Usages

- [`security.providers.google-login.outbound.outbound`](io.helidon.security.providers.common.OutboundConfig.md#outbound)
- [`security.providers.header-atn.outbound`](io.helidon.security.providers.header.HeaderAtnProvider.md#outbound)
- [`security.providers.http-basic-auth.outbound`](io.helidon.security.providers.httpauth.HttpBasicAuthProvider.md#outbound)
- [`security.providers.jwt.sign-token.outbound`](io.helidon.security.providers.common.OutboundConfig.md#outbound)
- [`security.providers.oidc.outbound`](io.helidon.security.providers.oidc.OidcProvider.md#outbound)
- [`server.features.security.security.providers.google-login.outbound.outbound`](io.helidon.security.providers.common.OutboundConfig.md#outbound)
- [`server.features.security.security.providers.header-atn.outbound`](io.helidon.security.providers.header.HeaderAtnProvider.md#outbound)
- [`server.features.security.security.providers.http-basic-auth.outbound`](io.helidon.security.providers.httpauth.HttpBasicAuthProvider.md#outbound)
- [`server.features.security.security.providers.jwt.sign-token.outbound`](io.helidon.security.providers.common.OutboundConfig.md#outbound)
- [`server.features.security.security.providers.oidc.outbound`](io.helidon.security.providers.oidc.OidcProvider.md#outbound)

---

See the [manifest](manifest.md) for all available types.
