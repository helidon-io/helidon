# io.helidon.messaging.connectors.jms.JmsConfigBuilder

## Description

Build Jms specific config

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Default Value</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>message-selector</code></td>
<td><code>String</code></td>
<td></td>
<td>JMS API message selector expression based on a subset of the SQL92</td>
</tr>
<tr>
<td><code>named-factory</code></td>
<td><code>String</code></td>
<td></td>
<td>To select from manually configured &lt;code&gt;jakarta.jms.ConnectionFactory ConnectionFactories&lt;/code&gt; over &lt;code&gt;JmsConnector.JmsConnectorBuilder#connectionFactory(String, jakarta.jms.ConnectionFactory) JmsConnectorBuilder#connectionFactory()&lt;/code&gt;</td>
</tr>
<tr>
<td><code>destination</code></td>
<td><code>String</code></td>
<td></td>
<td>Queue or topic name</td>
</tr>
<tr>
<td><code>transacted</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Indicates whether the session will use a local transaction</td>
</tr>
<tr>
<td><a id="type"></a><a href="io.helidon.messaging.connectors.jms.Type.md"><code>type</code></a></td>
<td><code>Type</code></td>
<td><code>QUEUE</code></td>
<td>Specify if connection is &lt;code&gt;Type#QUEUE queue&lt;/code&gt;  or &lt;code&gt;Type#TOPIC topic&lt;/code&gt;</td>
</tr>
<tr>
<td><code>jndi-initial-factory</code></td>
<td><code>String</code></td>
<td></td>
<td>JNDI initial factory</td>
</tr>
<tr>
<td><code>password</code></td>
<td><code>String</code></td>
<td></td>
<td>Password used for creating JMS connection</td>
</tr>
<tr>
<td><code>poll-timeout</code></td>
<td><code>Long</code></td>
<td><code>50</code></td>
<td>Timeout for polling for next message in every poll cycle in millis</td>
</tr>
<tr>
<td><code>jndi-jms-factory</code></td>
<td><code>String</code></td>
<td></td>
<td>JNDI name of JMS factory</td>
</tr>
<tr>
<td><code>topic</code></td>
<td><code>String</code></td>
<td></td>
<td>Use supplied destination name and &lt;code&gt;Type#TOPIC TOPIC&lt;/code&gt; as type</td>
</tr>
<tr>
<td><a id="acknowledge-mode"></a><a href="io.helidon.messaging.connectors.jms.AcknowledgeMode.md"><code>acknowledge-mode</code></a></td>
<td><code>AcknowledgeMode</code></td>
<td><code>AUTO_ACKNOWLEDGE</code></td>
<td>JMS acknowledgement mode</td>
</tr>
<tr>
<td><code>jndi-initial-context-properties</code></td>
<td><code>Map&lt;String, String&gt;</code></td>
<td></td>
<td>Environment properties used for creating initial context java.naming.factory.initial, java.naming.provider.url</td>
</tr>
<tr>
<td><code>jndi-provider-url</code></td>
<td><code>String</code></td>
<td></td>
<td>JNDI provider url</td>
</tr>
<tr>
<td><code>period-executions</code></td>
<td><code>Long</code></td>
<td><code>100</code></td>
<td>Period for executing poll cycles in millis</td>
</tr>
<tr>
<td><code>queue</code></td>
<td><code>String</code></td>
<td></td>
<td>Use supplied destination name and &lt;code&gt;Type#QUEUE QUEUE&lt;/code&gt; as type</td>
</tr>
<tr>
<td><code>session-group-id</code></td>
<td><code>String</code></td>
<td></td>
<td>When multiple channels share same session-group-id, they share same JMS session</td>
</tr>
<tr>
<td><code>username</code></td>
<td><code>String</code></td>
<td></td>
<td>User name used for creating JMS connection</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
