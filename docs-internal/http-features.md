Features
----

# Weights

| Feature                | Weight |
|------------------------|--------|
| Context                | 1100   |
| Access Log             | 1000   |
| Tracing                | 900    |
| CORS                   | 850    |
| Security               | 800    |
| Routing (all handlers) | 100    |
| OpenAPI                | 90     |
| Observe                | 80     |

Reasoning:

- context is needed for security (and for access log when security is used)
- access log needs to log every request (even ones that fail security)
- tracing should trace as much as possible that is happening in Helidon, and may register stuff to context
- CORS should refuse access to our resource, we should not use security, if we refuse the request due to CORS
- security evaluates authentication and authorization and may forbid access to other routes
- routing - all registered filters and handlers are added with this weight (to allow features that pick up requests that were not
  processed at all)
- openapi feature - after business routes
- Observability feature - after business routes