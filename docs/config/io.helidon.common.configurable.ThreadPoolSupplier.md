# io.helidon.common.configurable.ThreadPoolSupplier

## Description

Supplier of a custom thread pool

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
<code>10</code>
</td>
<td>Core pool size of the thread pool executor</td>
</tr>
<tr>
<td>
<code>max-<wbr>pool-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>50</code>
</td>
<td>Max pool size of the thread pool executor</td>
</tr>
<tr>
<td>
<code>keep-<wbr>alive</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT3M</code>
</td>
<td>Keep alive of the thread pool executor</td>
</tr>
<tr>
<td>
<code>should-<wbr>prestart</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
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
<code>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Name of this thread pool executor</td>
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
<tr>
<td>
<code>growth-<wbr>rate</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>0</code>
</td>
<td>The percentage of task submissions that should result in adding threads, expressed as a value from 1 to 100</td>
</tr>
<tr>
<td>
<code>queue-<wbr>capacity</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>10000</code>
</td>
<td>Queue capacity of the thread pool executor</td>
</tr>
<tr>
<td>
<code>growth-<wbr>threshold</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>1000</code>
</td>
<td>The queue size above which pool growth will be considered if the pool is not fixed size</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
