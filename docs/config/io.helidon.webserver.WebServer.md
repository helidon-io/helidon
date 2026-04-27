# io.helidon.webserver.WebServer

## Description

WebServer configuration bean

## Configuration options

<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
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
<code>restore-response-headers</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Copy and restore response headers before and after passing a request to Jersey for processing</td>
</tr>
<tr>
<td>
<a id="concurrency-limit"></a>
<a href="io.helidon.common.concurrency.limits.Limit.md">
<code>concurrency-limit</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Limit</code>
</td>
<td class="cm-default-cell">
</td>
<td>Concurrency limit to use to limit concurrent execution of incoming requests</td>
</tr>
<tr>
<td>
<a id="content-encoding"></a>
<a href="io.helidon.http.encoding.ContentEncodingContext.md">
<code>content-encoding</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="ContentEncodingContext">ContentEncodingContext</code>
</td>
<td class="cm-default-cell">
</td>
<td>Configure the listener specific <code>io.helidon.http.encoding.ContentEncodingContext</code></td>
</tr>
<tr>
<td>
<a id="media-context"></a>
<a href="io.helidon.http.media.MediaContext.md">
<code>media-context</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="MediaContext">MediaContext</code>
</td>
<td class="cm-default-cell">
</td>
<td>Configure the listener specific <code>io.helidon.http.media.MediaContext</code></td>
</tr>
<tr>
<td>
<code>max-payload-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">-1</code>
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
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;ServerFeature&gt;">List&lt;ServerFeature&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Server features allow customization of the server, listeners, or routings</td>
</tr>
<tr>
<td>
<code>use-nio</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>If set to <code>true</code>, use NIO socket channel, instead of a socket</td>
</tr>
<tr>
<td>
<code>protocols-discover-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to enable automatic service discovery for <code>protocols</code></td>
</tr>
<tr>
<td>
<code>enable-proxy-protocol</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Enable proxy protocol support for this socket</td>
</tr>
<tr>
<td>
<code>host</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">0.0.0.0</code>
</td>
<td>Host of the default socket</td>
</tr>
<tr>
<td>
<code>write-queue-length</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">0</code>
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
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, ListenerConfig&gt;">Map&lt;String, ListenerConfig&gt;</code>
</td>
<td class="cm-default-cell">
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
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;ProtocolConfig&gt;">List&lt;ProtocolConfig&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Configuration of protocols</td>
</tr>
<tr>
<td>
<code>max-tcp-connections</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">-1</code>
</td>
<td>Limits the number of connections that can be opened at a single point in time</td>
</tr>
<tr>
<td>
<code>bind-address</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="ListenerCustomMethods">ListenerCustomMethods</code>
</td>
<td class="cm-default-cell">
</td>
<td>The address to bind to</td>
</tr>
<tr>
<td>
<code>idle-connection-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT5M</code>
</td>
<td>How long should we wait before closing a connection that has no traffic on it</td>
</tr>
<tr>
<td>
<code>shutdown-grace-period</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT0.5S</code>
</td>
<td>Grace period in ISO 8601 duration format to allow running tasks to complete before listener's shutdown</td>
</tr>
<tr>
<td>
<code>max-concurrent-requests</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">-1</code>
</td>
<td>Limits the number of requests that can be executed at the same time (the number of active virtual threads of requests)</td>
</tr>
<tr>
<td>
<code>features-discover-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to enable automatic service discovery for <code>features</code></td>
</tr>
<tr>
<td>
<code>shutdown-hook</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>When true the webserver registers a shutdown hook with the JVM Runtime</td>
</tr>
<tr>
<td>
<a id="error-handling"></a>
<a href="io.helidon.webserver.ErrorHandling.md">
<code>error-handling</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="ErrorHandling">ErrorHandling</code>
</td>
<td class="cm-default-cell">
</td>
<td>Configuration for this listener's error handling</td>
</tr>
<tr>
<td>
<code>concurrency-limit-discover-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether to enable automatic service discovery for <code>concurrency-limit</code></td>
</tr>
<tr>
<td>
<code>backlog</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">1024</code>
</td>
<td>Accept backlog</td>
</tr>
<tr>
<td>
<code>max-in-memory-entity</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">131072</code>
</td>
<td>If the entity is expected to be smaller that this number of bytes, it would be buffered in memory to optimize performance when writing it</td>
</tr>
<tr>
<td>
<code>ignore-invalid-named-routing</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>If set to <code>true</code>, any named routing configured that does not have an associated named listener will NOT cause an exception to be thrown (default behavior is to throw an exception)</td>
</tr>
<tr>
<td>
<code>smart-async-writes</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>If enabled and <code>#writeQueueLength()</code> is greater than 1, then start with async writes but possibly switch to sync writes if async queue size is always below a certain threshold</td>
</tr>
<tr>
<td>
<a id="connection-options"></a>
<a href="io.helidon.common.socket.SocketOptions.md">
<code>connection-options</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="SocketOptions">SocketOptions</code>
</td>
<td class="cm-default-cell">
</td>
<td>Options for connections accepted by this listener</td>
</tr>
<tr>
<td>
<code>port</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">0</code>
</td>
<td>Port of the default socket</td>
</tr>
<tr>
<td>
<a id="requested-uri-discovery"></a>
<a href="io.helidon.http.RequestedUriDiscoveryContext.md">
<code>requested-uri-discovery</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="RequestedUriDiscoveryContext">RequestedUriDiscoveryContext</code>
</td>
<td class="cm-default-cell">
</td>
<td>Requested URI discovery context</td>
</tr>
<tr>
<td>
<code>idle-connection-period</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT2M</code>
</td>
<td>How often should we check for <code>#idleConnectionTimeout()</code></td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">@default</code>
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
<td class="cm-type-cell">
<code class="cm-truncate-value">Tls</code>
</td>
<td class="cm-default-cell">
</td>
<td>Listener TLS configuration</td>
</tr>
<tr>
<td>
<code>write-buffer-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">4096</code>
</td>
<td>Initial buffer size in bytes of <code>java.io.BufferedOutputStream</code> created internally to write data to a socket connection</td>
</tr>
</tbody>
</table>


### Deprecated Options


<table class="cm-table">
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
<code>connection-config</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="ConnectionConfig">ConnectionConfig</code>
</td>
<td>Configuration of a connection (established from client against our server)</td>
</tr>
<tr>
<td>
<code>receive-buffer-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td>Listener receive buffer size</td>
</tr>
</tbody>
</table>


## Usages

- [`server`](config_reference.md#server)

---

See the [manifest](manifest.md) for all available types.
