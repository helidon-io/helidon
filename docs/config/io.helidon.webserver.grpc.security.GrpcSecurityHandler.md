# io.<wbr>helidon.<wbr>webserver.<wbr>grpc.<wbr>security.<wbr>Grpc<wbr>Security<wbr>Handler

## Description

Configuration of a <code>io.<wbr>helidon.<wbr>webserver.<wbr>grpc.<wbr>security.<wbr>Grpc<wbr>Security<wbr>Handler</code>

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
<code>authentication-<wbr>optional</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>Whether authentication failure should continue as anonymous</td>
</tr>
<tr>
<td>
<code>authenticate</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>Whether to authenticate this request</td>
</tr>
<tr>
<td>
<code>audit-<wbr>event-<wbr>type</code>
</td>
<td>
<code>String</code>
</td>
<td>Override for audit event type</td>
</tr>
<tr>
<td>
<code>authorizer</code>
</td>
<td>
<code>String</code>
</td>
<td>Use a named authorizer</td>
</tr>
<tr>
<td>
<code>audit</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>Whether to audit this request</td>
</tr>
<tr>
<td>
<code>audit-<wbr>message-<wbr>format</code>
</td>
<td>
<code>String</code>
</td>
<td>Override for audit message format</td>
</tr>
<tr>
<td>
<code>authenticator</code>
</td>
<td>
<code>String</code>
</td>
<td>Use a named authenticator</td>
</tr>
<tr>
<td>
<code>authorize</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>Whether to authorize this request</td>
</tr>
<tr>
<td>
<code>roles-<wbr>allowed</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>An array of allowed roles for this gRPC method</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.webserver.grpc.security.GrpcSecurityConfig.md#defaults"><code>server.<wbr>protocols.<wbr>grpc.<wbr>grpc-<wbr>services.<wbr>security.<wbr>defaults</code></a>
- <a href="io.helidon.webserver.grpc.security.GrpcSecurityServiceConfig.md#defaults"><code>server.<wbr>protocols.<wbr>grpc.<wbr>grpc-<wbr>services.<wbr>security.<wbr>services.<wbr>defaults</code></a>
- <a href="io.helidon.webserver.grpc.security.GrpcSecurityConfig.md#defaults"><code>server.<wbr>sockets.<wbr>protocols.<wbr>grpc.<wbr>grpc-<wbr>services.<wbr>security.<wbr>defaults</code></a>
- <a href="io.helidon.webserver.grpc.security.GrpcSecurityServiceConfig.md#defaults"><code>server.<wbr>sockets.<wbr>protocols.<wbr>grpc.<wbr>grpc-<wbr>services.<wbr>security.<wbr>services.<wbr>defaults</code></a>

---

See the [manifest](manifest.md) for all available types.
