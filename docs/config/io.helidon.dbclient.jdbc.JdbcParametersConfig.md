# io.helidon.dbclient.jdbc.JdbcParametersConfig

## Description

JDBC parameters setter configuration

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
<code>use-byte-array-binding</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Use <code>java.sql.PreparedStatement#setBinaryStream(int, java.io.InputStream, int)</code> binding for <code>byte[]</code> values</td>
</tr>
<tr>
<td>
<code>use-string-binding</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Use <code>java.sql.PreparedStatement#setCharacterStream(int, java.io.Reader, int)</code> binding for <code>String</code> values with length above <code>#stringBindingSize()</code> limit</td>
</tr>
<tr>
<td>
<code>timestamp-for-local-time</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Use <code>java.sql.PreparedStatement#setTimestamp(int, java.sql.Timestamp)</code> to set <code>java.time.LocalTime</code> values when <code>true</code> or use <code>java.sql.PreparedStatement#setTime(int, java.sql.Time)</code> when <code>false</code></td>
</tr>
<tr>
<td>
<code>string-binding-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">1024</code>
</td>
<td><code>String</code> values with length above this limit will be bound using <code>java.sql.PreparedStatement#setCharacterStream(int, java.io.Reader, int)</code> if <code>#useStringBinding()</code> is set to <code>true</code></td>
</tr>
<tr>
<td>
<code>use-n-string</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Use SQL <code>NCHAR</code>, <code>NVARCHAR</code> or <code>LONGNVARCHAR</code> value conversion for <code>String</code> values</td>
</tr>
<tr>
<td>
<code>set-object-for-java-time</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Set all <code>java.time</code> Date/Time values directly using <code>java.sql.PreparedStatement#setObject(int, Object)</code></td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
