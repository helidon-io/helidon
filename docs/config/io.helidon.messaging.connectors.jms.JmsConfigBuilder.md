# io.<wbr>helidon.<wbr>messaging.<wbr>connectors.<wbr>jms.<wbr>JmsConfig<wbr>Builder

## Description

Build Jms specific config

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
<td>To select from manually configured <code>jakarta.<wbr>jms.<wbr>Connection<wbr>Factory Connection<wbr>Factories</code> over <code>Jms<wbr>Connector.<wbr>JmsConnector<wbr>Builder#<wbr>connection<wbr>Factory(<wbr>String,<wbr> jakarta.<wbr>jms.<wbr>Connection<wbr>Factory) Jms<wbr>Connector<wbr>Builder#<wbr>connection<wbr>Factory(<wbr>)</code></td>
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
<td>Specify if connection is <code>Type#<wbr>QUEUE queue</code>  or <code>Type#<wbr>TOPIC topic</code></td>
</tr>
<tr>
<td>
<code>jndi-<wbr>initial-<wbr>factory</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>JNDI initial factory</td>
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
<code>jndi-<wbr>jms-<wbr>factory</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>JNDI name of JMS factory</td>
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
<code>jndi-<wbr>initial-<wbr>context-<wbr>properties</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> String&gt;</code>
</td>
<td>
</td>
<td>Environment properties used for creating initial context java.naming.factory.initial, java.naming.provider.url</td>
</tr>
<tr>
<td>
<code>jndi-<wbr>provider-<wbr>url</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>JNDI provider url</td>
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
