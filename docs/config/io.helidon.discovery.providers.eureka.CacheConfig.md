# io.helidon.discovery.providers.eureka.CacheConfig

## Description

Prototypical state for the portion of Eureka Discovery configuration related to a local cache of Eureka server information

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
<code>sync-interval</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT30S</code>
</td>
<td>The time between retrievals of service information from the Eureka server; 30 seconds by default</td>
</tr>
<tr>
<td>
<code>defer-sync</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether to defer immediate cache synchronization; <code>false</code> by default</td>
</tr>
<tr>
<td>
<code>fetch-thread-name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value" title="Eureka registry fetch thread">Eureka registry fetch thread</code>
</td>
<td>The name of the <code>Thread</code> used to retrieve service information from the Eureka server; "Eureka registry fetch thread" by default</td>
</tr>
<tr>
<td>
<code>compute-changes</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether the state of the cache should be computed from changes reported by Eureka, or replaced in full; <code>true</code> by default</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether a local cache of Eureka information is used or not; <code>true</code> by default</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
