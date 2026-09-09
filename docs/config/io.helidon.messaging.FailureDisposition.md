# io.<wbr>helidon.<wbr>messaging.<wbr>Failure<wbr>Disposition

## Description

This type is an enumeration.

## Allowed Values

<table>
<thead>
<tr>
<th>Value</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>FAIL</code></td>
<td>Propagate the processing failure and leave the source delivery unsettled</td>
</tr>
<tr>
<td><code>DROP</code></td>
<td>Log the processing failure and settle the source delivery without delivering it</td>
</tr>
<tr>
<td><code>DEAD_<wbr>LETTER</code></td>
<td>Deliver the failed messages to another logical messaging channel before settling the source delivery</td>
</tr>
</tbody>
</table>

---

See the [manifest](manifest.md) for all available types.
