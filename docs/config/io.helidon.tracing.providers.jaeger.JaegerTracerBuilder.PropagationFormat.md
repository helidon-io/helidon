# io.helidon.tracing.providers.jaeger.JaegerTracerBuilder.PropagationFormat

## Description

This type is an enumeration.

## Allowed Values

<style>
    table.cm-table code {
        white-space: nowrap !important;
    }
</style>

<table class="cm-table">
<thead>
<tr>
<th>Value</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>B3</code></td>
<td>The Zipkin B3 trace context propagation format using multiple headers</td>
</tr>
<tr>
<td><code>B3_SINGLE</code></td>
<td>B3 trace context propagation using a single header</td>
</tr>
<tr>
<td><code>JAEGER</code></td>
<td>The Jaeger trace context propagation format</td>
</tr>
<tr>
<td><code>W3C</code></td>
<td>The W3C trace context propagation format</td>
</tr>
</tbody>
</table>

## Usages

- [`tracing.propagation`](io.helidon.TracingConfig.md#propagation)

---

See the [manifest](manifest.md) for all available types.
