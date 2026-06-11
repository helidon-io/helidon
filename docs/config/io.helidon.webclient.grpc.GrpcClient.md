# io.helidon.webclient.grpc.GrpcClient

## Description

Configuration of a grpc client

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
<a id="protocol-config"></a>
<a href="io.helidon.webclient.grpc.GrpcClientProtocolConfig.md">
<code>protocol-<wbr>config</code>
</a>
</td>
<td>
<code>Grpc<wbr>Client<wbr>Protocol<wbr>Config</code>
</td>
<td>
<code>create(<wbr>)</code>
</td>
<td>gRPC specific configuration</td>
</tr>
<tr>
<td>
<a id="grpc-services"></a>
<a href="io.helidon.webclient.grpc.spi.GrpcClientService.md">
<code>grpc-<wbr>services</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Grpc<wbr>Client<wbr>Service&gt;</code>
</td>
<td>
</td>
<td>gRPC client services</td>
</tr>
<tr>
<td>
<code>enable-<wbr>metrics</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether to collect metrics for gRPC client calls</td>
</tr>
<tr>
<td>
<code>grpc-<wbr>services-<wbr>discover-<wbr>services</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether to enable automatic service discovery for <code>grpc-<wbr>services</code></td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
