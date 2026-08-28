# io.<wbr>helidon.<wbr>security.<wbr>providers.<wbr>common.<wbr>Evictable<wbr>Cache

## Description

Generic cache with eviction support

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
<code>cache-<wbr>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>If the cacheEnabled is set to false, no caching will be done</td>
</tr>
<tr>
<td>
<code>parallelism-<wbr>threshold</code>
</td>
<td>
<code>Long</code>
</td>
<td>
<code>10000</code>
</td>
<td>Configure parallelism threshold</td>
</tr>
<tr>
<td>
<code>evictor-<wbr>class</code>
</td>
<td>
<code>Class</code>
</td>
<td>
</td>
<td>Configure evictor to check if a record is still valid</td>
</tr>
<tr>
<td>
<code>cache-<wbr>evict-<wbr>delay-<wbr>millis</code>
</td>
<td>
<code>Long</code>
</td>
<td>
<code>60000</code>
</td>
<td>Delay from the creation of the cache to first eviction</td>
</tr>
<tr>
<td>
<code>cache-<wbr>evict-<wbr>period-<wbr>millis</code>
</td>
<td>
<code>Long</code>
</td>
<td>
<code>300000</code>
</td>
<td>How often to evict records</td>
</tr>
<tr>
<td>
<code>max-<wbr>size</code>
</td>
<td>
<code>Long</code>
</td>
<td>
<code>100000</code>
</td>
<td>Configure maximal cache size</td>
</tr>
<tr>
<td>
<code>cache-<wbr>timeout-<wbr>millis</code>
</td>
<td>
<code>Long</code>
</td>
<td>
<code>3600000</code>
</td>
<td>Configure record timeout since last access</td>
</tr>
<tr>
<td>
<code>cache-<wbr>overall-<wbr>timeout-<wbr>millis</code>
</td>
<td>
<code>Long</code>
</td>
<td>
<code>3600000</code>
</td>
<td>Configure record timeout since its creation</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.security.providers.IdcsRoleMapperConfig.md#cache-config"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>cache-<wbr>config</code></a>
- <a href="io.helidon.server.features.security.security.providers.IdcsRoleMapperConfig.md#cache-config"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>cache-<wbr>config</code></a>

---

See the [manifest](manifest.md) for all available types.
