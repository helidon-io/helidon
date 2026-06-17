# io.<wbr>helidon.<wbr>webserver.<wbr>observe.<wbr>metrics.<wbr>Auto<wbr>Http<wbr>Metrics<wbr>Config

## Description

Automatic metrics collection settings

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
<code>opt-<wbr>in</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>Elective attribute for which to opt in</td>
</tr>
<tr>
<td>
<a id="paths"></a>
<a href="io.helidon.webserver.observe.metrics.AutoHttpMetricsPathConfig.md">
<code>paths</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Auto<wbr>Http<wbr>Metrics<wbr>Path<wbr>Config&gt;</code>
</td>
<td>
</td>
<td>Automatic metrics collection settings</td>
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
<td>Socket names for sockets to be instrumented with automatic metrics</td>
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
<td>Whether automatic metrics collection as a whole is enabled</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.MetricsConfig.md#auto-http-metrics"><code>metrics.<wbr>auto-<wbr>http-<wbr>metrics</code></a>
- <a href="io.helidon.webserver.observe.metrics.MetricsObserver.md#auto-http-metrics"><code>server.<wbr>features.<wbr>observe.<wbr>observers.<wbr>metrics.<wbr>auto-<wbr>http-<wbr>metrics</code></a>

---

See the [manifest](manifest.md) for all available types.
