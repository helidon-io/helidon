# io.<wbr>helidon.<wbr>webclient.<wbr>grpc.<wbr>Grpc<wbr>Client<wbr>Protocol<wbr>Config

## Description

Configuration of a gRPC client

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
<code>init-<wbr>buffer-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>2048</code>
</td>
<td>Initial buffer size used to serialize gRPC request payloads</td>
</tr>
<tr>
<td>
<code>next-<wbr>request-<wbr>wait-<wbr>time</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT1S</code>
</td>
<td>When data has been received from the server but not yet requested by the client (i.e., listener), the implementation will wait for this duration before signaling an error</td>
</tr>
<tr>
<td>
<code>abort-<wbr>poll-<wbr>time-<wbr>expired</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether to continue retrying after a poll wait timeout expired or not</td>
</tr>
<tr>
<td>
<code>poll-<wbr>wait-<wbr>time</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT10S</code>
</td>
<td>How long to wait for the next HTTP/2 data frame to arrive in underlying stream</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>grpc</code>
</td>
<td>Name identifying this client protocol</td>
</tr>
<tr>
<td>
<code>heartbeat-<wbr>period</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT0S</code>
</td>
<td>How often to send a heartbeat (HTTP/2 ping) to check if the connection is still alive</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
