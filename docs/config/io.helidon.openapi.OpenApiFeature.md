# io.helidon.openapi.OpenApiFeature

## Description

&lt;code&gt;OpenApiFeature&lt;/code&gt; prototype

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
<td><code>web-context</code></td>
<td><code>String</code></td>
<td><code>/openapi</code></td>
<td>Web context path for the OpenAPI endpoint</td>
</tr>
<tr>
<td><a id="manager"></a><a href="io.helidon.openapi.OpenApiManager.md"><code>manager</code></a></td>
<td><code>OpenApiManager</code></td>
<td></td>
<td>OpenAPI manager</td>
</tr>
<tr>
<td><code>services-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;services&lt;/code&gt;</td>
</tr>
<tr>
<td><code>roles</code></td>
<td><code>List&lt;String&gt;</code></td>
<td><code>openapi</code></td>
<td>Hints for role names the user is expected to be in</td>
</tr>
<tr>
<td><code>static-file</code></td>
<td><code>String</code></td>
<td></td>
<td>Path of the static OpenAPI document file</td>
</tr>
<tr>
<td><code>weight</code></td>
<td><code>Double</code></td>
<td><code>90.0</code></td>
<td>Weight of the OpenAPI feature</td>
</tr>
<tr>
<td><a id="services"></a><a href="io.helidon.openapi.OpenApiService.md"><code>services</code></a></td>
<td><code>List&lt;OpenApiService&gt;</code></td>
<td></td>
<td>OpenAPI services</td>
</tr>
<tr>
<td><code>sockets</code></td>
<td><code>List&lt;String&gt;</code></td>
<td></td>
<td>List of sockets to register this feature on</td>
</tr>
<tr>
<td><code>manager-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;manager&lt;/code&gt;</td>
</tr>
<tr>
<td><code>permit-all</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to allow anybody to access the endpoint</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Sets whether the feature should be enabled</td>
</tr>
</tbody>
</table>

### Deprecated Options

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><a id="cors"></a><a href="io.helidon.cors.CrossOriginConfig.md"><code>cors</code></a></td>
<td><code>CrossOriginConfig</code></td>
<td>CORS config</td>
</tr>
</tbody>
</table>

## Usages

- [`openapi`](config_reference.md#openapi)
- [`server.features.openapi`](io.helidon.webserver.spi.ServerFeature.md#openapi)

---

See the [manifest](manifest.md) for all available types.
