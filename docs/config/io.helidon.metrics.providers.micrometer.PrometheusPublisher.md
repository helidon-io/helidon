# io.helidon.metrics.providers.micrometer.PrometheusPublisher

## Description

Settings for a Micrometer Prometheus meter registry

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
<code>prefix</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Property name prefix</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td><code>N/A</code></td>
</tr>
<tr>
<td>
<code>interval</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
</td>
<td>Step size used in computing "windowed" statistics</td>
</tr>
<tr>
<td>
<code>descriptions</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Whether to include meter descriptions in Prometheus output</td>
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
<td>Whether the configured publisher is enabled</td>
</tr>
</tbody>
</table>



## Usages

- [`metrics.publishers.prometheus`](io.helidon.metrics.api.MetricsPublisher.md#prometheus)
- [`server.features.observe.observers.metrics.publishers.prometheus`](io.helidon.metrics.api.MetricsPublisher.md#prometheus)

---

See the [manifest](manifest.md) for all available types.
