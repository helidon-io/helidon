# com.acme.AcmeServerConfig

## Description

ACME Server configuration.

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
<td><a id="features"></a><a href="com.acme.AcmeFeature.md"><code>features</code></a></td>
<td><code>List&lt;AcmeFeature&gt;</code></td>
<td></td>
<td>Dynamic features</td>
</tr>
<tr>
<td><code>port</code></td>
<td><code>Integer</code></td>
<td><code>8080</code></td>
<td>Listen port</td>
</tr>
<tr>
<td><code>host</code></td>
<td><code>String</code></td>
<td><code>localhost</code></td>
<td>Listen address</td>
</tr>
<tr>
<td><a id="sockets"></a><a href="com.acme.AcmeListenerConfig.md"><code>sockets</code></a></td>
<td><code>Map&lt;String, AcmeListenerConfig&gt;</code></td>
<td></td>
<td>Sockets</td>
</tr>
</tbody>
</table>


## Usages

- [`server`](config_reference.md#server)

---

See the [manifest](manifest.md) for all available types.
