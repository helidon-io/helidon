# Helidon Zipkin HTTP Exporter

This deprecated compatibility bridge replaces OpenTelemetry's Zipkin exporter, which stopped
being published in OpenTelemetry 1.65.0. It sends Zipkin HTTP/HTTPS requests using Helidon
WebClient's HTTP/1.1 implementation.

## Migration

Remove `io.opentelemetry:opentelemetry-exporter-zipkin` and add:

```xml
<dependency>
    <groupId>io.helidon.tracing</groupId>
    <artifactId>helidon-tracing-exporter-zipkin</artifactId>
</dependency>
```

Do not include both exporters: both register the OpenTelemetry provider name `zipkin`.
Keep `otel.traces.exporter=zipkin`, `otel.exporter.zipkin.endpoint`, and
`otel.exporter.zipkin.timeout`, or their corresponding `OTEL_*` environment variables.
OpenTelemetry still controls property/environment precedence and duration parsing.

Helidon's existing `ZipkinExporterConfig` API and telemetry configuration also remain supported:

```yaml
telemetry:
  service: inventory
  signals:
    tracing:
      exporters:
        - type: zipkin
          endpoint: https://collector.example.com/api/v2/spans
      processors:
        - type: simple
```

An exporter declaration must be connected to a processor to export spans. A processor without
an explicit exporter list uses all configured exporters. Use a batch processor in production
when asynchronous batching is desired.

Direct use of OpenTelemetry's old exporter classes requires changing Java imports and builder
calls. The replacement uses Helidon Blueprint conventions:

```java
var exporter = ZipkinSpanExporter.builder()
        .endpoint(URI.create("https://collector.example.com/api/v2/spans"))
        .build();
```

The separate Brave/OpenTracing `helidon-tracing-providers-zipkin` component retains its
configuration and public builder signatures. Its built-in URLConnection sender is aligned with
Zipkin Reporter 3.5.3. Applications supplying custom Reporter 2.x senders may need to recompile
and adapt to Reporter 3.x interfaces (notably reporter encoding, call, and encoder types).
The former OpenTelemetry 1.62.0/1.64.0 exporter already depended on Reporter 3.5.3, so this
additional caveat concerns applications using the older Brave provider dependency stack.

## Configuration reference

| Property | Default | Meaning |
| --- | --- | --- |
| `endpoint` | `http://localhost:9411/api/v2/spans` | HTTP or HTTPS collector URI |
| `timeout` | `PT10S` | Socket read timeout, normalized to milliseconds |
| `encoder` | `JSON_V2` | Span encoding and default sender's HTTP content type/framing |
| `compression` | `gzip` | `gzip` or `none` |
| `internal-telemetry-version` | `LEGACY` | `LEGACY` or `LATEST` self-observability metrics |

The OpenTelemetry provider exposes endpoint and timeout, as did the former provider. The
Helidon configuration API additionally exposes encoder and compression. Custom sender,
local-IP supplier, and meter provider are available programmatically. Both legacy Zipkin
encoders/senders and the current reporter interfaces are supported. A custom sender determines
its own transport settings; endpoint, timeout, and compression do not configure it.

The local-IP supplier may return null to omit the local endpoint IP, following Zipkin's inherited
contract. Otherwise Helidon public APIs do not accept null. Exporter metrics retain upstream
instrument names, scopes, attributes, and units, including lazy use of the global meter provider.

## Transport behavior and differences

The default sender connects with a ten-second connect timeout and uses the configured socket
read timeout. As in the old exporter, zero and positive sub-millisecond timeout values become
`Integer.MAX_VALUE` milliseconds, and larger values are capped at that value. Negative durations
are rejected. The sender compresses batches using gzip by default and includes `b3: 0` to
suppress tracing in compatible proxies. Automatic instrumentation is also suppressed locally;
WebClient tracing services are not installed on the exporter client.

The default sender's framing and content type follow the selected encoder. Unlike the old
exporter's JSON-only default sender, selecting protobuf encoding also configures the default
sender for protobuf; a matching custom sender is no longer necessary for that combination.

The sender accepts all 2xx statuses. Redirects follow the former sender's POST-to-GET behavior
for 300/301/302/303, without replaying POST bodies on 307/308. GET redirects retain GET, with a
maximum of twenty redirects. Responses are drained and closed; response payloads and transport
exception messages are not logged. The exporter adds no retry loop. WebClient connection
recovery differs from OkHttp's, and WebClient does not expose OkHttp's independent socket-write
timeout. These are transport behavior differences, rather than new configuration requirements.
TLS and system proxy settings are provided by Helidon's WebClient/JDK infrastructure.

Export is synchronous; batching belongs to OpenTelemetry's span processor. Flush has no
exporter buffer to drain. Shutdown stops admission, waits for admitted exports to exit, then
releases the owned client or custom sender. Interrupted or failed shutdown returns failure,
keeps admission closed, and permits retry. Unlike the old exporter, a failed sender close is
not reported as a successful shutdown.

## Provenance and maintenance

Span conversion and event annotation code are adapted from OpenTelemetry Java 1.64.0,
`exporters/zipkin`, under Apache-2.0. The baseline conversion code is identical to the 1.62.0
version previously used by Helidon. Existing conversion limitations, including omitted links
and peer-service requirements for remote endpoints, are retained. Helper classes remain
package-private and do not depend on OpenTelemetry internal exporter code.

Named unused exception bindings are retained where needed for Java 21 source compatibility;
unnamed bindings require a later Java source level. This is an exception to development
guideline 1.5 for this Java 21 branch.

This exporter is a temporary bridge. Migrate to an OTLP exporter for long-term support.
