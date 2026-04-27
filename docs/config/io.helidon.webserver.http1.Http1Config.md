# io.helidon.webserver.http1.Http1Config

## Description

HTTP/1.1 server configuration

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
<code>continue-immediately</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>When true WebServer answers to expect continue with 100 continue immediately, not waiting for user to actually request the data</td>
</tr>
<tr>
<td>
<code>validate-response-headers</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether to validate headers</td>
</tr>
<tr>
<td>
<code>validate-prologue</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>If set to false, any query and fragment is accepted (even containing illegal characters)</td>
</tr>
<tr>
<td>
<code>validate-path</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>If set to false, any path is accepted (even containing illegal characters)</td>
</tr>
<tr>
<td>
<code>max-headers-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">16384</code>
</td>
<td>Maximal size of received headers in bytes</td>
</tr>
<tr>
<td>
<code>max-buffered-entity-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Size</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">64 KB</code>
</td>
<td>Configure the maximum size allowed for an entity that can be explicitly buffered by the application by calling <code>io.helidon.http.media.ReadableEntity#buffer</code></td>
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
<td>Requested URI discovery settings</td>
</tr>
<tr>
<td>
<code>validate-request-headers</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to validate headers</td>
</tr>
<tr>
<td>
<code>max-prologue-length</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">4096</code>
</td>
<td>Maximal size of received HTTP prologue (GET /path HTTP/1.1)</td>
</tr>
<tr>
<td>
<code>send-log</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Logging of sent packets</td>
</tr>
<tr>
<td>
<code>recv-log</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Logging of received packets</td>
</tr>
<tr>
<td>
<code>send-keep-alive-header</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to send the default <code>Connection: keep-alive</code> response header for persistent HTTP/1.1 connections</td>
</tr>
</tbody>
</table>


### Deprecated Options


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
<code>validate-request-host-header</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Request host header validation</td>
</tr>
</tbody>
</table>


## Usages

- [`server.protocols.http_1_1`](io.helidon.webserver.spi.ProtocolConfig.md#http_1_1)
- [`server.sockets.protocols.http_1_1`](io.helidon.webserver.spi.ProtocolConfig.md#http_1_1)

---

See the [manifest](manifest.md) for all available types.
