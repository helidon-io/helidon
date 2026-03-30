# Service Registry Reference

The following section lists all services and modules that provide them.

Note: this is a work in progress, not listing the full set of contracts yet!

## Service registry contracts

<table>
<colgroup>
<col style="width: 12%" />
<col style="width: 6%" />
<col style="width: 12%" />
<col style="width: 37%" />
<col style="width: 31%" />
</colgroup>
<tbody>
<tr>
<td style="text-align: left;"><p>Contract (package, class)</p></td>
<td style="text-align: left;"><p>Weight</p></td>
<td style="text-align: left;"><p>Module</p></td>
<td style="text-align: left;"><p>Description</p></td>
<td style="text-align: left;"><p>Qualifiers</p></td>
</tr>
<tr>
<td rowspan="2" style="text-align: left;"><p><code>io.helidon.common.config</code> <code>Config</code></p></td>
<td style="text-align: left;"><p><code>80</code></p></td>
<td style="text-align: left;"><p><code>io.helidon.common.config</code></p></td>
<td style="text-align: left;"><p>Empty config instance</p></td>
<td style="text-align: left;"><p>N/A</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>90</code></p></td>
<td style="text-align: left;"><p><code>io.helidon.config</code></p></td>
<td style="text-align: left;"><p>Configuration either from meta configuration (config profiles), or from service registry</p></td>
<td style="text-align: left;"><p>N/A</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>io.helidon.config</code> <code>Config</code></p></td>
<td style="text-align: left;"><p><code>90</code></p></td>
<td style="text-align: left;"><p><code>io.helidon.config</code></p></td>
<td style="text-align: left;"><p>Configuration either from meta configuration (config profiles), or from service registry (same instance that implements <code>io.helidon.common.config.Config</code>)</p></td>
<td style="text-align: left;"><p>N/A</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>io.helidon.scheduling</code> <code>TaskManager</code></p></td>
<td style="text-align: left;"><p><code>90</code></p></td>
<td style="text-align: left;"><p><code>io.helidon.scheduling</code></p></td>
<td style="text-align: left;"><p>Management of scheduled tasks</p></td>
<td style="text-align: left;"><p>N/A</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>java.time</code> <code>Clock</code></p></td>
<td style="text-align: left;"><p><code>90</code></p></td>
<td style="text-align: left;"><p><code>io.helidon.validation</code></p></td>
<td style="text-align: left;"><p>Clock used to check calendar related constraints, defaults to current time-zone</p></td>
<td style="text-align: left;"><p>N/A</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>io.helidon.validation</code> <code>TypeValidation</code></p></td>
<td style="text-align: left;"><p><code>90</code></p></td>
<td style="text-align: left;"><p><code>io.helidon.validation</code></p></td>
<td style="text-align: left;"><p>Methods to validate type annotated with <code>@Validation.Validated</code></p></td>
<td style="text-align: left;"><p>N/A</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>io.helidon.validation.spi</code> <code>ConstraintValidatorProvider</code></p></td>
<td style="text-align: left;"><p><code>70</code></p></td>
<td style="text-align: left;"><p><code>io.helidon.validation</code></p></td>
<td style="text-align: left;"><p>Constraint validator providers for each built-in constraint</p></td>
<td style="text-align: left;"><p>Named by the constraint annotation type (for each built-in constraint)</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>io.helidon.common.mapper</code> <code>Mappers</code></p></td>
<td style="text-align: left;"><p><code>100</code></p></td>
<td style="text-align: left;"><p><code>io.helidon.common.mapper</code></p></td>
<td style="text-align: left;"><p>Access to mappers, to map (convert) types</p></td>
<td style="text-align: left;"><p>N/A</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>io.helidon.common.mapper</code> <code>MapperProvider</code></p></td>
<td style="text-align: left;"><p><code>0.1</code></p></td>
<td style="text-align: left;"><p><code>io.helidon.common.mapper</code></p></td>
<td style="text-align: left;"><p>A provider of mappers</p></td>
<td style="text-align: left;"><p>N/A</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>io.helidon.common.mapper</code> <code>DefaultResolver</code></p></td>
<td style="text-align: left;"><p><code>100</code></p></td>
<td style="text-align: left;"><p><code>io.helidon.common.mapper</code></p></td>
<td style="text-align: left;"><p>Resolver of defaults annotation to a list of expected types</p></td>
<td style="text-align: left;"><p>N/A</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>*</code></p></td>
<td style="text-align: left;"><p>N/A</p></td>
<td style="text-align: left;"><p><code>io.helidon.config</code></p></td>
<td style="text-align: left;"><p>Injection point of a configured object</p></td>
<td style="text-align: left;"><p><code>@Configuration.Value</code></p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>io.helidon.config</code> <code>MetaConfig</code></p></td>
<td style="text-align: left;"><p><code>100</code></p></td>
<td style="text-align: left;"><p><code>io.helidon.config</code></p></td>
<td style="text-align: left;"><p>Config "meta-configuration" - the whole content of a file, such as <code>meta-config.yaml</code></p></td>
<td style="text-align: left;"><p>N/A</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>io.helidon.config</code> <code>MetaConfig</code></p></td>
<td style="text-align: left;"><p><code>100</code></p></td>
<td style="text-align: left;"><p><code>io.helidon.config</code></p></td>
<td style="text-align: left;"><p>Config source "meta-configuration" - section of the single config source</p></td>
<td style="text-align: left;"><p>Named with a config type (i.e. <code>@Service.Named("file")</code>)</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>io.helidon.webserver.http.spi</code> <code>ErrorHandlerProvider</code></p></td>
<td style="text-align: left;"><p><code>100</code></p></td>
<td style="text-align: left;"><p>N/A</p></td>
<td style="text-align: left;"><p>Error handler provider to add to WebServer</p></td>
<td style="text-align: left;"><p>N/A</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>io.helidon.webserver</code> <code>WebServer</code></p></td>
<td style="text-align: left;"><p><code>100</code></p></td>
<td style="text-align: left;"><p><code>io.helidon.webserver</code></p></td>
<td style="text-align: left;"><p>WebServer instance, only available in Helidon Declarative</p></td>
<td style="text-align: left;"><p>N/A</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>io.helidon.security</code> <code>Security</code></p></td>
<td style="text-align: left;"><p><code>100</code></p></td>
<td style="text-align: left;"><p><code>io.helidon.security</code></p></td>
<td style="text-align: left;"><p>Security</p></td>
<td style="text-align: left;"><p>N/A</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>io.helidon.health</code> <code>HealthCheck</code></p></td>
<td style="text-align: left;"><p>N/A</p></td>
<td style="text-align: left;"><p>N/A</p></td>
<td style="text-align: left;"><p>Health check instances to be added to WebServer health observer</p></td>
<td style="text-align: left;"><p>N/A</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>io.helidon.metrics.api</code> <code>MetricsFactory</code></p></td>
<td style="text-align: left;"><p>90</p></td>
<td style="text-align: left;"><p><code>io.helidon.metrics.api</code></p></td>
<td style="text-align: left;"><p>Factory of meter registries</p></td>
<td style="text-align: left;"><p>N/A</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>io.helidon.metrics.api</code> <code>MeterRegistry</code></p></td>
<td style="text-align: left;"><p>90</p></td>
<td style="text-align: left;"><p><code>io.helidon.metrics.api</code></p></td>
<td style="text-align: left;"><p>The "global" meter registry, can be used to create/get custom metrics that cannot be achieved through interception</p></td>
<td style="text-align: left;"><p>N/A</p></td>
</tr>
</tbody>
</table>
