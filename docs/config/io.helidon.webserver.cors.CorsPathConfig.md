# io.<wbr>helidon.<wbr>webserver.<wbr>cors.<wbr>Cors<wbr>Path<wbr>Config

## Description

Configuration of CORS for a specific path

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
<code>allow-<wbr>headers</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
<code>*</code>
</td>
<td>Set of allowed headers, defaults to all</td>
</tr>
<tr>
<td>
<code>allow-<wbr>credentials</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether to allow credentials</td>
</tr>
<tr>
<td>
<code>max-<wbr>age</code>
</td>
<td>
<code>Path<wbr>Custom<wbr>Methods</code>
</td>
<td>
<code>PT1H</code>
</td>
<td>Max age as a duration</td>
</tr>
<tr>
<td>
<code>allow-<wbr>origins</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
<code>*</code>
</td>
<td>Set of allowed origins, defaults to all</td>
</tr>
<tr>
<td>
<code>expose-<wbr>headers</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>Set of exposed headers, defaults to none</td>
</tr>
<tr>
<td>
<code>path-<wbr>pattern</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Path pattern to apply this configuration for</td>
</tr>
<tr>
<td>
<code>allow-<wbr>methods</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
<code>*</code>
</td>
<td>Set of allowed methods, defaults to all</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether this CORS configuration should be enabled or not</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.webserver.cors.CorsFeature.md#paths"><code>cors.<wbr>paths</code></a>
- <a href="io.helidon.webserver.cors.CorsFeature.md#paths"><code>server.<wbr>features.<wbr>cors.<wbr>paths</code></a>

---

See the [manifest](manifest.md) for all available types.
