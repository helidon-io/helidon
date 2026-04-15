# io.helidon.webserver.grpc.GrpcConfig

## Description

&lt;code&gt;N/A&lt;/code&gt;

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
<td><code>max-read-buffer-size</code></td>
<td><code>Integer</code></td>
<td><code>2097152</code></td>
<td>Max size of gRPC reading buffer</td>
</tr>
<tr>
<td><a id="grpc-services"></a><a href="io.helidon.webserver.grpc.spi.GrpcServerService.md"><code>grpc-services</code></a></td>
<td><code>List&lt;GrpcServerService&gt;</code></td>
<td></td>
<td>gRPC server services</td>
</tr>
<tr>
<td><code>enable-compression</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to support compression if requested by a client</td>
</tr>
<tr>
<td><code>enable-metrics</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether to collect metrics for gRPC server calls</td>
</tr>
<tr>
<td><code>grpc-services-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;grpc-services&lt;/code&gt;</td>
</tr>
</tbody>
</table>


## Usages

- [`server.protocols.grpc`](io.helidon.webserver.spi.ProtocolConfig.md#grpc)
- [`server.sockets.protocols.grpc`](io.helidon.webserver.spi.ProtocolConfig.md#grpc)

---

See the [manifest](manifest.md) for all available types.
