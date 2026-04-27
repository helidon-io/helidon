# io.helidon.webserver.staticcontent.TemporaryStorage

## Description

Configuration of temporary storage for classpath based handlers

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
<code>file-prefix</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">helidon-ws</code>
</td>
<td>Prefix of the files in temporary storage</td>
</tr>
<tr>
<td>
<code>delete-on-exit</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether temporary files should be deleted on JVM exit</td>
</tr>
<tr>
<td>
<code>file-suffix</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">.je</code>
</td>
<td>Suffix of the files in temporary storage</td>
</tr>
<tr>
<td>
<code>directory</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Path</code>
</td>
<td class="cm-default-cell">
</td>
<td>Location of the temporary storage, defaults to temporary storage configured for the JVM</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether the temporary storage is enabled, defaults to <code>true</code></td>
</tr>
</tbody>
</table>



## Usages

- [`server.features.static-content.classpath.temporary-storage`](io.helidon.webserver.staticcontent.ClasspathHandlerConfig.md#temporary-storage)
- [`server.features.static-content.temporary-storage`](io.helidon.webserver.staticcontent.StaticContentFeature.md#temporary-storage)

---

See the [manifest](manifest.md) for all available types.
