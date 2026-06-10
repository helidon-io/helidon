# com.acme.AcmeServerConfig

## Description

ACME server configuration.

## Configuration options


<table>
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<a id="mode"></a>
<a href="com.acme.AcmeMode.md">
<code>mode</code>
</a>
</td>
<td>
<code>Acme<wbr>Mode</code>
</td>
<td>Mode</td>
</tr>
<tr>
<td>
<a id="features"></a>
<a href="com.acme.AcmeFeature.md">
<code>features</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Acme<wbr>Feature&gt;</code>
</td>
<td>Dynamic features</td>
</tr>
<tr>
<td>
<code>external-<wbr>handlers</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> External<wbr>Handler&gt;</code>
</td>
<td>Handlers</td>
</tr>
<tr>
<td>
<a id="default-socket"></a>
<a href="com.acme.AcmeListenerConfig.md">
<code>default-<wbr>socket</code>
</a>
</td>
<td>
<code>Acme<wbr>Listener<wbr>Config</code>
</td>
<td>Default socket</td>
</tr>
<tr>
<td>
<a id="modes"></a>
<a href="com.acme.AcmeMode.md">
<code>modes</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Acme<wbr>Mode&gt;</code>
</td>
<td>Modes</td>
</tr>
<tr>
<td>
<a id="named-modes"></a>
<a href="com.acme.AcmeNamedMode.md">
<code>named-<wbr>modes</code>
</a>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> Acme<wbr>Named<wbr>Mode&gt;</code>
</td>
<td>Named modes</td>
</tr>
<tr>
<td>
<code>external-<wbr>listeners</code>
</td>
<td>
<code>List&lt;<wbr>External<wbr>Listener&gt;</code>
</td>
<td>Listeners</td>
</tr>
<tr>
<td>
<code>host</code>
</td>
<td>
<code>String</code>
</td>
<td>Host</td>
</tr>
<tr>
<td>
<code>external-<wbr>scalar</code>
</td>
<td>
<code>External<wbr>Scalar</code>
</td>
<td>External scalar</td>
</tr>
<tr>
<td>
<a id="sockets"></a>
<a href="com.acme.AcmeListenerConfig.md">
<code>sockets</code>
</a>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> Acme<wbr>Listener<wbr>Config&gt;</code>
</td>
<td>Sockets</td>
</tr>
</tbody>
</table>



## Usages

- [`server`](config_reference.md#server)

---

See the [manifest](manifest.md) for all available types.
