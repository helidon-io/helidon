# io.<wbr>helidon.<wbr>webserver.<wbr>grpc.<wbr>security.<wbr>Grpc<wbr>Security<wbr>Service<wbr>Config

## Description

Configuration of gRPC service security

## Configuration options


<table>
<thead>
<tr>
<th>Key</th>
<th>Type</th>
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
<td>Default security handler for this gRPC service</td>
</tr>
<tr>
<td>
<a id="methods"></a>
<a href="io.helidon.webserver.grpc.security.GrpcSecurityMethodConfig.md">
<code>methods</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Grpc<wbr>Security<wbr>Method<wbr>Config&gt;</code>
</td>
<td>Method-specific security configuration</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td>
<code>String</code>
</td>
<td>Name of the gRPC service, either the full name such as <code>package.<wbr>String<wbr>Service</code> or the final service name segment such as <code>String<wbr>Service</code></td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.webserver.grpc.security.GrpcSecurityConfig.md#services"><code>server.<wbr>protocols.<wbr>grpc.<wbr>grpc-<wbr>services.<wbr>security.<wbr>services</code></a>
- <a href="io.helidon.webserver.grpc.security.GrpcSecurityConfig.md#services"><code>server.<wbr>sockets.<wbr>protocols.<wbr>grpc.<wbr>grpc-<wbr>services.<wbr>security.<wbr>services</code></a>

---

See the [manifest](manifest.md) for all available types.
