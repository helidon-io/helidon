# io.helidon.webclient.grpc.GrpcClient

## Description

Configuration of a grpc client

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
<td><a id="protocol-config"></a><a href="io.helidon.webclient.grpc.GrpcClientProtocolConfig.md"><code>protocol-config</code></a></td>
<td><code>GrpcClientProtocolConfig</code></td>
<td><code>create()</code></td>
<td>gRPC specific configuration</td>
</tr>
<tr>
<td><a id="grpc-services"></a><a href="io.helidon.webclient.grpc.spi.GrpcClientService.md"><code>grpc-services</code></a></td>
<td><code>List&lt;GrpcClientService&gt;</code></td>
<td></td>
<td>gRPC client services</td>
</tr>
<tr>
<td><code>enable-metrics</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether to collect metrics for gRPC client calls</td>
</tr>
<tr>
<td><code>grpc-services-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;grpc-services&lt;/code&gt;</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
