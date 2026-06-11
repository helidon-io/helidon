# io.helidon.webserver.observe.metrics.AutoHttpMetricsPathConfig

## Description

Settings for path-based automatic metrics configuration

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
<code>path</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Path matching expression for this path config entry</td>
</tr>
<tr>
<td>
<code>methods</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>HTTP methods for which this path config applies; default is to match all HTTP methods</td>
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
<td>Whether automatic metrics are to be enabled for requests which match the specified <code>io.<wbr>helidon.<wbr>http.<wbr>Path<wbr>Matcher</code> and HTTP methods</td>
</tr>
</tbody>
</table>



## Usages

- [`metrics.auto-http-metrics.paths`](io.helidon.webserver.observe.metrics.AutoHttpMetricsConfig.md#paths)
- [`server.features.observe.observers.metrics.auto-http-metrics.paths`](io.helidon.webserver.observe.metrics.AutoHttpMetricsConfig.md#paths)

---

See the [manifest](manifest.md) for all available types.
