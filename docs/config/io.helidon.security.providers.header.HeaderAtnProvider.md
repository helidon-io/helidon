# io.helidon.security.providers.header.HeaderAtnProvider

## Description

Security provider that extracts a username (or service name) from a header

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



## Usages

- [`security.providers.header-atn`](io.helidon.security.spi.SecurityProvider.md#header-atn)
- [`server.features.security.security.providers.header-atn`](io.helidon.security.spi.SecurityProvider.md#header-atn)

---

See the [manifest](manifest.md) for all available types.
