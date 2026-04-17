# com.acme.AcmeBulkheadConfig

## Description

ACME Bulkhead configuration

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
<td><code>limit</code></td>
<td class="cm-type-cell"><code class="cm-truncate-value">Integer</code></td>
<td class="cm-default-cell"><code class="cm-truncate-value">10</code></td>
<td>Concurrent limit</td>
</tr>
<tr>
<td><code>fair</code></td>
<td class="cm-type-cell"><code class="cm-truncate-value">Boolean</code></td>
<td class="cm-default-cell"><code class="cm-truncate-value">false</code></td>
<td>Fair queue ordering</td>
</tr>
</tbody>
</table>


## Usages

- [`fault-tolerance.bulkheads`](io.helidon.FaultToleranceConfig.md#bulkheads)

---

See the [manifest](manifest.md) for all available types.
