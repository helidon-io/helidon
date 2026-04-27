# io.helidon.webserver.security.PathsConfig

## Description

Configuration of a single path security setup

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
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>authentication-optional</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td>If called, authentication failure will not abort request and will continue as anonymous (defaults to false)</td>
</tr>
<tr>
<td>
<code>path</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Path to secure</td>
</tr>
<tr>
<td>
<code>authenticate</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td>If called, request will go through authentication process - defaults to false (even if authorize is true)</td>
</tr>
<tr>
<td>
<code>audit-event-type</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Override for event-type, defaults to <code>SecurityHandler#DEFAULT_AUDIT_EVENT_TYPE</code></td>
</tr>
<tr>
<td>
<code>authorizer</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Use a named authorizer (as supported by security - if not defined, default authorizer is used, if none defined, all is permitted)</td>
</tr>
<tr>
<td>
<code>methods</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;PathConfigCustomMethods&gt;">List&lt;PathConfigCustomMethods&gt;</code>
</td>
<td>HTTP methods to match when applying this configured path</td>
</tr>
<tr>
<td>
<code>audit</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td>Whether to audit this request - defaults to false, if enabled, request is audited with event type "request"</td>
</tr>
<tr>
<td>
<code>audit-message-format</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Override for audit message format, defaults to <code>SecurityHandler#DEFAULT_AUDIT_MESSAGE_FORMAT</code></td>
</tr>
<tr>
<td>
<code>sockets</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td>List of sockets this configuration should be applied to</td>
</tr>
<tr>
<td>
<code>authenticator</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Use a named authenticator (as supported by security - if not defined, default authenticator is used)</td>
</tr>
<tr>
<td>
<code>authorize</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td>Enable authorization for this route</td>
</tr>
<tr>
<td>
<code>roles-allowed</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td>An array of allowed roles for this path - must have a security provider supporting roles (either authentication or authorization provider)</td>
</tr>
</tbody>
</table>



## Usages

- [`server.features.security.paths`](io.helidon.webserver.security.SecurityFeature.md#paths)

---

See the [manifest](manifest.md) for all available types.
