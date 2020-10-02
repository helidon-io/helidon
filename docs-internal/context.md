# Helidon Context Support Proposal

Provide a way to propagate Context across thread boundaries.
We already use the WebServer request context to store and retrieve
information (such as in security).

This proposal would allow us to use the context:

- in any thread managed (or wrapped) by Helidon
- to propagate information all the way from server inbound
    request to a client outbound request
- to handle correctly things such as parent SpanContext in tracing
    where we currently have access only to the overall WebServer
    parent span
    
## Problem 

We currently have context available only when we can reach
ServerRequest. There are cases, where we need to propagate
context information and where dependency on WebServer is not desired,
such as in Helidon DB.
Also with introduction of gRPC server, we have another source
of contexts, that should not depend on http modules.
    
## Proposal

This proposal defines:

- new common API
- updates to existing modules

### New API

A new module `helidon-common-context` is to be created.
Public API consists of:

- `io.helidon.common.context.Context` - a copy of existing `ContextualRegistry` in `http`,
    to be used as the main point of registering and retrieving contextual values
- `io.helidon.common.context.Contexts` - a utility class with helpful static methods
    to work with `Context`:
    - `Optional<Context> context()`: to retrieve current context (if there is one)
    - `ExecutorService wrap(ExecutorService)`: to wrap any executor service and create a context-aware executor service
    - `ScheduledExecutorService wrap(ScheduledExecutorService)`: to wrap any scheduled executor service and create a 
            context-aware scheduled executor service
    - `void inContext(Context, Runnable)`: to execute a runnable in a context in current thread
    - `<T> T inContext(Context, Callable<T>)`: to execute a callable in a context in current thread
- `io.helidon.common.context.ExecutorException` - unchecked exception to use in this module 

The new implementation can be source code compatible with previous
 Helidon versions, as long as we keep the `ContextualRegistry` in
 our `http` module and it extends the new `Context` interface.

### Updates to existing modules

Each module that creates a new context (e.g. WebServer and gRPC server when starting processing of a new request)
 should execute subsequent methods in that context using `Contexts.inContext()`.
 
Each module that hands processing over to an executor service should wrap that
executor service using `Contexts.wrap()`.
 
Modules that are interested in using the context should retrieve
the current context using `Contexts.context()`. 

## Examples

New context created (`RequestRouting.next()`):
```java
Contexts.inContext(nextRequest.context(), () -> nextItem.handlerRoute
                            .handler()
                            .accept(nextRequest, nextResponse));
```

Processing through an executor service (`JerseySupport`):
```java
ExecutorService executorService = (service != null) 
                ? service 
                : Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
        
this.service = Contexts.wrap(executorService);

//...
service.submit(() -> {...});
```

Using the context (`HelidonDb`):
```java
Executors.context()
         .ifPresent(context -> services
              .forEach(interceptor -> interceptor.statement(context, statementName, statement, statementFuture)));
```