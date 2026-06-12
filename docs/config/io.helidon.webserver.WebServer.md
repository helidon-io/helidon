# io.helidon.webserver.WebServer

## Description

WebServer configuration bean

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
<code>restore-<wbr>response-<wbr>headers</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Copy and restore response headers before and after passing a request to Jersey for processing</td>
</tr>
<tr>
<td>
<a id="concurrency-limit"></a>
<a href="io.helidon.common.concurrency.limits.Limit.md">
<code>concurrency-<wbr>limit</code>
</a>
</td>
<td>
<code>Limit</code>
</td>
<td>
</td>
<td>Concurrency limit to use to limit concurrent execution of incoming requests</td>
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
</td>
<td>Configure the listener specific <code>io.<wbr>helidon.<wbr>http.<wbr>media.<wbr>Media<wbr>Context</code></td>
</tr>
<tr>
<td>
<code>max-<wbr>payload-<wbr>size</code>
</td>
<td>
<code>Long</code>
</td>
<td>
<code>&#8288;-&#8288;1</code>
</td>
<td>Maximal number of bytes an entity may have</td>
</tr>
<tr>
<td>
<a id="features"></a>
<a href="io.helidon.webserver.spi.ServerFeature.md">
<code>features</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Server<wbr>Feature&gt;</code>
</td>
<td>
</td>
<td>Server features allow customization of the server, listeners, or routings</td>
</tr>
<tr>
<td>
<code>use-<wbr>nio</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>If set to <code>true</code>, use NIO socket channel, instead of a socket</td>
</tr>
<tr>
<td>
<code>protocols-<wbr>discover-<wbr>services</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to enable automatic service discovery for <code>protocols</code></td>
</tr>
<tr>
<td>
<code>enable-<wbr>proxy-<wbr>protocol</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Enable proxy protocol support for this socket</td>
</tr>
<tr>
<td>
<code>host</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>0.<wbr>0.0.<wbr>0</code>
</td>
<td>Host of the default socket</td>
</tr>
<tr>
<td>
<code>write-<wbr>queue-<wbr>length</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>0</code>
</td>
<td>Number of buffers queued for write operations</td>
</tr>
<tr>
<td>
<a id="sockets"></a>
<a href="io.helidon.webserver.ListenerConfig.md">
<code>sockets</code>
</a>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> Listener<wbr>Config&gt;</code>
</td>
<td>
</td>
<td>Socket configurations</td>
</tr>
<tr>
<td>
<a id="protocols"></a>
<a href="io.helidon.webserver.spi.ProtocolConfig.md">
<code>protocols</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Protocol<wbr>Config&gt;</code>
</td>
<td>
</td>
<td>Configuration of protocols</td>
</tr>
<tr>
<td>
<code>max-<wbr>tcp-<wbr>connections</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>&#8288;-&#8288;1</code>
</td>
<td>Limits the number of connections that can be opened at a single point in time</td>
</tr>
<tr>
<td>
<code>bind-<wbr>address</code>
</td>
<td>
<code>Listener<wbr>Custom<wbr>Methods</code>
</td>
<td>
</td>
<td>The address to bind to</td>
</tr>
<tr>
<td>
<code>idle-<wbr>connection-<wbr>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT5M</code>
</td>
<td>How long should we wait before closing a connection that has no traffic on it</td>
</tr>
<tr>
<td>
<code>shutdown-<wbr>grace-<wbr>period</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT0.<wbr>5S</code>
</td>
<td>Grace period in ISO 8601 duration format to allow running tasks to complete before listener's shutdown</td>
</tr>
<tr>
<td>
<code>max-<wbr>concurrent-<wbr>requests</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>&#8288;-&#8288;1</code>
</td>
<td>Limits the number of requests that can be executed at the same time (the number of active virtual threads of requests)</td>
</tr>
<tr>
<td>
<code>features-<wbr>discover-<wbr>services</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to enable automatic service discovery for <code>features</code></td>
</tr>
<tr>
<td>
<code>shutdown-<wbr>hook</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>When true the webserver registers a shutdown hook with the JVM Runtime</td>
</tr>
<tr>
<td>
<a id="error-handling"></a>
<a href="io.helidon.webserver.ErrorHandling.md">
<code>error-<wbr>handling</code>
</a>
</td>
<td>
<code>Error<wbr>Handling</code>
</td>
<td>
</td>
<td>Configuration for this listener's error handling</td>
</tr>
<tr>
<td>
<code>concurrency-<wbr>limit-<wbr>discover-<wbr>services</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether to enable automatic service discovery for <code>concurrency-<wbr>limit</code></td>
</tr>
<tr>
<td>
<code>backlog</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>1024</code>
</td>
<td>Accept backlog</td>
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
<td>If the entity is expected to be smaller that this number of bytes, it would be buffered in memory to optimize performance when writing it</td>
</tr>
<tr>
<td>
<code>ignore-<wbr>invalid-<wbr>named-<wbr>routing</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
</td>
<td>If set to <code>true</code>, any named routing configured that does not have an associated named listener will NOT cause an exception to be thrown (default behavior is to throw an exception)</td>
</tr>
<tr>
<td>
<code>smart-<wbr>async-<wbr>writes</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>If enabled and <code>#write<wbr>Queue<wbr>Length(<wbr>)</code> is greater than 1, then start with async writes but possibly switch to sync writes if async queue size is always below a certain threshold</td>
</tr>
<tr>
<td>
<a id="connection-options"></a>
<a href="io.helidon.common.socket.SocketOptions.md">
<code>connection-<wbr>options</code>
</a>
</td>
<td>
<code>Socket<wbr>Options</code>
</td>
<td>
</td>
<td>Options for connections accepted by this listener</td>
</tr>
<tr>
<td>
<code>port</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>0</code>
</td>
<td>Port of the default socket</td>
</tr>
<tr>
<td>
<a id="requested-uri-discovery"></a>
<a href="io.helidon.http.RequestedUriDiscoveryContext.md">
<code>requested-<wbr>uri-<wbr>discovery</code>
</a>
</td>
<td>
<code>Requested<wbr>UriDiscovery<wbr>Context</code>
</td>
<td>
</td>
<td>Requested URI discovery context</td>
</tr>
<tr>
<td>
<code>idle-<wbr>connection-<wbr>period</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT2M</code>
</td>
<td>How often should we check for <code>#idle<wbr>Connection<wbr>Timeout(<wbr>)</code></td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>@default</code>
</td>
<td>Name of this socket</td>
</tr>
<tr>
<td>
<a id="tls"></a>
<a href="io.helidon.common.tls.Tls.md">
<code>tls</code>
</a>
</td>
<td>
<code>Tls</code>
</td>
<td>
</td>
<td>Listener TLS configuration</td>
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
<td>Initial buffer size in bytes of <code>java.<wbr>io.Buffered<wbr>Output<wbr>Stream</code> created internally to write data to a socket connection</td>
</tr>
</tbody>
</table>


### Deprecated Options


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
<a id="connection-config"></a>
<a href="io.helidon.webserver.ConnectionConfig.md">
<code>connection-<wbr>config</code>
</a>
</td>
<td>
<code>Connection<wbr>Config</code>
</td>
<td>Configuration of a connection (established from client against our server)</td>
</tr>
<tr>
<td>
<code>receive-<wbr>buffer-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Listener receive buffer size</td>
</tr>
</tbody>
</table>


## Usages

- [`server`](config_reference.md#server)

---

See the [manifest](manifest.md) for all available types.
