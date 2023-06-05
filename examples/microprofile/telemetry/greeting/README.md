# Helidon MicroProfile Telemetry Example

This example demonstrates how to use MP Telemetry Tracing.

## Build and run

With JDK17+
```bash
mvn package
java -jar target/helidon-examples-microprofile-telemetry-greeting.jar
```

Run Jaeger tracer

```bash
docker run -d --name jaeger \
  -e COLLECTOR_ZIPKIN_HOST_PORT=:9411 \
  -e COLLECTOR_OTLP_ENABLED=true \
  -p 6831:6831/udp \
  -p 6832:6832/udp \
  -p 5778:5778 \
  -p 16686:16686 \
  -p 4317:4317 \
  -p 4318:4318 \
  -p 14250:14250 \
  -p 14268:14268 \
  -p 14269:14269 \
  -p 9411:9411 \
  jaegertracing/all-in-one:1.41
```

Run the Secondary service. Go to `secondary` project and run:

With JDK17+
```bash
mvn package
java -jar target/helidon-examples-microprofile-telemetry-secondary.jar
```

## Exercise the application

```
curl -X GET http://localhost:8080/greet
"Hello World!"

curl -X GET http://localhost:8080/greet/span
{"Span":"PropagatedSpan{ImmutableSpanContext{traceId=00000000000000000000000000000000, spanId=0000000000000000, traceFlags=00, traceState=ArrayBasedTraceState{entries=[]}, remote=false, valid=false}}"}

curl -X GET http://localhost:8080/greet/custom
{
  "Custom Span": "SdkSpan{traceId=bea7da56d1fe82400af8ec0a8adb370d, spanId=57647ead5dc32ae7, parentSpanContext=ImmutableSpanContext{traceId=bea7da56d1fe82400af8ec0a8adb370d, spanId=0ca670f1e3330ea5, traceFlags=01, traceState=ArrayBasedTraceState{entries=[]}, remote=false, valid=true}, name=custom, kind=INTERNAL, attributes=AttributesMap{data={attribute=value}, capacity=128, totalAddedValues=1}, status=ImmutableStatusData{statusCode=UNSET, description=}, totalRecordedEvents=0, totalRecordedLinks=0, startEpochNanos=1683724682576003542, endEpochNanos=1683724682576006000}"
}

curl -X GET http://localhost:8080/greet/outbound   
Secondary    

```

Open a browser and enter the Jaeger URL: http://localhost:16686. Once the site is loaded, open the top-down menu and select "greeting-service" , then click Search. The tracing information should become available.