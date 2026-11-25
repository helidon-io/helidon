<!--@frontmatter
description: "Logging to a Caller-Supplied OpenTelemetry Instance"
-->
# OpenTelemetry Logging Adapters

Helidon provides optional adapters that capture records from a native logging
backend and forward them to an `OpenTelemetry` instance supplied by the caller.
The adapters work independently of SDK construction, configuration, and ownership.
They neither select a global SDK nor configure exporters or close the SDK.

Add one matching adapter using group ID `io.helidon.telemetry`:

| Backend | Artifact ID |
|---------|-------------|
| Java Util Logging | `helidon-telemetry-logging-jul` |
| Log4j 2 | `helidon-telemetry-logging-log4j2` |
| Logback through SLF4J | `helidon-telemetry-logging-logback` |

The adapters depend on `helidon-telemetry-logging`, which contains the contracts
and discovery API. The ordinary Helidon logging integration artifacts do not
acquire OpenTelemetry dependencies. The Logback adapter selects a specific SLF4J
backend; the ordinary SLF4J integration remains backend-independent.

## Supplying Telemetry and Publication Admission

Extend `io.helidon.telemetry.logging.LogBridgeContext`, passing the caller-selected
`OpenTelemetry` instance to its constructor. That instance remains fixed for the
registration lifetime; SDK replacement requires a new context and bridge.

Implement `runIfActive(Runnable)` as a synchronous admission boundary. Retain the
SDK and other required resources until the operation returns, or decline work
when the application is stopping. Release reservations in a `finally` block.
The final `telemetry()` method returns the fixed instance supplied at construction.

Native adapters call the context's final `publish(String, Runnable)` method. It
suppresses recursive publication and OpenTelemetry diagnostic loggers before
calling `runIfActive`. Formatting and emission happen
inside that boundary. `publishing()` reports whether this context is publishing
on the current thread; shutdown that drains active operations must reject calls
from its own publication to avoid waiting for itself.

The adapters preserve timestamps, severity, exceptions, and trace correlation.
Log4j 2 selects a full-context injector through `log4j2.component.properties`
at JVM bootstrap. This preserves producer context, including trace state, with
asynchronous loggers. An application-provided context-data injector must delegate
to `LogBridgeContextDataInjector`; changing injectors after Log4j initializes is
not supported. Optional MDC and source-location attributes are not
enabled automatically. `System.Logger` records are captured when routed through
the matching Helidon logging integration.

## Export-Time Suppression

Add `io.helidon.telemetry:helidon-telemetry-logging-sdk` when owning an SDK. This
optional SDK integration is separate from the API-only bridge contracts. It
registers an OpenTelemetry autoconfiguration customizer that decorates log
exporters late, without constructing or owning the SDK. For programmatic SDK
construction, use `LogRecordExporters.suppressLogging(exporter)` before adding an
exporter to its processor. Install suppression as the outermost decorator;
repeated decoration is harmless.

Export-time suppression covers batch-worker callbacks and is carried through
native async logging queues. Custom exporters launching their own asynchronous
work must propagate `Context.current()` to those tasks. The decorator forwards
export, flush, shutdown, and close to the underlying exporter and preserves their
results and failures. A borrowed SDK must have this protection installed by its
owner; a bridge never reconfigures an existing SDK or agent.

## Discovery and Lifecycle

`io.helidon.telemetry.logging.LogBridges.findProvider(ClassLoader)` discovers an
optional `LogBridgeProvider` through the supplied class loader. It validates that
there is at most one adapter and that it matches the logging provider already
selected by `LogConfig`. Discovery does not create a bridge or attach hooks.
Without an adapter, the caller can continue using OpenTelemetry APIs normally.

Pass the context to `LogBridgeProvider.create(context)`. Retain the returned,
unstarted `LogBridge` before calling `start()`, so partially attached hooks can
be cleaned up even if startup fails. Preserve startup failures and suppress any
cleanup failures behind the original exception; retain the bridge for retry when
cleanup fails.

During shutdown:

1. Stop admitting new publications through the caller's context.
2. Call `stop()` to detach the adapter's hooks, keeping resources needed by
   admitted callbacks available.
3. Drain admitted operations in the caller's lifecycle.
4. Call `close()` to release the adapter's resources.
5. Close a caller-owned SDK after all its users have finished. Leave borrowed
   SDKs and application-owned logging contexts open.

Start is permitted once, and `active()` reports whether the registration is active.
A stopped Logback context terminates its registration: it becomes inactive and
will not attach again after backend restart. Close that registration and create
a fresh bridge after restarting the backend. Ordinary configuration reset
preserves an active registration. A failed start or stop keeps the registration stopping,
with reattachment disabled. Stop and close are idempotent after success and
retryable after failure. Backend reconfiguration or reset reattaches an active
adapter's hook; shutdown removes only integration-owned hooks and listeners.
Native logging queues remain application-owned: draining admitted callbacks does
not imply that every record queued in an asynchronous backend has been delivered.

Capture follows the backend's root logging route and respects its filtering and
logger propagation. Loggers that do not propagate to the root require their own
integration. Callers using a shared process-wide logging context must coordinate
registrations for different SDKs to avoid duplicate capture or cross-application
routing. Likewise, coordinate with manually installed OpenTelemetry appenders and
Java agents; adapter discovery does not decide whether an agent already owns
instrumentation.

## Runnable Example

The examples repository's `examples/telemetry/otel/otel-logging` example shows
caller-owned SDK setup, a fixed bridge context, partial-startup cleanup, admission
closure, draining, interruption, and retryable shutdown. It exports correlated
logs and spans to the console and needs no collector. The application owner
retains its bridge before start and remains available for cleanup retries.
