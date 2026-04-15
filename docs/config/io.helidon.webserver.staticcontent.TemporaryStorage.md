# io.helidon.webserver.staticcontent.TemporaryStorage

## Description

Configuration of temporary storage for classpath based handlers

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
<td><code>file-prefix</code></td>
<td><code>String</code></td>
<td><code>helidon-ws</code></td>
<td>Prefix of the files in temporary storage</td>
</tr>
<tr>
<td><code>delete-on-exit</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether temporary files should be deleted on JVM exit</td>
</tr>
<tr>
<td><code>file-suffix</code></td>
<td><code>String</code></td>
<td><code>.je</code></td>
<td>Suffix of the files in temporary storage</td>
</tr>
<tr>
<td><code>directory</code></td>
<td><code>Path</code></td>
<td></td>
<td>Location of the temporary storage, defaults to temporary storage configured for the JVM</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether the temporary storage is enabled, defaults to &lt;code&gt;true&lt;/code&gt;</td>
</tr>
</tbody>
</table>


## Usages

- [`server.features.static-content.classpath.temporary-storage`](io.helidon.webserver.staticcontent.ClasspathHandlerConfig.md#temporary-storage)
- [`server.features.static-content.temporary-storage`](io.helidon.webserver.staticcontent.StaticContentFeature.md#temporary-storage)

---

See the [manifest](manifest.md) for all available types.
