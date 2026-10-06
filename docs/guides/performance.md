<!--@frontmatter
description: "Learn how to tune your Helidon application"
navigation:
  icon: i-lucide-gauge
-->
# Performance Tuning

## Introduction

In this guide you will find basic advice for performance tuning of your Helidon
application. Most of this concerns tuning Helidon WebServer, but you should also
consider configuring/tuning Java heap size as per any Java application.

## WebServer Tuning

Helidon WebServer is in large part self tuning. It uses default values that will
satisfy most use cases, and with the adoption of Java virtual threads there is
no longer a need to tune pools of platform threads. Still, there might be cases
where you wish to change configuration options from their default values.

For details on the following options please see:

- [WebServer Configuration][webserver-config]
- [WebServer Socket Configuration][webserver-socket]

## Summary of Tuning Options

The following `application.yaml` snippet shows some configuration options that
can be used to tune your application. It is intended to show configuration
options in context. Please make sure you understand these options before using
them. See the documentation referenced above.

`application.yaml` snippet:

<!--@mdc ::code-collapse -->
```yaml [application.yaml]
server:
  # These are used to prevent unbounded resource consumption of the server
  idle-connection-period: PT2M  # Check idle connections every 2 minutes
  idle-connection-timeout: PT5M # Close connections that have been idle for 5 minutes
  max-concurrent-requests: NNNN # Maximum number of concurrent requests. 0 or -1 is unlimited.
  # For throughput limiting, use server.concurrency-limit.throughput.amount
  # (0 is unlimited) with a matching duration.
  # Listener-wide connection admission capacity. Connection-oriented bindings
  # acquire a permit before accepting or admitting work and release it when the
  # connection ends or admission/setup fails.
  max-connections: NNNN         # 0 or -1 is unlimited.
  max-in-memory-entity: NNNNNN  # Entities smaller than this are buffered in memory vs streamed (bytes)
  max-payload-size: NNNNNNN     # Reject requests with payload sizes greater than this. -1 is unlimited (bytes)

  # Depends on the workload and kernel version
  backlog: NNNN
  write-buffer-size: NNNNN
  write-queue-length: NN # 0 means direct write

  connection-options:
    # 0 means indefinite (and less clutter on socket impl)
    read-timeout: PT0S
    connect-timeout: PT0S

    # Default (false: Nagle's algorithm enabled) is best for most cases. But for some OS and
    # workloads enabling TCP_NODELAY (disable Nagle's algorithm) can improve performance.
    tcp-no-delay: true|false

    # The default is TCP autotuning which is best for most cases.
    socket-send-buffer-size: NNNNN
    socket-receive-buffer-size: NNNNN

  # Protocol validation.
  # Careful with this! Can be dangerous if you turn these off.
  protocols:
    "http_1_1":
      max-buffered-entity-size: 1 MiB # Decoded bytes materialized by entity readers; default 1 MiB
      validate-request-headers: true|false
      validate-response-headers: true|false
      validate-path: true|false
      log:
        recv-log: true|false
        send-log: true|false
        log-safe-headers: [content-type, content-length]
```
<!--@mdc :: -->

`max-buffered-entity-size` also applies to HTTP/2 protocol configuration and
WebClient responses. It limits decoded input bytes for byte arrays, strings,
forms, JSON trees/binding, and other materializing media readers, including
application-defined readers by default. It is not an exact heap quota: the
resulting objects may use more memory. Direct `InputStream` access and
streaming multipart reads are unaffected; streaming media readers can declare
that they do not materialize the entity. Materializing a multipart part, such
as with `part.as(String.class)`, applies the limit independently to that part,
including nested multipart parts. The combined size of separately read parts
may exceed this limit. On the server, an explicit
`ReadableEntity.buffer()` call also retains decoded bytes under this budget.
The separate server-wide
`max-payload-size` still applies to decoded streaming and materialized requests
and defaults to unlimited (`-1`).

Use `MultiPart` with try-with-resources when processing can stop before the
last part. Closing the multipart entity closes its retained input stream
without iterating the remaining parts itself. The stream's close behavior
still applies: closing a nested multipart entity drains its enclosing part
through the part boundary, which can read additional input, and leaves the
outer multipart entity available for further iteration. Closing an individual
part's stream likewise finishes only that part. Transport-specific request or
response cleanup still applies.

Compatibility note: the previous default of `max-buffered-entity-size` was
`64 KB` and covered only explicit `ReadableEntity.buffer()` calls. The new
`1 MiB` default (1,048,576 bytes) allows
larger explicit buffers but rejects ordinary in-memory
entity conversions above that limit. Buffered compressed requests now store
decoded rather than encoded bytes and enforce both configured limits on the
buffer-only path. If an existing application intentionally
materializes larger bodies, set `max-buffered-entity-size` on each affected
HTTP protocol (or client protocol) to an appropriate value.

[webserver-config]: ../modules/webserver/webserver.md#configuration-options
[webserver-socket]: ../config/io.helidon.common.socket.SocketOptions.md
