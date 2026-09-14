# io.<wbr>helidon.<wbr>webserver.<wbr>observe.<wbr>log.<wbr>LogStream<wbr>Config

## Description

Log stream configuration for Log Observer

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
<code>idle-<wbr>message-<wbr>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT5S</code>
</td>
<td>How long to wait before we send the idle message, to make sure we keep the stream alive</td>
</tr>
<tr>
<td>
<code>queue-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>100</code>
</td>
<td>Length of the in-memory queue that buffers log messages from loggers before sending them over the network</td>
</tr>
<tr>
<td>
<code>content-<wbr>type</code>
</td>
<td>
<code>Http<wbr>Media<wbr>Type</code>
</td>
<td>
</td>
<td><code>N/<wbr>A</code></td>
</tr>
<tr>
<td>
<code>idle-<wbr>string</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>%
</code>
</td>
<td>String sent when there are no log messages within the <code>#idle<wbr>Message<wbr>Timeout(<wbr>)</code></td>
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
<td>Whether stream is enabled</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.webserver.observe.log.LogObserver.md#stream"><code>server.<wbr>features.<wbr>observe.<wbr>observers.<wbr>log.<wbr>stream</code></a>

---

See the [manifest](manifest.md) for all available types.
