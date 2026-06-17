# io.<wbr>helidon.<wbr>security.<wbr>providers.<wbr>httpsign.<wbr>Signed<wbr>Headers<wbr>Config.<wbr>Headers<wbr>Config

## Description

Configuration of headers to be signed

## Configuration options


<table>
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>always</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>Headers that must be signed (and signature validation or creation should fail if not signed or present)</td>
</tr>
<tr>
<td>
<code>if-<wbr>present</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>Headers that must be signed if present in request</td>
</tr>
<tr>
<td>
<code>method</code>
</td>
<td>
<code>String</code>
</td>
<td>HTTP method this header configuration is bound to. If not present, it is considered default header configuration</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
