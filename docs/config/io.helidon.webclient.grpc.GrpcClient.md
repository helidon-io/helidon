# io.helidon.webclient.grpc.GrpcClient

## Description

Configuration of a grpc client

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
<a id="protocol-config"></a>
<a href="io.helidon.webclient.grpc.GrpcClientProtocolConfig.md">
<code>protocol-config</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="GrpcClientProtocolConfig">GrpcClientProtocolConfig</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">create()</code>
</td>
<td>gRPC specific configuration</td>
</tr>
<tr>
<td>
<a id="grpc-services"></a>
<a href="io.helidon.webclient.grpc.spi.GrpcClientService.md">
<code>grpc-services</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;GrpcClientService&gt;">List&lt;GrpcClientService&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>gRPC client services</td>
</tr>
<tr>
<td>
<code>enable-metrics</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether to collect metrics for gRPC client calls</td>
</tr>
<tr>
<td>
<code>grpc-services-discover-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether to enable automatic service discovery for <code>grpc-services</code></td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
