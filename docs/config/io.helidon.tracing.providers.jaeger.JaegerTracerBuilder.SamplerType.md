# io.helidon.tracing.providers.jaeger.JaegerTracerBuilder.SamplerType

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
<td><code>CONSTANT</code></td>
<td>Constant sampler always makes the same decision for all traces</td>
</tr>
<tr>
<td><code>RATIO</code></td>
<td>Ratio of the requests to sample, double value</td>
</tr>
</tbody>
</table>

## Usages

- [`tracing.sampler-type`](io.helidon.TracingConfig.md#sampler-type)

---

See the [manifest](manifest.md) for all available types.
