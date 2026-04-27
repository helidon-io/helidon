# io.helidon.messaging.connectors.aq.AqConfigBuilder

## Description

Build AQ specific config

## Configuration options

<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
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
<code>message-selector</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>JMS API message selector expression based on a subset of the SQL92</td>
</tr>
<tr>
<td>
<code>named-factory</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Select <code>jakarta.jms.ConnectionFactory ConnectionFactory</code> in case factory is injected as a named bean or configured with name</td>
</tr>
<tr>
<td>
<code>destination</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Queue or topic name</td>
</tr>
<tr>
<td>
<code>subscriber-name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Subscriber name used to identify a durable subscription</td>
</tr>
<tr>
<td>
<code>transacted</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Indicates whether the session will use a local transaction</td>
</tr>
<tr>
<td>
<code>non-local</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
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
<td class="cm-type-cell">
<code class="cm-truncate-value">Type</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">QUEUE</code>
</td>
<td>Specify if connection is <code>io.helidon.messaging.connectors.jms.Type#QUEUE queue</code> or <code>io.helidon.messaging.connectors.jms.Type#TOPIC topic</code></td>
</tr>
<tr>
<td>
<code>durable</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Indicates whether the consumer should be created as durable (only relevant for topic destinations)</td>
</tr>
<tr>
<td>
<code>password</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Password used for creating JMS connection</td>
</tr>
<tr>
<td>
<code>poll-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">50</code>
</td>
<td>Timeout for polling for next message in every poll cycle in millis</td>
</tr>
<tr>
<td>
<code>data-source</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Mapping to <code>javax.sql.DataSource DataSource</code> supplied with <code>io.helidon.messaging.connectors.aq.AqConnector.AqConnectorBuilder#dataSource(String, javax.sql.DataSource) AqConnectorBuilder.dataSource()</code></td>
</tr>
<tr>
<td>
<code>topic</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Use supplied destination name and <code>Type#TOPIC TOPIC</code> as type</td>
</tr>
<tr>
<td>
<a id="acknowledge-mode"></a>
<a href="io.helidon.messaging.connectors.jms.AcknowledgeMode.md">
<code>acknowledge-mode</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="AcknowledgeMode">AcknowledgeMode</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value" title="AUTO_ACKNOWLEDGE">AUTO_ACKNOWLEDGE</code>
</td>
<td>JMS acknowledgement mode</td>
</tr>
<tr>
<td>
<code>client-id</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Client identifier for JMS connection</td>
</tr>
<tr>
<td>
<code>period-executions</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">100</code>
</td>
<td>Period for executing poll cycles in millis</td>
</tr>
<tr>
<td>
<code>queue</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Use supplied destination name and <code>Type#QUEUE QUEUE</code> as type</td>
</tr>
<tr>
<td>
<code>session-group-id</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>When multiple channels share same session-group-id, they share same JMS session</td>
</tr>
<tr>
<td>
<code>username</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>User name used for creating JMS connection</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
