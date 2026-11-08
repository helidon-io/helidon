# io.<wbr>helidon.<wbr>openapi.<wbr>Open<wbr>ApiFeature

## Description

OpenAPI feature configuration

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
<a id="document"></a>
<a href="io.helidon.openapi.spi.OpenApiVersion.md">
<code>document</code>
</a>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> Open<wbr>ApiVersion&gt;<wbr> or List&lt;<wbr>Open<wbr>ApiVersion&gt;</code>
</td>
<td>
</td>
<td>OpenAPI version implementation for rendered generated or merged documents</td>
</tr>
<tr>
<td>
<code>document-<wbr>discover-<wbr>services</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to enable automatic service discovery for <code>document</code></td>
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
<td>Whether the feature is enabled</td>
</tr>
<tr>
<td>
<code>generated.<wbr>document-<wbr>sources</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>Named generated document metadata sources to use, in the order configured</td>
</tr>
<tr>
<td>
<a id="generated-mode"></a>
<a href="io.helidon.openapi.OpenApiGeneratedMode.md">
<code>generated.<wbr>mode</code>
</a>
</td>
<td>
<code>Open<wbr>ApiGenerated<wbr>Mode</code>
</td>
<td>
<code>STATIC_<wbr>FIRST</code>
</td>
<td>Generated document source handling mode</td>
</tr>
<tr>
<td>
<code>generated.<wbr>operation-<wbr>ids</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> String&gt;</code>
</td>
<td>
</td>
<td>Operation ids to use for generated Java methods</td>
</tr>
<tr>
<td>
<code>generated.<wbr>resolve-<wbr>config-<wbr>expressions</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether generated document sources resolve annotation string values as Helidon config expressions at runtime</td>
</tr>
<tr>
<td>
<a id="manager"></a>
<a href="io.helidon.openapi.OpenApiManager.md">
<code>manager</code>
</a>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> Open<wbr>ApiManager&gt;<wbr> or List&lt;<wbr>Open<wbr>ApiManager&gt;</code>
</td>
<td>
</td>
<td>OpenAPI manager</td>
</tr>
<tr>
<td>
<code>manager-<wbr>discover-<wbr>services</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether to enable automatic service discovery for <code>manager</code></td>
</tr>
<tr>
<td>
<code>permit-<wbr>all</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to allow anybody to access the endpoint</td>
</tr>
<tr>
<td>
<code>roles</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
<code>openapi</code>
</td>
<td>Hints for role names the user is expected to be in</td>
</tr>
<tr>
<td>
<a id="services"></a>
<a href="io.helidon.openapi.OpenApiService.md">
<code>services</code>
</a>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> Open<wbr>ApiService&gt;<wbr> or List&lt;<wbr>Open<wbr>ApiService&gt;</code>
</td>
<td>
</td>
<td>OpenAPI services</td>
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
<code>sockets</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>List of sockets to register this feature on</td>
</tr>
<tr>
<td>
<code>static-<wbr>file</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Path of the static OpenAPI document file</td>
</tr>
<tr>
<td>
<code>web-<wbr>context</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>/openapi</code>
</td>
<td>Web context path for the OpenAPI endpoint</td>
</tr>
<tr>
<td>
<code>weight</code>
</td>
<td>
<code>Double</code>
</td>
<td>
<code>90.<wbr>0</code>
</td>
<td>Weight of the OpenAPI feature</td>
</tr>
</tbody>
</table>



## Usages

- <a href="config_reference.md#openapi"><code>openapi</code></a>
- <a href="io.helidon.webserver.spi.ServerFeature.md#openapi"><code>server.<wbr>features.<wbr>openapi</code></a>

---

See the [manifest](manifest.md) for all available types.
