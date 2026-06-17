# io.<wbr>helidon.<wbr>webserver.<wbr>concurrency.<wbr>limits.<wbr>Limits<wbr>Feature

## Description

Server feature that adds limits as filters

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
<code>concurrency-<wbr>limit-<wbr>discover-<wbr>services</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether to enable automatic service discovery for <code>concurrency-<wbr>limit</code></td>
</tr>
<tr>
<td>
<a id="concurrency-limit"></a>
<a href="io.helidon.common.concurrency.limits.Limit.md">
<code>concurrency-<wbr>limit</code>
</a>
</td>
<td>
<code>Limit</code>
</td>
<td>
</td>
<td>Concurrency limit to use to limit concurrent execution of incoming requests</td>
</tr>
<tr>
<td>
<code>weight</code>
</td>
<td>
<code>Double</code>
</td>
<td>
<code>2000.<wbr>0</code>
</td>
<td>Weight of the context feature</td>
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
<td>Whether this feature is enabled, defaults to <code>true</code></td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.webserver.spi.ServerFeature.md#limits"><code>server.<wbr>features.<wbr>limits</code></a>

---

See the [manifest](manifest.md) for all available types.
