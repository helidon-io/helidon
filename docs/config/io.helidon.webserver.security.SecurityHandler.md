# io.helidon.webserver.security.SecurityHandler

## Description

Configuration of a &lt;code&gt;io.helidon.webserver.security.SecurityHandler&lt;/code&gt;

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>authentication-optional</code></td>
<td><code>Boolean</code></td>
<td>If called, authentication failure will not abort request and will continue as anonymous (defaults to false)</td>
</tr>
<tr>
<td><code>authenticate</code></td>
<td><code>Boolean</code></td>
<td>If called, request will go through authentication process - defaults to false (even if authorize is true)</td>
</tr>
<tr>
<td><code>audit-event-type</code></td>
<td><code>String</code></td>
<td>Override for event-type, defaults to &lt;code&gt;SecurityHandler#DEFAULT_AUDIT_EVENT_TYPE&lt;/code&gt;</td>
</tr>
<tr>
<td><code>authorizer</code></td>
<td><code>String</code></td>
<td>Use a named authorizer (as supported by security - if not defined, default authorizer is used, if none defined, all is permitted)</td>
</tr>
<tr>
<td><code>audit</code></td>
<td><code>Boolean</code></td>
<td>Whether to audit this request - defaults to false, if enabled, request is audited with event type &quot;request&quot;</td>
</tr>
<tr>
<td><code>audit-message-format</code></td>
<td><code>String</code></td>
<td>Override for audit message format, defaults to &lt;code&gt;SecurityHandler#DEFAULT_AUDIT_MESSAGE_FORMAT&lt;/code&gt;</td>
</tr>
<tr>
<td><code>sockets</code></td>
<td><code>List&lt;String&gt;</code></td>
<td>List of sockets this configuration should be applied to</td>
</tr>
<tr>
<td><code>authenticator</code></td>
<td><code>String</code></td>
<td>Use a named authenticator (as supported by security - if not defined, default authenticator is used)</td>
</tr>
<tr>
<td><code>authorize</code></td>
<td><code>Boolean</code></td>
<td>Enable authorization for this route</td>
</tr>
<tr>
<td><code>roles-allowed</code></td>
<td><code>List&lt;String&gt;</code></td>
<td>An array of allowed roles for this path - must have a security provider supporting roles (either authentication or authorization provider)</td>
</tr>
</tbody>
</table>


## Dependent Types

- [io.helidon.webserver.security.PathsConfig](io.helidon.webserver.security.PathsConfig.md)

## Usages

- [`server.features.security.defaults`](io.helidon.webserver.security.SecurityFeature.md#defaults)

---

See the [manifest](manifest.md) for all available types.
