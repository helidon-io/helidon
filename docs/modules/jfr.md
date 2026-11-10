<!--@frontmatter
description: "Diagnose Helidon applications with JDK Flight Recorder"
navigation:
  icon: i-lucide-activity
-->
# JDK Flight Recorder

Helidon WebServer adds HTTP request context to JDK Flight Recorder (JFR).
When tracing is enabled, a separate event adds the existing trace ID. A
recording can combine this context with JDK CPU, allocation, locking, and I/O
events.

The events are included in the WebServer and tracing-observer modules. No extra
Helidon dependency or configuration switch is needed. Both modules require
`jdk.jfr`, including when recording is disabled. Include that module in custom
runtimes; a modular `jlink` application resolves it through Helidon's module
dependencies.

## Recording and Settings

Helidon does not start recordings. Start one using the JDK tooling or JDK
Mission Control, for example:

```shell
java -XX:StartFlightRecording=filename=helidon.jfr,settings=profile -jar application.jar
```

Or attach to a running JVM:

```shell
jcmd <pid> JFR.start name=helidon settings=profile filename=helidon.jfr
jcmd <pid> JFR.dump name=helidon filename=helidon.jfr
```

Inspect Helidon events using Mission Control or the command line:

```shell
jfr print --events 'io.helidon.*' helidon.jfr
```

Enablement, duration thresholds, stack traces, and throttling are controlled by
JFR settings. For example, extend the JDK profile configuration to include
stack traces for request events:

```shell
jfr configure --input profile.jfc --output diagnostics.jfc \
  '+io.helidon.HttpServerRequest#stackTrace=true'
java -XX:StartFlightRecording=filename=helidon.jfr,settings=diagnostics.jfc -jar application.jar
```

## Event Catalogue

The defaults below apply unless a recording overrides them. Each event type has
a throttle of 100 events per second, with no minimum duration, so fast requests
remain eligible. Throttling and duration thresholds control which completed
events are recorded; they do not remove all enabled instrumentation cost.

| Event | Recorded operation | Default | Stack trace |
| --- | --- | --- | --- |
| `io.helidon.HttpServerRequest` | HTTP routing and filters on the request thread | Enabled, 0 ms, 100/s | No |
| `io.helidon.RequestTraceContext` | Existing tracing context during downstream request processing | Enabled, 0 ms, 100/s | No |

For a diagnostic recording that retains every eligible request and trace-context
event, disable throttling for both types:

```shell
jfr configure --input default.jfc --output request-context.jfc \
  '+io.helidon.HttpServerRequest#enabled=true' \
  '+io.helidon.HttpServerRequest#threshold=0 ms' \
  '+io.helidon.HttpServerRequest#throttle=off' \
  '+io.helidon.RequestTraceContext#enabled=true' \
  '+io.helidon.RequestTraceContext#threshold=0 ms' \
  '+io.helidon.RequestTraceContext#throttle=off'
java -XX:StartFlightRecording=filename=helidon.jfr,settings=request-context.jfc -jar application.jar
```

Full capture increases recording volume with request traffic. Concurrent
recordings use the most permissive settings for each event type; a recording
that disables throttling can therefore increase volume in other recordings too.

Event names, field names and types, and their meanings form a diagnostic
contract. Human-readable labels and categories can change independently.

### HTTP Request Context

`HttpServerRequest` measures application processing through HTTP routing and
filters, including synchronous response handling and protocol-upgrade
handshakes. HTTP/1 and HTTP/2 use the same event definition. A long-lived
upgraded connection is outside the handshake event's duration.

The interval excludes header parsing, admission rejection before routing,
post-routing draining of an unread request body, and delivery to the remote
client. It ends when synchronous routing and filtering return or throw.
`statusCode` records the HTTP status at routing completion, or `0` when routing
fails before a response is sent. An already-sent response or upgrade handshake
retains its known status. `outcome` identifies the HTTP status class or an
exception escaping routing. Handled exceptions can therefore appear as HTTP
error outcomes without an escaping exception.

The following fields provide context for overlapping JFR events on that same
thread:

| Field | Meaning |
| --- | --- |
| `serverSocketId` | Literal listener identifier used by Helidon logs |
| `socketId` | Literal accepted-connection identifier used by Helidon logs |
| `requestId` | Per-connection HTTP/1 request counter or HTTP/2 stream ID |
| `requestName` | Known HTTP method and the last selected safe route template, or `<unknown>` |

`method` and `routeTemplate` are also available as event details. Nonstandard
methods are recorded as `<other>`, which cannot be an HTTP method token.
Route templates are limited to 256 characters and come from the
`PathMatcher.routeTemplate()` contract. Built-in matchers provide their
configured templates. Custom matchers omit templates by default and can opt in
by returning a configured template independent of request data. Unmatched
requests, matchers without a template, and oversized templates produce an
absent template. Nested routes and reroutes retain the same restriction. No raw
path or query string is substituted for a missing template.

Socket and request identifiers are scoped to a recording's process and server
context. They are not globally unique distributed trace identifiers; combine
the listener, connection, and request fields when matching log entries.

The contextual name describes the final selected route over the recorded
interval. It does not imply that every earlier filter or reroute executed
under that route.

`RequestTraceContext` supplements this information when the tracing observer
already has a trace ID. Its contextual `traceId` is the existing literal ID;
`origin` distinguishes a created server span (`serverSpan`) from an existing
inbound context (`inboundContext`) when creation of the server span is disabled.
Absent, blank, no-op, oversized, or OpenTelemetry's invalid all-zero IDs are
omitted. Valid unsampled trace IDs remain eligible. Core request correlation
works without tracing, and JFR does not enable tracing or generate trace IDs.

Context uses JFR's `@Contextual` same-thread interval correlation. It does not
propagate to child threads or executor tasks, even when Helidon request context
is inherited. Default throttling retains only a sample when traffic exceeds
the configured rate, so context coverage is partial. JFR samples the two event
types independently: recording a request event does not guarantee recording
its trace-context event, or vice versa. Disabling, threshold-filtering, or
sampling an event removes its context for the affected interval.

Use JDK 28 build 19 or newer when correlating request context with
`jdk.ExecutionSample` and `jdk.NativeMethodSample`. The recorded request must
overlap the sample on the same request thread.

## Recorded Data

Helidon records bounded diagnostic metadata: HTTP methods, configured route
templates, outcomes, status codes, durations, and the correlation identifiers
above.

Helidon events exclude request payloads, headers, cookies, credentials, user
identities, token claims, certificate or key material, remote addresses,
SQL and statement parameters, configuration keys and values, source locations,
and exception messages. JDK and application events in the same recording have
their own data contracts.
