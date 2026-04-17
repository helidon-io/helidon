# com.acme.AcmeTracingConfig

## Description

ACME Tracing configuration

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
<th>Key</th><th>Type</th><th>Default</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>port</code></td>
<td class="cm-type-cell"><code class="cm-truncate-value">Integer</code></td>
<td class="cm-default-cell"><code class="cm-truncate-value">16686</code></td>
<td>Tracing backend port</td>
</tr>
<tr>
<td><code>host</code></td>
<td class="cm-type-cell"><code class="cm-truncate-value">String</code></td>
<td class="cm-default-cell"></td>
<td>Tracing backend host</td>
</tr>
<tr>
<td><code>tags</code></td>
<td class="cm-type-cell"><code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code></td>
<td class="cm-default-cell"></td>
<td>System tags</td>
</tr>
</tbody>
</table>


## Usages

- [`server.features.tracing`](com.acme.AcmeFeature.md#tracing)

---

See the [manifest](manifest.md) for all available types.
