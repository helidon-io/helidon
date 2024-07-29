Tracing:
```
curl -X GET http://localhost:8080/tracing
"Hello World!"

curl -X GET http://localhost:8080/tracing/span
{"Span":"PropagatedSpan{ImmutableSpanContext{traceId=...}}"}

curl -X GET http://localhost:8080/tracing/custom
{
  "Custom Span": "SdkSpan{traceId=..."
}
```
