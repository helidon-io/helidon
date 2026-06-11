# io.helidon.webclient.api.WebClientCookieManager

## Description

Helidon WebClient cookie manager

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
<code>automatic-<wbr>store-<wbr>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether automatic cookie store is enabled or not</td>
</tr>
<tr>
<td>
<code>cookie-<wbr>policy</code>
</td>
<td>
<code>Cookie<wbr>Policy</code>
</td>
<td>
<code>java.<wbr>net.<wbr>Cookie<wbr>Policy.<wbr>ACCEPT_<wbr>ORIGINAL_<wbr>SERVER</code>
</td>
<td>Current cookie policy for this client</td>
</tr>
<tr>
<td>
<code>default-<wbr>cookies</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> String&gt;</code>
</td>
<td>
</td>
<td>Map of default cookies to include in all requests if cookies enabled</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
