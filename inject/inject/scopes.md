Helidon Inject Scopes
----

Helidon inject supports the following scopes out of the box:

1. A `@Injection.Singleton` service has at maximum a single instance within a service registry.
2. A `@Injection.RequestScope` service has at maximum a single instance per request (the concept of request may differ, consider HTTP request)
3. A `@Injection.Service` service gets a new instance per injection, and the instance is not managed (no `@PreDestroy` support)

# Singleton Scope

Singleton scope is "set in stone" by the implementation, and its behavior cannot be modified

A service in singleton scope has a single instance within the service registry. Instances are created lazily (when needed), and
destroyed when the service registry is shut down.

This means that even if there is a `@Injection.PostConstruct`, or `@Injection.PreDestroy` defined on a singleton service, these
may never be invoked if the service itself is never used.

# Other Scopes

To implement a different scope, a service must exist that implements `io.helidon.inject.ScopeHandler`. The handler provides
a way for the service registry to obtain the current scope (if available) to be able to obtain services from it.

The way a scope is started, and the way it is discovered, is left to the implementation, as this may differ
depending on the implemented scope.

When a scope is started, it may provide "initial bindings" - a set of service descriptor and service instances that for the
core of the scope, and that can be injected to other services within the scope.

Currently a service can be injected into a different scope only through a `Supplier<Contract>` 
(except for Singleton, which always uses the same instance, and a service without a scope, which always creates a new instance), 
and the scope is resolved each time the `get()` method is called.
We do not support proxying of types for scope purposes (yet?).

# Request Scope

Request scope is implemented through APIs and can be replaced by a custom implementation if desired

Our implementation of the Request scope uses a `RequestScopeControl` to start the scope. Once started, it is kept in a thread 
local of the current thread (as Helidon always uses exactly one virtual thread for the duration of a request), and it is closed
when the request finishes (this must be handled by the component starting the context, such as an HTTP Service).

The request scope is designed to be general (can be started by any component that has a concept of a request, such as HTTP 
request, or maybe a messaging message).

# No scope

If a service does not have a scope defined, or is only annotated with `@Injection.Service`, we treat it as "Service" scope.

This means the service:
- does not have its instance lifecycle managed - instance is created, injected, post constructed and then forgotten
- each injection point receives a new instance
- each lookup receives a new instance
- each call to `Supplier.get()` receives a new instance