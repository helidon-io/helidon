# io.helidon.dbclient.jdbc.JdbcParametersConfig

## Description

JDBC parameters setter configuration

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
<td><code>use-byte-array-binding</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Use &lt;code&gt;java.sql.PreparedStatement#setBinaryStream(int, java.io.InputStream, int)&lt;/code&gt; binding for &lt;code&gt;byte[]&lt;/code&gt; values</td>
</tr>
<tr>
<td><code>use-string-binding</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Use &lt;code&gt;java.sql.PreparedStatement#setCharacterStream(int, java.io.Reader, int)&lt;/code&gt; binding for &lt;code&gt;String&lt;/code&gt; values with length above &lt;code&gt;#stringBindingSize()&lt;/code&gt; limit</td>
</tr>
<tr>
<td><code>timestamp-for-local-time</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Use &lt;code&gt;java.sql.PreparedStatement#setTimestamp(int, java.sql.Timestamp)&lt;/code&gt; to set &lt;code&gt;java.time.LocalTime&lt;/code&gt; values when &lt;code&gt;true&lt;/code&gt; or use &lt;code&gt;java.sql.PreparedStatement#setTime(int, java.sql.Time)&lt;/code&gt; when &lt;code&gt;false&lt;/code&gt;</td>
</tr>
<tr>
<td><code>string-binding-size</code></td>
<td><code>Integer</code></td>
<td><code>1024</code></td>
<td>&lt;code&gt;String&lt;/code&gt; values with length above this limit will be bound using &lt;code&gt;java.sql.PreparedStatement#setCharacterStream(int, java.io.Reader, int)&lt;/code&gt; if &lt;code&gt;#useStringBinding()&lt;/code&gt; is set to &lt;code&gt;true&lt;/code&gt;</td>
</tr>
<tr>
<td><code>use-n-string</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Use SQL &lt;code&gt;NCHAR&lt;/code&gt;, &lt;code&gt;NVARCHAR&lt;/code&gt; or &lt;code&gt;LONGNVARCHAR&lt;/code&gt; value conversion for &lt;code&gt;String&lt;/code&gt; values</td>
</tr>
<tr>
<td><code>set-object-for-java-time</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Set all &lt;code&gt;java.time&lt;/code&gt; Date/Time values directly using &lt;code&gt;java.sql.PreparedStatement#setObject(int, Object)&lt;/code&gt;</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
