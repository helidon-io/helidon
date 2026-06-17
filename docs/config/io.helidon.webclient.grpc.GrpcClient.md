# io.<wbr>helidon.<wbr>webclient.<wbr>grpc.<wbr>Grpc<wbr>Client

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
<code>default-<wbr>headers</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> String&gt;</code>
</td>
<td>
</td>
<td>Default headers to be used in every request from configuration</td>
</tr>
<tr>
<td>
<code>base-<wbr>uri</code>
</td>
<td>
<code>Http<wbr>Custom<wbr>Methods</code>
</td>
<td>
</td>
<td>Base uri used by the client in all requests</td>
</tr>
<tr>
<td>
<code>connection-<wbr>cache-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>256</code>
</td>
<td>Maximal size of the connection cache for a single connection key</td>
</tr>
<tr>
<td>
<a id="content-encoding"></a>
<a href="io.helidon.http.encoding.ContentEncodingContext.md">
<code>content-<wbr>encoding</code>
</a>
</td>
<td>
<code>Content<wbr>Encoding<wbr>Context</code>
</td>
<td>
</td>
<td>Configure the listener specific <code>io.<wbr>helidon.<wbr>http.<wbr>encoding.<wbr>Content<wbr>Encoding<wbr>Context</code></td>
</tr>
<tr>
<td>
<a id="media-context"></a>
<a href="io.helidon.http.media.MediaContext.md">
<code>media-<wbr>context</code>
</a>
</td>
<td>
<code>Media<wbr>Context</code>
</td>
<td>
<code>create(<wbr>)</code>
</td>
<td>Configure the listener specific <code>io.<wbr>helidon.<wbr>http.<wbr>media.<wbr>Media<wbr>Context</code></td>
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
<code>base-<wbr>address</code>
</td>
<td>
<code>Http<wbr>Custom<wbr>Methods</code>
</td>
<td>
</td>
<td>Base address used by the client in all requests</td>
</tr>
<tr>
<td>
<a id="cookie-manager"></a>
<a href="io.helidon.webclient.api.WebClientCookieManager.md">
<code>cookie-<wbr>manager</code>
</a>
</td>
<td>
<code>Web<wbr>Client<wbr>Cookie<wbr>Manager</code>
</td>
<td>
</td>
<td>WebClient cookie manager</td>
</tr>
<tr>
<td>
<a id="services"></a>
<a href="io.helidon.webclient.spi.WebClientService.md">
<code>services</code>
</a>
</td>
<td>
<code>List&lt;<wbr>WebClient<wbr>Service&gt;</code>
</td>
<td>
</td>
<td>WebClient services</td>
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
<code>filter-<wbr>redirect-<wbr>headers</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether headers sensitive to cross-origin redirects should be filtered before the redirected request is sent</td>
</tr>
<tr>
<td>
<code>relative-<wbr>uris</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Can be set to <code>true</code> to force the use of relative URIs in all requests, regardless of the presence or absence of proxies or no-proxy lists</td>
</tr>
<tr>
<td>
<code>send-<wbr>expect-<wbr>continue</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether Expect-100-Continue header is sent to verify server availability before sending an entity</td>
</tr>
<tr>
<td>
<a id="media-type-parser-mode"></a>
<a href="io.helidon.common.media.type.ParserMode.md">
<code>media-<wbr>type-<wbr>parser-<wbr>mode</code>
</a>
</td>
<td>
<code>Parser<wbr>Mode</code>
</td>
<td>
<code>STRICT</code>
</td>
<td>Configure media type parsing mode for HTTP <code>Content-<wbr>Type</code> header</td>
</tr>
<tr>
<td>
<code>max-<wbr>in-memory-<wbr>entity</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>131072</code>
</td>
<td>If the entity is expected to be smaller that this number of bytes, it would be buffered in memory to optimize performance</td>
</tr>
<tr>
<td>
<code>share-<wbr>connection-<wbr>cache</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to share connection cache between all the WebClient instances in JVM</td>
</tr>
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
<code>services-<wbr>discover-<wbr>services</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to enable automatic service discovery for <code>services</code></td>
</tr>
<tr>
<td>
<a id="socket-options"></a>
<a href="io.helidon.common.socket.SocketOptions.md">
<code>socket-<wbr>options</code>
</a>
</td>
<td>
<code>Socket<wbr>Options</code>
</td>
<td>
</td>
<td>Socket options for connections opened by this client</td>
</tr>
<tr>
<td>
<code>write-<wbr>buffer-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>4096</code>
</td>
<td>Buffer size used when writing data to the underlying socket on a client TCP connection</td>
</tr>
<tr>
<td>
<code>redirect-<wbr>sensitive-<wbr>headers</code>
</td>
<td>
<code>List&lt;<wbr>Http<wbr>Custom<wbr>Methods&gt;</code>
</td>
<td>
</td>
<td>Request header names to strip on cross-origin redirects</td>
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
<tr>
<td>
<code>read-<wbr>continue-<wbr>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT1S</code>
</td>
<td>Socket 100-Continue read timeout</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
