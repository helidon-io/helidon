# io.<wbr>helidon.<wbr>messaging.<wbr>Messaging<wbr>Execution<wbr>Config

## Description

Optional messaging execution overrides

## Configuration options


<table>
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>max-<wbr>pending-<wbr>messages</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Positive maximum total messages retained by waiting callers and open connector reservations</td>
</tr>
<tr>
<td>
<code>max-<wbr>in-flight-<wbr>messages</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Positive maximum number of admitted messages</td>
</tr>
<tr>
<td>
<code>max-<wbr>pending-<wbr>admissions</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Positive maximum number of callers waiting for blocking admission and open connector reservations</td>
</tr>
<tr>
<td>
<code>admission-<wbr>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>Optional positive maximum time to wait for capacity, representable in nanoseconds</td>
</tr>
<tr>
<td>
<code>queue-<wbr>capacity</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Maximum number of admitted tasks that may wait for an execution slot; must be zero or greater</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.messaging.MessagingChannelConfig.md#execution"><code>messaging.<wbr>channel.<wbr>execution</code></a>

---

See the [manifest](manifest.md) for all available types.
