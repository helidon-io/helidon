# io.helidon.webserver.security.SecurityFeature

## Description

Configuration of security feature fow webserver

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
<td><a id="security"></a><a href="io.helidon.security.Security.md"><code>security</code></a></td>
<td><code>Security</code></td>
<td></td>
<td>Security associated with this feature</td>
</tr>
<tr>
<td><a id="defaults"></a><a href="io.helidon.webserver.security.SecurityHandler.md"><code>defaults</code></a></td>
<td><code>SecurityHandler</code></td>
<td></td>
<td>The default security handler</td>
</tr>
<tr>
<td><a id="paths"></a><a href="io.helidon.webserver.security.PathsConfig.md"><code>paths</code></a></td>
<td><code>List&lt;PathsConfig&gt;</code></td>
<td></td>
<td>Configuration for webserver paths</td>
</tr>
<tr>
<td><code>weight</code></td>
<td><code>Double</code></td>
<td><code>800.0</code></td>
<td>Weight of the security feature</td>
</tr>
</tbody>
</table>


## Usages

- [`server.features.security`](io.helidon.webserver.spi.ServerFeature.md#security)

---

See the [manifest](manifest.md) for all available types.
