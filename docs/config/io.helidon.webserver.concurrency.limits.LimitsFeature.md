# io.helidon.webserver.concurrency.limits.LimitsFeature

## Description

Server feature that adds limits as filters

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
<td><code>concurrency-limit-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;concurrency-limit&lt;/code&gt;</td>
</tr>
<tr>
<td><a id="concurrency-limit"></a><a href="io.helidon.common.concurrency.limits.Limit.md"><code>concurrency-limit</code></a></td>
<td><code>Limit</code></td>
<td></td>
<td>Concurrency limit to use to limit concurrent execution of incoming requests</td>
</tr>
<tr>
<td><code>weight</code></td>
<td><code>Double</code></td>
<td><code>2000.0</code></td>
<td>Weight of the context feature</td>
</tr>
<tr>
<td><code>sockets</code></td>
<td><code>List&lt;String&gt;</code></td>
<td></td>
<td>List of sockets to register this feature on</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether this feature is enabled, defaults to &lt;code&gt;true&lt;/code&gt;</td>
</tr>
</tbody>
</table>


## Usages

- [`server.features.limits`](io.helidon.webserver.spi.ServerFeature.md#limits)

---

See the [manifest](manifest.md) for all available types.
