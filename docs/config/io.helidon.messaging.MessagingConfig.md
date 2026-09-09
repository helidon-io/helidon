# io.<wbr>helidon.<wbr>messaging.<wbr>Messaging<wbr>Config

## Description

Messaging configuration

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
<a id="execution"></a>
<a href="io.helidon.messaging.MessagingExecutionConfig.md">
<code>execution</code>
</a>
</td>
<td>
<code>Messaging<wbr>Execution<wbr>Config</code>
</td>
<td>
</td>
<td>Messaging execution configuration</td>
</tr>
<tr>
<td>
<code>incoming</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> Config&gt;</code>
</td>
<td>
</td>
<td>Incoming channel configurations, keyed by channel name</td>
</tr>
<tr>
<td>
<code>outgoing</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> Config&gt;</code>
</td>
<td>
</td>
<td>Outgoing channel configurations, keyed by channel name</td>
</tr>
<tr>
<td>
<a id="connector"></a>
<a href="io.helidon.messaging.spi.MessagingConnector.md">
<code>connector</code>
</a>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> Messaging<wbr>Connector&gt;<wbr> or List&lt;<wbr>Messaging<wbr>Connector&gt;</code>
</td>
<td>
</td>
<td>Configured messaging connectors</td>
</tr>
<tr>
<td>
<code>connector-<wbr>discover-<wbr>services</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether to enable automatic service discovery for <code>connector</code></td>
</tr>
</tbody>
</table>



## Usages

- <a href="config_reference.md#messaging"><code>messaging</code></a>

---

See the [manifest](manifest.md) for all available types.
