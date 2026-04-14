# com.acme.AcmeTracingConfig

## Description

ACME Tracing configuration

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
<td><code>port</code></td>
<td><code>Integer</code></td>
<td><code>16686</code></td>
<td>Tracing backend port</td>
</tr>
<tr>
<td><code>host</code></td>
<td><code>String</code></td>
<td></td>
<td>Tracing backend host</td>
</tr>
<tr>
<td><code>tags</code></td>
<td><code>List&lt;String&gt;</code></td>
<td></td>
<td>System tags</td>
</tr>
</tbody>
</table>


## Usages

- [`server.features.tracing`](com.acme.AcmeFeature.md#tracing)

---

See the [manifest](manifest.md) for all available types.
