# io.helidon.webclient.grpc.GrpcClientProtocolConfig

## Description

Configuration of a gRPC client

## Configuration options

<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
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
<code>init-buffer-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">2048</code>
</td>
<td>Initial buffer size used to serialize gRPC request payloads</td>
</tr>
<tr>
<td>
<code>next-request-wait-time</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT1S</code>
</td>
<td>When data has been received from the server but not yet requested by the client (i.e., listener), the implementation will wait for this duration before signaling an error</td>
</tr>
<tr>
<td>
<code>abort-poll-time-expired</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether to continue retrying after a poll wait timeout expired or not</td>
</tr>
<tr>
<td>
<code>poll-wait-time</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT10S</code>
</td>
<td>How long to wait for the next HTTP/2 data frame to arrive in underlying stream</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">grpc</code>
</td>
<td>Name identifying this client protocol</td>
</tr>
<tr>
<td>
<code>heartbeat-period</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT0S</code>
</td>
<td>How often to send a heartbeat (HTTP/2 ping) to check if the connection is still alive</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
