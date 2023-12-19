# helidon-inject

<b>Helidon Injection</b> is an optional feature in Helidon. At its core it provides these main features:

1. A service registry. The service registry holds service providers and are (or otherwise can produce) services. Each service provider in the registry advertises its meta-information for what each service provides, and what it requires in the way of dependencies.

2. A lifecycle engine. Each service provider in the registry remains dormant until there is a "demand for activation". This "demand for activation" is also known as "lazy activation" that can come in different ways. One way is to simply "get" a service (or services) from the registry that matches the meta-information criteria you provide programmatically. If the service (or services) need other services as part of the activation then those services are chain activated, recursively. Helidon Injection provides graceful startup and shutdown at a micro service-level as well as at a macro application level for all services in the service registry.

3. Integrations and Extensibility. More will be mentioned on this later.

Over the foundation of these three main features of "services registry", "lifecycle", and "extensibility" there are a number of other tooling and layers that are delivered from various Injection submodules that provide the following additional features and benefits:

1. A minimalist, compile-time generated dependency injection framework that is free from reflection, and compliant to the JSR-330 injection specification. Compile-time source code generation has a number of advantages, including: (a) pre-runtime validation of the DI model, (b) visibility into your application by providing "less magic", which in turn fosters understandability and debug-ability of your application, (c) deterministic behavior (instead of depending on reflection and classpath ordering, etc.) and (c) performance, since binding the model at compile-time is more efficient than computing it at runtime. Helidon Injection (through its tooling) provides you with a mix of declarative and programmatic ways to build your application. It doesn't have to be just one or the other like other popular frameworks in use today require. Inspiration for Helidon Injection, however, did come from many libraries and frameworks that came before it (e.g., Jakarta Hk2, Google Guice, Spring, CDI, and even OSGi). Foundationally, Injection provides a way to develop declarative code using standard (i.e., javax/jakarta, not Helidon specific) annotation types.

2. Integrations. Blending services from different providers (e.g., Helidon services like WebServer, your application service, 3rd party service, etc.) becomes natural in the Injection framework, and enables you to build fully-integrated, feature-rich applications more easily.

3. Extensibility. At the micro level developers can provide their own templates for things like code generation, or even provide an entirely different implementation from this reference implementation Helidon provides. At a macro level (and post the initial release), Injection will be providing a foundation to extend your Guice, Spring, Hk2, CDI, <fill-in the blank> application naturally into one application runtime. The Helidon team fields a number of support questions that involve this area involving "battling DI frameworks" like CDI w/ Hk2. In time, Helidon Injection aims to smooth out this area through the integrations and extensibility features that it will be providing.

4. Interception. Annotations are provided, that in conjunction with Helidon Injection's code-generation annotation processors, allow services in the service registry to support interception and decoration patterns - without the use of reflection at runtime, which is conducive to native image.

The Helidon Team believes that the above features help developers achieve the following goals:
* More IoC options. With Helidon Injection... developers can choose to use an imperative coding style or a declarative IoC style previously only available with CDI using Helidon MP. At the initial release (Helidon 4.0), however, Injection will only be available with Helidon Nima support.
* Compile-time benefits. With Injection... developers can decide to use compile-time code generation into their build process, thereby statically and deterministically wiring their injection model while still enjoying the benefits of a declarative approach for writing their application. Added to this, all code-generated artifacts are in source form instead of bytecode thereby making your application more readable, understandable, consistent, and debuggable. Furthermore, DI model inconsistencies can be found during compile-time instead of at runtime.
* Improved performance. Pushing more into compile-time helps reduce what otherwise would need to occur (often times via reflection) to built/compile-time processing. Native code is generated that is further optimized by the compiler. Additionally, with lazy activation of services, only what is needed is activated. Anything not used may be in the classpath is available, but unless and until there is demand for those services they remain dormant. You control the lifecycle in your application code.
* Additional lifecycle options. Injection can handle micro, service-level activations for your services, as well as offer controlled shutdown if desired.

Many DI frameworks start simple and over time become bloated with "bells and whistle" type features - the majority of which most developers don't need and will never use; especially in today's world of microservices where the application scope is the JVM process itself.

***
The <b>Helidon Injection Framework</b> is a reset back to basics, and perfect for such use cases requiring minimalism but yet still be extensible. This is why Injection intentionally chose to implement the earlier JSR-330 specification at its foundation. <i>Application Scope</i> == <i>Singleton Scope</i> in a microservices world.
***

Request and Session scopes are simply not made available in Helidon Injection. We believe that scoping is a recipe for undo complexity, confusion, and bugs for the many developers today. 

## Terminology
* DI - Dependency Injection.
* Inject - The assignment of a <i>service</i> instance to a field or method setter that has been annotated with <i>@Inject</i> - also referred to as an injection point. In Spring this would be referred to as 'Autowired'.
* Injection Plan - The act of determining how your application will resolve each <i>injection point</i>. In Injection this can optionally be performed at compile-time. But even when the injection plan is deferred to runtime it is resolved without using reflection, and is therefore conducive to native image restrictions and enhanced performance.
* Service (aka Bean) - In Spring this would be referred to as a <i>bean</i> with a <i>@Service</i> annotation; These are concrete class types in your application that represents some sort of business logic.
* Scope - This refers to the cardinality of a <i>service</i> instance in your application.
* Singleton - jakarta.inject.Singleton or javax.inject.Singleton - This is the default scope for services in Helidon Injection just like it is in Spring.
* Provided - jakarta.inject.Provider or javax.inject.Provider - If the <i>scope</i> of a <i>service</i> is not <i>Singleton</i> then it is considered to be a Provided scope - and the cardinality will be ascribed to the implementation of the Provider to determine its cardinality. The provider can optionally use the <i>injection point</i> context to determine the appropriate instance and/or cardinality it provides.
* Contract - These are how a service can alias itself for injection. Contracts are typically the interface or abstract base class definitions of a <i>service</i> implementation. <i>Injection points</i> must be based upon either using a contract or service that Injection is aware of, usually through annotation processing at compile time.
* Qualifier - jakarta.inject.qualifier or javax.inject.qualifier - These are meta annotations that can be ascribed to other annotations. One built-in qualifier type is <i>@Named</i> in the same package.
* RunLevel - A way for you to describe when a service shut start up during process lifecycle. The lower the RunLevel the sooner it should start (usually based at 0).
* Dependency - An <i>injection point</i> represents what is considered to be a dependency, perhaps <i>qualified</i> or Optional, on another service or contract.  This is just another what to describe an <i>injection point</i>.
* Activator (aka ServiceProvider) - This is what is code generated by Injection to lazily activate your <i>service</i> instance(s) in the Injection <i>Services</i> registry, and it handles resolving all <i>dependencies</i> it has, along with <i>inject</i>ing the fields, methods, etc. that are required to be satisfied as part of that activation process.
* Services (aka services registry) - This is the collection of all services that are known to the JVM/runtime in Injection.
* ModuleComponent - This is where your application will "bind" services into the <i>services registry</i> - typically code generated, and typically with one module per jar/module in your application.
* Application - The fully realized set of modules and services/service providers that constitute your application, and code-generated using <b>Helidon Injection Tooling</b>.

## Getting Started
As stated in the introduction above, the Injection framework aims to provide a minimalist API implementation. As a result, it might be surprising to learn how small the actual API is for Injection - see [inject api](./inject) and the API/annotation types at [inject api](./api/src/main/java/io/helidon/inject).  If you are already familiar with [jakarta.inject](https://javadoc.io/doc/jakarta.inject/jakarta.inject-api/latest/index.html) and optionally, [jakarta.annotation](https://javadoc.io/doc/jakarta.annotation/jakarta.annotation-api/latest/jakarta.annotation/jakarta/annotation/package-summary.html) then basically you are ready to go. But if you've never used DI before then first review the basics of [dependency injection](https://en.wikipedia.org/wiki/Dependency_injection).

The best way to learn Helidon Injection is by looking at [the examples](../examples/inject). But if you want to immediately get started here are the basics steps:

1. Put these in your pom.xml or gradle.build file:
   Annotation processor dependency / path:
```
<path>
	<groupId>io.helidon.codegen</groupId>
	<artifactId>helidon-codegen-apt</artifactId>
	<version>${helidon.version}</version>
</path>
<path>
	<groupId>io.helidon.inject</groupId>
	<artifactId>helidon-inject-codegen</artifactId>
	<version>${helidon.version}</version>
</path>
```
Compile-time dependency:
```
  <dependency>
    <groupId>io.helidon.inject</groupId>
    <artifactId>helidon-inject</artifactId>
    <version>${helidon.version}</version>
  </dependency>
```

2. Write your application using w/ standard jakarta.inject.* and jakarta.annotation.* types. Again, see any of [the examples](./examples/README.md) for pointers as needed.

3. Build and run. In a DI-based framework, the frameworks "owns" the creation of services in accordance with the <i>Scope</i> each service is declared as. You therefore need to get things started by creating demand for the initial service(s) instead of ever calling <i>new</i> directly in your application code. Generally speaking, there are two such ways to get things started at runtime:

* If you know the class you want to create then look it up directly using the <i>Services</i> SPI. Here is a sample excerpt from [the book example](./examples/book/README.md):

```
    Services services = InjectionServices.realizedServices();
    // query
    ServiceProvider<MyService> serviceProvider = services.lookupFirst(MyService.class);
    // lazily activate
    MyService myLazyActivatedService = serviceProvider.get();
```

* If there are a collection of services requiring activation at startup then we recommend annotating those service implementation types with <i>RunLevel(RunLevel.STARTUP)</i> and then use code below in main() to lazily activate those services. Note that whenever List-based injection is used in Injection all services matching the injection criteria will be in the injected (and immutable) list. The list will always be in order according to the <i>Weight</i> annotation value, ranking from the highest weight to the lowest weight. If services are not weighted explicitly, then a default weight is assigned.  If the weight is the same for two services, then the secondary ordering will be based on the FN class name of the service types. While <i>Weight</i> determines list order, the <i>RunLevel</i> annotation is used to rank the startup ordering, from the lowest value to the highest value, where <i>RunLevel.STARTUP == 0</i>.  The developer is expected to activate these directly using code like the following (the <i>get()</i> lazily creates & activates the underlying service type):

```
      List<ServiceProvider<Object>> startupServices = services
              .lookup(ServiceInfoCriteria.builder().runLevel(RunLevel.STARTUP).build());
      startupServices.stream().forEach(ServiceProvider::get);
```

* If the ordering of the list of services is important, remember to use the <i>Weight</i> and/or <i>RunLevel</i> annotations to establish the priority / weighted ordering, and startup ordering.

## More Advanced Features

* Injection provides a means to generate "Activators" (the DI supporting types) for externally built modules as well as supporting javax annotated types.

* Injection offers services the ability to be intercepted. If your service contains any annotation that itself is annotated with <i>InterceptorTrigger</i> then the code generated for that service will support interception. The <i>Helidon Nima</i> project provides these types of examples.

* Injection provides meta-information for each service in its service registry, including such information as what contracts are provided by each service as well as describing its dependencies.

* Java Module System support / generation. Injection generates a proposed <i>module-info.java.inject</i> file for your module (look for module-info.java.inject under ./target/inject).

* Injection provides a maven-plugin that allows the injection graph to be (a) validated for completeness, and (b) deterministically bound to the service implementation - at compile-time. This is demonstrated in each of the examples, the result of which leads to early detection of issues at compile-time instead of at runtime as well as a marked performance enhancement.

* Testability. The [testing](./testing) module offers a set of types in order to facility for creating fake/mock services for various testing scenarios.

* Extensibility. The entire Injection Framework is designed to by extended either at a micro level (developers can override mustache/handlebar templates) to the macro level (developers can provide their own implementation of any SPI). Another example of internal extensibility is via our [config-driven](./configdriven) services.

* Determinism. Injection strives to keep your application as deterministic as possible. Dynamically adding services post-initialization will not be allowed, and will even result in a runtime exception (configurable). Any service that is "a provider" that dynamically creates a service in runtime code will issue build failures or warnings (configurable). All services are always ordered first according to <i>Weight</i> and secondarily according to type name (instead of relying on classpath ordering). Essentially, all areas of Injection attempts to keep your application as deterministic as possible at production runtime.

## Modules

* [api](./api) - the Injection API and SPI; depends on jakarta-inject and jakarta-annotations. Required as a maven compile-time dependency for runtime consumption.
* [runtime](./runtime) - contains the default runtime implementation of the Injection API/SPI; depends on the Inject API module above. Requires as a maven compile-time dependency for runtime consumption.
* [config-driven](./configdriven) - Extensions to Injection to integrate directly with the [Helidon Config](../config) subsystem.
* [tools](./tools) - contains the libraries and template-based codegen mustache resources as well as model validation tooling; depends on runtime services. Only required at build time and is not required for Injection at runtime.
* [processor](./processor) - contains the libraries for annotation processing; depends on tools. Only required at build time and is not required for Injection at runtime.
* [maven-plugin](./maven-plugin) - provides code generation Mojo wrappers for maven; depends on tools. Only required at build time and is not required for Injection at runtime. This is what would be used to create your <b>Application</b>.
* [testing](./testing) - provides testing types useful for Injection unit & integration testing.
* [tests](./tests) - used internally for testing Injection.
* [examples](../examples/inject) - providing examples for how to use Injection as well as side-by-side comparisons for Injection compared to Guice, Dagger2, Hk2, etc.

## How Injection Works

* The Injection annotation [processor](./processor) will look for standard jakarta/javax inject and jakarta/javax annotation types. When these types are found in a class that is being compiled by javac, Helidon Injection will trigger the creation of an <i>Activator</i> for that service class/type. For example, if you have a FooImpl class implementing Foo interface, and the FooImpl either contains "@Inject" or "@Singleton" then the presence of either of these annotations will trigger the creation of a FooImpl$$injectionActivator to be created. The Activator is used to (a) describe the service in terms of what service contracts (i.e., interfaces) are advertised by FooImpl - in this case <i>Foo</i> (if Foo is annotated with @Contract or if "-Aio.helidon.inject.autoAddNonContractInterfaces=true" is used at compile-time), (b) lifecycle of services including creation, calling injection-based setters, and any <i>PostConstruct or PreDestroy</i> methods.

* If one or more activators are created at compile-time, then a <i>Injection$$Module</i> is also created to aggregate the services for the given module. Below is an example if a <i>injectionModule</i> from [examples/logger](./examples/logger). At initialization time of Injection, all <i>ModuleComponent</i>s will be located using the ServiceLocator and each service will be bound into the <i>Services</i> registry.

```java
@Generated(value = "io.helidon.inject.tools.creator.impl.DefaultActivatorCreator", comments = "version = 1")
@Singleton @Named(Injection$$Module.NAME)
public class Injection$$Module implements Module {
    static final String NAME = "inject.examples.logger.common";
    
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
        binder.bind(io.helidon.inject.examples.logger.common.AnotherCommunicationMode$$injectionActivator.INSTANCE);
        binder.bind(io.helidon.inject.examples.logger.common.Communication$$injectionActivator.INSTANCE);
        binder.bind(io.helidon.inject.examples.logger.common.DefaultCommunicator$$injectionActivator.INSTANCE);
        binder.bind(io.helidon.inject.examples.logger.common.EmailCommunicationMode$$injectionActivator.INSTANCE);
        binder.bind(io.helidon.inject.examples.logger.common.ImCommunicationMode$$injectionActivator.INSTANCE);
        binder.bind(io.helidon.inject.examples.logger.common.LoggerProvider$$injectionActivator.INSTANCE);
        binder.bind(io.helidon.inject.examples.logger.common.SmsCommunicationMode$$injectionActivator.INSTANCE);
    }
}
```

And just randomly taking one of the generated <i>Activators</i>:
```java
@Generated(value = "io.helidon.inject.tools.ActivatorCreatorDefault", comments = "version=1")
public class SmsCommunicationMode$$Injection$$Activator
            extends io.helidon.inject.runtime.AbstractServiceProvider<SmsCommunicationMode> {
    private static final ServiceInfo serviceInfo =
        ServiceInfo.builder()
            .serviceTypeName(io.helidon.examples.inject.logger.common.SmsCommunicationMode.class.getName())
            .addExternalContractsImplemented(io.helidon.examples.inject.logger.common.CommunicationMode.class.getName())
            .activatorTypeName(SmsCommunicationMode$$Injection$$Activator.class.getName())
            .addScopeTypeName(jakarta.inject.Singleton.class.getName())
            .addQualifier(io.helidon.inject.DefaultQualifier.create(jakarta.inject.Named.class, "sms"))
            .build();

    /**
     * The global singleton instance for this service provider activator.
     */
    public static final SmsCommunicationMode$$Injection$$Activator INSTANCE = new SmsCommunicationMode$$Injection$$Activator();

    /**
     * Default activator constructor.
     */
    protected SmsCommunicationMode$$Injection$$Activator() {
        serviceInfo(serviceInfo);
    }

    /**
     * The service type of the managed service.
     *
     * @return the service type of the managed service
     */
    public Class<?> serviceType() {
        return io.helidon.examples.inject.logger.common.SmsCommunicationMode.class;
    }

    @Override
    public DependenciesInfo dependencies() {
        DependenciesInfo deps = Dependencies.builder(io.helidon.examples.inject.logger.common.SmsCommunicationMode.class.getName())
                .add("logger", java.util.logging.Logger.class, ElementKind.FIELD, Access.PACKAGE_PRIVATE)
                .build();
        return Dependencies.combine(super.dependencies(), deps);
    }

    @Override
    protected SmsCommunicationMode createServiceProvider(Map<String, Object> deps) { 
        return new io.helidon.examples.inject.logger.common.SmsCommunicationMode();
    }

    @Override
    protected void doInjectingFields(Object t, Map<String, Object> deps, Set<String> injections, String forServiceType) {
        super.doInjectingFields(t, deps, injections, forServiceType);
        SmsCommunicationMode target = (SmsCommunicationMode) t;
        target.logger = (java.util.logging.Logger) get(deps, "io.helidon.examples.inject.logger.common.logger");
    }

}
```

* As you can see from above example, the <i>Activators</i> are effectively managing the lifecycle and injection of your classes. These generated <i>Activator</i> types are placed in the same package as your class(es). Helidon Injection avoids reflection. This means that only public, protected, and package private injection points are supported. <b>private</b> and <b>static</b> injection points are not supported by the Injection framework.

* If an annotation in your service is meta-annotated with <i>InterceptedTrigger</i>, then an extra service type is created that will trigger interceptor service code generation. For example, if FooImpl was found to have one such annotation then FooImpl$$Injection$$Interceptor would also be created along with an activator for that interceptor. The interceptor would be created with a higher weight than your FooImpl, and would therefore be "preferred" when a single <i>@Inject</i> is used for Foo or FooImpl. If a list is injected then it would appear towards the head of the list.  Once again, all reflection is avoided in these generated classes. Any calls to Foo/FooImpl will be interceptable for any <i>Interceptor</i> that is <i>@Named</i> to handle that type name. Search the test code and Nima code for such examples as this is an advanced feature.

* The [maven-plugin](./maven-plugin) can optionally be used to avoid Injection lookup resolutions at runtime within each service activation. At startup the Injection  framework will attempt to first use the <i>Application</i> to avoid lookups. The best practice is to apply the <i>maven-plugin</i> to <i>create-application</i> on your maven assembly - this is usually your "final" application module that depends upon every other service / module in your entire deployed application. Here is the <i>Injection$$Application</i> from [examples/logger](../examples/inject/logger):

```java
@Generated({"generator=io.helidon.inject.maven.plugin.ApplicationCreatorMojo", "ver=1"})
@Singleton @Named(Injection$$Application.NAME)
public class Injection$$Application implements Application {
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
     * In module name "inject.examples.logger.common".
     * @see {@link io.helidon.inject.examples.logger.common.AnotherCommunicationMode }
     */
    binder.bindTo(io.helidon.inject.examples.logger.common.AnotherCommunicationMode$$injectionActivator.INSTANCE)
            .bind("io.helidon.inject.examples.logger.common.logger",
                  io.helidon.inject.examples.logger.common.LoggerProvider$$injectionActivator.INSTANCE)
            .commit();

    /**
     * In module name "inject.examples.logger.common".
     * @see {@link io.helidon.inject.examples.logger.common.Communication }
     */
    binder.bindTo(io.helidon.inject.examples.logger.common.Communication$$injectionActivator.INSTANCE)
            .bind("io.helidon.inject.examples.logger.common.<init>|2(1)",
                  io.helidon.inject.examples.logger.common.LoggerProvider$$injectionActivator.INSTANCE)
            .bind("io.helidon.inject.examples.logger.common.<init>|2(2)",
                  io.helidon.inject.examples.logger.common.DefaultCommunicator$$injectionActivator.INSTANCE)
            .commit();

    /**
     * In module name "inject.examples.logger.common".
     * @see {@link io.helidon.inject.examples.logger.common.DefaultCommunicator }
     */
    binder.bindTo(io.helidon.inject.examples.logger.common.DefaultCommunicator$$injectionActivator.INSTANCE)
            .bind("io.helidon.inject.examples.logger.common.sms",
                  io.helidon.inject.examples.logger.common.SmsCommunicationMode$$injectionActivator.INSTANCE)
            .bind("io.helidon.inject.examples.logger.common.email",
                  io.helidon.inject.examples.logger.common.EmailCommunicationMode$$injectionActivator.INSTANCE)
            .bind("io.helidon.inject.examples.logger.common.im",
                  io.helidon.inject.examples.logger.common.ImCommunicationMode$$imjectionActivator.INSTANCE)
            .bind("io.helidon.inject.examples.logger.common.<init>|1(1)",
                  io.helidon.inject.examples.logger.common.AnotherCommunicationMode$$injectionActivator.INSTANCE)
            .commit();

    /**
     * In module name "inject.examples.logger.common".
     * @see {@link io.helidon.inject.examples.logger.common.EmailCommunicationMode }
     */
    binder.bindTo(io.helidon.inject.examples.logger.common.EmailCommunicationMode$$injectionActivator.INSTANCE)
            .bind("io.helidon.inject.examples.logger.common.logger",
                  io.helidon.inject.examples.logger.common.LoggerProvider$$injectionActivator.INSTANCE)
            .commit();

    /**
     * In module name "inject.examples.logger.common".
     * @see {@link io.helidon.inject.examples.logger.common.ImCommunicationMode }
     */
    binder.bindTo(io.helidon.inject.examples.logger.common.ImCommunicationMode$$injectionActivator.INSTANCE)
            .bind("io.helidon.inject.examples.logger.common.logger",
                  io.helidon.inject.examples.logger.common.LoggerProvider$$injectionActivator.INSTANCE)
            .commit();

    /**
     * In module name "inject.examples.logger.common".
     * @see {@link io.helidon.inject.examples.logger.common.LoggerProvider }
     */
    binder.bindTo(io.helidon.inject.examples.logger.common.LoggerProvider$$injectionActivator.INSTANCE)
            .commit();

    /**
     * In module name "inject.examples.logger.common".
     * @see {@link io.helidon.inject.examples.logger.common.SmsCommunicationMode }
     */
    binder.bindTo(io.helidon.inject.examples.logger.common.SmsCommunicationMode$$injectionActivator.INSTANCE)
            .bind("io.helidon.inject.examples.logger.common.logger",
                  io.helidon.inject.examples.logger.common.LoggerProvider$$injectionActivator.INSTANCE)
            .commit();
  }
}
```

* The <i>maven-plugin</i> can additionally be used to create the DI supporting types (Activators, Modules, Interceptors, Applications, etc.) from introspecting an external jar - see the [examples](../examples/inject) for details.

That is basically all there is to know to get started and become productive using Injection.

## Special Notes to Providers & Contributors
Helidon Injection aims to provide an extensible, SPI-based mechanism. There are many ways Injection can be overridden, extended, or even replaced with a different implementation than what is provided out of the built-in reference implementation modules included. Of course, you can also contribute directly by becoming a committer. However, if you are looking to fork the implementation then you are strongly encouraged to honor the "spirit of this framework" and follow this as a high-level guide:

* In order to be a Injection provider implementation, the provider must supply an implementation for <i>InjectionServices</i> discoverable by the
  ServiceLoader with a higher-than-default <i>Weight</i>.
* All SPI class definitions from the <i>io.helidon.inject.spi</i> package are considered primordial and therefore should not participate in injection or conventionally be considered injectable.
* All service classes that are not targets for injection should be represented under
  /META-INF/services/<serviceClassName> to be found by the standard ServiceLocator.
* Providers are encouraged to fail-fast during compile time - this implies a sophisticated set of tooling that can and should be applied to create and validate the integrity of the dependency graph at compile time instead of at runtime.
* Providers are encouraged to avoid reflection completely at runtime.
* Providers are encouraged to advertise capabilities and configuration using <i>InjectionServicesConfig</i>.
