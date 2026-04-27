# io.helidon.cors.CrossOriginConfig

## Description

Represents information about cross origin request sharing

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
<code>allow-headers</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">*</code>
</td>
<td>Sets the allow headers</td>
</tr>
<tr>
<td>
<code>allow-credentials</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Sets the allow credentials flag</td>
</tr>
<tr>
<td>
<code>max-age-seconds</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">3600</code>
</td>
<td>Sets the maximum age</td>
</tr>
<tr>
<td>
<code>allow-origins</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">*</code>
</td>
<td>Sets the allowOrigins</td>
</tr>
<tr>
<td>
<code>expose-headers</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Sets the expose headers</td>
</tr>
<tr>
<td>
<code>path-pattern</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">{+}</code>
</td>
<td>Updates the path prefix for this cross-origin config</td>
</tr>
<tr>
<td>
<code>allow-methods</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">*</code>
</td>
<td>Sets the allow methods</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Sets whether this config should be enabled or not</td>
</tr>
</tbody>
</table>



## Usages

- [`openapi.cors`](io.helidon.openapi.OpenApiFeature.md#cors)
- [`security.providers.idcs-role-mapper.oidc-config.cors`](io.helidon.security.providers.oidc.common.OidcConfig.md#cors)
- [`security.providers.oidc.cors`](io.helidon.security.providers.oidc.OidcProvider.md#cors)
- [`server.features.observe.cors`](io.helidon.webserver.observe.ObserveFeature.md#cors)
- [`server.features.openapi.cors`](io.helidon.openapi.OpenApiFeature.md#cors)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.cors`](io.helidon.security.providers.oidc.common.OidcConfig.md#cors)
- [`server.features.security.security.providers.oidc.cors`](io.helidon.security.providers.oidc.OidcProvider.md#cors)

---

See the [manifest](manifest.md) for all available types.
