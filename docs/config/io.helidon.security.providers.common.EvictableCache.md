# io.helidon.security.providers.common.EvictableCache

## Description

Generic cache with eviction support

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
<code>cache-enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>If the cacheEnabled is set to false, no caching will be done</td>
</tr>
<tr>
<td>
<code>parallelism-threshold</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">10000</code>
</td>
<td>Configure parallelism threshold</td>
</tr>
<tr>
<td>
<code>evictor-class</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Class</code>
</td>
<td class="cm-default-cell">
</td>
<td>Configure evictor to check if a record is still valid</td>
</tr>
<tr>
<td>
<code>cache-evict-delay-millis</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">60000</code>
</td>
<td>Delay from the creation of the cache to first eviction</td>
</tr>
<tr>
<td>
<code>cache-evict-period-millis</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">300000</code>
</td>
<td>How often to evict records</td>
</tr>
<tr>
<td>
<code>max-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">100000</code>
</td>
<td>Configure maximal cache size</td>
</tr>
<tr>
<td>
<code>cache-timeout-millis</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">3600000</code>
</td>
<td>Configure record timeout since last access</td>
</tr>
<tr>
<td>
<code>cache-overall-timeout-millis</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">3600000</code>
</td>
<td>Configure record timeout since its creation</td>
</tr>
</tbody>
</table>



## Usages

- [`security.providers.idcs-role-mapper.cache-config`](io.helidon.security.providers.IdcsRoleMapperConfig.md#cache-config)
- [`server.features.security.security.providers.idcs-role-mapper.cache-config`](io.helidon.server.features.security.security.providers.IdcsRoleMapperConfig.md#cache-config)

---

See the [manifest](manifest.md) for all available types.
