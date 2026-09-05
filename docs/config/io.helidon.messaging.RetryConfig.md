# io.<wbr>helidon.<wbr>messaging.<wbr>Retry<wbr>Config

## Description

Retry configuration for incoming delivery failures

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
<code>delay</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT1S</code>
</td>
<td>Positive delay before retrying a failed delivery</td>
</tr>
<tr>
<td>
<code>max-<wbr>attempts</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>0</code>
</td>
<td>Maximum total delivery attempts, including the initial attempt; zero means unlimited attempts</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
