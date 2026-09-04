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
<td>Terminal disposition after delivery attempts are exhausted; <code>Failure<wbr>Disposition#<wbr>DROP</code> and <code>Failure<wbr>Disposition#<wbr>DEAD_<wbr>LETTER</code> require positive maximum attempts, and dead letter also requires a target channel</td>
</tr>
<tr>
<td>
<a id="retry"></a>
<a href="io.helidon.messaging.RetryConfig.md">
<code>retry</code>
</a>
</td>
<td>
<code>Retry<wbr>Config</code>
</td>
<td>
</td>
<td>Retry configuration</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
