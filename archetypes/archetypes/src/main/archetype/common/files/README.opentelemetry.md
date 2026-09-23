## Tracing

### Set up OpenTelemetry (OTLP)

This project is configured to export traces using OpenTelemetry OTLP.

You can run a local Jaeger 2 all-in-one instance with OTLP enabled:

```
docker run -d --name jaeger \
   -p 16686:16686 \
   -p 4317:4317 \
   -p 4318:4318 \
   cr.jaegertracing.io/jaegertracing/jaeger:2.21.0
```

### View Tracing

Jaeger provides a web-based UI at http://localhost:16686 where you can inspect traces and spans.
