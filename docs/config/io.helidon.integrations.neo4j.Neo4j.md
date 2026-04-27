# io.helidon.integrations.neo4j.Neo4j

## Description

Main entry point for Neo4j support for Helidon

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
<code>idle-time-before-connection-test</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT1MS</code>
</td>
<td>Set idle time</td>
</tr>
<tr>
<td>
<code>certificate</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Path</code>
</td>
<td class="cm-default-cell">
</td>
<td>Set certificate path</td>
</tr>
<tr>
<td>
<code>max-connection-pool-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">100</code>
</td>
<td>Set pool size</td>
</tr>
<tr>
<td>
<code>hostname-verification-enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Enable hostname verification</td>
</tr>
<tr>
<td>
<code>metrics-enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Enable metrics</td>
</tr>
<tr>
<td>
<a id="trust-strategy"></a>
<a href="io.helidon.integrations.neo4j.Neo4j.Builder.TrustStrategy.md">
<code>trust-strategy</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="TrustStrategy">TrustStrategy</code>
</td>
<td class="cm-default-cell">
</td>
<td>Set trust strategy</td>
</tr>
<tr>
<td>
<code>uri</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Create uri</td>
</tr>
<tr>
<td>
<code>connection-acquisition-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT1M</code>
</td>
<td>Set connection acquisition timeout</td>
</tr>
<tr>
<td>
<code>authentication-enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Enable authentication</td>
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
<td>Create password</td>
</tr>
<tr>
<td>
<code>encrypted</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Enable encrypted field</td>
</tr>
<tr>
<td>
<code>log-leaked-sessions</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Enable log leaked sessions</td>
</tr>
<tr>
<td>
<code>max-connection-lifetime</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT5H</code>
</td>
<td>Set max life time</td>
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
<td>Create username</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
