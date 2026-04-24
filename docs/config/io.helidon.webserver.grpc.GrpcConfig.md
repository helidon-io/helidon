# io.helidon.webserver.grpc.GrpcConfig

## Description

<code>N/A</code>

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }

    .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


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
<code>max-read-buffer-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">2097152</code>
</td>
<td>Max size of gRPC reading buffer</td>
</tr>
<tr>
<td>
<a id="grpc-services"></a>
<a href="io.helidon.webserver.grpc.spi.GrpcServerService.md">
<code>grpc-services</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;GrpcServerService&gt;">List&lt;GrpcServerService&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>gRPC server services</td>
</tr>
<tr>
<td>
<code>enable-compression</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to support compression if requested by a client</td>
</tr>
<tr>
<td>
<code>enable-metrics</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether to collect metrics for gRPC server calls</td>
</tr>
<tr>
<td>
<code>grpc-services-discover-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether to enable automatic service discovery for <code>grpc-services</code></td>
</tr>
</tbody>
</table>



## Usages

- [`server.protocols.grpc`](io.helidon.webserver.spi.ProtocolConfig.md#grpc)
- [`server.sockets.protocols.grpc`](io.helidon.webserver.spi.ProtocolConfig.md#grpc)

---

See the [manifest](manifest.md) for all available types.
