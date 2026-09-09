# io.<wbr>helidon.<wbr>messaging.<wbr>Messaging<wbr>Execution<wbr>Config

## Description

Messaging admission and shutdown configuration

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
<code>max-<wbr>pending-<wbr>messages</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>1024</code>
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
<td>
<code>1024</code>
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
<td>
<code>64</code>
</td>
<td>Positive maximum number of callers waiting for blocking admission and open connector reservations</td>
</tr>
<tr>
<td>
<code>shutdown-<wbr>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT10S</code>
</td>
<td>Positive global maximum time, representable in nanoseconds, to wait for admitted messaging work to finish and graph-owned resources to close during shutdown or failed-startup rollback</td>
</tr>
<tr>
<td>
<code>admission-<wbr>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
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
<td>
<code>0</code>
</td>
<td>Maximum number of admitted tasks that may wait for an execution slot; must be zero or greater</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.MessagingConfig.md#execution"><code>messaging.<wbr>execution</code></a>

---

See the [manifest](manifest.md) for all available types.
