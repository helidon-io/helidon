# io.<wbr>helidon.<wbr>messaging.<wbr>Messaging<wbr>Failure<wbr>Config

## Description

Incoming failure-policy overrides applied to the declared policy or messaging defaults

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
<a id="dead-letter"></a>
<a href="io.helidon.messaging.DeadLetterConfig.md">
<code>dead-<wbr>letter</code>
</a>
</td>
<td>
<code>Dead<wbr>Letter<wbr>Config</code>
</td>
<td>Dead-letter target override</td>
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
<td>Disposition used when retry attempts are exhausted</td>
</tr>
<tr>
<td>
<code>retry</code>
</td>
<td>
<code>Retry<wbr>Config</code>
</td>
<td>Complete retry instance for this incoming channel</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.messaging.spi.MessagingIncomingConfig.md#failure"><code>messaging.<wbr>incoming.<wbr>failure</code></a>

---

See the [manifest](manifest.md) for all available types.
