# io.helidon.webserver.observe.tracing.TracingObserver

## Description

Configuration of Tracing observer

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
<td><code>wait-tracing-enabled</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether waiting due to concurrency limit constraints should be traced</td>
</tr>
<tr>
<td><code>paths</code></td>
<td><code>List&lt;PathTracingConfig&gt;</code></td>
<td></td>
<td>Path specific configuration of tracing</td>
</tr>
<tr>
<td><code>weight</code></td>
<td><code>Double</code></td>
<td><code>900.0</code></td>
<td>Weight of the feature registered with WebServer</td>
</tr>
</tbody>
</table>


## Usages

- [`server.features.observe.observers.tracing`](io.helidon.webserver.observe.spi.Observer.md#tracing)

---

See the [manifest](manifest.md) for all available types.
