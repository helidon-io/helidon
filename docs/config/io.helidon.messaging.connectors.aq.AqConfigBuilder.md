# io.helidon.messaging.connectors.aq.AqConfigBuilder

## Description

Build AQ specific config

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
<td>Select &lt;code&gt;jakarta.jms.ConnectionFactory ConnectionFactory&lt;/code&gt; in case factory is injected as a named bean or configured with name</td>
</tr>
<tr>
<td><code>destination</code></td>
<td><code>String</code></td>
<td></td>
<td>Queue or topic name</td>
</tr>
<tr>
<td><code>subscriber-name</code></td>
<td><code>String</code></td>
<td></td>
<td>Subscriber name used to identify a durable subscription</td>
</tr>
<tr>
<td><code>transacted</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Indicates whether the session will use a local transaction</td>
</tr>
<tr>
<td><code>non-local</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>When set to &lt;code&gt;true&lt;/code&gt;, messages published by this connection, or any connection with the same client identifier, will not be delivered to this durable subscription</td>
</tr>
<tr>
<td><a id="type"></a><a href="io.helidon.messaging.connectors.jms.Type.md"><code>type</code></a></td>
<td><code>Type</code></td>
<td><code>QUEUE</code></td>
<td>Specify if connection is &lt;code&gt;io.helidon.messaging.connectors.jms.Type#QUEUE queue&lt;/code&gt; or &lt;code&gt;io.helidon.messaging.connectors.jms.Type#TOPIC topic&lt;/code&gt;</td>
</tr>
<tr>
<td><code>durable</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Indicates whether the consumer should be created as durable (only relevant for topic destinations)</td>
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
<td><code>data-source</code></td>
<td><code>String</code></td>
<td></td>
<td>Mapping to &lt;code&gt;javax.sql.DataSource DataSource&lt;/code&gt; supplied with &lt;code&gt;io.helidon.messaging.connectors.aq.AqConnector.AqConnectorBuilder#dataSource(String, javax.sql.DataSource) AqConnectorBuilder.dataSource()&lt;/code&gt;</td>
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
<td><code>client-id</code></td>
<td><code>String</code></td>
<td></td>
<td>Client identifier for JMS connection</td>
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
