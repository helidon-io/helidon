# io.helidon.messaging.connectors.aq.AqConfigBuilder

## Description

Build AQ specific config

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
<code>message-<wbr>selector</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>JMS API message selector expression based on a subset of the SQL92</td>
</tr>
<tr>
<td>
<code>named-<wbr>factory</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Select <code>jakarta.<wbr>jms.<wbr>Connection<wbr>Factory Connection<wbr>Factory</code> in case factory is injected as a named bean or configured with name</td>
</tr>
<tr>
<td>
<code>destination</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Queue or topic name</td>
</tr>
<tr>
<td>
<code>subscriber-<wbr>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Subscriber name used to identify a durable subscription</td>
</tr>
<tr>
<td>
<code>transacted</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Indicates whether the session will use a local transaction</td>
</tr>
<tr>
<td>
<code>non-<wbr>local</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>When set to <code>true</code>, messages published by this connection, or any connection with the same client identifier, will not be delivered to this durable subscription</td>
</tr>
<tr>
<td>
<a id="type"></a>
<a href="io.helidon.messaging.connectors.jms.Type.md">
<code>type</code>
</a>
</td>
<td>
<code>Type</code>
</td>
<td>
<code>QUEUE</code>
</td>
<td>Specify if connection is <code>io.<wbr>helidon.<wbr>messaging.<wbr>connectors.<wbr>jms.<wbr>Type#<wbr>QUEUE queue</code> or <code>io.<wbr>helidon.<wbr>messaging.<wbr>connectors.<wbr>jms.<wbr>Type#<wbr>TOPIC topic</code></td>
</tr>
<tr>
<td>
<code>durable</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Indicates whether the consumer should be created as durable (only relevant for topic destinations)</td>
</tr>
<tr>
<td>
<code>password</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Password used for creating JMS connection</td>
</tr>
<tr>
<td>
<code>poll-<wbr>timeout</code>
</td>
<td>
<code>Long</code>
</td>
<td>
<code>50</code>
</td>
<td>Timeout for polling for next message in every poll cycle in millis</td>
</tr>
<tr>
<td>
<code>data-<wbr>source</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Mapping to <code>javax.<wbr>sql.<wbr>Data<wbr>Source Data<wbr>Source</code> supplied with <code>io.<wbr>helidon.<wbr>messaging.<wbr>connectors.<wbr>aq.Aq<wbr>Connector.<wbr>AqConnector<wbr>Builder#<wbr>data<wbr>Source(<wbr>String,<wbr> javax.<wbr>sql.<wbr>Data<wbr>Source) Aq<wbr>Connector<wbr>Builder.<wbr>data<wbr>Source(<wbr>)</code></td>
</tr>
<tr>
<td>
<code>topic</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Use supplied destination name and <code>Type#<wbr>TOPIC TOPIC</code> as type</td>
</tr>
<tr>
<td>
<a id="acknowledge-mode"></a>
<a href="io.helidon.messaging.connectors.jms.AcknowledgeMode.md">
<code>acknowledge-<wbr>mode</code>
</a>
</td>
<td>
<code>Acknowledge<wbr>Mode</code>
</td>
<td>
<code>AUTO_<wbr>ACKNOWLEDGE</code>
</td>
<td>JMS acknowledgement mode</td>
</tr>
<tr>
<td>
<code>client-<wbr>id</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Client identifier for JMS connection</td>
</tr>
<tr>
<td>
<code>period-<wbr>executions</code>
</td>
<td>
<code>Long</code>
</td>
<td>
<code>100</code>
</td>
<td>Period for executing poll cycles in millis</td>
</tr>
<tr>
<td>
<code>queue</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Use supplied destination name and <code>Type#<wbr>QUEUE QUEUE</code> as type</td>
</tr>
<tr>
<td>
<code>session-<wbr>group-<wbr>id</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>When multiple channels share same session-group-id, they share same JMS session</td>
</tr>
<tr>
<td>
<code>username</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>User name used for creating JMS connection</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
