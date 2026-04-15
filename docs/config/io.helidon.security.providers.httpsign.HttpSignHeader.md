# io.helidon.security.providers.httpsign.HttpSignHeader

## Description

This type is an enumeration.

## Allowed Values

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Value</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>SIGNATURE</code></td>
<td>Creates (or validates) a &quot;Signature&quot; header</td>
</tr>
<tr>
<td><code>AUTHORIZATION</code></td>
<td>Creates (or validates) an &quot;Authorization&quot; header, that contains &quot;Signature&quot; as the beginning of its content (the rest of the header is the same as for &lt;code&gt;#SIGNATURE&lt;/code&gt;</td>
</tr>
<tr>
<td><code>CUSTOM</code></td>
<td>Custom provided using a &lt;code&gt;io.helidon.security.util.TokenHandler&lt;/code&gt;</td>
</tr>
</tbody>
</table>

---

See the [manifest](manifest.md) for all available types.
