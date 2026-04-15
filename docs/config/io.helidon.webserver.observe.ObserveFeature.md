# io.helidon.webserver.observe.ObserveFeature

## Description

Configuration for observability feature itself

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
<td><code>endpoint</code></td>
<td><code>String</code></td>
<td><code>/observe</code></td>
<td>Root endpoint to use for observe providers</td>
</tr>
<tr>
<td><code>weight</code></td>
<td><code>Double</code></td>
<td><code>80.0</code></td>
<td>Change the weight of this feature</td>
</tr>
<tr>
<td><code>sockets</code></td>
<td><code>List&lt;String&gt;</code></td>
<td></td>
<td>Sockets the observability endpoint should be exposed on</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether the observe support is enabled</td>
</tr>
<tr>
<td><a id="observers"></a><a href="io.helidon.webserver.observe.spi.Observer.md"><code>observers</code></a></td>
<td><code>List&lt;Observer&gt;</code></td>
<td></td>
<td>Observers to use with this observe features</td>
</tr>
<tr>
<td><code>observers-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;observers&lt;/code&gt;</td>
</tr>
</tbody>
</table>

### Deprecated Options

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><a id="cors"></a><a href="io.helidon.cors.CrossOriginConfig.md"><code>cors</code></a></td>
<td><code>CrossOriginConfig</code></td>
<td>Cors support inherited by each observe provider, unless explicitly configured</td>
</tr>
</tbody>
</table>

## Usages

- [`server.features.observe`](io.helidon.webserver.spi.ServerFeature.md#observe)

---

See the [manifest](manifest.md) for all available types.
