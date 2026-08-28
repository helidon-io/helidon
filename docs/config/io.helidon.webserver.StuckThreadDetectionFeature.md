# io.<wbr>helidon.<wbr>webserver.<wbr>Stuck<wbr>Thread<wbr>Detection<wbr>Feature

## Description

Configuration of the stuck thread detection feature

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
<code>check-<wbr>period</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT1M</code>
</td>
<td>Period between scans of executing request threads</td>
</tr>
<tr>
<td>
<code>weight</code>
</td>
<td>
<code>Double</code>
</td>
<td>
<code>1050.<wbr>0</code>
</td>
<td>Weight of the feature</td>
</tr>
<tr>
<td>
<code>threshold</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT10M</code>
</td>
<td>Minimum time a request must be executing before its thread is reported as stuck</td>
</tr>
<tr>
<td>
<code>sockets</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>List of sockets to register this feature on</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether this feature is enabled</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.webserver.spi.ServerFeature.md#stuck-thread-detection"><code>server.<wbr>features.<wbr>stuck-<wbr>thread-<wbr>detection</code></a>

---

See the [manifest](manifest.md) for all available types.
