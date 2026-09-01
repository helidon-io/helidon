# io.<wbr>helidon.<wbr>webserver.<wbr>http.<wbr>AltSvc

## Description

Configuration of a single advertised alternative service

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
<code>protocol</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>h3</code>
</td>
<td>Advertised protocol name</td>
</tr>
<tr>
<td>
<code>port</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
</td>
<td>Advertised port</td>
</tr>
<tr>
<td>
<code>max-<wbr>age</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
</td>
<td>Advertised maximum age</td>
</tr>
<tr>
<td>
<code>persist</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether to emit the <code>persist=<wbr>1</code> parameter</td>
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
<td>Whether the alternative service should be advertised</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
