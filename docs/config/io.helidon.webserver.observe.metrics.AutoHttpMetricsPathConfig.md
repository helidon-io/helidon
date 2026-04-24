# io.helidon.webserver.observe.metrics.AutoHttpMetricsPathConfig

## Description

Settings for path-based automatic metrics configuration

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }

    .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


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
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Path matching expression for this path config entry</td>
</tr>
<tr>
<td>
<code>methods</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>HTTP methods for which this path config applies; default is to match all HTTP methods</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether automatic metrics are to be enabled for requests which match the specified <code>io.helidon.http.PathMatcher</code> and HTTP methods</td>
</tr>
</tbody>
</table>



## Usages

- [`metrics.auto-http-metrics.paths`](io.helidon.webserver.observe.metrics.AutoHttpMetricsConfig.md#paths)
- [`server.features.observe.observers.metrics.auto-http-metrics.paths`](io.helidon.webserver.observe.metrics.AutoHttpMetricsConfig.md#paths)

---

See the [manifest](manifest.md) for all available types.
