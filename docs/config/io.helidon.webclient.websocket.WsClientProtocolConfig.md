# io.<wbr>helidon.<wbr>webclient.<wbr>websocket.<wbr>WsClient<wbr>Protocol<wbr>Config

## Description

WebSocket client protocol configuration

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
<code>max-<wbr>buffered-<wbr>message-<wbr>size</code>
</td>
<td>
<code>Size</code>
</td>
<td>
<code>1 Mi<wbr>B</code>
</td>
<td>Maximum size of a WebSocket message buffered for delivery to a listener</td>
</tr>
<tr>
<td>
<code>sub-<wbr>protocols</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>WebSocket sub-protocols requested by the client</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>websocket</code>
</td>
<td>Name of this protocol configuration</td>
</tr>
<tr>
<td>
<code>max-<wbr>frame-<wbr>size</code>
</td>
<td>
<code>Size</code>
</td>
<td>
<code>1 Mi<wbr>B</code>
</td>
<td>Maximum WebSocket frame payload size accepted by the client, from <code>1 B</code> through <code>Integer.<wbr>MAX_<wbr>VALUE</code> bytes; the default is 1 MiB, replacing the earlier effectively unbounded behavior, and exceeding the configured limit causes the client to close the connection with status code <code>1009</code> (<code>Message Too Big</code>)</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
