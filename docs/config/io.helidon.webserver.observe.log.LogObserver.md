# io.helidon.webserver.observe.log.LogObserver

## Description

Log Observer configuration

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
<td><code>log</code></td>
<td>&lt;code&gt;N/A&lt;/code&gt;</td>
</tr>
<tr>
<td><a id="stream"></a><a href="io.helidon.webserver.observe.log.LogStreamConfig.md"><code>stream</code></a></td>
<td><code>LogStreamConfig</code></td>
<td></td>
<td>Configuration of log stream</td>
</tr>
<tr>
<td><code>permit-all</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Permit all access, even when not authorized</td>
</tr>
</tbody>
</table>


## Usages

- [`server.features.observe.observers.log`](io.helidon.webserver.observe.spi.Observer.md#log)

---

See the [manifest](manifest.md) for all available types.
