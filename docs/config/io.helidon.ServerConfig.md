# io.helidon.ServerConfig

## Description

Merged configuration for server

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
<td><code>backlog</code></td>
<td><code>Integer</code></td>
<td><code>1024</code></td>
<td>Accept backlog</td>
</tr>
<tr>
<td><code>bind-address</code></td>
<td><code>ListenerCustomMethods</code></td>
<td></td>
<td>The address to bind to</td>
</tr>
<tr>
<td><a id="concurrency-limit"></a><a href="io.helidon.common.concurrency.limits.Limit.md"><code>concurrency-limit</code></a></td>
<td><code>Limit</code></td>
<td></td>
<td>Concurrency limit to use to limit concurrent execution of incoming requests</td>
</tr>
<tr>
<td><code>concurrency-limit-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;concurrency-limit&lt;/code&gt;</td>
</tr>
<tr>
<td><a id="connection-options"></a><a href="io.helidon.common.socket.SocketOptions.md"><code>connection-options</code></a></td>
<td><code>SocketOptions</code></td>
<td></td>
<td>Options for connections accepted by this listener</td>
</tr>
<tr>
<td><a id="content-encoding"></a><a href="io.helidon.http.encoding.ContentEncodingContext.md"><code>content-encoding</code></a></td>
<td><code>ContentEncodingContext</code></td>
<td></td>
<td>Configure the listener specific &lt;code&gt;io.helidon.http.encoding.ContentEncodingContext&lt;/code&gt;</td>
</tr>
<tr>
<td><code>enable-proxy-protocol</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Enable proxy protocol support for this socket</td>
</tr>
<tr>
<td><a id="error-handling"></a><a href="io.helidon.webserver.ErrorHandling.md"><code>error-handling</code></a></td>
<td><code>ErrorHandling</code></td>
<td></td>
<td>Configuration for this listener&#x27;s error handling</td>
</tr>
<tr>
<td><a id="features"></a><a href="io.helidon.webserver.spi.ServerFeature.md"><code>features</code></a></td>
<td><code>List&lt;ServerFeature&gt;</code></td>
<td></td>
<td>Server features allow customization of the server, listeners, or routings</td>
</tr>
<tr>
<td><code>features-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;features&lt;/code&gt;</td>
</tr>
<tr>
<td><code>host</code></td>
<td><code>String</code></td>
<td></td>
<td>Configure listen host</td>
</tr>
<tr>
<td><code>idle-connection-period</code></td>
<td><code>Duration</code></td>
<td><code>PT2M</code></td>
<td>How often should we check for &lt;code&gt;#idleConnectionTimeout()&lt;/code&gt;</td>
</tr>
<tr>
<td><code>idle-connection-timeout</code></td>
<td><code>Duration</code></td>
<td><code>PT5M</code></td>
<td>How long should we wait before closing a connection that has no traffic on it</td>
</tr>
<tr>
<td><code>ignore-invalid-named-routing</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>If set to &lt;code&gt;true&lt;/code&gt;, any named routing configured that does not have an associated named listener will NOT cause an exception to be thrown (default behavior is to throw an exception)</td>
</tr>
<tr>
<td><code>max-concurrent-requests</code></td>
<td><code>Integer</code></td>
<td><code>-1</code></td>
<td>Limits the number of requests that can be executed at the same time (the number of active virtual threads of requests)</td>
</tr>
<tr>
<td><code>max-in-memory-entity</code></td>
<td><code>Integer</code></td>
<td><code>131072</code></td>
<td>If the entity is expected to be smaller that this number of bytes, it would be buffered in memory to optimize performance when writing it</td>
</tr>
<tr>
<td><code>max-payload-size</code></td>
<td><code>Long</code></td>
<td><code>-1</code></td>
<td>Maximal number of bytes an entity may have</td>
</tr>
<tr>
<td><code>max-tcp-connections</code></td>
<td><code>Integer</code></td>
<td><code>-1</code></td>
<td>Limits the number of connections that can be opened at a single point in time</td>
</tr>
<tr>
<td><a id="media-context"></a><a href="io.helidon.http.media.MediaContext.md"><code>media-context</code></a></td>
<td><code>MediaContext</code></td>
<td></td>
<td>Configure the listener specific &lt;code&gt;io.helidon.http.media.MediaContext&lt;/code&gt;</td>
</tr>
<tr>
<td><code>name</code></td>
<td><code>String</code></td>
<td><code>@default</code></td>
<td>Name of this socket</td>
</tr>
<tr>
<td><code>port</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Configure listen port</td>
</tr>
<tr>
<td><a id="protocols"></a><a href="io.helidon.webserver.spi.ProtocolConfig.md"><code>protocols</code></a></td>
<td><code>List&lt;ProtocolConfig&gt;</code></td>
<td></td>
<td>Configuration of protocols</td>
</tr>
<tr>
<td><code>protocols-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;protocols&lt;/code&gt;</td>
</tr>
<tr>
<td><a id="requested-uri-discovery"></a><a href="io.helidon.http.RequestedUriDiscoveryContext.md"><code>requested-uri-discovery</code></a></td>
<td><code>RequestedUriDiscoveryContext</code></td>
<td></td>
<td>Requested URI discovery context</td>
</tr>
<tr>
<td><code>restore-response-headers</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Copy and restore response headers before and after passing a request to Jersey for processing</td>
</tr>
<tr>
<td><code>shutdown-grace-period</code></td>
<td><code>Duration</code></td>
<td><code>PT0.5S</code></td>
<td>Grace period in ISO 8601 duration format to allow running tasks to complete before listener&#x27;s shutdown</td>
</tr>
<tr>
<td><code>shutdown-hook</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>When true the webserver registers a shutdown hook with the JVM Runtime</td>
</tr>
<tr>
<td><code>smart-async-writes</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>If enabled and &lt;code&gt;#writeQueueLength()&lt;/code&gt; is greater than 1, then start with async writes but possibly switch to sync writes if async queue size is always below a certain threshold</td>
</tr>
<tr>
<td><a id="sockets"></a><a href="io.helidon.webserver.ListenerConfig.md"><code>sockets</code></a></td>
<td><code>Map&lt;String, ListenerConfig&gt;</code></td>
<td></td>
<td>Socket configurations</td>
</tr>
<tr>
<td><a id="tls"></a><a href="io.helidon.common.tls.Tls.md"><code>tls</code></a></td>
<td><code>Tls</code></td>
<td></td>
<td>Listener TLS configuration</td>
</tr>
<tr>
<td><code>use-nio</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>If set to &lt;code&gt;true&lt;/code&gt;, use NIO socket channel, instead of a socket</td>
</tr>
<tr>
<td><code>write-buffer-size</code></td>
<td><code>Integer</code></td>
<td><code>4096</code></td>
<td>Initial buffer size in bytes of &lt;code&gt;java.io.BufferedOutputStream&lt;/code&gt; created internally to write data to a socket connection</td>
</tr>
<tr>
<td><code>write-queue-length</code></td>
<td><code>Integer</code></td>
<td><code>0</code></td>
<td>Number of buffers queued for write operations</td>
</tr>
</tbody>
</table>

### Deprecated Options

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><a id="connection-config"></a><a href="io.helidon.webserver.ConnectionConfig.md"><code>connection-config</code></a></td>
<td><code>ConnectionConfig</code></td>
<td>Configuration of a connection (established from client against our server)</td>
</tr>
<tr>
<td><code>receive-buffer-size</code></td>
<td><code>Integer</code></td>
<td>Listener receive buffer size</td>
</tr>
</tbody>
</table>

## Merged Types

- [io.helidon.microprofile.server.Server](io.helidon.microprofile.server.Server.md)
- [io.helidon.webserver.WebServer](io.helidon.webserver.WebServer.md)

## Usages

- [`server`](config_reference.md#server)

---

See the [manifest](manifest.md) for all available types.
