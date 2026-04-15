# io.helidon.integrations.oci.metrics.OciMetricsSupport

## Description

OCI Metrics Support

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
<td><code>batch-size</code></td>
<td><code>Integer</code></td>
<td><code>50</code></td>
<td>Sets the maximum no</td>
</tr>
<tr>
<td><code>delay</code></td>
<td><code>Long</code></td>
<td><code>60</code></td>
<td>Sets the delay interval between metric posting (defaults to &lt;code&gt;#DEFAULT_SCHEDULER_DELAY&lt;/code&gt;)</td>
</tr>
<tr>
<td><code>description-enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Sets whether the description should be enabled or not</td>
</tr>
<tr>
<td><code>compartment-id</code></td>
<td><code>String</code></td>
<td></td>
<td>Sets the compartment ID</td>
</tr>
<tr>
<td><code>namespace</code></td>
<td><code>String</code></td>
<td></td>
<td>Sets the namespace</td>
</tr>
<tr>
<td><code>batch-delay</code></td>
<td><code>Long</code></td>
<td><code>1</code></td>
<td>Sets the delay interval if metrics are posted in batches (defaults to &lt;code&gt;#DEFAULT_BATCH_DELAY&lt;/code&gt;)</td>
</tr>
<tr>
<td><a id="scheduling-time-unit"></a><a href="java.util.concurrent.TimeUnit.md"><code>scheduling-time-unit</code></a></td>
<td><code>TimeUnit</code></td>
<td><code>TimeUnit.SECONDS</code></td>
<td>Sets the time unit applied to the initial delay and delay values (defaults to &lt;code&gt;TimeUnit.SECONDS&lt;/code&gt;)</td>
</tr>
<tr>
<td><code>resource-group</code></td>
<td><code>String</code></td>
<td></td>
<td>Sets the resource group</td>
</tr>
<tr>
<td><code>scopes</code></td>
<td><code>List&lt;String&gt;</code></td>
<td><code>All scopes</code></td>
<td>Sets which metrics scopes (e.g., base, vendor, application) should be sent to OCI</td>
</tr>
<tr>
<td><code>initial-delay</code></td>
<td><code>Long</code></td>
<td><code>1</code></td>
<td>Sets the initial delay before metrics are sent to OCI (defaults to &lt;code&gt;#DEFAULT_SCHEDULER_INITIAL_DELAY&lt;/code&gt;)</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Sets whether metrics transmission to OCI is enabled</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
