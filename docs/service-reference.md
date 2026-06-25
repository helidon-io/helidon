<!--@frontmatter
description: "Service reference"
navigation:
  icon: i-lucide-cable
-->
# Service Registry Reference

The following section lists all services and modules that provide them.

> [!NOTE]
>  Work in progress!

## Contracts

<table>
<thead>
<tr>
<th>Contract</th>
<th>Weight</th>
<th>Module</th>
<th>Description</th>
<th>Qualifiers</th>
</tr>
</thead>
<tbody>
<tr>
<td rowspan="2" style="vertical-align: middle"><code>io.<wbr>helidon.<wbr>common.<wbr>config.<wbr>Config</code></td>
<td><code>80</code></td>
<td><code>io.<wbr>helidon.<wbr>common.<wbr>config</code></td>
<td>Empty config instance</td>
<td></td>
</tr>
<tr>
<td><code>90</code></td>
<td><code>io.<wbr>helidon.<wbr>config</code></td>
<td>Current configuration</td>
<td></td>
</tr>
<tr>
<td><code>io.<wbr>helidon.<wbr>config.<wbr>Config</code></td>
<td><code>90</code></td>
<td><code>io.<wbr>helidon.<wbr>config</code></td>
<td>Current configuration</td>
<td></td>
</tr>
<tr>
<td><code>io.<wbr>helidon.<wbr>scheduling.<wbr>Task<wbr>Manager</code></td>
<td><code>90</code></td>
<td><code>io.<wbr>helidon.<wbr>scheduling</code></td>
<td>Current task manager</td>
<td></td>
</tr>
<tr>
<td><code>java.<wbr>time.<wbr>Clock</code></td>
<td><code>90</code></td>
<td><code>io.<wbr>helidon.<wbr>validation</code></td>
<td>Clock used to check calendar related constraints, defaults to current time-zone</td>
<td></td>
</tr>
<tr>
<td><code>io.<wbr>helidon.<wbr>validation.<wbr>Type<wbr>Validation</code></td>
<td><code>90</code></td>
<td><code>io.<wbr>helidon.<wbr>validation</code></td>
<td>Methods to validate types annotated with <code>@Validation.<wbr>Validated</code></td>
<td></td>
</tr>
<tr>
<td><code>io.<wbr>helidon.<wbr>validation.<wbr>spi.<wbr>Constraint<wbr>Validator<wbr>Provider</code></td>
<td><code>70</code></td>
<td><code>io.<wbr>helidon.<wbr>validation</code></td>
<td>Constraint validator providers for each built-in constraint</td>
<td><code>@Service.Named(<wbr>"typeName")</code></td>
</tr>
<tr>
<td><code>io.<wbr>helidon.<wbr>common.<wbr>mapper.<wbr>Mappers</code></td>
<td><code>100</code></td>
<td><code>io.<wbr>helidon.<wbr>common.<wbr>mapper</code></td>
<td>Current mappers</td>
<td></td>
</tr>
<tr>
<td><code>io.<wbr>helidon.<wbr>common.<wbr>mapper.<wbr>Mapper<wbr>Provider</code></td>
<td><code>0.1</code></td>
<td><code>io.<wbr>helidon.<wbr>common.<wbr>mapper</code></td>
<td>A provider of mappers</td>
<td></td>
</tr>
<tr>
<td><code>io.<wbr>helidon.<wbr>common.<wbr>mapper.<wbr>Default<wbr>Resolver</code></td>
<td><code>100</code></td>
<td><code>io.<wbr>helidon.<wbr>common.<wbr>mapper</code></td>
<td>Resolver of defaults annotation to a list of expected types</td>
<td></td>
</tr>
<tr>
<td><code>*</code></td>
<td></td>
<td><code>io.<wbr>helidon.<wbr>config</code></td>
<td>Injection point of a configured object</td>
<td><code>@Configuration.<wbr>Value</code></td>
</tr>
<tr>
<td><code>io.<wbr>helidon.<wbr>config.<wbr>Meta<wbr>Config</code></td>
<td><code>100</code></td>
<td><code>io.<wbr>helidon.<wbr>config</code></td>
<td>Current meta configuration, or for a specific file</td>
<td><code>@Service.Named(<wbr>"file.<wbr>yaml")</code></td>
</tr>
<tr>
<td><code>io.<wbr>helidon.<wbr>webserver.<wbr>http.<wbr>spi.<wbr>Error<wbr>Handler<wbr>Provider</code></td>
<td><code>100</code></td>
<td></td>
<td>Error handler provider to add to WebServer</td>
<td></td>
</tr>
<tr>
<td><code>io.<wbr>helidon.<wbr>webserver.<wbr>WebServer</code></td>
<td><code>100</code></td>
<td><code>io.<wbr>helidon.<wbr>webserver</code></td>
<td>WebServer instance, only available in Helidon Declarative</td>
<td></td>
</tr>
<tr>
<td><code>io.<wbr>helidon.<wbr>security.<wbr>Security</code></td>
<td><code>100</code></td>
<td><code>io.<wbr>helidon.<wbr>security</code></td>
<td>Current security</td>
<td></td>
</tr>
<tr>
<td><code>io.<wbr>helidon.<wbr>health.<wbr>Health<wbr>Check</code></td>
<td></td>
<td></td>
<td>Health check instances to be added to WebServer health observer</td>
<td></td>
</tr>
<tr>
<td><code>io.<wbr>helidon.<wbr>metrics.<wbr>api.<wbr>Metrics<wbr>Factory</code></td>
<td>90</td>
<td><code>io.<wbr>helidon.<wbr>metrics.<wbr>api</code></td>
<td>Factory of meter registries</td>
<td></td>
</tr>
<tr>
<td><code>io.<wbr>helidon.<wbr>metrics.<wbr>api.<wbr>Meter<wbr>Registry</code></td>
<td>90</td>
<td><code>io.<wbr>helidon.<wbr>metrics.<wbr>api</code></td>
<td>The "global" meter registry, can be used to create/get custom metrics that cannot be achieved through interception</td>
<td></td>
</tr>
</tbody>
</table>
