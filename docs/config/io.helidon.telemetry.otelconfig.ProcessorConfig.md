# io.<wbr>helidon.<wbr>telemetry.<wbr>otelconfig.<wbr>Processor<wbr>Config

## Description

Generic configuration for a processor such as a <code>io.<wbr>opentelemetry.<wbr>sdk.<wbr>trace.<wbr>Span<wbr>Processor</code>, linked to an exporter such as a <code>io.<wbr>opentelemetry.<wbr>sdk.<wbr>trace.<wbr>export.<wbr>Span<wbr>Exporter</code> by its name in the configuration

## Configuration options


<table>
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
<a id="type"></a>
<a href="io.helidon.telemetry.otelconfig.ProcessorType.md">
<code>type</code>
</a>
</td>
<td>
<code>Processor<wbr>Type</code>
</td>
<td>Processor type</td>
</tr>
<tr>
<td>
<code>exporters</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>Name(s) of the  exporter(s) this processor should use; specifying no names uses all configured exporters (or if no exporters are configured, the default OpenTelemetry exporter(s))</td>
</tr>
</tbody>
</table>



## Dependent Types

- [io.<wbr>helidon.<wbr>telemetry.<wbr>otelconfig.<wbr>Batch<wbr>Processor<wbr>Config](io.helidon.telemetry.otelconfig.BatchProcessorConfig.md)

---

See the [manifest](manifest.md) for all available types.
