# io.helidon.webserver.observe.metrics.AutoHttpMetricsConfig

## Description

Automatic metrics collection settings

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
<td><code>opt-in</code></td>
<td><code>List&lt;String&gt;</code></td>
<td></td>
<td>Elective attribute for which to opt in</td>
</tr>
<tr>
<td><a id="paths"></a><a href="io.helidon.webserver.observe.metrics.AutoHttpMetricsPathConfig.md"><code>paths</code></a></td>
<td><code>List&lt;AutoHttpMetricsPathConfig&gt;</code></td>
<td></td>
<td>Automatic metrics collection settings</td>
</tr>
<tr>
<td><code>sockets</code></td>
<td><code>List&lt;String&gt;</code></td>
<td></td>
<td>Socket names for sockets to be instrumented with automatic metrics</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether automatic metrics collection as a whole is enabled</td>
</tr>
</tbody>
</table>


## Usages

- [`metrics.auto-http-metrics`](io.helidon.MetricsConfig.md#auto-http-metrics)
- [`server.features.observe.observers.metrics.auto-http-metrics`](io.helidon.webserver.observe.metrics.MetricsObserver.md#auto-http-metrics)

---

See the [manifest](manifest.md) for all available types.
