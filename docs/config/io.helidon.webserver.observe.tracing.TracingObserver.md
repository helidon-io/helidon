# io.<wbr>helidon.<wbr>webserver.<wbr>observe.<wbr>tracing.<wbr>Tracing<wbr>Observer

## Description

Configuration of Tracing observer

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
<code>wait-<wbr>tracing-<wbr>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether waiting due to concurrency limit constraints should be traced</td>
</tr>
<tr>
<td>
<code>paths</code>
</td>
<td>
<code>List&lt;<wbr>Path<wbr>Tracing<wbr>Config&gt;</code>
</td>
<td>
</td>
<td>Path specific configuration of tracing</td>
</tr>
<tr>
<td>
<code>weight</code>
</td>
<td>
<code>Double</code>
</td>
<td>
<code>900.<wbr>0</code>
</td>
<td>Weight of the feature registered with WebServer</td>
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

- <a href="io.helidon.webserver.observe.spi.Observer.md#tracing"><code>server.<wbr>features.<wbr>observe.<wbr>observers.<wbr>tracing</code></a>

---

See the [manifest](manifest.md) for all available types.
