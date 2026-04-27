# io.helidon.webserver.observe.tracing.TracingObserver

## Description

Configuration of Tracing observer

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
<code>wait-tracing-enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether waiting due to concurrency limit constraints should be traced</td>
</tr>
<tr>
<td>
<code>paths</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;PathTracingConfig&gt;">List&lt;PathTracingConfig&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Path specific configuration of tracing</td>
</tr>
<tr>
<td>
<code>weight</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Double</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">900.0</code>
</td>
<td>Weight of the feature registered with WebServer</td>
</tr>
</tbody>
</table>



## Usages

- [`server.features.observe.observers.tracing`](io.helidon.webserver.observe.spi.Observer.md#tracing)

---

See the [manifest](manifest.md) for all available types.
