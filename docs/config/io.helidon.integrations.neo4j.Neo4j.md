# io.helidon.integrations.neo4j.Neo4j

## Description

Main entry point for Neo4j support for Helidon

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
<td><code>idle-time-before-connection-test</code></td>
<td><code>Duration</code></td>
<td><code>PT1MS</code></td>
<td>Set idle time</td>
</tr>
<tr>
<td><code>certificate</code></td>
<td><code>Path</code></td>
<td></td>
<td>Set certificate path</td>
</tr>
<tr>
<td><code>max-connection-pool-size</code></td>
<td><code>Integer</code></td>
<td><code>100</code></td>
<td>Set pool size</td>
</tr>
<tr>
<td><code>hostname-verification-enabled</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Enable hostname verification</td>
</tr>
<tr>
<td><code>metrics-enabled</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Enable metrics</td>
</tr>
<tr>
<td><a id="trust-strategy"></a><a href="io.helidon.integrations.neo4j.Neo4j.Builder.TrustStrategy.md"><code>trust-strategy</code></a></td>
<td><code>TrustStrategy</code></td>
<td></td>
<td>Set trust strategy</td>
</tr>
<tr>
<td><code>uri</code></td>
<td><code>String</code></td>
<td></td>
<td>Create uri</td>
</tr>
<tr>
<td><code>connection-acquisition-timeout</code></td>
<td><code>Duration</code></td>
<td><code>PT1M</code></td>
<td>Set connection acquisition timeout</td>
</tr>
<tr>
<td><code>authentication-enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Enable authentication</td>
</tr>
<tr>
<td><code>password</code></td>
<td><code>String</code></td>
<td></td>
<td>Create password</td>
</tr>
<tr>
<td><code>encrypted</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Enable encrypted field</td>
</tr>
<tr>
<td><code>log-leaked-sessions</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Enable log leaked sessions</td>
</tr>
<tr>
<td><code>max-connection-lifetime</code></td>
<td><code>Duration</code></td>
<td><code>PT5H</code></td>
<td>Set max life time</td>
</tr>
<tr>
<td><code>username</code></td>
<td><code>String</code></td>
<td></td>
<td>Create username</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
