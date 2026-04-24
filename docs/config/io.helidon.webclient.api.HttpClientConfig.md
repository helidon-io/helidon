# io.helidon.webclient.api.HttpClientConfig

## Description

This can be used by any HTTP client version, and does not act as a factory, for easy extensibility

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }

    .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


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
<code>default-headers</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, String&gt;">Map&lt;String, String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Default headers to be used in every request from configuration</td>
</tr>
<tr>
<td>
<code>follow-redirects</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to follow redirects</td>
</tr>
<tr>
<td>
<code>base-uri</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="HttpCustomMethods">HttpCustomMethods</code>
</td>
<td class="cm-default-cell">
</td>
<td>Base uri used by the client in all requests</td>
</tr>
<tr>
<td>
<code>read-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
</td>
<td>Read timeout</td>
</tr>
<tr>
<td>
<code>connection-cache-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">256</code>
</td>
<td>Maximal size of the connection cache for a single connection key</td>
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
<code class="cm-truncate-value">create()</code>
</td>
<td>Configure the listener specific <code>io.helidon.http.media.MediaContext</code></td>
</tr>
<tr>
<td>
<a id="cookie-manager"></a>
<a href="io.helidon.webclient.api.WebClientCookieManager.md">
<code>cookie-manager</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="WebClientCookieManager">WebClientCookieManager</code>
</td>
<td class="cm-default-cell">
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
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;WebClientService&gt;">List&lt;WebClientService&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>WebClient services</td>
</tr>
<tr>
<td>
<code>relative-uris</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Can be set to <code>true</code> to force the use of relative URIs in all requests, regardless of the presence or absence of proxies or no-proxy lists</td>
</tr>
<tr>
<td>
<code>send-expect-continue</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether Expect-100-Continue header is sent to verify server availability before sending an entity</td>
</tr>
<tr>
<td>
<code>connect-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
</td>
<td>Connect timeout</td>
</tr>
<tr>
<td>
<a id="proxy"></a>
<a href="io.helidon.webclient.api.Proxy.md">
<code>proxy</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Proxy</code>
</td>
<td class="cm-default-cell">
</td>
<td>Proxy configuration to be used for requests</td>
</tr>
<tr>
<td>
<a id="media-type-parser-mode"></a>
<a href="io.helidon.common.media.type.ParserMode.md">
<code>media-type-parser-mode</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">ParserMode</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">STRICT</code>
</td>
<td>Configure media type parsing mode for HTTP <code>Content-Type</code> header</td>
</tr>
<tr>
<td>
<code>keep-alive</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Determines if connection keep alive is enabled (NOT socket keep alive, but HTTP connection keep alive, to re-use the same connection for multiple requests)</td>
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
<td>If the entity is expected to be smaller that this number of bytes, it would be buffered in memory to optimize performance</td>
</tr>
<tr>
<td>
<code>share-connection-cache</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to share connection cache between all the WebClient instances in JVM</td>
</tr>
<tr>
<td>
<code>services-discover-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to enable automatic service discovery for <code>services</code></td>
</tr>
<tr>
<td>
<code>max-redirects</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">10</code>
</td>
<td>Max number of followed redirects</td>
</tr>
<tr>
<td>
<a id="socket-options"></a>
<a href="io.helidon.common.socket.SocketOptions.md">
<code>socket-options</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="SocketOptions">SocketOptions</code>
</td>
<td class="cm-default-cell">
</td>
<td>Socket options for connections opened by this client</td>
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
<td>TLS configuration for any TLS request from this client</td>
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
<td>Buffer size used when writing data to the underlying socket on a client TCP connection</td>
</tr>
<tr>
<td>
<code>properties</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, String&gt;">Map&lt;String, String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Properties configured for this client</td>
</tr>
<tr>
<td>
<code>read-continue-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT1S</code>
</td>
<td>Socket 100-Continue read timeout</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
