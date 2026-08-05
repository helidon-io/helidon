# io.<wbr>helidon.<wbr>webserver.<wbr>grpc.<wbr>security.<wbr>Grpc<wbr>Security<wbr>Config

## Description

Helidon gRPC security configuration

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
<a id="defaults"></a>
<a href="io.helidon.webserver.grpc.security.GrpcSecurityHandler.md">
<code>defaults</code>
</a>
</td>
<td>
<code>Grpc<wbr>Security<wbr>Handler</code>
</td>
<td>
</td>
<td>Default gRPC security handler</td>
</tr>
<tr>
<td>
<a id="services"></a>
<a href="io.helidon.webserver.grpc.security.GrpcSecurityServiceConfig.md">
<code>services</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Grpc<wbr>Security<wbr>Service<wbr>Config&gt;</code>
</td>
<td>
</td>
<td>Service-specific gRPC security configuration</td>
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
<td>Whether gRPC security is enabled</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.webserver.grpc.spi.GrpcServerService.md#security"><code>server.<wbr>protocols.<wbr>grpc.<wbr>grpc-<wbr>services.<wbr>security</code></a>
- <a href="io.helidon.webserver.grpc.spi.GrpcServerService.md#security"><code>server.<wbr>sockets.<wbr>protocols.<wbr>grpc.<wbr>grpc-<wbr>services.<wbr>security</code></a>

---

See the [manifest](manifest.md) for all available types.
