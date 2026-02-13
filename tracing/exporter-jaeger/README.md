# Helidon's Jaeger Tracing Exporter

Both Jaeger and OpenTelemetry have eliminated their support for _sending_ tracing span data from monitored servers using the Jaeger tracing protocol. That said, many backend products (including Jaeger's) continue to accept input in this way.

The Helidon Jaeger tracing exporter offers a _temporary_ bridge so applications can continue to send data to backends using the Jaeger protocol while their developers work on migrating to another exporter.

Helidon SE applications adopting Helidon 4.4.0 can continue--in the _short term_--to depend on `io.helidon.tracing:helidon-tracing-providers-jaeger` (also deprecated). That provider now depends on this component to send data using the Jaeger protocol. 

Helidon MP applications adopting Helidon 4.4.0 must remove any dependency on OpenTelemetry's `opentelemetry-exporter-jaeger` component; it no longer exists. Replace those dependencies with a dependency on this component, `io.helidon.tracing:helidon-tracing-exporter-jaeger`.

# Deprecation
Note that both this Jaeger exporter and Helidon's Jaeger tracing provider are deprecated and are likely to be removed in a future major Helidon release.