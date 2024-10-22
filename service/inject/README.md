Inject Service Registry
----

An extension to the core service registry.

All features are implemented in a way that can use no reflection, mostly through code generating required handling classes.

Helidon Inject includes:

- [Dependency Injection](#dependency-injection)
- [Lifecycle Support](#service-lifecycle)
- [Factories and Services](#factories-and-services)
- [Aspect Oriented Programming (interceptors)](#interceptors)
- [Events](events)
- [Programmatic Lookup](#programmatic-lookup)
- [Other](#other)
- [Glossary](#glossary)

# Dependency Injection

The basic building stone for inversion of control, dependency injection provides a mechanism to obtain an instance of a service
at runtime, from the service registry, rather than constructing service instances through a constructor or a factory method.

When using dependency injection, we can separate the concerns of "how to create a service instance" from
"how to use the contract". The consumer of the contract is not burdened with the details of how to obtain a valid instance,
and the provider of the service is not burdened with providing an API to build/setup a service instance.
In some cases such interaction would be quite cumbersome, as we would need to carry a shared instance through constructors to
reach the correct place where we want to create a service instance.

One of the advantages of such an approach is the capability to exchange the service that implements a contract without the need
to modify the consumers of such a contract.

## Injection points

In Helidon, dependency injection can be done in the following ways:

- Through a constructor annotated with `@Injection.Inject` - each parameter is considered an injection point; this is the
  recommended way of injecting dependencies (as it can be unit tested easily, and fields can be declared `private final`)
- Through field(s) annotated with `@Injection.Inject` - each field is considered an injection point; this is not recommended, as
  the fields must be accessible (at least package local), and cannot be declared as `final`

An injection point is satisfied by a service with the highest weight implementing the requested contract.

## Services

Services are:

1. Java classes annotated with one of the `Injection.Scope` annotations, such as
    - `@Injection.Singleton` - up to one instance exists in the service registry
    - `@Injection.PerLookup` - an instance is created each time a lookup is done (injecting into an injection point is considered
      a lookup as well)
    - `@Injection.PerRequest` - up to one instance exists in the service registry per request (what is a request is not defined in
      the injection framework itself, but it matches concepts such as HTTP request/response interaction, or consuming of a
      messaging message)
2. Any class with `@Injection.Inject` annotation that does not have a scope annotation. In such a case, the
   service will be `@Injection.PerLookup`.
3. Any `core` service defined for Helidon Service Registry (using annotation `Service.Provider`), the scope is `PerLookup` if the
   service implements a `Supplier`, and `@Singleton` otherwise; all dependencies are considered injection points

Only services can have Injection points.

## Qualifiers

Any annotation "meta-annotated" with `@Injection.Qualifier` is considered a qualifier.
Qualifier annotations can be used to "qualify" injection points and services.

If an injection points is qualified (it has one or more qualifiers), it will only be satisfied with services that match all
the specified qualifiers.

### Named

One qualifier is provided out-of-the-box - the `@Injection.Named` (and `@Injection.NamedByType` which does the same thing,
only the name is the fully qualified class name of the provided class).

Named instances are used by some feature of Helidon Inject itself.

# Service Lifecycle

The service registry manages lifecycle of services.

To manage lifecycle, you can use the following annotations:

- `@Injection.PostConstruct` - a method annotated with this annotation will be invoked after the instance is constructed and fully
  injected
- `@Injection.PreDestroy` - a method annotated with this annotation will be invoked after the service is no longer used by the
  registry

The behavior depends on the scope of the bean as follows:

- `@Injection.PerLookup` - only "post construct" lifecycle method is invoked, as we do not control the instance after is is
  injected
- Any other scope - the "pre destroy" lifecycle method is invoked when the scope is deactivated (Singletons on registry shutdown
  or JVM shutdown)

# Factories and Services

Let's consider we have a contract named `MyContract`.

The simple case is that we have a class that implements the contract, and that is a service, such as:

```java

@Injection.Singleton
class MyImpl implements MyContract {
}
```

This means the service instance itself is an implementation of the contract, and when this service is used to satisfy an injection
point, we will get an instance of `MyImpl`.

But such an approach is only feasible if the contract is an interface, and we are fine with doing a full implementation.
There may be cases, where this is not sufficient:

- we need to provide an instance created by somebody else
- the provided contract is not an interface
- the provided instance may not be created at all (i.e. it is optional)

This can be done by implementing one of the factory interfaces Helidon Inject supports:

- `java.util.function.Supplier` - a factory that supplies a single instance (can also be `Supplier<Optional<MyContract>>`)
- `io.helidon.service.inject.api.Injection.ServicesFactory` - a factory that creates zero or more contract implementations
- `io.helidon.service.inject.api.Injection.InjectionPointFactory` - a factory that provides zero or more instances for each
  injection point
- `io.helidon.service.inject.api.Injection.QualifiedFactory` - a factory that provides zero or more instances for a specific
  qualifier and contract

The factory interfaces above should provide enough tooling to implement any injection use case.

# Interceptors

Interception provides capability to intercept call to a constructor or a method (even to fields when used as injection points).

Interception is (by default) only enabled for elements annotated with an annotation that is a `Interception.Intercepted`.
Annotation processor configuration allows for creating interception "plumbing" for any annotation, or to disable it altogether.

Interception works "around" the invocation, so it can:

- do something before actual invocation
- modify invocation parameters
- do something after actual invocation
- modify response
- handle exceptions

Annotation type: `io.helidon.service.inject.api.Interception`

Annotations:

| Annotation class   | Description                                                                                                                                                                                                                                                                                                                                    |
|--------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Intercepted`      | Marker for annotations that should trigger interception                                                                                                                                                                                                                                                                                        |
| `Delegate`         | Marks a class as supporting interception delegation. Classes are not good candidates for delegation, as you need to create an instance that delegates to another instance, opening space for side-effects. To use a class, it must have an accessible no-arg constructor, and it should be designed not to have side-effects from construction |
| `ExternalDelegate` | Add this to a service provider that provides a class that requires delegation, if the class is not part of your current project (i.e. you cannot annotate it with `Delegate`                                                                                                                                                                   | 

Interfaces:

| Interface class | Description                                                                                                                                                                                                                                          |
|-----------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Interceptor`   | A service implementing this interface, and named with the annotation type (maybe using `NamedByType`) will be used as interceptor of methods annotated with that annotation. Interceptor must call `proceed` method to handle the interception chain |

# Events

Events allow in-application communication between services, by providing a mechanism to emit an event, and
to create a consumer/consumers of events.

One event can be delivered to (0..n) consumers.

Basic terminology:

- `Event Producer` - a service that calls an emitter (origin of an event)
- `Event Emitter` - a service that emits an event to the event system
- `Event Object` - an arbitrary object that is sent around as an event
- `Event Observer` - a service that receives events, and has a method annotated with
  `io.helidon.service.inject.api.Event.Observer`
- `Qualified Event` - event published by an emitter providing a qualifier (annotation annotated with `Injection.Qualifier`)

## Emitting Events

Event Emitters are code generated by Helidon. To create an Event Producer, simply inject the emitter.
Event producers can be in any scope, the generated event emitter is always in `Injection.Singleton` scope.

A simple singleton service that injects an event emitter for event object of type `MyEventObject`.

```java

@Injection.Singleton
class MyService {
    private final Event.Emitter<MyEventObject> emitter;

    @Injection.Inject
    MyService(Event.Emitter<MyEventObject> emitter) {
        this.emitter = emitter;
    }
}
```

To emit an event, you simply call `emitter.emit(myEventObjectInstance)`.
The method will return once all event observers were notified (unless they are asynchronous - see below).
In case any of the observers throws an exception, an `EventDispatchException` will be thrown with all exceptions caught added as
suppressed (i.e. we will invoke all observers, even after we catch an exception).

Event emitters are code generated for each Event Producer, so we may end up with more than one in the system. As all of them
provide the exact same function, this is not an issue.

_Explanation of the above statement: we cannot code generate classes into packages that do not belong to the current module, so we
always code generate the emitter to the same package as the service that needs the emitter. Even though this may duplicate code,
it is the only safe way we can do during annotation processing (where we do not have access to the classpath of the application)_

## Consuming Events

An event can be consumed by declaring an observer method.
Event consumers can only be in `Injection.Singleton` or `Injection.PerLookup` scopes. The lookup is done exactly once, and all
events are delivered to the same instance for the lifetime of the service registry.

Helidon code generates an `EventObserverRegistration` service, which is used by the event manager to gather all observers for
event handling.

To create an event observer:

- create an observer method, with a single parameter of the event type you want to observe
- annotate the method with `Event.Observer`

Example:

```java

@Event.Observer
void event(MyEventObject eventObject) {
    // do something with the event
}
```

## Asynchronous Events

Events can be emitted asynchronously, and event observer can be asynchronous.
Executor service for asynchronous events can be provided via service registry, as a service that implements contract
`java.util.concurrent.ExecutorService`, and is named `io.helidon.service.inject.api.EventManager`.
If none is provided, the service will use a thread per task executor with Virtual threads, thread names will be prefixed with
`inject-event-manager-`.

### Asynchronous Event Producer

Rules of asynchronous event producing:

1. Method `Event.Emitter.emitAsync(..)` returns a `CompletionStage<MyEventType>`
2. All *synchronous* Event Consumer are submitted to an executor service, and the returned completion stage will provide either
   success (the event object itself), or will provide an exception, which will have `EventDispatchException` as a cause
3. The method returns once all the event observers are submitted to the executor service (there is no guarantee that anything has
   been delivered - we may have delivered 0 to n events (where n is number of synchronous observers))
4. All *asynchronous* Event Observer are invoked outside of the returned completion stage

### Asynchronous Observer

Asynchronous observer methods are invoked from separate threads (through the executor service mentioned above), and their results
are ignored by the Event Emitter; if there is an exception thrown from the observer method, it is logged with `WARNING` log level
into logger named `io.helidon.service.inject.api.EventManager`.

To declare an asynchronous observer use annotation `Event.AsyncObserver` instead of `Event.Observer`.

Example:

```java

@Event.AsyncObserver
void event(MyEventObject eventObject) {
    // handle event
}
```

## Qualified Events

A Qualified Event is only delivered to Event Consumers that use the same qualifier.

### Qualified Event Producer

A qualified event can be produced with two options:

1. The injection point of `Event.Emitter` (the constructor parameter, or field) is annotated with a qualifier annotation
2. The `Event.Emitter.emit(..)` method is called with explicit qualifier(s), note that if combined, the qualifier specified by the
   injection point will always be present!

Example (combination of both):

```java
import io.helidon.service.inject.api.Qualifier;

// class declaration
private static final Qualifier BLUE = Qualifier.create(Blue.class);

        @Injection.Inject
        EventEmitter(@Black Event.Emitter<EventObject> event) {
            // the event producer will implicitly have Black qualifier added
            this.event = event;
        }

        void emit(MyEventObject eventObject) {
            // the event will be emitted with both Blue and Black qualifiers
            this.event.emit(eventObject, BLUE);
        }
```

### Qualified Event Observers

To consume a qualified event, observer method must be annotated with the correct qualifier(s).

Example:

```java

@Injection.Singleton
class EventObserver {
    @Event.Observer
    @Black
    void event(MyEventObject eventObject) {
        // handle event that is qualified with Black (and none other)
    }
}
```

# Programmatic Lookup

As usual with Helidon, what can be done via automation (dependency injection in this case) can also be done programmatically.

The service registry can be used and handled "from outside" - you can create a registry instance, lookup services, call methods on
them.

It can also be used "from inside" - you can inject an `InjectRegistry` into your services. In case this approach is done, we
cannot work around lookup costs as we can when only dependency injection is used.

To create a registry instance:

```java
// create an instance of a registry manager - can be configured and shut down
var registryManager = InjectRegistryManager.create();
// get the associated service registry
var registry = registryManager.registry();
```

Note that all instances are created lazily, so the registry will do "nothing" by default. If a service does something during
construction or post construction, you must lookup an instance from the registry first.

Special registry operations:

- `List<InjectServiceInfo> lookupServices(Lookup lookup)` - get all service descriptors that match the lookup
- `Optional<T> get(ServiceInfo)` - get an instance for the provided service descriptor

The common registry operations are grouped by method name. Acceptable parameters are described below.

Registry methods:

- `T get(...)` - immediately get an instance of a contract from the registry; throws if implementation not available
- `Optional<T> first(...)` - immediately get an instance of a contract from the registry; there may not be an implementation
  available
- `List<T> all(...)` - immediately get all instances of a contract from the registry; result may be empty
- `Supplier<T> supply(...)` - get a supplier of an instance; the service may be instantiated only when `get` is called
- `Supplier<Optional<T>> supplyFirst(...)` - get a supplier of an optional instance
- `Supplier<List<T>> supplyAll(...)` - get a supplier of all instances

Lookup parameter options:

- `Class<?>` - the contract we are looking for
- `TypeName` - the same, but using Helidon abstraction of type names (may have type arguments)
- `Lookup` - a full search criteria for a registry lookup

# Other

## API types quick reference

Annotation type: `io.helidon.service.inject.api.Injection`

Annotations:

| Annotation class | Description                                                                                                               |
|------------------|---------------------------------------------------------------------------------------------------------------------------|
| `Inject`         | Marks element as an injection point; although we prefer constructor injection, field and method injection works as well   |
| `Qualifier`      | Marker for annotations that are qualifiers                                                                                |
| `Named`          | A qualifier that provides a name                                                                                          |
| `NamedByType`    | An equivalent of `Named`, that uses the fully qualified class name of the configured class as name                        |
| `Scope`          | Marker for annotations that are scopes                                                                                    |
| `PerLookup`      | Service instance is created per lookup (either for injection point, or via registry lookup)                               |
| `Singleton`      | Singleton scope - a service registry will create zero or one instances of this service (instantiation is lazy)            |
| `PerRequest`     | Request scope - a service registry will create zero or one instance of this service per request scope instance            |
| `RunLevel`       | A "layer" in which this service should be instantiated; not executed by injection, will be used when starting application |
| `PerInstance`    | Create a service instance for each instance of the configured contract available in registry (usually for named)          |
| `InstanceName`   | Parameter or field that will be injected with the name this service instance is created for (see `PerInstance`)           |
| `Describe`       | Create a descriptor for a type that is not a service itself, but an instance would be provided at scope creation time     | 

Interfaces:

| Interface class         | Description                                                                                                     |
|-------------------------|-----------------------------------------------------------------------------------------------------------------|
| `ServicesFactory`       | A service factory that creates zero or more qualified service instances at runtime                              |
| `InjectionPointFactory` | A service factory that creates values for specific injection points                                             |
| `QualifiedFactory`      | A service factory to resolve qualified injection points of any type (used for example by config value injection |
| `QualifiedInstance`     | Used as a return type of some of the interfaces above, not to be implemented by users                           |
| `ScopeHandler`          | Extension point to support additional scopes                                                                    |

## Injection point options

An injection point may have the following forms (`Contract` stands for a contract interface, or class):

Instance based:

1. `Contract` - injects an instance of the contract with the highest weight from the registry
2. `Optional<Contract>` - same as previous, the contract may not have an implementation available in registry
3. `List<Contract>` - a list of all available instances in the registry

Supplier based (to break cyclic dependency, and to create instances as late as possible):

1. `Supplier<Contract>`
2. `Supplier<Optional<Contract>>`
3. `Supplier<List<Contract>>`

Service instance based (to obtain registry metadata in addition to the instance):

1. `ServiceInstance<Contract>`
2. `Optional<ServiceInstance<Contract>>`
3. `List<ServiceInstance<Contract>>`

# Glossary

| Term            | Description                                                                                                                                                                                                   |
|-----------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Core Service    | A class annotated with `@Service.Provider`                                                                                                                                                                    |
| Contract        | A class extended by a service, or an interface implemented by a service, can be used to lookup instances                                                                                                      |
| Dependency      | A "Core Service" constructor parameter (type must be another service or a "Contract")                                                                                                                         |
| Service         | A class annotated with one of the scope annotations, or a core service                                                                                                                                        |
| Factory         | A "Core Service" or "Service" that implements one of the factory interfaces; Core service is a factory only if it implements a `Supplier                                                                      |
| Injection Point | Field annotated with `@Injection.Inject`, or a constructor parameter of a constructor used for injection (either the only accessible constructor, or the only constructor annotated with `@Injection.Inject`) |

