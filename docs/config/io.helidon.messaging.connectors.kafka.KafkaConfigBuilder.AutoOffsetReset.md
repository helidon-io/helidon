# io.<wbr>helidon.<wbr>messaging.<wbr>connectors.<wbr>kafka.<wbr>Kafka<wbr>Config<wbr>Builder.<wbr>Auto<wbr>Offset<wbr>Reset

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
<td><code>LATEST</code></td>
<td>Automatically reset the offset to the earliest offset</td>
</tr>
<tr>
<td><code>EARLIEST</code></td>
<td>Automatically reset the offset to the latest offset</td>
</tr>
<tr>
<td><code>NONE</code></td>
<td>Throw exception to the consumer if no previous offset is found for the consumer's group</td>
</tr>
</tbody>
</table>

---

See the [manifest](manifest.md) for all available types.
