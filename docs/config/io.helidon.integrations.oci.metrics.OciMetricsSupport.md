# io.helidon.integrations.oci.metrics.OciMetricsSupport

## Description

OCI Metrics Support

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
<code>batch-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">50</code>
</td>
<td>Sets the maximum no</td>
</tr>
<tr>
<td>
<code>delay</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">60</code>
</td>
<td>Sets the delay interval between metric posting (defaults to <code>#DEFAULT_SCHEDULER_DELAY</code>)</td>
</tr>
<tr>
<td>
<code>description-enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Sets whether the description should be enabled or not</td>
</tr>
<tr>
<td>
<code>compartment-id</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Sets the compartment ID</td>
</tr>
<tr>
<td>
<code>namespace</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Sets the namespace</td>
</tr>
<tr>
<td>
<code>batch-delay</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">1</code>
</td>
<td>Sets the delay interval if metrics are posted in batches (defaults to <code>#DEFAULT_BATCH_DELAY</code>)</td>
</tr>
<tr>
<td>
<a id="scheduling-time-unit"></a>
<a href="java.util.concurrent.TimeUnit.md">
<code>scheduling-time-unit</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">TimeUnit</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value" title="TimeUnit.SECONDS">TimeUnit.SECONDS</code>
</td>
<td>Sets the time unit applied to the initial delay and delay values (defaults to <code>TimeUnit.SECONDS</code>)</td>
</tr>
<tr>
<td>
<code>resource-group</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Sets the resource group</td>
</tr>
<tr>
<td>
<code>scopes</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">All scopes</code>
</td>
<td>Sets which metrics scopes (e.g., base, vendor, application) should be sent to OCI</td>
</tr>
<tr>
<td>
<code>initial-delay</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">1</code>
</td>
<td>Sets the initial delay before metrics are sent to OCI (defaults to <code>#DEFAULT_SCHEDULER_INITIAL_DELAY</code>)</td>
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
<td>Sets whether metrics transmission to OCI is enabled</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
