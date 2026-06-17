# io.<wbr>helidon.<wbr>webserver.<wbr>observe.<wbr>health.<wbr>Health<wbr>Observer

## Description

Configuration of Health observer

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
<code>endpoint</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>health</code>
</td>
<td><code>N/<wbr>A</code></td>
</tr>
<tr>
<td>
<code>details</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether details should be printed</td>
</tr>
<tr>
<td>
<code>exclude</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>Health check names to exclude in computing the overall health of the server</td>
</tr>
<tr>
<td>
<code>use-<wbr>system-<wbr>services</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to use services discovered by <code>java.<wbr>util.<wbr>Service<wbr>Loader</code></td>
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
<td>Whether this observer is enabled</td>
</tr>
</tbody>
</table>



## Usages

- <a href="config_reference.md#health"><code>health</code></a>
- <a href="io.helidon.webserver.observe.spi.Observer.md#health"><code>server.<wbr>features.<wbr>observe.<wbr>observers.<wbr>health</code></a>

---

See the [manifest](manifest.md) for all available types.
