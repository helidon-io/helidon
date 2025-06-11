Context Feature
----

The context feature adds the following to Helidon WebServer:

1. [Request Context](#request-context)
2. [Context Propagation](#context-propagation)

Both can be configured through configuration or a builder.

This feature is a separate module, as it adds a performance overhead to processing, and is not required be every application.

# Request Context

The Context Feature adds a filter to WebServer, that runs each (subsequent processing of) request within an `io.helidon.common.context.Context`.

This enables the method `io.helidon.common.context.Contexts.context()`. This method will return the same instance you can obtain from `ServerRequest` even when an instance of the request is not available.

# Context Propagation

*NOTE* The headers are added as is to the context from EVERY incoming request. Be extremely careful when using these values, as they may be fabricated by the user!

The Context Feature allows reading explicitly configured headers and adding them to the request context.

This is implemented in the same filter as the addition of the request context as mentioned above.

The headers must be named, to avoid full materialization of headers from the request.

This feature is only available for String and String array values.

Example configuration:

```yaml
server:
  features:
    context:
      records:
          - header: "X-Custom"
            default-value: "default-value"
          - header: "traceparent"
            classifier: "opentelemetry.tracing.parent"
          - header: "X-Helidon-TID"
            array: true
            default-values: ["first", "second"]
```

For such a configuration, each request will be scanned for the configured headers.
If there is a `traceparent` header present, it will be added to the context as a String with `opentelemetry.tracing.parent` classifier, if it is not present, a value `default-value` will be added with that classifier.
If there is an `X-Custom` header present, it will be added to the context as a String with `X-Custom` classifier.
If there is an `X-Helidon-TID` header present, its values will be added to context as a `String[]` with `X-Helidon-TID` classifier; if not present, the value will be array of `first` and `second`. 