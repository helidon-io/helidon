# io.helidon.webserver.cors.CorsPathConfig

## Description

Configuration of CORS for a specific path

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
<code>allow-headers</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">*</code>
</td>
<td>Set of allowed headers, defaults to all</td>
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
<td>Whether to allow credentials</td>
</tr>
<tr>
<td>
<code>max-age</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="PathCustomMethods">PathCustomMethods</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT1H</code>
</td>
<td>Max age as a duration</td>
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
<td>Set of allowed origins, defaults to all</td>
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
<td>Set of exposed headers, defaults to none</td>
</tr>
<tr>
<td>
<code>path-pattern</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Path pattern to apply this configuration for</td>
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
<td>Set of allowed methods, defaults to all</td>
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
<td>Whether this CORS configuration should be enabled or not</td>
</tr>
</tbody>
</table>



## Usages

- [`cors.paths`](io.helidon.webserver.cors.CorsFeature.md#paths)
- [`server.features.cors.paths`](io.helidon.webserver.cors.CorsFeature.md#paths)

---

See the [manifest](manifest.md) for all available types.
