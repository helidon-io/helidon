# io.<wbr>helidon.<wbr>integrations.<wbr>oci.<wbr>metrics.<wbr>OciMetrics<wbr>Support

## Description

OCI Metrics Support

## Configuration options


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
<code>batch-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>50</code>
</td>
<td>Sets the maximum no</td>
</tr>
<tr>
<td>
<code>delay</code>
</td>
<td>
<code>Long</code>
</td>
<td>
<code>60</code>
</td>
<td>Sets the delay interval between metric posting (defaults to <code>#DEFAULT_<wbr>SCHEDULER_<wbr>DELAY</code>)</td>
</tr>
<tr>
<td>
<code>description-<wbr>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Sets whether the description should be enabled or not</td>
</tr>
<tr>
<td>
<code>compartment-<wbr>id</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Sets the compartment ID</td>
</tr>
<tr>
<td>
<code>namespace</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Sets the namespace</td>
</tr>
<tr>
<td>
<code>batch-<wbr>delay</code>
</td>
<td>
<code>Long</code>
</td>
<td>
<code>1</code>
</td>
<td>Sets the delay interval if metrics are posted in batches (defaults to <code>#DEFAULT_<wbr>BATCH_<wbr>DELAY</code>)</td>
</tr>
<tr>
<td>
<a id="scheduling-time-unit"></a>
<a href="java.util.concurrent.TimeUnit.md">
<code>scheduling-<wbr>time-<wbr>unit</code>
</a>
</td>
<td>
<code>Time<wbr>Unit</code>
</td>
<td>
<code>Time<wbr>Unit.<wbr>SECONDS</code>
</td>
<td>Sets the time unit applied to the initial delay and delay values (defaults to <code>Time<wbr>Unit.<wbr>SECONDS</code>)</td>
</tr>
<tr>
<td>
<code>resource-<wbr>group</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Sets the resource group</td>
</tr>
<tr>
<td>
<code>scopes</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
<code>All scopes</code>
</td>
<td>Sets which metrics scopes (e.g., base, vendor, application) should be sent to OCI</td>
</tr>
<tr>
<td>
<code>initial-<wbr>delay</code>
</td>
<td>
<code>Long</code>
</td>
<td>
<code>1</code>
</td>
<td>Sets the initial delay before metrics are sent to OCI (defaults to <code>#DEFAULT_<wbr>SCHEDULER_<wbr>INITIAL_<wbr>DELAY</code>)</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Sets whether metrics transmission to OCI is enabled</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
