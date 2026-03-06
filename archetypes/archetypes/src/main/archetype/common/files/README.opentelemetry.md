## Tracing

### Set up OpenTelemetry (OTLP)

This project is configured to export traces using OpenTelemetry OTLP.

You can run a local Jaeger all-in-one instance with OTLP enabled:

```
docker run -d --name jaeger\
   -e COLLECTOR_ZIPKIN_HOST_PORT=:9411\
   -e COLLECTOR_OTLP_ENABLED=true\
   -p 6831:6831/udp\
   -p 6832:6832/udp\
   -p 5778:5778\
   -p 16686:16686\
   -p 4317:4317\
   -p 4318:4318\
   -p 14250:14250\
   -p 14268:14268\
   -p 14269:14269\
   -p 9411:9411\
   jaegertracing/all-in-one:1.43
```

### View Tracing

Jaeger provides a web-based UI at http://localhost:16686 where you can inspect traces and spans.
