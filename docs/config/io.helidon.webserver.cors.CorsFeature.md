# io.<wbr>helidon.<wbr>webserver.<wbr>cors.<wbr>Cors<wbr>Feature

## Description

Configuration of CORS feature

## Configuration options


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
<a id="paths"></a>
<a href="io.helidon.webserver.cors.CorsPathConfig.md">
<code>paths</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Cors<wbr>Path<wbr>Config&gt;</code>
</td>
<td>
</td>
<td>Per path configuration</td>
</tr>
<tr>
<td>
<code>weight</code>
</td>
<td>
<code>Double</code>
</td>
<td>
<code>850.<wbr>0</code>
</td>
<td>Weight of the CORS feature</td>
</tr>
<tr>
<td>
<code>add-<wbr>defaults</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to add a default path configuration, that matches all paths, <code>GET,<wbr> HEAD,<wbr> POST</code> methods, and allows all origins, methods, and headers</td>
</tr>
<tr>
<td>
<code>sockets</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>List of sockets to register this feature on</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
</td>
<td>This feature can be disabled</td>
</tr>
<tr>
<td>
<code>paths-<wbr>discover-<wbr>services</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to enable automatic service discovery for <code>paths</code></td>
</tr>
</tbody>
</table>



## Usages

- <a href="config_reference.md#cors"><code>cors</code></a>
- <a href="io.helidon.webserver.spi.ServerFeature.md#cors"><code>server.<wbr>features.<wbr>cors</code></a>

---

See the [manifest](manifest.md) for all available types.
