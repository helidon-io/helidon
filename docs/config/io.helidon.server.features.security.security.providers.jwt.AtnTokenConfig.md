# io.helidon.server.features.security.security.providers.jwt.AtnTokenConfig

## Description

Configuration for server.features.security.security.providers.jwt.atn-token

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
<a id="handler"></a>
<a href="io.helidon.security.util.TokenHandler.md">
<code>handler</code>
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
<a id="jwk"></a>
<a href="io.helidon.server.features.security.security.providers.jwt.atnToken.JwkConfig.md">
<code>jwk</code>
</a>
</td>
<td class="cm-type-cell">
</td>
<td class="cm-default-cell">
</td>
<td>Configuration for jwk</td>
</tr>
<tr>
<td>
<code>jwt-audience</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Audience expected in inbound JWTs</td>
</tr>
<tr>
<td>
<code>verify-signature</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Configure whether to verify signatures</td>
</tr>
</tbody>
</table>



## Usages

- [`server.features.security.security.providers.jwt.atn-token`](io.helidon.security.providers.jwt.JwtProvider.md#atn-token)

---

See the [manifest](manifest.md) for all available types.
