# io.<wbr>helidon.<wbr>webserver.<wbr>hsts.<wbr>Hsts<wbr>Feature

## Description

Configuration of the HSTS feature

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
<code>include-<wbr>sub-<wbr>domains</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether the policy should apply to subdomains</td>
</tr>
<tr>
<td>
<code>max-<wbr>age</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>P365D</code>
</td>
<td>Max age of the HSTS policy</td>
</tr>
<tr>
<td>
<code>weight</code>
</td>
<td>
<code>Double</code>
</td>
<td>
<code>875.<wbr>0</code>
</td>
<td>Weight of the HSTS feature</td>
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
<code>preload</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether the policy should include the non-standard preload token used by browser preload lists</td>
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
<td>Whether this feature is enabled</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.webserver.spi.ServerFeature.md#hsts"><code>server.<wbr>features.<wbr>hsts</code></a>

---

See the [manifest](manifest.md) for all available types.
