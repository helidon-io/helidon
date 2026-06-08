# Service Registry Reference

The following section lists all services and modules that provide them.

> [!NOTE]
>  this is a work in progress, not listing the full set of contracts yet!

## Service registry contracts

<table>
<thead>
<tr>
<th><p>Contract (package, class)</p></th>
<th><p>Weight</p></th>
<th><p>Module</p></th>
<th><p>Description</p></th>
<th><p>Qualifiers</p></th>
</tr>
</thead>
<tbody>
<tr>
<td rowspan="2"><p><code>io.helidon.common.config</code> <code>Config</code></p></td>
<td><p><code>80</code></p></td>
<td><p><code>io.helidon.common.config</code></p></td>
<td><p>Empty config instance</p></td>
<td><p>N/A</p></td>
</tr>
<tr>
<td><p><code>90</code></p></td>
<td><p><code>io.helidon.config</code></p></td>
<td><p>Configuration either from meta configuration (config profiles), or from service registry</p></td>
<td><p>N/A</p></td>
</tr>
<tr>
<td><p><code>io.helidon.config</code> <code>Config</code></p></td>
<td><p><code>90</code></p></td>
<td><p><code>io.helidon.config</code></p></td>
<td><p>Configuration either from meta configuration (config profiles), or from service registry (same instance that implements <code>io.helidon.common.config.Config</code>)</p></td>
<td><p>N/A</p></td>
</tr>
<tr>
<td><p><code>io.helidon.scheduling</code> <code>TaskManager</code></p></td>
<td><p><code>90</code></p></td>
<td><p><code>io.helidon.scheduling</code></p></td>
<td><p>Management of scheduled tasks</p></td>
<td><p>N/A</p></td>
</tr>
<tr>
<td><p><code>java.time</code> <code>Clock</code></p></td>
<td><p><code>90</code></p></td>
<td><p><code>io.helidon.validation</code></p></td>
<td><p>Clock used to check calendar related constraints, defaults to current time-zone</p></td>
<td><p>N/A</p></td>
</tr>
<tr>
<td><p><code>io.helidon.validation</code> <code>TypeValidation</code></p></td>
<td><p><code>90</code></p></td>
<td><p><code>io.helidon.validation</code></p></td>
<td><p>Methods to validate type annotated with <code>@Validation.Validated</code></p></td>
<td><p>N/A</p></td>
</tr>
<tr>
<td><p><code>io.helidon.validation.spi</code> <code>ConstraintValidatorProvider</code></p></td>
<td><p><code>70</code></p></td>
<td><p><code>io.helidon.validation</code></p></td>
<td><p>Constraint validator providers for each built-in constraint</p></td>
<td><p>Named by the constraint annotation type (for each built-in constraint)</p></td>
</tr>
<tr>
<td><p><code>io.helidon.common.mapper</code> <code>Mappers</code></p></td>
<td><p><code>100</code></p></td>
<td><p><code>io.helidon.common.mapper</code></p></td>
<td><p>Access to mappers, to map (convert) types</p></td>
<td><p>N/A</p></td>
</tr>
<tr>
<td><p><code>io.helidon.common.mapper</code> <code>MapperProvider</code></p></td>
<td><p><code>0.1</code></p></td>
<td><p><code>io.helidon.common.mapper</code></p></td>
<td><p>A provider of mappers</p></td>
<td><p>N/A</p></td>
</tr>
<tr>
<td><p><code>io.helidon.common.mapper</code> <code>DefaultResolver</code></p></td>
<td><p><code>100</code></p></td>
<td><p><code>io.helidon.common.mapper</code></p></td>
<td><p>Resolver of defaults annotation to a list of expected types</p></td>
<td><p>N/A</p></td>
</tr>
<tr>
<td><p><code>*</code></p></td>
<td><p>N/A</p></td>
<td><p><code>io.helidon.config</code></p></td>
<td><p>Injection point of a configured object</p></td>
<td><p><code>@Configuration.Value</code></p></td>
</tr>
<tr>
<td><p><code>io.helidon.config</code> <code>MetaConfig</code></p></td>
<td><p><code>100</code></p></td>
<td><p><code>io.helidon.config</code></p></td>
<td><p>Config "meta-configuration" - the whole content of a file, such as <code>meta-config.yaml</code></p></td>
<td><p>N/A</p></td>
</tr>
<tr>
<td><p><code>io.helidon.config</code> <code>MetaConfig</code></p></td>
<td><p><code>100</code></p></td>
<td><p><code>io.helidon.config</code></p></td>
<td><p>Config source "meta-configuration" - section of the single config source</p></td>
<td><p>Named with a config type (i.e. <code>@Service.Named("file")</code>)</p></td>
</tr>
<tr>
<td><p><code>io.helidon.webserver.http.spi</code> <code>ErrorHandlerProvider</code></p></td>
<td><p><code>100</code></p></td>
<td><p>N/A</p></td>
<td><p>Error handler provider to add to WebServer</p></td>
<td><p>N/A</p></td>
</tr>
<tr>
<td><p><code>io.helidon.webserver</code> <code>WebServer</code></p></td>
<td><p><code>100</code></p></td>
<td><p><code>io.helidon.webserver</code></p></td>
<td><p>WebServer instance, only available in Helidon Declarative</p></td>
<td><p>N/A</p></td>
</tr>
<tr>
<td><p><code>io.helidon.security</code> <code>Security</code></p></td>
<td><p><code>100</code></p></td>
<td><p><code>io.helidon.security</code></p></td>
<td><p>Security</p></td>
<td><p>N/A</p></td>
</tr>
<tr>
<td><p><code>io.helidon.health</code> <code>HealthCheck</code></p></td>
<td><p>N/A</p></td>
<td><p>N/A</p></td>
<td><p>Health check instances to be added to WebServer health observer</p></td>
<td><p>N/A</p></td>
</tr>
<tr>
<td><p><code>io.helidon.metrics.api</code> <code>MetricsFactory</code></p></td>
<td><p>90</p></td>
<td><p><code>io.helidon.metrics.api</code></p></td>
<td><p>Factory of meter registries</p></td>
<td><p>N/A</p></td>
</tr>
<tr>
<td><p><code>io.helidon.metrics.api</code> <code>MeterRegistry</code></p></td>
<td><p>90</p></td>
<td><p><code>io.helidon.metrics.api</code></p></td>
<td><p>The "global" meter registry, can be used to create/get custom metrics that cannot be achieved through interception</p></td>
<td><p>N/A</p></td>
</tr>
</tbody>
</table>
