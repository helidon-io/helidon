# io.<wbr>helidon.<wbr>integrations.<wbr>oci.<wbr>metrics.<wbr>OciMetrics<wbr>Service

## Description

OCI metrics configuration blueprint

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
<td>Size of each batch of meter data transmitted to the backend system</td>
</tr>
<tr>
<td>
<code>delay</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT60S</code>
</td>
<td>Delay between successive checks for data to transmit</td>
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
<td>Whether meter descriptions should be sent along with data to the backend system</td>
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
<td>Compartment ID</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Name of the config section</td>
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
<td>Namespace associated with each transmission</td>
</tr>
<tr>
<td>
<code>batch-<wbr>delay</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT1S</code>
</td>
<td>Delay between sending successive batches during a single check of data to transmit</td>
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
<td>Resource group associated with each transmission</td>
</tr>
<tr>
<td>
<code>scopes</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>Scopes for filtering which meters are transmitted</td>
</tr>
<tr>
<td>
<code>initial-<wbr>delay</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT1S</code>
</td>
<td>Initial delay before first check for data to transmit</td>
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
<td>Whether meter data transmission is enabled</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
