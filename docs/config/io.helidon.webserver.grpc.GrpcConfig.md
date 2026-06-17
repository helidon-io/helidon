# io.<wbr>helidon.<wbr>webserver.<wbr>grpc.<wbr>Grpc<wbr>Config

## Description

<code>N/<wbr>A</code>

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
<code>max-<wbr>read-<wbr>buffer-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>2097152</code>
</td>
<td>Max size of gRPC reading buffer</td>
</tr>
<tr>
<td>
<a id="grpc-services"></a>
<a href="io.helidon.webserver.grpc.spi.GrpcServerService.md">
<code>grpc-<wbr>services</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Grpc<wbr>Server<wbr>Service&gt;</code>
</td>
<td>
</td>
<td>gRPC server services</td>
</tr>
<tr>
<td>
<code>enable-<wbr>compression</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to support compression if requested by a client</td>
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
<td>Whether to collect metrics for gRPC server calls</td>
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



## Usages

- <a href="io.helidon.webserver.spi.ProtocolConfig.md#grpc"><code>server.<wbr>protocols.<wbr>grpc</code></a>
- <a href="io.helidon.webserver.spi.ProtocolConfig.md#grpc"><code>server.<wbr>sockets.<wbr>protocols.<wbr>grpc</code></a>

---

See the [manifest](manifest.md) for all available types.
