# io.helidon.discovery.providers.eureka.CacheConfig

## Description

Prototypical state for the portion of Eureka Discovery configuration related to a local cache of Eureka server information

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
<td><code>sync-interval</code></td>
<td><code>Duration</code></td>
<td><code>PT30S</code></td>
<td>The time between retrievals of service information from the Eureka server; 30 seconds by default</td>
</tr>
<tr>
<td><code>defer-sync</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether to defer immediate cache synchronization; &lt;code&gt;false&lt;/code&gt; by default</td>
</tr>
<tr>
<td><code>fetch-thread-name</code></td>
<td><code>String</code></td>
<td><code>Eureka registry fetch thread</code></td>
<td>The name of the &lt;code&gt;Thread&lt;/code&gt; used to retrieve service information from the Eureka server; &quot;Eureka registry fetch thread&quot; by default</td>
</tr>
<tr>
<td><code>compute-changes</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether the state of the cache should be computed from changes reported by Eureka, or replaced in full; &lt;code&gt;true&lt;/code&gt; by default</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether a local cache of Eureka information is used or not; &lt;code&gt;true&lt;/code&gt; by default</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
