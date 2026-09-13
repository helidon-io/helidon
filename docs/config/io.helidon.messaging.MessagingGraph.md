# io.<wbr>helidon.<wbr>messaging.<wbr>Messaging<wbr>Graph

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
<a id="incoming"></a>
<a href="io.helidon.messaging.spi.MessagingIncomingConfig.md">
<code>incoming</code>
</a>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> Messaging<wbr>Incoming<wbr>Config&gt;</code>
</td>
<td>
</td>
<td>Incoming channel configurations, keyed by channel name, whose execution settings take precedence over outgoing configurations of the same logical channel</td>
</tr>
<tr>
<td>
<a id="outgoing"></a>
<a href="io.helidon.messaging.spi.MessagingOutgoingConfig.md">
<code>outgoing</code>
</a>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> Messaging<wbr>Outgoing<wbr>Config&gt;</code>
</td>
<td>
</td>
<td>Outgoing channel configurations, keyed by channel name; their execution settings apply only when the logical channel has no incoming configuration</td>
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
<code>max-<wbr>pending-<wbr>messages</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>1024</code>
</td>
<td>Positive maximum number of messages retained by waiting callers and open connector reservations</td>
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
<td>Positive maximum number of admitted messages, including queued and executing deliveries</td>
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
<td>Positive maximum number of waiting callers and open connector reservations</td>
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
<td>Positive global maximum shutdown and failed-startup rollback time, representable in nanoseconds</td>
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
<td>Positive maximum capacity-wait time, representable in nanoseconds</td>
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
<td>Maximum number of admitted deliveries waiting for execution</td>
</tr>
</tbody>
</table>



## Usages

- <a href="config_reference.md#messaging"><code>messaging</code></a>

---

See the [manifest](manifest.md) for all available types.
