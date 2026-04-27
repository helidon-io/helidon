# io.helidon.telemetry.otelconfig.HelidonOpenTelemetry

## Description

OpenTelemetry settings

## Configuration options

<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
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
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether the OpenTelemetry support is enabled</td>
</tr>
<tr>
<td>
<code>global</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether the <code>io.opentelemetry.api.OpenTelemetry</code> instance created from this configuration should be made the global one</td>
</tr>
<tr>
<td>
<code>propagators</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;CustomMethods&gt;">List&lt;CustomMethods&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>OpenTelemetry <code>io.opentelemetry.context.propagation.TextMapPropagator</code> instances added explicitly by the app</td>
</tr>
<tr>
<td>
<code>service</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Service name used in sending telemetry data to the collector</td>
</tr>
<tr>
<td>
<a id="signals"></a>
<a href="io.helidon.telemetry.SignalsConfig.md">
<code>signals</code>
</a>
</td>
<td class="cm-type-cell">
</td>
<td class="cm-default-cell">
</td>
<td>Configuration for signals</td>
</tr>
</tbody>
</table>



## Usages

- [`telemetry`](config_reference.md#telemetry)

---

See the [manifest](manifest.md) for all available types.
