# io.helidon.webserver.accesslog.AccessLogFeature

## Description

Configuration of access log feature

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
<td><code>format</code></td>
<td><code>String</code></td>
<td></td>
<td>The format for log entries (similar to the Apache &lt;code&gt;LogFormat&lt;/code&gt;)</td>
</tr>
<tr>
<td><code>logger-name</code></td>
<td><code>String</code></td>
<td><code>io.helidon.webserver.AccessLog</code></td>
<td>Name of the logger used to obtain access log logger from &lt;code&gt;System#getLogger(String)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>weight</code></td>
<td><code>Double</code></td>
<td><code>1000.0</code></td>
<td>Weight of the access log feature</td>
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
<td>Whether this feature will be enabled</td>
</tr>
</tbody>
</table>


## Usages

- [`server.features.access-log`](io.helidon.webserver.spi.ServerFeature.md#access-log)

---

See the [manifest](manifest.md) for all available types.
