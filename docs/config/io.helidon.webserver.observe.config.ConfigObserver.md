# io.<wbr>helidon.<wbr>webserver.<wbr>observe.<wbr>config.<wbr>Config<wbr>Observer

## Description

Configuration of Config Observer

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
<code>config</code>
</td>
<td>Endpoint this observer is available on</td>
</tr>
<tr>
<td>
<code>permit-<wbr>all</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
</td>
<td>Permit all access, even when not authorized</td>
</tr>
<tr>
<td>
<code>secrets</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
<code>.*password,<wbr> .*passphrase,<wbr> .*secret</code>
</td>
<td>Secret patterns (regular expressions) to exclude from output</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.webserver.observe.spi.Observer.md#config"><code>server.<wbr>features.<wbr>observe.<wbr>observers.<wbr>config</code></a>

---

See the [manifest](manifest.md) for all available types.
