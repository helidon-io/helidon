# io.helidon.security.providers.common.EvictableCache

## Description

Generic cache with eviction support

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
<td><code>cache-enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>If the cacheEnabled is set to false, no caching will be done</td>
</tr>
<tr>
<td><code>parallelism-threshold</code></td>
<td><code>Long</code></td>
<td><code>10000</code></td>
<td>Configure parallelism threshold</td>
</tr>
<tr>
<td><code>evictor-class</code></td>
<td><code>Class</code></td>
<td></td>
<td>Configure evictor to check if a record is still valid</td>
</tr>
<tr>
<td><code>cache-evict-delay-millis</code></td>
<td><code>Long</code></td>
<td><code>60000</code></td>
<td>Delay from the creation of the cache to first eviction</td>
</tr>
<tr>
<td><code>cache-evict-period-millis</code></td>
<td><code>Long</code></td>
<td><code>300000</code></td>
<td>How often to evict records</td>
</tr>
<tr>
<td><code>max-size</code></td>
<td><code>Long</code></td>
<td><code>100000</code></td>
<td>Configure maximal cache size</td>
</tr>
<tr>
<td><code>cache-timeout-millis</code></td>
<td><code>Long</code></td>
<td><code>3600000</code></td>
<td>Configure record timeout since last access</td>
</tr>
<tr>
<td><code>cache-overall-timeout-millis</code></td>
<td><code>Long</code></td>
<td><code>3600000</code></td>
<td>Configure record timeout since its creation</td>
</tr>
</tbody>
</table>


## Usages

- [`security.providers.idcs-role-mapper.cache-config`](io.helidon.security.providers.IdcsRoleMapperConfig.md#cache-config)
- [`server.features.security.security.providers.idcs-role-mapper.cache-config`](io.helidon.server.features.security.security.providers.IdcsRoleMapperConfig.md#cache-config)

---

See the [manifest](manifest.md) for all available types.
