# Performance Tuning

## Introduction

In this guide you fill find basic advice for performance tuning of your Helidon application. Most of this concerns tuning Helidon WebServer, but you should also consider configuring/tuning Java heap size as per any Java application.

## WebServer Tuning

Helidon WebServer is in large part self tuning. It uses default values that will satisfy most use cases, and with the adoption of Java virtual threads there is no longer a need to tune pools of platform threads. Still, there might be cases where you wish to change configuration options from their default values.

For details on the following options please see:

- [WebServer Configuration](../../se/webserver/webserver.md#_configuration_options)
- [WebServer Socket Configuration](../../config/io_helidon_common_socket_SocketOptions.md)

## Summary of Tuning Options

The following `application.yaml` snippet shows some configuration options that can be used to tune your application. It is intended to show configuration options in context. Please make sure you understand these options before using them. See the documentation referenced above.

*application.yaml snippet*

```yaml
server:
  # These are used to prevent unbounded resource consumption of the server
  idle-connection-period: PT2M  # Check idle connections every 2 minutes
  idle-connection-timeout: PT5M # Close connections that have been idle for 5 minutes
  max-concurrent-requests: NNNN # Maximum number of concurrent requests. -1 is unlimited.
  max-requests-per-second: NNNN # Maximum throughput of requests over a one-second duration. -1 is unlimited.
  max-tcp-connections: NNNN     # Max number of concurrent tcp connections. -1 is unlimited.
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
      validate-request-headers: true|false
      validate-response-headers: true|false
      validate-path: true|false
      recv-log: true|false
      send-log: true|false
```
