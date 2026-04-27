# io.helidon.webserver.cors.CorsFeature

## Description

Configuration of CORS feature

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
<a id="paths"></a>
<a href="io.helidon.webserver.cors.CorsPathConfig.md">
<code>paths</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;CorsPathConfig&gt;">List&lt;CorsPathConfig&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Per path configuration</td>
</tr>
<tr>
<td>
<code>weight</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Double</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">850.0</code>
</td>
<td>Weight of the CORS feature</td>
</tr>
<tr>
<td>
<code>add-defaults</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to add a default path configuration, that matches all paths, <code>GET, HEAD, POST</code> methods, and allows all origins, methods, and headers</td>
</tr>
<tr>
<td>
<code>sockets</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>List of sockets to register this feature on</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>This feature can be disabled</td>
</tr>
<tr>
<td>
<code>paths-discover-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to enable automatic service discovery for <code>paths</code></td>
</tr>
</tbody>
</table>



## Usages

- [`cors`](config_reference.md#cors)
- [`server.features.cors`](io.helidon.webserver.spi.ServerFeature.md#cors)

---

See the [manifest](manifest.md) for all available types.
