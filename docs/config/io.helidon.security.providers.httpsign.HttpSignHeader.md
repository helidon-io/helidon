# io.helidon.security.providers.httpsign.HttpSignHeader

## Description

This type is an enumeration.

## Allowed Values

<style>
    table.cm-table code {
        white-space: nowrap !important;
    }
</style>

<table class="cm-table">
<thead>
<tr>
<th>Value</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>SIGNATURE</code></td>
<td>Creates (or validates) a "Signature" header</td>
</tr>
<tr>
<td><code>AUTHORIZATION</code></td>
<td>Creates (or validates) an "Authorization" header, that contains "Signature" as the beginning of its content (the rest of the header is the same as for <code>#SIGNATURE</code></td>
</tr>
<tr>
<td><code>CUSTOM</code></td>
<td>Custom provided using a <code>io.helidon.security.util.TokenHandler</code></td>
</tr>
</tbody>
</table>

---

See the [manifest](manifest.md) for all available types.
