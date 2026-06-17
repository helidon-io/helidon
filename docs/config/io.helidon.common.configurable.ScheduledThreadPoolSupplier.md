# io.<wbr>helidon.<wbr>common.<wbr>configurable.<wbr>Scheduled<wbr>Thread<wbr>Pool<wbr>Supplier

## Description

Supplier of a custom scheduled thread pool

## Configuration options


<table>
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
<code>core-<wbr>pool-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>16</code>
</td>
<td>Core pool size of the thread pool executor</td>
</tr>
<tr>
<td>
<code>prestart</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether to prestart core threads in this thread pool executor</td>
</tr>
<tr>
<td>
<code>thread-<wbr>name-<wbr>prefix</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>helidon-</code>
</td>
<td>Name prefix for threads in this thread pool executor</td>
</tr>
<tr>
<td>
<code>virtual-<wbr>threads</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
</td>
<td>When configured to <code>true</code>, an unbounded virtual executor service (project Loom) will be used</td>
</tr>
<tr>
<td>
<code>is-<wbr>daemon</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Is daemon of the thread pool executor</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
