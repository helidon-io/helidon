# io.<wbr>helidon.<wbr>messaging.<wbr>Failure<wbr>Policy

## Description

Portable incoming delivery failure policy

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
<a id="dead-letter"></a>
<a href="io.helidon.messaging.DeadLetterConfig.md">
<code>dead-<wbr>letter</code>
</a>
</td>
<td>
<code>Dead<wbr>Letter<wbr>Config</code>
</td>
<td>
</td>
<td>Dead-letter delivery configuration; required for <code>Failure<wbr>Disposition#<wbr>DEAD_<wbr>LETTER</code> and invalid for other dispositions</td>
</tr>
<tr>
<td>
<a id="on-exhausted"></a>
<a href="io.helidon.messaging.FailureDisposition.md">
<code>on-<wbr>exhausted</code>
</a>
</td>
<td>
<code>Failure<wbr>Disposition</code>
</td>
<td>
<code>FAIL</code>
</td>
<td>Terminal disposition after delivery attempts are exhausted; dead letter requires a target channel</td>
</tr>
<tr>
<td>
<code>retry</code>
</td>
<td>
<code>Retry<wbr>Config</code>
</td>
<td>
</td>
<td>Fault tolerance retry configuration under <code>failure.<wbr>retry</code>, with <code>calls</code>, <code>delay</code>, <code>delay-<wbr>factor</code>, <code>jitter</code>, <code>jitter-<wbr>factor</code>, <code>max-<wbr>delay</code>, <code>overall-<wbr>timeout</code>, and <code>enable-<wbr>metrics</code>; omitted keys use messaging defaults of <code>Integer.<wbr>MAX_<wbr>VALUE</code> calls, a one-second initial delay, factor-two exponential backoff, no jitter, a one-minute maximum delay, a practically unbounded timeout, and disabled metrics</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
