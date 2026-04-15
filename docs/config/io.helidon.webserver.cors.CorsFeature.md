# io.helidon.webserver.cors.CorsFeature

## Description

Configuration of CORS feature

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
<td><a id="paths"></a><a href="io.helidon.webserver.cors.CorsPathConfig.md"><code>paths</code></a></td>
<td><code>List&lt;CorsPathConfig&gt;</code></td>
<td></td>
<td>Per path configuration</td>
</tr>
<tr>
<td><code>weight</code></td>
<td><code>Double</code></td>
<td><code>850.0</code></td>
<td>Weight of the CORS feature</td>
</tr>
<tr>
<td><code>add-defaults</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to add a default path configuration, that matches all paths, &lt;code&gt;GET, HEAD, POST&lt;/code&gt; methods, and allows all origins, methods, and headers</td>
</tr>
<tr>
<td><code>sockets</code></td>
<td><code>List&lt;String&gt;</code></td>
<td></td>
<td>List of sockets to register this feature on</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>This feature can be disabled</td>
</tr>
<tr>
<td><code>paths-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;paths&lt;/code&gt;</td>
</tr>
</tbody>
</table>


## Usages

- [`cors`](config_reference.md#cors)
- [`server.features.cors`](io.helidon.webserver.spi.ServerFeature.md#cors)

---

See the [manifest](manifest.md) for all available types.
