# io.helidon.webserver.observe.log.LogStreamConfig

## Description

Log stream configuration for Log Observer

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
<td><code>idle-message-timeout</code></td>
<td><code>Duration</code></td>
<td><code>PT5S</code></td>
<td>How long to wait before we send the idle message, to make sure we keep the stream alive</td>
</tr>
<tr>
<td><code>queue-size</code></td>
<td><code>Integer</code></td>
<td><code>100</code></td>
<td>Length of the in-memory queue that buffers log messages from loggers before sending them over the network</td>
</tr>
<tr>
<td><code>content-type</code></td>
<td><code>HttpMediaType</code></td>
<td></td>
<td>&lt;code&gt;N/A&lt;/code&gt;</td>
</tr>
<tr>
<td><code>idle-string</code></td>
<td><code>String</code></td>
<td><code>%
</code></td>
<td>String sent when there are no log messages within the &lt;code&gt;#idleMessageTimeout()&lt;/code&gt;</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether stream is enabled</td>
</tr>
</tbody>
</table>


## Usages

- [`server.features.observe.observers.log.stream`](io.helidon.webserver.observe.log.LogObserver.md#stream)

---

See the [manifest](manifest.md) for all available types.
