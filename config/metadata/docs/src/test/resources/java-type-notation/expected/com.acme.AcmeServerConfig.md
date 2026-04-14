# com.acme.AcmeServerConfig

## Description

ACME server configuration.

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><a id="mode"></a><a href="com.acme.AcmeMode.md"><code>mode</code></a></td>
<td><code>AcmeMode</code></td>
<td>Mode</td>
</tr>
<tr>
<td><a id="features"></a><a href="com.acme.AcmeFeature.md"><code>features</code></a></td>
<td><code>List&lt;AcmeFeature&gt;</code></td>
<td>Dynamic features</td>
</tr>
<tr>
<td><code>external-handlers</code></td>
<td><code>Map&lt;String, ExternalHandler&gt;</code></td>
<td>Handlers</td>
</tr>
<tr>
<td><a id="default-socket"></a><a href="com.acme.AcmeListenerConfig.md"><code>default-socket</code></a></td>
<td><code>AcmeListenerConfig</code></td>
<td>Default socket</td>
</tr>
<tr>
<td><a id="modes"></a><a href="com.acme.AcmeMode.md"><code>modes</code></a></td>
<td><code>List&lt;AcmeMode&gt;</code></td>
<td>Modes</td>
</tr>
<tr>
<td><a id="named-modes"></a><a href="com.acme.AcmeNamedMode.md"><code>named-modes</code></a></td>
<td><code>Map&lt;String, AcmeNamedMode&gt;</code></td>
<td>Named modes</td>
</tr>
<tr>
<td><code>external-listeners</code></td>
<td><code>List&lt;ExternalListener&gt;</code></td>
<td>Listeners</td>
</tr>
<tr>
<td><code>host</code></td>
<td><code>String</code></td>
<td>Host</td>
</tr>
<tr>
<td><code>external-scalar</code></td>
<td><code>ExternalScalar</code></td>
<td>External scalar</td>
</tr>
<tr>
<td><a id="sockets"></a><a href="com.acme.AcmeListenerConfig.md"><code>sockets</code></a></td>
<td><code>Map&lt;String, AcmeListenerConfig&gt;</code></td>
<td>Sockets</td>
</tr>
</tbody>
</table>


## Usages

- [`server`](config_reference.md#server)

---

See the [manifest](manifest.md) for all available types.
