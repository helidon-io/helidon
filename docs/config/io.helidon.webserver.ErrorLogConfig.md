# io.<wbr>helidon.<wbr>webserver.<wbr>Error<wbr>LogConfig

## Description

Limits for WebServer's internal error diagnostics, shared by all listeners of a server

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
<code>max-<wbr>errors</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>100</code>
</td>
<td>Maximum error identities retained per window</td>
</tr>
<tr>
<td>
<code>max-<wbr>total</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>100</code>
</td>
<td>Maximum detailed logs across all error identities and listeners per window</td>
</tr>
<tr>
<td>
<code>report-<wbr>interval</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT10M</code>
</td>
<td>Interval for suppression reports</td>
</tr>
<tr>
<td>
<code>window</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT1H</code>
</td>
<td>Collection window for detailed logs and retained error identities</td>
</tr>
<tr>
<td>
<code>max-<wbr>per-<wbr>error</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>1</code>
</td>
<td>Maximum detailed logs for one error identity per window</td>
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
<td>Whether to limit internal error logging</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.webserver.WebServer.md#error-log"><code>server.<wbr>error-<wbr>log</code></a>

---

See the [manifest](manifest.md) for all available types.
