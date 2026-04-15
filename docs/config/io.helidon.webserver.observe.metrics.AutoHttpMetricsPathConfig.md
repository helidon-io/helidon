# io.helidon.webserver.observe.metrics.AutoHttpMetricsPathConfig

## Description

Settings for path-based automatic metrics configuration

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Default Value</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>path</code></td>
<td><code>String</code></td>
<td></td>
<td>Path matching expression for this path config entry</td>
</tr>
<tr>
<td><code>methods</code></td>
<td><code>List&lt;String&gt;</code></td>
<td></td>
<td>HTTP methods for which this path config applies; default is to match all HTTP methods</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether automatic metrics are to be enabled for requests which match the specified &lt;code&gt;io.helidon.http.PathMatcher&lt;/code&gt; and HTTP methods</td>
</tr>
</tbody>
</table>


## Usages

- [`metrics.auto-http-metrics.paths`](io.helidon.webserver.observe.metrics.AutoHttpMetricsConfig.md#paths)
- [`server.features.observe.observers.metrics.auto-http-metrics.paths`](io.helidon.webserver.observe.metrics.AutoHttpMetricsConfig.md#paths)

---

See the [manifest](manifest.md) for all available types.
