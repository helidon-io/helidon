# Telemetry Tracing Testing Support

OpenTelemetry provides a tracing exporter `io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingSpanExporter` which sends JSON data to a JUL `Logger`. The example output below illustrates the layout describing a single span. 
```json
{
  "resource": {
    "attributes": [
      {
        "key": "service.name",
        "value": {
          "stringValue": "otel-config-example"
        }
      },
      {
        "key": "telemetry.sdk.language",
        "value": {
          "stringValue": "java"
        }
      },
      {
        "key": "telemetry.sdk.name",
        "value": {
          "stringValue": "opentelemetry"
        }
      },
      {
        "key": "telemetry.sdk.version",
        "value": {
          "stringValue": "1.29.0"
        }
      },
      {
        "key": "x",
        "value": {
          "stringValue": "x-value"
        }
      },
      {
        "key": "y",
        "value": {
          "intValue": "9"
        }
      }
    ]
  },
  "scopeSpans": [
    {
      "scope": {
        "name": "helidon-service",
        "attributes": []
      },
      "spans": [
        {
          "traceId": "06a87f826019401a48741388b83d7d7d",
          "spanId": "56f8e6dfcf49374e",
          "parentSpanId": "1f8dc272a31e5fa5",
          "name": "content-write",
          "kind": 1,
          "startTimeUnixNano": "1756404539615086000",
          "endTimeUnixNano": "1756404539615246291",
          "attributes": [],
          "events": [],
          "links": [],
          "status": {}
        }
      ]
    }
  ]
}
```

This component gives developers a (relatively) easy way to capture that data so unit tests can check that tracing attributes and spans are created and reported as expected.

## Preparation

1. Add a test-scoped dependency on this component to a project:
   ```xml
   <dependency>
       <groupId>io.helidon.telemetry.testing</groupId>
       <artifactId>helidon-telemetry-testing-tracing
          </artifactId>
       <scope>test</scope>
   </dependency>
   ```

2. Set up the test configuration similar to the following:
   ```yaml
   telemetry:
     service: "otel-config-example"
     signals:
       tracing:
         processors:
           - type: simple
         exporters:
           - type: logging_otlp
   ```

   * Using the `simple` processor avoids batching of results so span information reaches the log quickly.
   * Using the `logging_otlp` exporter sends the span data to the logger so the utility class in this component can extract it.

## Using the utility class
Unit test code should instantiate the `JsonLogConverter` class in a `try-with-resources` block:
```java
import io.helidon.telemetry.testing.tracing.JsonLogConverter;
...
        try (JsonLogConverter converter = JsonLogConverter.create()) {...}
...
```

Within the `try-with-resources` block, the test code should invoke user code or access app endpoints that trigger the creation of one or more tracing spans.

Then the test code can invoke `converter.resourceSpans(expectedCount)` to retrieve a `List<LogResourceScopeSpans>` containing span data.

## Working with the span data
The OTel `io.opentelemetry.exporter.internal.otlp.traces.ResourceSpansMarshaler` class organizes the log data into the JSON structure outlined below. (This document excludes some elements that might appear in the log that the test utility class does not support.)

The interfaces in this component bear names that correspond directly to the OpenTelemetry-provided JSON keys to reinforce the relationship between the Java types and the OTel-provided JSON data.

The `logging-otlp` exporter emits a JSON block--and therefore the utility class creates--a `LogResourceScopeSpans`
instance--for each tracing span. The earlier example illustrates how the JSON looks. For a single span. If your test creates multiple spans, most likely the `logging-otlp` exporter will create multiple top-level `resource` items, each containing a single span. 

The emitted JSON data follows this format:
* `resource` (see `io.opentelemetry.exporter.internal.otlp.ResourceMarshaler`)
   * `attributes` array (see `io.opentelemetry.exporter.internal.otlp.KeyValueMarshaler`)
      
      Various marshalers (some private to {@code KeyValueMarshaler} deal with their respective datatypes:
      * `stringValue`
      * `boolValue`
      * `intValue`
      * `doubleValue`
   * `scopeSpans` (see `io.opentelemetry.exporter.internal.otlp.traces.InstrumentationScopeSpansMarshaler`
      * `scope`
         * `name`
         * `attributes` (see above)
      * `spans` - for each span:
        * `traceId`
        * `spanId`
        * `parentSpanId`
        * `name`
        * `kind`
        * `startTimeUnixNanos`
        * `endTimeUnixNanos`
        * `attributes`

The utility class transforms the JSON at each level into various Java data structures:
* `LogResourceScopeSpans` - top-level entity
  * `LogResource` - mostly just a container for telemetry-level attributes
  * `LogScopeSpans` - aggregation of scope and one or more spans
    * `LogScope` - mostly the scope name plus tracer-level attributes
    * `List<LogSpan>` - list of spans, each of which has a trace ID, span ID, etc.

Developer test code navigates through the data structures anchored at the `List<LogResourceScopeSpans>` returned by the utility class to decide whether the spans were created as expected.