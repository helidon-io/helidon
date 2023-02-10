# helidon-pico

<b>Helidon Pico</b> is an optional feature in Helidon, that aims to offer the following:

* A minimalist, compile-time based dependency injection framework free from reflection, and compliant to the JSR-330 injection specification.
* A service registry that provides lazy service activation and meta-information about each service in terms APIs for describing what each service provides and what are their dependencies on other services.
* Support for Java 11+, using jakarta.inject or javax.inject, jakarta.annotations or javax.annotations.
* Extensibility. Developers can either provide their own templates for code generation, or provide an entirely different implementation different from this reference implementation. Pico will also (eventually) provide extensibility between other frameworks (e.g., Spring, Guice, CDI, etc.).
* Interception. Services can be intercepted using code generation at compile-time - also avoiding all use of reflection at runtime.
* Lifecycle. Pico can function as a kernel for your application, providing graceful startup and shutdown lifecycle processing.

The Helidon Team believes that the above features help developers achieve the following goals:
* Improved developer options. With Pico... developers can choose to use an imperative coding style or now with Pico and IoC style previously only available with CDI in Helidon MP.
* Improved determinism. With Pico... developers can decide to use compile-time code generation into their build process, thereby statically determining the injection path while still using a declarative approach for building their application. Added to this, all code-generated artifacts are in source form instead of bytecode thereby making your application more readable, understandable, consistent, and debuggable.
* Detect errors at compile time. With Pico... developers can ferret out issues in their dependency model at compile-time instead of discovering about issues at runtime.
* Improved performance. Pushing more into compile-time helps reduce what otherwise would need to occur (often times via reflection) to compile-time. Native code is generated that is further optimized by the compiler. Additionally, with lazy activation of services, only what is needed is activated. Anything not used may be in the classpath, but unless there is demand for these services that can be short-circuited from starting unless and until they are needed.

<u>Many DI frameworks start simple and over time become bloated with "bells and whistle" type features - the majority of which most developers don't need and never use; especially in today's world of microservices where the application scope is the JVM process itself. The Helidon Pico Framework is a reset back to basics, and perfect for such use cases requiring minimalism yet still be extensible.</u>

## Terminology
* DI - Dependency Injection.
* Inject - The assignment of a <i>service</i> instance to a field or method setter that has been annotated with <i>@Inject</i> - also referred to as an injection point. In Spring this would be referred to as 'Autowired'.
* Injection Plan - The act of determining how your application will resolve each <i>injection point</i>. In Pico this can optionally be performed at compile-time. But even when the injection plan is deferred to runtime it is resolved without using reflection, and is therefore conducive to native image restrictions and enhanced performance.
* Service - In Spring this would be referred to as a bean with a <i>@Service</i> annotation; These are concrete class types in your application that represents some sort of business logic.
* Scope - This refers to the cardinality of a <i>service</i> instance in your application.
* Singleton - jakarta.inject.Singleton or javax.inject.Singleton - This is the default scope for services in Pico just like it is in Spring.
* Provided - jakarta.inject.Provider or javax.inject.Provider - If the <i>scope</i> of a <i>service</i> is not <i>Singleton</i> then it is considered to be a Provided scope - and the cardinality will be ascribed to the implementation of the Provider to determine its cardinality. The provider can optionally use the <i>injection point</i> context to determine the appropriate instance and/or cardinality it provides.
* Contract - These are how a service can alias itself for injection. Contracts are typically the interface or abstract base class definitions of a <i>service</i> implementation. <i>Injection points</i> must be based upon either using a contract or service that pico is aware of, usually through annotation processing at compile time.
* Qualifier - jakarta.inject.qualifier or javax.inject.qualifier - These are meta annotations that can be ascribed to other annotations. One built-in qualifier type is <i>@Named</i> in the same package.
* Dependency - An <i>injection point</i> represents what is considered to be a dependency, perhaps <i>qualified</i> or Optional, on another service or contract.  This is just another what to describe an <i>injection point</i>.
* Activator (aka ServiceProvider) - This is what is code generated by Pico to lazily activate your <i>service</i> instance(s) in the Pico <i>services registry</i>, and it handles resolving all <i>dependencies</i> it has, along with <i>inject</i>ing the fields, methods, etc. that are required to be satisfied as part of that activation process.
* Services (aka services registry) - This is the collection of all services that are known to the JVM/runtime in Pico.
* Module - This is where your application will "bind" services into the <i>services registry</i> - typically code generated, and typically with one module per jar/module in your application.
* Application - The fully realized set of modules and services/service providers that constitute your application, and code-generated using <b>Helidon Pico Tooling</b>.

## Getting Started
As stated in the introduction above, the Pico framework aims to provide a minimalist API implementation. As a result, it might be surprising to learn how small the actual API is for Pico - see [api](./api/README.md) and the API/annotation types at [pico api](./api/src/main/java/io/helidon/pico).  If you are already familiar with [jakarta.inject](https://javadoc.io/doc/jakarta.inject/jakarta.inject-api/latest/index.html) and optionally, [jakarta.annotation](https://javadoc.io/doc/jakarta.annotation/jakarta.annotation-api/latest/jakarta.annotation/jakarta/annotation/package-summary.html) then basically you are ready to go. But if you've never used DI before then first review the basics of [dependency injection](https://en.wikipedia.org/wiki/Dependency_injection).

The prerequisites are familiarity with dependency injection, Java v11+, and maven 3.8.5+.

The best way to learn Helidon Pico is by looking at [the examples](./examples/README.md) --- comming soon .  But if you want to immediately get started here are the basics steps:

1. Put these in your pom.xml or gradle.build file:
   Annotation processor dependency / path:
```
    <groupId>io.helidon.pico</groupId>
    <artifactId>helidon-pico-processor</artifactId>
    <version>${helidon.version}</version>
```
Compile-time dependency:
```
  <dependency>
    <groupId>io.helidon.pico</groupId>
    <artifactId>helidon-pico</artifactId>
    <version>${helidon.version}</version>
  </dependency>
```

2. Write your application using w/ standard jakarta.inject.* and jakarta.annotation.* types. Again, see any of [the examples](./examples/README.md) for pointers as needed.

3. Build and run. In a DI-based framework, the frameworks "owns" the creation of services in accordance with the <i>Scope</i> each service is declared as. You therefore need to get things started by creating demand for the initial service(s) instead of ever calling <i>new</i> directly in your application code. Generally speaking, there are two such ways to get things started at runtime:

* If you know the class you want to create then look it up directly using the <i>Services</i> SPI. Here is a sample excerpt from [the book example](./examples/book/README.md):

```
        Services services = PicoServices.picoServices().orElseThrow().services();
        ServiceProvider<MyService> serviceProvider = services.lookupFirst(MyService.class);
        MyService myLazyActivatedService = serviceProvider.get();
```

* If there are a collection of services requiring activation at startup then we recommend annotating those service implementation types with <i>RunLevel(RunLevel.STARTUP)</i> and then use code below in main() to lazily activate those services. Note that whenever List-based injection is used in Pico all services matching the injection criteria will be in the injected (and immutable) list. The list will always be in order according to the <i>Weight</i> annotation value, ranking from the highest weight to the lowest weight. If services are not weighted explicitly, then a default weight is assigned.  If the weight is the same for two services, then the secondary ordering will be based on the FN class name of the service types. While <i>Weight</i> determines list order, the <i>RunLevel</i> annotation is used to rank the startup ordering, from the lowest value to the highest value, where <i>RunLevel.STARTUP == 0</i>.  The developer is expected to activate these directly using code like the following (the <i>get()</i> lazily creates & activates the underlying service type):

```
      List<ServiceProvider<Object>> startupServices = services
              .lookup(DefaultServiceInfoCriteria.builder().runLevel(RunLevel.STARTUP).build());
      startupServices.stream().forEach(ServiceProvider::get);
```

* If the ordering of the list of services is important, remember to use the <i>Weight</i> and/or <i>RunLevel</i> annotations to establish the priority / weighted ordering, and startup ordering.

## More Advanced Features

* Pico provides a means to generate "Activators" (the DI supporting types) for externally built modules as well as supporting javax annotated types. See [the logger example](./examples/logger/README.md) for use of these features.

* Pico offers services the ability to be intercepted. If your service contains any annotation that itself is annotated with <i>InterceptorTrigger</i> then the code generated for that service will support interception. The <i>Helidon Nima</i> project provides these types of examples.

* Pico provides meta-information for each service in its service registry, including such information as what contracts are provided by each service as well as describing its dependencies.

* Pico generates a proposed <i>module-info.java.pico</i> file for your module (look for module-info.java.pico under ./target/classes or ./target/test-classes).

* Pico provides a maven plugin that allows the injection graph to be (a) validated for completeness, and (b) deterministically bound to the service implementation - at compile-time. This is demonstrated in each of the examples, the result of which leads to early detection of issues at compile-time instead of at runtime as well as a marked performance enhancement.

* Testability. The <b>testing</b> is a module that offers a set of types in order to facility for creating fake/mock services for various testing scenarios.

* Extensibility. The entire Pico Framework is designed to by extended either at a micro level (developers can override mustache/handlebar templates) to the macro level (developers can provide their own implementation of any SPI).

## Modules

* [pico](./pico) - the Pico API and SPI; depends on jakarta-inject and jakarta-annotations. Required as a maven compile-time dependency for runtime consumption.
* [services](./services) - contains the default implementation of the Pico API/SPI; depends on the pico api module above. Requires as a maven compile-time dependency for runtime consumption.
* [configdriven](./configdriven) - Extensions to Pico to integrate directly with [Helidon Config](../config).
* [tools](./tools) - contains the libraries and template-based codegen mustache resources as well as model validation tooling; depends on runtime services. Only required at build time and is not required for Pico at runtime.
* [processor](./processor) - contains the libraries for annotation processing; depends on tools. Only required at build time and is not required for Pico at runtime.
* [maven-plugin](./maven-plugin) - provides code generation Mojo wrappers for maven; depends on tools. Only required at build time and is not required for Pico at runtime. This is what would be used to create your <b>Application</b>.
* [testing](./testing) - provides testing types useful for Pico unit & integration testing.
* [tests](./tests) - used internally for testing Pico.
* [examples](./examples) --- coming soon --- providing examples for how to use Pico as well as side-by-side comparisons for Pico compared to Guice, Dagger2, Hk2, etc.

## How Pico Works

* The Pico annotation [processor](./processor) will look for standard jakarta/javax inject and jakarta/javax annotation types. When these types are found in a class that is being compiled by javac, Pico will trigger the creation of an <i>Activator</i> for that service class/type.  For example, if you have a FooImpl class implementing Foo interface, and the FooImpl either contains "@Inject" or "@Singleton" then the presence of either of these annotations will trigger the creation of a FooImpl$$picoActivator to be created. The Activator is used to (a) describe the service in terms of what service contracts (i.e., interfaces) are advertised by FooImpl - in this case <i>Foo</> (if Foo is annotated with @Contract or if "-Aio.helidon.pico.autoAddNonContractInterfaces=true" is used at compile-time), (b) lifecycle of services including creation, calling injection-based setters, and any <i>PostConstruct or PreDestroy</i> methods.

* If one or more activators are created at compile-time, then a <i>picoModule</i> is also created to aggregate the services for the given module. Below is an example if a <i>picoModule</i> from [examples/logger](./examples/logger). At initialization time of Pico, all <i>Module</i>s will be located using the ServiceLocator and each service will be binded into the Pico service registry.

```java
@Generated({"provider=oracle", "generator=io.helidon.pico.tools.creator.impl.DefaultActivatorCreator", "ver=1.0-SNAPSHOT"})
@Singleton @Named(picoModule.NAME)
public class picoModule implements Module {
    static final String NAME = "pico.examples.logger.common";
    
    @Override
    public Optional<String> getName() {
        return Optional.of(NAME);
    }

    @Override
    public String toString() {
        return NAME + ":" + getClass().getName();
    }

    @Override
    public void configure(ServiceBinder binder) {
        binder.bind(io.helidon.pico.examples.logger.common.AnotherCommunicationMode$$picoActivator.INSTANCE);
        binder.bind(io.helidon.pico.examples.logger.common.Communication$$picoActivator.INSTANCE);
        binder.bind(io.helidon.pico.examples.logger.common.DefaultCommunicator$$picoActivator.INSTANCE);
        binder.bind(io.helidon.pico.examples.logger.common.EmailCommunicationMode$$picoActivator.INSTANCE);
        binder.bind(io.helidon.pico.examples.logger.common.ImCommunicationMode$$picoActivator.INSTANCE);
        binder.bind(io.helidon.pico.examples.logger.common.LoggerProvider$$picoActivator.INSTANCE);
        binder.bind(io.helidon.pico.examples.logger.common.SmsCommunicationMode$$picoActivator.INSTANCE);
    }
}
```

* If an annotation in your service is meta-annotated with <i>InterceptedTrigger</i>, then an extra service type is created. For example, if FooImpl was found to have one such annotation then FooImpl$$picoInterceptor would also be created along with an activator for that interceptor. The interceptor would be created with a higher weight than your FooImpl, and would therefore be "preferred" when a single <i>@Inject</i> is used for Foo or FooImpl.  If a list is injected then it would appear towards the head of the list.  Once again, all reflection is avoided in these generated classes.  Any calls to Foo/FooImpl will be interceptable for any <i>Interceptor</i> that is <i>@Named</i> to handle that type name. Search the test code and Nima code for such examples as this is an advanced feature.

* The [maven-plugin](./maven-plugin) can optionally be used to avoid Pico lookup resolutions at runtime within each service activation. At startup Pico will attempt to first use the <i>Application</i> to avoid lookups. The best practice is to apply the <i>maven-plugin</i> to <i>create-application</i> on your maven assembly - this is usually your "final" application module that depends upon every other service / module in your entire deployed application. Here is the <i>picoApplication</i> from [examples/logger](./examples/logger):

```java
@Generated({"provider=oracle", "generator=io.helidon.pico.maven.plugin.ApplicationCreatorMojo", "ver=1.0-SNAPSHOT"})
@Singleton @Named(picoApplication.NAME)
public class picoApplication implements Application {
  static final String NAME = "unnamed";

  @Override
  public Optional<String> getName() {
    return Optional.of(NAME);
  }

  @Override
  public String toString() {
    return NAME + ":" + getClass().getName();
  }

  @Override
  public void configure(ServiceInjectionPlanBinder binder) {
    /**
     * In module name "pico.examples.logger.common".
     * @see {@link io.helidon.pico.examples.logger.common.AnotherCommunicationMode }
     */
    binder.bindTo(io.helidon.pico.examples.logger.common.AnotherCommunicationMode$$picoActivator.INSTANCE)
            .bind("io.helidon.pico.examples.logger.common.logger",
                  io.helidon.pico.examples.logger.common.LoggerProvider$$picoActivator.INSTANCE)
            .commit();

    /**
     * In module name "pico.examples.logger.common".
     * @see {@link io.helidon.pico.examples.logger.common.Communication }
     */
    binder.bindTo(io.helidon.pico.examples.logger.common.Communication$$picoActivator.INSTANCE)
            .bind("io.helidon.pico.examples.logger.common.<init>|2(1)",
                  io.helidon.pico.examples.logger.common.LoggerProvider$$picoActivator.INSTANCE)
            .bind("io.helidon.pico.examples.logger.common.<init>|2(2)",
                  io.helidon.pico.examples.logger.common.DefaultCommunicator$$picoActivator.INSTANCE)
            .commit();

    /**
     * In module name "pico.examples.logger.common".
     * @see {@link io.helidon.pico.examples.logger.common.DefaultCommunicator }
     */
    binder.bindTo(io.helidon.pico.examples.logger.common.DefaultCommunicator$$picoActivator.INSTANCE)
            .bind("io.helidon.pico.examples.logger.common.sms",
                  io.helidon.pico.examples.logger.common.SmsCommunicationMode$$picoActivator.INSTANCE)
            .bind("io.helidon.pico.examples.logger.common.email",
                  io.helidon.pico.examples.logger.common.EmailCommunicationMode$$picoActivator.INSTANCE)
            .bind("io.helidon.pico.examples.logger.common.im",
                  io.helidon.pico.examples.logger.common.ImCommunicationMode$$picoActivator.INSTANCE)
            .bind("io.helidon.pico.examples.logger.common.<init>|1(1)",
                  io.helidon.pico.examples.logger.common.AnotherCommunicationMode$$picoActivator.INSTANCE)
            .commit();

    /**
     * In module name "pico.examples.logger.common".
     * @see {@link io.helidon.pico.examples.logger.common.EmailCommunicationMode }
     */
    binder.bindTo(io.helidon.pico.examples.logger.common.EmailCommunicationMode$$picoActivator.INSTANCE)
            .bind("io.helidon.pico.examples.logger.common.logger",
                  io.helidon.pico.examples.logger.common.LoggerProvider$$picoActivator.INSTANCE)
            .commit();

    /**
     * In module name "pico.examples.logger.common".
     * @see {@link io.helidon.pico.examples.logger.common.ImCommunicationMode }
     */
    binder.bindTo(io.helidon.pico.examples.logger.common.ImCommunicationMode$$picoActivator.INSTANCE)
            .bind("io.helidon.pico.examples.logger.common.logger",
                  io.helidon.pico.examples.logger.common.LoggerProvider$$picoActivator.INSTANCE)
            .commit();

    /**
     * In module name "pico.examples.logger.common".
     * @see {@link io.helidon.pico.examples.logger.common.LoggerProvider }
     */
    binder.bindTo(io.helidon.pico.examples.logger.common.LoggerProvider$$picoActivator.INSTANCE)
            .commit();

    /**
     * In module name "pico.examples.logger.common".
     * @see {@link io.helidon.pico.examples.logger.common.SmsCommunicationMode }
     */
    binder.bindTo(io.helidon.pico.examples.logger.common.SmsCommunicationMode$$picoActivator.INSTANCE)
            .bind("io.helidon.pico.examples.logger.common.logger",
                  io.helidon.pico.examples.logger.common.LoggerProvider$$picoActivator.INSTANCE)
            .commit();
  }
}
```

* The <i>maven-plugin</i> can additionally be used to create the Pico DI supporting types (Activators, Modules, Interceptors, Applications, etc.) from introspecting an external jar - see the [examples](./examples) for details.

That is basically all there is to know to get started and become productive using Pico.

## Special Notes to Providers & Contributors
Pico aims to provide an extensible, SPI-based mechanism. There are many ways Pico can be overridden, extended, or even replaced with a different implementation than what is provided out of the built-in reference implementation modules included. Of course, you can also contribute directly by becoming a committer. However, if you are looking to fork the implementation then you are strongly encouraged to honor the "spirit of this framework" and follow this as a high-level guide:

* In order to be a Pico provider implementation, the provider must supply an implementation for <i>PicoServices</i> discoverable by the
  ServiceLoader with a higher-than-default <i>Weight</i>.
* All SPI class definitions from the <i>io.helidon.pico.spi</i> package are considered primordial and therefore should not participate in injection or conventionally be considered injectable.
* All service classes that are not targets for injection should be represented under
  /META-INF/services/<serviceClassName> to be found by the standard ServiceLocator.
* Providers are encouraged to fail-fast during compile time - this implies a sophisticated set of tooling that can and should be applied to create and validate the integrity of the dependency graph at compile time instead of at runtime.
* Providers are encouraged to avoid reflection completely at runtime.
* Providers are encouraged to advertise capabilities and configuration using <i>PicoServicesConfig</i>.
