# Injection

## Overview

Injection is the basic building stone for inversion of control. Dependency injection provides a mechanism to get an instance of a service at runtime, from the service registry, rather than constructing service instances through a constructor or a factory method.

> [!NOTE]
> Injection is a preview feature. The APIs shown here are subject to change. These APIs will be finalized in a future release of Helidon.

## Maven Coordinates

To enable Injection, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

``` xml
<dependency>
    <groupId>io.helidon.service</groupId>
    <artifactId>helidon-service-registry</artifactId>
</dependency>
```

## Usage

To start using Helidon Inject, you need to create both:

- A service that will be injected.
- An injection point, where the service instance will be injected.

Let’s begin by explaining some basic terms.

## Basic terms

### Dependency Injection

Injection is a way to automatically provide instances of dependencies without having to create them manually. Instead of a class creating an object itself, something else (like a [service registry](#service-registry)) hands it over when needed. This makes code cleaner, easier to manage, and more flexible.

For example, if a Car needs an Engine, instead of the Car making an Engine itself, it just asks for one, and the system provides it. This is called Dependency Injection (DI).

### Declarative style of programming

In a declarative approach, you use annotations on classes, constructors, and constructor arguments to express your intent.

For example, instead of manually managing dependencies, you declare that a class should be injectable using annotations like [`@Service.Singleton`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.Singleton.html) (More about that later).

See [Helidon Declarative](declarative.md#Overview)

### Service registry

A service registry is a tool that enables declarative programming by supporting inversion of control (IoC).

It manages the lifecycle of services, handles dependency injection, and ensures that the correct instances are provided where needed and without requiring manual instantiation.

### Inversion of control

Instead of manually creating an instance of a certain type, you can delegate its creation to the service registry.

This allows the registry to handle the entire instantiation process and provide the instance when needed, ensuring proper lifecycle management and dependency resolution.

### Contract

A contract is a type that defines what the service registry should provide. It represents an API that will be used.

For simplicity, a contract can be thought of as an interface, but it can also be an abstract class or even a concrete class.

*Contract example*

``` java
interface GreetingContract {

    String greet(String name);

}
```

### Service

This can be either a concrete class, which implements the contract (or is contract itself if it was a concrete class), or it can be a factory/producer (more about [Factories](#factories)), which creates a new instances to be registered into the service registry.

*Service example*

``` java
@Service.Singleton
class MyGreetingService implements GreetingContract {

    @Override
    public String greet(String name) {
        return "Hello %s!".formatted(name);
    }

}
```

### Contract vs. service

Contract and service can be the same thing, but also separate entities. It all depends on the design of your application and which approach you choose.

## How are services defined

Services are defined by:

1.  Java classes annotated with one of the [`@Service.Scope`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.Scope.html) annotations (see [Scopes](#scopes))
2.  Any class with [`@Service.Inject`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.Inject.html) annotation even when it doesn’t have a scope annotation. In such a case, the scope of the service will be set as [`@Service.PerLookup`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.PerLookup.html).

Keep in mind that if you create any service instance directly, it will not get its injection points resolved! This works only when using service registry.

Now, let’s talk about an injection points.

## Injection points

In Helidon, dependency injection can be done into the injection point in the following ways:

1.  Through a constructor annotated with [`@Service.Inject`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.Inject.html) - each parameter is considered an injection point; this is the recommended way of injecting dependencies (as it can be unit tested easily, and fields can be declared private final)
2.  Through field(s) annotated with [`@Service.Inject`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.Inject.html) - each field is considered an injection point; this is not recommended, as the fields must be accessible (at least package local), and can’t be declared as final

Injection points are satisfied by services that match the required contract and qualifiers. If more than one service satisfies an injection point, the service with the highest weight is chosen (see [`@Weight`](/apidocs/io.helidon.common/io/helidon/common/Weight.html), [`Weighted`](/apidocs/io.helidon.common/io/helidon/common/Weighted.html)). If two services have the same weight, the order is undefined. If the injection point accepts a `java.util.List` of contracts, all available services are used to satisfy the injection point ordered by weight.

It is also important to note, that only services can have injection points.

### Injected dependency formats

Dependencies can be injected in different formats, depending on the required behavior:

- `Contract` - Retrieves an instance of another service.
- `Optional<Contract>` - Retrieves an instance, but if there is no service providing the contract, an empty optional is provided.
- `List<Contract>` - Retrieves all available instances of a given service.
- `Supplier<Contract>`, `Supplier<Optional<Contract>>`, `Supplier<List<Contract>>` - Similar to the above, but the value is only resolved when get() is called, allowing lazy evaluation to resolve cyclic dependencies and a "storm" of initializations when a service is looked up from the registry. When suppliers aren’t used, all instances MUST be created when the service instance is created (during construction).

## Scopes

There are three built-in scopes:

- [`@Service.Singleton`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.Singleton.html) – A single instance exists in the service registry for the registry lifetime.
- [`@Service.PerLookup`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.PerLookup.html) – A new instance is created each time a lookup occurs (including when injected into an injection point).
- [`@Service.PerRequest`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.PerRequest.html) – A single instance per request exists in the service registry. The definition of a "request" is not enforced by the injection framework but aligns with concepts like an HTTP request-response cycle or message consumption in a messaging system.

## Build time

Helidon injection is compile/build time based injection. This provides a significant performance boost since it eliminates the need for reflection or dynamic proxying at runtime, resulting in faster startup. Additionally, it integrates well with Native Image, making it an efficient choice for high-performance applications.

Because of that it needs to generate all the needed classes at compile time, so it minimizes the need of runtime processing. To ensure everything works correctly, you need to add the following annotation processors to your application’s compilation process.

These processors generate the necessary metadata and wiring for dependency injection and service registration.

*Example annotation processor configuration in Maven*

``` xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>io.helidon.codegen</groupId>
                        <artifactId>helidon-codegen-apt</artifactId>
                        <version>${helidon.version}</version>
                    </path>
                    <path>
                        <groupId>io.helidon.service</groupId>
                        <artifactId>helidon-service-codegen</artifactId>
                        <version>${helidon.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### Why are these annotation processors needed?

Annotation processor `helidon-service-codegen` generates a service descriptor (`ServiceProvider__ServiceDescriptor`) for each discovered service. This descriptor is discovered at runtime and is used to instantiate the service without relying on reflection, improving performance and reducing overhead during service creation.

## Basic injection example

To demonstrate how injection works, let’s create a simple working example where one service is injected into another.

*Creating simple Greeter service.*

``` java
@Service.Singleton
class Greeter {

    String greet(String name) {
        return "Hello %s!".formatted(name);
    }

}
```

Once the Greeter service is created, an injection point for this service is now required. Let’s create another service that injects the Greeter service as its constructor parameter.

*Create simple injection point*

``` java
@Service.Singleton
class GreetingInjectionService {

    private final Greeter greeter;

    @Service.Inject
    GreetingInjectionService(Greeter greeter) {
        this.greeter = greeter;
    }

    void printGreeting(String name) {
        System.out.println(greeter.greet(name));
    }
}
```

Now it just needs to be tested. The easiest way is to make a main method. The following piece of code initializes Service registry. After that we search for our `GreetingInjectionService` and execute it to print out `Hello David!`. To find out more about this manual approach, please take a look into the [Programmatic Lookup](#programmatic-lookup) chapter.

*Lookup our created service and execute it manually*

``` java
public static void main(String[] args) {
    var greetings = Services.get(GreetingInjectionService.class);
    greetings.printGreeting("David");
}
```

The last step is ensuring that everything necessary for your application to compile correctly with injection is included. See [Build time](#build-time).

If everything went as expected, no problems occurred and a Service registry gave us fully initialized and ready to use service.

## Service Lifecycle

The service registry manages the lifecycle of services. To ensure a method is invoked at a specific lifecycle phase, you can use the following annotations:

- [`@Service.PostConstruct`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.PostConstruct.html) – Invokes the annotated method after the instance has been created and fully injected.
- [`@Service.PreDestroy`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.PreDestroy.html) – Invokes the annotated method when the service is no longer in use by the registry. (Such as if the intended scope ends)
  - [`@Service.PerLookup`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.PerLookup.html) – PreDestroy annotated method is not invoked, since it is not managed by the service registry after the injection.
  - **Other scopes** – The pre-destroy method is invoked when the scope is deactivated (e.g. for singletons this happens during registry or JVM shutdown).

## Qualifiers

In dependency injection, a qualifier is a way to tell the framework which dependency to use when there are multiple options available.

Annotations are considered qualifier if they’re "meta-annotated" with [`@Service.Qualifier`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.Qualifier.html).

Helidon Inject provides two built-in qualifier:

- [`@Service.Named`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.Named.html) – Uses a `String` name to qualify a service.
- [`@Service.NamedByType`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.NamedByType.html) – Works the same way as `@Service.Named` but uses a class type instead. The name that would be used is the fully qualified name of the type.

Both [`@Service.Named`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.Named.html) and [`@Service.NamedByType`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.NamedByType.html) are interchangeable, so one can combine them. To see an example of this see [Named by the type](#named-by-the-type) chapter.

### Named service injection

Services can be assigned names, allowing them to be specified by name during injection. This ensures that the correct service is injected. To achieve this, we use the [`@Service.Named`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.Named.html) annotation.

*Create named services*

``` java
interface Color {
    String hexCode();
}

@Service.Named("blue")
@Service.Singleton
public class Blue implements Color {

    @Override
    public String hexCode() {
        return "0000FF";
    }
}

@Service.Named("green")
@Service.Singleton
public class Green implements Color {

    @Override
    public String hexCode() {
        return "008000";
    }
}
```

These named services can now be injected at specific injection points using their assigned names

*Create BlueCircle with Blue color injected*

``` java
@Service.Singleton
record BlueCircle(@Service.Named("blue") Color color) {
}
```

*Create GreenCircle with Green color injected*

``` java
@Service.Singleton
record GreenCircle(@Service.Named("green") Color color) {
}
```

### Named by the type

Alternatively, instead of using string-based names for services, a specific class can be used to "name" them. For this purpose, we use the [`@Service.NamedByType`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.NamedByType.html) annotation.

*Named by type usage example*

``` java
@Service.NamedByType(Green.class)
@Service.Singleton
public class GreenNamedByType implements Color {

    @Override
    public String hexCode() {
        return "008000";
    }
}
```

The way it is used on the injection point, it is the same as it was in case of the [`@Service.Named`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.Named.html).

*Named by type injection point*

``` java
@Service.Singleton
record GreenCircleType(@Service.NamedByType(Green.class) Color color) {
}
```

[`@Service.Named`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.Named.html) and [`@Service.NamedByType`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.NamedByType.html) are even interchangeable. So it is possible to use [`@Service.Named`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.Named.html) annotation with fully qualified class name of a class we used before. Let’s assume for this example, that our previously used class `Green` was in the `my.test` package. Now we can specify it as any other `String` name.

*Named injection point*

``` java
@Service.Singleton
record GreenCircleStringType(@Service.Named("my.test.Green") Color color) {
}
```

### Custom qualifiers

To make custom qualifiers, it is necessary to "meta-annotated" it with [`@Service.Qualifier`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.Qualifier.html).

*Create Blue and Green custom qualifiers*

``` java
@Service.Qualifier
public @interface Blue {
}

@Service.Qualifier
public @interface Green {
}
```

The `@Blue` and `@Green` annotations serve as our new qualifiers. It can now be used to qualify services in the same way as [`@Service.Named`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.Named.html).

*Custom qualifier Blue and Green usage*

``` java
interface Color {
    String name();
}

@Blue
@Service.Singleton
static class BlueColor implements Color {

    @Override
    public String name() {
        return "blue";
    }
}

@Green
@Service.Singleton
static class GreenColor implements Color {

    @Override
    public String name() {
        return "green";
    }
}
```

Once the services are created and qualified, they can be injected in the same way as before using the following approach

*Custom qualifier usage on injection point*

``` java
@Service.Singleton
record BlueCircle(@Blue Color color) {
}

@Service.Singleton
record GreenCircle(@Green Color color) {
}
```

## Factories

Let’s consider we have a contract named `MyContract`.

The simple case is that we have a class that implements the contract, and that is a service, such as:

``` java
@Service.Singleton
class MyImpl implements MyContract {
}
```

This means that the service instance itself serves as the implementation of the contract. When this service is injected into a dependency injection point, we receive an instance of MyImpl.

However, this approach only works if the contract is an interface and we’re implementing it fully. In some cases, this may not be enough, such as when:

- The instance needs to be provided by an external source.
- The contract is not an interface.
- The contract is a sealed interface.
- The instance may not always be created (e.g., it is optional).

These challenges can be addressed by implementing one of the factory interfaces supported by the Helidon Service Registry:

- [Supplier](#supplier)
- [ServicesFactory](#servicesfactory)
- [InjectionPointFactory](#injectionpointfactory)
- [QualifiedFactory](#qualifiedfactory)

### Supplier

A factory that supplies a single instance (it can also return `Optional<MyContract>`)

*Supplier factory*

``` java
/**
 * Supplier service factory.
 */
@Service.Singleton
class MyServiceProvider implements Supplier<MyService> {

    @Override
    public MyService get() {
        return new MyService();
    }
}
```

### ServicesFactory

The [`ServicesFactory`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.ServicesFactory.html) should be used to create zero to n instances at runtime, each assigned different qualifiers if needed. This allows for dynamic and flexible service creation while ensuring proper differentiation between instances based on their qualifiers.

``` java
@Service.Singleton
class MyServiceFactory implements Service.ServicesFactory<MyService> {
    @Override
    public List<Service.QualifiedInstance<MyService>> services() {
        var named = Service.QualifiedInstance.create(new MyService(), Qualifier.createNamed("name"));
        var named2 = Service.QualifiedInstance.create(new MyService(), Qualifier.createNamed("name2"));
        return List.of(named, named2);
    }
}
```

### QualifiedFactory

[`QualifiedFactory`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.QualifiedFactory.html) is strictly bound to a specific qualifier type. It will get executed only for injection points that are annotated with this selected qualifier and are intended for a selected contract. It will be ignored for any other injection points.

However, there is one special case. If the selected contract is `java.lang.Object`, the factory will be used for any contract, as long as the qualifier matches.

It receives:

- [`Qualifier`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Qualifier.html) - metadata of the qualifier that is on the annotated injection point, with each annotation property available
- [`Lookup`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Lookup.html) - information about injection request
- [`GenericType`](/apidocs/io.helidon.common/io/helidon/common/GenericType.html) - injection point type

This allows the factory to create instances dynamically based on the injection point information.

``` java
@Service.Qualifier
@interface SystemProperty {
    String value();
}

@Service.Singleton
class SystemProperties {

    private final String httpHost;
    private final String httpPort;

    SystemProperties(@SystemProperty("http.host") String httpHost,
                     @SystemProperty("http.port") String httpPort) {
        this.httpHost = httpHost;
        this.httpPort = httpPort;
    }

}

@Service.Singleton
class SystemPropertyFactory implements Service.QualifiedFactory<String, SystemProperty> {

    @Override
    public Optional<Service.QualifiedInstance<String>> first(Qualifier qualifier,
                                                             Lookup lookup,
                                                             GenericType<String> genericType) {
        return qualifier.stringValue()
                .map(System::getProperty)
                .map(propertyValue -> Service.QualifiedInstance.create(propertyValue, qualifier));
    }

}
```

### InjectionPointFactory

[`InjectionPointFactory`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.InjectionPointFactory.html) is very similar to [`QualifiedFactory`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.QualifiedFactory.html), but with one key difference—it is executed for each injection point and is not bound to a specific qualifier/s (unless specified).

It receives a Lookup object as a parameter, which contains all necessary information about the injection point. This allows the factory to create instances dynamically based on the injection point information. If the factory type `T` is `java.lang.Object`, the factory can handle ANY contract - such as when injecting configuration properties, which may be of any type (boolean, int, String etc.)

It is possible to restrict this factory to specific qualifiers by specifying them at the class level of the factory.

This ensures that the factory is executed only for injection points that match the intended contract and qualifier.

``` java
@Service.Singleton
class TestClass {

    private final System.Logger logger;

    TestClass(System.Logger logger) {
        this.logger = logger;
    }

}

@Service.Singleton
class LoggerFactory implements Service.InjectionPointFactory<System.Logger> {
    private static final System.Logger DEFAULT_LOGGER = System.getLogger(LoggerFactory.class.getName());

    @Override
    public Optional<Service.QualifiedInstance<System.Logger>> first(Lookup lookup) {
        System.Logger logger = lookup.dependency()
                .map(dep -> System.getLogger(dep.service().fqName()))
                .orElse(DEFAULT_LOGGER);

        return Optional.of(Service.QualifiedInstance.create(logger));
    }
}
```

## Interceptors

Interception allows adding behavior to constructors, methods, and injected fields without the need to explicitly code the required functionality in place.

By default, interception is enabled only for elements annotated with [`Interception.Intercepted`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Interception.Intercepted.html). However, annotation processor configurations can enable interception for any annotation or disable it entirely.

Interception wraps around the invocation, enabling it to:

- Execute logic before the actual invocation
- Modify invocation parameters
- Execute logic after the actual invocation
- Modify the response
- Handle exceptions

### Intercepted annotation

The [`Interception.Intercepted`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Interception.Intercepted.html) annotation is a marker used to indicate that an annotation should trigger interception.

*Custom annotation for interception*

``` java
/**
 * An annotation to mark methods to be intercepted.
 */
@Interception.Intercepted
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@interface Traced {
}
```

### Interceptor interface

The [`Interception.Interceptor`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Interception.Interceptor.html) interface defines an interceptor service that intercepts methods/constructors/fields annotated with the configured marker annotation. The interceptor service must be named by the fully qualified name of the [`Interception.Intercepted`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Interception.Intercepted.html) annotation (either via [`@Service.Named`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.Named.html) or [`@Service.NamedByType`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.NamedByType.html)).

To properly handle the interception chain, the interceptor must always invoke the `proceed` method if the invocation should continue normally. However, it is also possible to return a custom value directly from the interceptor and effectively bypass the original method execution.

*Sample Interceptor interface implementation*

``` java
@Service.Singleton
@Service.NamedByType(Traced.class) 
class MyServiceInterceptor implements Interception.Interceptor {
    @Override
    public <V> V proceed(InterceptionContext ctx, Chain<V> chain, Object... args) throws Exception {
        //Do something
        return chain.proceed(args); 
    }
}
```

- Binds this Interceptor to process elements annotated with `@Traced`
- Passing interceptor processing to another interceptor in the chain

### Delegate annotation

The [`@Interception.Delegate`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Interception.Delegate.html) annotation enables interception for classes that aren’t created through the service registry but are instead produced by a factory (More about factories can be found here - [Factory chapter](#factories)).

Let’s make the same `@Traced` annotation and Interceptor as in the previous examples

*Custom annotation and interceptor*

``` java
@Interception.Intercepted
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@interface Traced {
}

@Service.Singleton
@Service.NamedByType(Traced.class)
class MyServiceInterceptor implements Interception.Interceptor {

    @Override
    public <V> V proceed(InterceptionContext ctx, Chain<V> chain, Object... args) throws Exception {
        //Do something
        return chain.proceed(args);
    }
}
```

Now, let’s create the factory of the service instance.

*Instance producer*

``` java
@Service.Singleton
class MyServiceProvider implements Supplier<MyService> {
    @Override
    public MyService get() {
        return new MyService();
    }
}
```

Method calls on an instance created this way can’t be intercepted. To enable interception in such cases, we use the [`@Interception.Delegate`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Interception.Delegate.html) annotation. However, keep in mind that usage of this annotation doesn’t add the ability to intercept constructor calls. To enable interception, this annotation must be present on the class that the factory produces. While it is not required on interfaces, it will still work correctly if applied there.

If you need to enable interception for classes using delegation, you should make sure about the following:

- The class must have accessible no-arg constructor (at least package local)
- The class must be extensible (not final)
- The constructor should have no side effects, as the instance will act only as a wrapper for the delegate
- All invoked methods must be accessible (at least package local) and non-final

*Delegate used on the class*

``` java
@Service.Contract
@Interception.Delegate
class MyService {

    @Traced
    String sayHello(String name) {
        return "Hello %s!".formatted(name);
    }

}
```

### ExternalDelegate annotation

The [`@Interception.ExternalDelegate`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Interception.ExternalDelegate.html) annotation works similarly to [`@Interception.Delegate`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Interception.Delegate.html). However, the key difference is that [`@Interception.ExternalDelegate`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Interception.ExternalDelegate.html) is designed for classes that you don’t have control over. This means it allows you to apply the interception mechanism even to third-party classes.

It is not required to apply this annotation on the interfaces, however, it needs to be present for classes.

Let’s make the same `@Traced` annotation and Interceptor as in the previous examples

``` java
@Interception.Intercepted
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@interface Traced {
}

@Service.Singleton
@Service.NamedByType(Traced.class)
class MyServiceInterceptor implements Interception.Interceptor {

    @Override
    public <V> V proceed(InterceptionContext ctx, Chain<V> chain, Object... args) throws Exception {
        //Do something
        return chain.proceed(args);
    }
}
```

In this example, we can’t change the class we’re intercepting in any way. Let’s assume we have this external class, and we need to add an interception mechanism to it.

``` java
/**
 * Assume this is the class we have no control over.
 */
class SomeExternalClass {
    @Traced
    String sayHello(String name) {
        return "Hello %s!".formatted(name);
    }
}
```

Now we need to apply the [`@Interception.ExternalDelegate`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Interception.ExternalDelegate.html) annotation on the factory class. Once this is done, interception will work as expected

*ExternalDelegate annotation used on the factory*

``` java
@Service.Singleton
@Interception.ExternalDelegate(SomeExternalClass.class)
class SomeExternalClassProvider implements Supplier<SomeExternalClass> {
    @Override
    public SomeExternalClass get() {
        return new SomeExternalClass();
    }
}
```

## Events

Events enable in-application communication between services by providing a mechanism to emit events and register consumers to handle them.

A single event can be delivered to zero or more consumers.

Key Terminology:

- **[Event Object](#event-object)** – Any object that is sent as an event.
- **[Event Emitter](#event-emitter)** – Helidon generated service responsible for emitting events into the event system.
- **[Event Producer](#event-producer)** – A service that triggers an event by calling an emitter.
- **[Event Observer](#event-observer)** – A service that listens for events, with a method annotated using [`@Event.Observer`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Event.Observer.html).
- **[Qualified Events](#qualified-events)** – An event emitted with a qualifier, using an annotation marked with [`@Service.Qualifier`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.Qualifier.html).

### Event Object

To begin emitting events, the first step is to define the event type itself.

*Create a desired event type*

``` java
/**
 * A custom event payload.
 * @param msg message
 */
record MyEvent(String msg) {
}
```

### Event Emitter

Event emitters are code generated by Helidon when an injection point is discovered that expects it.

All the emitters are generated as implementations of the [`Event.Emitter`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Event.Emitter.html) interface.

### Event Producer

An event producer is a service that triggers the event by using the event emitter.

To emit an event, inject the desired [`Event.Emitter`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Event.Emitter.html) Event.Emitter instance, construct the corresponding event object, and call the emit method on the emitter instance.

*Event producer example*

``` java
@Service.Singleton
record MyEventProducer(Event.Emitter<MyEvent> emitter) {

    void produce(String msg) {
        emitter.emit(new MyEvent(msg));
    }
}
```

The method returns only after all event observers have been notified. If any observer throws an exception, an [`EventDispatchException`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/EventDispatchException.html) is thrown, with all caught exceptions added as suppressed. This ensures that all observers are invoked, even if an exception occurs.

### Event Observer

An event observer is a service which processes fired event.

To create an event observer:

- create an observer method, with a single parameter of the event type you want to observe
- annotate the method with [`@Event.Observer`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Event.Observer.html)

*Event observer example*

``` java
@Service.Singleton
class MyEventObserver {

    @Event.Observer
    void event(MyEvent event) {
        //Do something with the event
    }
}
```

## Qualified Events

A Qualified Event is only delivered to Event Observers that use the same qualifier.

### Qualified Event Producer

A qualified event can be produced with two options:

1.  The injection point of [`@Event.Emitter`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Event.Emitter.html) (the constructor parameter, or field) is annotated with a qualifier annotation
2.  The `Event.Emitter.emit(..)` method is called with explicit qualifier(s), note that if combined, the qualifier specified by the injection point will always be present!

We are using qualifier created in the chapter [Custom qualifier](#custom-qualifiers), to demonstrate how events work with qualifiers. Now we need to create a new event producer, which fires event only to observers qualified with `@Blue`.

*Qualified event producer*

``` java
@Service.Singleton
record MyBlueProducer(@Blue Event.Emitter<String> emitter) {

    void produce(String msg) {
        emitter.emit(msg);
    }
}
```

### Qualified Event Observers

To consume a qualified event, observer method must be annotated with the correct qualifier(s). If we want to consume the same messages which are produced by the producer in the example above, our observer needs to be annotated with the same qualifier. In this case it is `@Blue`.

*Qualified event observer*

``` java
@Service.Singleton
class MyBlueObserver {

    @Event.Observer
    @Blue
    void event(MyEvent event) {
        //Do something with the event
    }
}
```

## Asynchronous Events

Events can be emitted asynchronously, and event observers can also operate asynchronously.

An executor service for handling asynchronous events can be registered in the service registry by providing a service that implements the `java.util.concurrent.ExecutorService` contract and is named [`EventManager`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/EventManager.html).

If no custom executor service is provided, the system defaults to a thread-per-task executor using Virtual Threads, with thread names prefixed as `inject-event-manager-`.

### Asynchronous Event Producer

All asynchronous event producers must use the `Event.Emitter.emitAsync(..)` method instead of the synchronous `Event.Emitter.emit(..)`.

The `emitAsync` method returns a `CompletionStage<MyEvent>` instance. When executed, it completes once all event observers have been submitted to the executor service. However, there is no guarantee that any event has been delivered—it may have been sent to anywhere from 0 to n observers (where n represents the number of synchronous observers).

*Asynchronous Event Producer Example*

``` java
@Service.Singleton
record MyAsyncProducer(Event.Emitter<MyEvent> emitter) {

    void produce(String msg) {
        CompletionStage<MyEvent> completionStage = emitter.emitAsync(new MyEvent(msg));
        //Do something with the completion stage
    }
}
```

### Asynchronous Observer

Asynchronous observer methods are invoked from separate threads (through the executor service mentioned above), and their results are ignored by the Event Emitter; if there is an exception thrown from the observer method, it is logged with `WARNING` log level into logger named [`EventManager`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/EventManager.html).

To declare an asynchronous observer use annotation [`@Event.AsyncObserver`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Event.AsyncObserver.html) instead of [`@Event.Observer`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Event.Observer.html).

*Asynchronous Event Observer Example*

``` java
@Service.Singleton
class MyEventAsyncObserver {

    @Event.AsyncObserver
    void event(MyEvent event) {
        //Do something with the event
    }
}
```

## Programmatic Lookup

If you want to use programmatic lookup, there are several ways how to get a [`ServiceRegistry`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/ServiceRegistry.html) instance.

We can either get the global one or create a custom one (more advanced use case). In most of the cases, the global service registry is enough (and expected to be used) for a single applications. A custom service registry should only be created for specific use cases.

To get the global [`ServiceRegistry`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/ServiceRegistry.html) we need to use [`GlobalServiceRegistry`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/GlobalServiceRegistry.html) and select `registry` method.

However, it is also possible to access global service registry via [`Services`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Services.html) static methods. This is the shortcut for accessing the services from the global service registry. Global service registry shouldn’t be used from the factories or services. Intended use is in the `main` methods or when one needs static access.

Note that all instances in the registry are created lazily, meaning the service registry does nothing by default. If a service performs any actions during construction or post-construction, you must first retrieve an instance from the registry to trigger its initialization.

Registry methods:

- `T get(…​)` - immediately get an instance of a contract from the registry; throws if implementation not available
- `Optional<T> first(…​)` - immediately get an instance of a contract from the registry; there may not be an implementation available
- `List<T> all(…​)` - immediately get all instances of a contract from the registry; result may be empty
- `Supplier<T> supply(…​)` - get a supplier of an instance; the service may be instantiated only when `get` is called
- `Supplier<Optional<T>> supplyFirst(…​)` - get a supplier of an optional instance
- `Supplier<List<T>> supplyAll(…​)` - get a supplier of all instances

Lookup parameter options:

- `Class<?>` - the contract we’re looking for
- [`TypeName`](/apidocs/io.helidon.common.types/io/helidon/common/types/TypeName.html) - the same, but using Helidon abstraction of type names (may have type arguments)
- [`Lookup`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Lookup.html) - a full search criteria for a registry lookup

### Service registry injection

It is also possible to inject [`ServiceRegistry`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/ServiceRegistry.html). Keep in mind that when injected, the provided instance will be the same one, which is used to create the service instance.

### Custom service registry

It is possible to create custom [`ServiceRegistry`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/ServiceRegistry.html) instance. Keep in mind, this whole process is advanced and should be used only when you know what you are doing.

To create a custom registry and manager instance:

``` java
// create an instance of a registry manager - can be configured and shut down
var registryManager = ServiceRegistryManager.create();
// get the associated service registry
var registry = registryManager.registry();
```

Keep in mind, that custom [`ServiceRegistryManager`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/ServiceRegistryManager.html) must be shut down, once it is not needed.

``` java
// create an instance of a registry manager - can be configured and shut down
var registryManager = ServiceRegistryManager.create();
// Your desired logic with ServiceRegistry

// Once ServiceRegistryManager is no longer needed, it needs to be closed
registryManager.shutdown();
```

## Startup

Helidon provides a Maven plugin (`io.helidon.service:helidon-service-maven-plugin`, goal `create-application`) to generate build time bindings, that can be used to start the service registry without any classpath discovery and reflection. Default name is `ApplicationBinding` (customizable)

Methods that accept the bindings are on [`ServiceRegistryManager`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/ServiceRegistryManager.html):

- `start(Binding)` - starts the service registry with the generated binding, initializing all singleton and per-lookup services annotated with a [`@Service.RunLevel`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.RunLevel.html) annotation (i.e. `start(ApplicationBinding.create())`)
- `start(Binding, ServiceRegistryConfig)` - same as above, allows for customization of configuration, if used, remember to set discovery to `false` to prevent automated discovery from the classpath

Application binding contains reference to all services that can be used by the application at runtime. As a result, when using the generated binding and JPMS (`module-info.java`), all modules that contain services (or Java ServiceLoader providers used by the registry) must be configured as `required` in the module info, otherwise the binding cannot be compiled.

All options to start a Helidon application that uses service registry: - A generated `ApplicationMain` - optional feature of the Maven plugin, requires property `generateMain` to be set to `true`. It uses [`@Service.RunLevel`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.RunLevel.html) actively, but via code generated classes → See [RunLevel](#runlevel) for more information. This is the only approach that is fully reflection free and skips lookups for injection points. - The Helidon startup class `io.helidon.Main`, which will start the registry manager and initialize all [`@Service.RunLevel`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.RunLevel.html) services, though it uses service discover (which in turn must use reflection to get service descriptor instances)

### ServiceRegistryManager

Manager is responsible for managing the state of a single [`ServiceRegistry`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/ServiceRegistry.html) instance.

When created programmatically, two possible methods can be chosen.

- `create` - Creates a new [`ServiceRegistry`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/ServiceRegistry.html) instance, but does not create any service instance. Service instances are created only when needed.
- `start` - Creates a new [`ServiceRegistry`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/ServiceRegistry.html) instance and creates all services annotated with [`@Service.RunLevel`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.RunLevel.html). See [RunLevel](#runlevel) chapter.

It is important to note, that once you don’t need your service registry, method `shutdown` on the manager must be called to ensure proper termination of the service registry.

### RunLevel

[`@Service.RunLevel`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.RunLevel.html) is the annotation used for specifying the steps in which certain services should be started. It always starts from the lowest number to the highest. It is possible to have one or more services in each run level. When more than one service is defined to a specific run level, the order of creation is defined by [`@Weight`](/apidocs/io.helidon.common/io/helidon/common/Weight.html) from highest weight to lowest.

When the service manager shuts down it destroys services in the opposite order, from highest run-level value to lowest and, within a single run level, from lowest weight to highest.

We can also add methods which are executed after service construction and before service destruction, respectively.

``` java
@Service.RunLevel(1)
@Service.Singleton
class Level1 {

    @Service.PostConstruct
    void onCreate() {
        System.out.println("level1 created");
    }

    @Service.PreDestroy
    void onDestroy() {
        System.out.println("level1 destroyed"); }
}

@Service.RunLevel(2)
@Service.Singleton
class Level2 {

    @Service.PostConstruct
    void onCreate() {
        System.out.println("level2 created");
    }

    @Service.PreDestroy
    void onDestroy() {
        System.out.println("level2 destroyed"); }
}
```

The easiest way for us to use these annotations, is to use `start` method on the [`ServiceRegistryManager`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/ServiceRegistryManager.html).

``` java
public static void main(String[] args) {
    ServiceRegistryManager registryManager = ServiceRegistryManager.start();
    registryManager.shutdown();
}
```

Once executed, we get the following output:

``` text
level1 created
level2 created
level2 destroyed
level1 destroyed
```
