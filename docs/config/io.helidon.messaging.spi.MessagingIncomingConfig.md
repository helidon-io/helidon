# io.<wbr>helidon.<wbr>messaging.<wbr>spi.<wbr>Messaging<wbr>Incoming<wbr>Config

## Description

Common configuration of an incoming messaging channel connection

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
<a id="execution"></a>
<a href="io.helidon.messaging.MessagingExecutionConfig.md">
<code>execution</code>
</a>
</td>
<td>
<code>Messaging<wbr>Execution<wbr>Config</code>
</td>
<td>Execution overrides for this channel</td>
</tr>
<tr>
<td>
<code>connector</code>
</td>
<td>
<code>String</code>
</td>
<td>Name of the configured connector instance used by this channel connection</td>
</tr>
<tr>
<td>
<a id="failure"></a>
<a href="io.helidon.messaging.MessagingFailureConfig.md">
<code>failure</code>
</a>
</td>
<td>
<code>Messaging<wbr>Failure<wbr>Config</code>
</td>
<td>Typed failure-policy overrides for incoming deliveries</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.messaging.MessagingGraph.md#incoming"><code>messaging.<wbr>incoming</code></a>

---

See the [manifest](manifest.md) for all available types.
