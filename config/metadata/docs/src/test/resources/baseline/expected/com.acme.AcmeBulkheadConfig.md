# com.acme.AcmeBulkheadConfig

## Description

ACME Bulkhead configuration

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
<td><code>limit</code></td>
<td><code>Integer</code></td>
<td><code>10</code></td>
<td>Concurrent limit</td>
</tr>
<tr>
<td><code>fair</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Fair queue ordering</td>
</tr>
</tbody>
</table>


## Usages

- [`fault-tolerance.bulkheads`](io.helidon.FaultToleranceConfig.md#bulkheads)

---

See the [manifest](manifest.md) for all available types.
