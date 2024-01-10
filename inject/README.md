# helidon-inject

<b>Helidon Injection</b> is an optional feature in Helidon. At its core it provides these main features:

1. A service registry. The service registry holds services and service providers (services that can produce other services). Each service provider in the registry advertises its meta-information for what each service provides, and what it requires in the way of dependencies.

2. A lifecycle engine. Each service provider in the registry remains dormant until there is a "demand for activation". This "demand for activation" is also known as "lazy activation" that can come in different ways. One way is to simply "get" a service (or services) from the registry that matches the meta-information criteria you provide programmatically. If the service (or services) need other services as part of the activation then those services are chain activated, recursively. Helidon Injection provides graceful startup and shutdown at a micro service-level as well as at a macro application level for all services in the service registry.

3. Integrations and Extensibility. More will be mentioned on this later.

Over the foundation of these three main features of "services registry", "lifecycle", and "extensibility" there are a number of other tooling and layers that are delivered from various Injection submodules that provide the following additional features and benefits:

1. A minimalist, compile-time generated dependency injection framework that is free from reflection, and compliant to the JSR-330 injection specification. Compile-time source code generation has a number of advantages, including: (a) pre-runtime validation of the DI model, (b) visibility into your application by providing "less magic", which in turn fosters understandability and debug-ability of your application, (c) deterministic behavior (instead of depending on reflection and classpath ordering, etc.) and (c) performance, since binding the model at compile-time is more efficient than computing it at runtime. Helidon Injection (through its tooling) provides you with a mix of declarative and programmatic ways to build your application. It doesn't have to be just one or the other like other popular frameworks in use today require. Inspiration for Helidon Injection, however, did come from many libraries and frameworks that came before it (e.g., Jakarta Hk2, Google Guice, Spring, CDI, and even OSGi). Foundationally, Injection provides a way to develop declarative code using simple annotation types (both Helidon specific - see module `helidon-inject-service`, and through extensions for code generation also Jakarta Inject/Annotatation (both in `javax` and `jakarta` namespaces).

2. Integrations. Blending services from different providers (e.g., Helidon services like WebServer, your application service, 3rd party service, etc.) becomes natural in the Injection framework, and enables you to build fully-integrated, feature-rich applications more easily.

3. Extensibility. At the micro level developers can provide their own extensions for things like code generation. At a macro level (and post the initial release), Injection will be providing a foundation to extend your Guice, Spring, Hk2, CDI, <fill-in the blank> application naturally into one application runtime. The Helidon team fields a number of support questions that involve this area involving "battling DI frameworks" like CDI w/ Hk2. In time, Helidon Injection aims to smooth out this area through the integrations and extensibility features that it will be providing.

4. Interception. Annotations are provided, that in conjunction with Helidon Injection's code-generation annotation processors, allow services in the service registry to support interception and decoration patterns - without the use of reflection at runtime, which is conducive to native image.

The Helidon Team believes that the above features help developers achieve the following goals:
* More IoC options. With Helidon Injection... developers can choose to use an imperative coding style or a declarative IoC style previously only available with CDI using Helidon MP.
* Compile-time benefits. With Injection... developers can decide to use compile-time code generation into their build process, thereby statically and deterministically wiring their injection model while still enjoying the benefits of a declarative approach for writing their application. Added to this, all code-generated artifacts are in source form instead of bytecode thereby making your application more readable, understandable, consistent, and debuggable. Furthermore, DI model inconsistencies can be found during compile-time instead of at runtime.
* Improved performance. Pushing more into compile-time helps reduce what otherwise would need to occur (often times via reflection) to built/compile-time processing. Native code is generated that is further optimized by the compiler. Additionally, with lazy activation of services, only what is needed is activated. Anything not used may be in the classpath is available, but unless and until there is demand for those services they remain dormant. You control the lifecycle in your application code.
* Additional lifecycle options. Injection can handle micro, service-level activations for your services, as well as offer controlled shutdown if desired.

Many DI frameworks start simple and over time become bloated with "bells and whistle" type features - the majority of which most developers don't need and will never use; especially in today's world of microservices where the application scope is the JVM process itself.

***
The <b>Helidon Injection Framework</b> is a reset back to basics, and perfect for such use cases requiring minimalism but yet still be extensible. <i>Application Scope</i> == <i>Singleton Scope</i> in a microservices world.
***

Request scope is an optional feature of the registry, as it brings complexity. Even if used, we do not support proxying of services. 

## Terminology
* DI - Dependency Injection.
* Inject - The assignment of a <i>service</i> instance to a field or method setter that has been annotated with <i>@Inject</i> - also referred to as an injection point. In Spring this would be referred to as 'Autowired'.
* Injection Plan - The act of determining how your application will resolve each <i>injection point</i>. In Injection this can optionally be performed at compile-time. But even when the injection plan is deferred to runtime it is resolved without using reflection, and is therefore conducive to native image restrictions and enhanced performance.
* Service (aka Bean) - In Spring this would be referred to as a <i>bean</i> with a <i>@Service</i> annotation; These are concrete class types in your application that represents some sort of business logic.
* Scope - This refers to the cardinality of a <i>service</i> instance in your application.
* Singleton - This is the default scope for services in Helidon Injection just like it is in Spring.
* Provided - If the <i>scope</i> of a <i>service</i> is not <i>Singleton</i> then it is considered to be a provider scope - and the cardinality will be ascribed to the implementation of the Provider to determine its cardinality. The provider can optionally use the <i>injection point</i> context to determine the appropriate instance and/or cardinality it provides.
* Contract - These are how a service can alias itself for injection. Contracts are typically the interface or abstract base class definitions of a <i>service</i> implementation. <i>Injection points</i> must be based upon either using a contract or service that Injection is aware of, usually through annotation processing at compile time.
* Qualifier - These are meta annotations that can be ascribed to other annotations. One built-in qualifier type is <i>@Injection.Named</i>
* RunLevel - A way for you to describe when a service should start up during process lifecycle. The lower the RunLevel the sooner it should start (usually based at 0).
* Dependency - An <i>injection point</i> represents what is considered to be a dependency, perhaps <i>qualified</i> or Optional, on another service or contract.  This is just another way to describe an <i>injection point</i>.
* Service Descriptor - This is what is code generated by Injection to provide metadata about the service, such as its <i>scope</i>, <i>dependencies</i> etc. In addition it provides generated code to instantiate, inject, post-construct, and pre-destroy the service, so we can fully avoid using reflection 
* Services (aka services registry) - This is the collection of all services that are known to Injection.
* ModuleComponent - This is where your application will "bind" services into the <i>services registry</i> - typically code generated, and typically with one module per jar/module in your application.
* Application - The fully realized set of modules and services/service providers that constitute your application, and code-generated using <b>Helidon Injection Tooling</b>.

## Getting Started
As stated in the introduction above, the Injection framework aims to provide a minimalist API implementation. As a result, it might be surprising to learn how small the actual API is for Injection - see [inject-service](./service). If you are already familiar with [jakarta.inject](https://javadoc.io/doc/jakarta.inject/jakarta.inject-api/latest/index.html) and optionally, [jakarta.annotation](https://javadoc.io/doc/jakarta.annotation/jakarta.annotation-api/latest/jakarta.annotation/jakarta/annotation/package-summary.html) then basically you are ready to go. But if you've never used DI before then first review the basics of [dependency injection](https://en.wikipedia.org/wiki/Dependency_injection).

The best way to learn Helidon Injection is by looking at [the examples](../examples/inject). But if you want to immediately get started here are the basics steps:

1. Put these in your Maven pom.xml or Gradle gradle.build file (pom format used here):
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
    <artifactId>helidon-inject-service</artifactId>
    <version>${helidon.version}</version>
  </dependency>
```

Run-time dependency (or programmatic approach dependency):
```
  <dependency>
    <groupId>io.helidon.inject</groupId>
    <artifactId>helidon-inject</artifactId>
    <version>${helidon.version}</version>
  </dependency>
```

2. Write your application using annotations in `io.helidon.inject.service.Injection`. Again, see any of [the examples](./examples/README.md) for pointers as needed.

3. Build and run. In a DI-based framework, the frameworks "owns" the creation of services in accordance with the <i>Scope</i> each service is declared as. You therefore need to get things started by creating demand for the initial service(s) instead of ever calling <i>new</i> directly in your application code. Generally speaking, there are two such ways to get things started at runtime:

* If you know the class you want to create then look it up directly using the <i>Services</i> API. A short example:

```
    InjectionServices registryManager = InjectionServices.create(); // create new service registry manager
    Services services = registryManager.services(); // get the service registry
    // query
    Supplier<MyService> serviceSupplier = services.supply(MyService.class);
    // lazily activate
    MyService myLazyActivatedService = serviceSupplier.get();
```

* If there are a collection of services requiring activation at startup then we recommend annotating those service implementation types with <i>RunLevel(RunLevel.STARTUP)</i> and then use code below in main() to lazily activate those services. Note that whenever List-based injection is used in Injection all services matching the injection criteria will be in the injected (and immutable) list. The list will always be in order according to the <i>Weight</i> annotation value, ranking from the highest weight to the lowest weight. If services are not weighted explicitly, then a default weight is assigned.  If the weight is the same for two services, then the secondary ordering will be based on the FN class name of the service types. While <i>Weight</i> determines list order, the <i>RunLevel</i> annotation is used to rank the startup ordering, from the lowest value to the highest value, where <i>RunLevel.STARTUP == 0</i>.  The developer is expected to activate these directly using code like the following (the <i>get()</i> lazily creates & activates the underlying service type):

```
      Supplier<List<Object>> startupServices = services
              .lookup(Lookup.builder().runLevel(RunLevel.STARTUP).build());
      startupServices.get();
```

* If the ordering of the list of services is important, remember to use the <i>Weight</i> and/or <i>RunLevel</i> annotations to establish the priority / weighted ordering, and startup ordering.

## More Advanced Features

* Injection provides a means to generate "ServiceDescriptors" (the DI supporting types) for externally built modules as well as supporting Jakarta `jakarta` and `javax` annotated types.

* Injection offers services the ability to be intercepted. If your service contains any annotation that itself is annotated with <i>InterceptorTrigger</i> then the code generated for that service will support interception.

* Injection provides meta-information for each service in its service registry, including such information as what contracts are provided by each service as well as describing its dependencies.

* Java Module System support. Injection will guide you (through errors during compilation, as we cannot modify existing sources from annotation processors) on how to update your `module-info.java` to correctly support the module and application

* Injection provides a maven-plugin that allows the injection graph to be (a) validated for completeness, and (b) deterministically bound to the service implementation - at compile-time. This is demonstrated in each of the examples, the result of which leads to early detection of issues at compile-time instead of at runtime as well as a marked performance enhancement.

* Testability. The [testing](./testing) module offers a set of types in order to facility for creating fake/mock services for various testing scenarios.

* Extensibility. The entire Injection Framework is designed to by extended.

* Determinism. Injection strives to keep your application as deterministic as possible. Dynamically adding services post-initialization is not be allowed, and will even result in a runtime exception. Any service that is "a provider" that dynamically creates a service in runtime code will issue build failures or warnings (configurable). All services are always ordered first according to <i>Weight</i> and secondarily according to type name (instead of relying on classpath ordering). Essentially, all areas of Injection attempts to keep your application as deterministic as possible at production runtime.

## Modules

* [service](./service) - types required to declare a service and compile its code generated descriptor, intercepted type, or config bean (depending on annotations chosen)
* [inject](./inject) - contains the implementation of the service registry
* [codegen](./codegen) - contains the libraries for annotation processing; Only required at build time and is not required for Injection at runtime.
* [maven-plugin](./maven-plugin) - provides code generation Mojo wrappers for maven; depends on tools. Only required at build time and is not required for Injection at runtime. This is what would be used to create your <b>Application</b>.
* [testing](./testing) - provides testing types useful for Injection unit & integration testing.
* [tests](./tests) - used internally for testing Injection.
* [examples](../examples/inject) - providing examples for how to use Injection

## How Injection Works

* The Injection annotation [codegen](./codegen) will look for annotations in `helidon-inject-services` module. When these types are found in a class that is being compiled by javac, Helidon Injection will trigger the creation of a <i>ServiceDescriptor</i> for that service class/type. For example, if you have a FooImpl class implementing Foo interface, and the FooImpl either contains `@Injection.Inject` or `@Injection.Singleton` then the presence of either of these annotations will trigger the creation of a `FooImpl__ServiceDescriptor` to be created. The descriptor is used to (a) describe the service in terms of what service contracts (i.e., interfaces) are advertised by FooImpl - in this case <i>Foo</i> (if Foo is annotated with @Contract or if "-Ahelidon.inject.autoAddNonContractInterfaces=true" is used at compile-time), (b) generated code to instantiate the service, inject its injection points, post-construct it, and pre-destroy it.

* If one or more descriptors are created at compile-time, then a `Injection__Module` is also created to aggregate the services for the given module. Below is an example if a <i>injectionModule</i>. At initialization time of Injection, all <i>ModuleComponent</i>s will be located using the ServiceLocator and each service will be bound into the <i>Services</i> registry.

```java
@Generated(value = "io.helidon.inject.codegen.ModuleComponentHandler", trigger = "io.helidon.inject.codegen.ModuleComponentHandler")
public final class Injection__Module implements ModuleComponent {
    static final String NAME = "inject.examples.logger.common";

    static final String NAME = "io.helidon.config";

    /**
     * Constructor for ServiceLoader.
     *
     * @deprecated for use by Java ServiceLoader, do not use directly
     */
    @Deprecated
    public Injection__Module() {
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String toString() {
        return NAME + ":" + getClass().getName();
    }

    @Override
    public void configure(ServiceBinder binder) {
        binder.bind(ConfigProducer__ServiceDescriptor.INSTANCE);
    }
}
```

And the relevant service descriptor for `ConfigProducer` (this is an existing type in `helidon-config` at the
time this document is written):

```java
/**
 * Service descriptor for {@link io.helidon.config.ConfigProducer}.
 *
 * @param <T> type of the service, for extensibility
 */
@Generated(value = "io.helidon.inject.codegen.InjectionExtension", trigger = "io.helidon.config.ConfigProducer")
public class ConfigProducer__ServiceDescriptor<T extends io.helidon.config.ConfigProducer> implements ServiceDescriptor<T> {

    /**
     * Global singleton instance for this descriptor.
     */
    public static final ConfigProducer__ServiceDescriptor<ConfigProducer> INSTANCE = new ConfigProducer__ServiceDescriptor<>();
    private static final TypeName SERVICE_TYPE = TypeName.create("io.helidon.config.ConfigProducer");
    private static final TypeName INFO_TYPE = TypeName.create("io.helidon.config.ConfigProducer__ServiceDescriptor");
    private static final TypeName TYPE_0 = TypeName.create("java.util.function.Supplier<java.util.List<io.helidon.config.spi.ConfigSource>>");
    private static final TypeName TYPE_1 = TypeName.create("io.helidon.config.spi.ConfigSource");
    private static final Set<TypeName> CONTRACTS = Set.of(TypeName.create("io.helidon.common.config.Config"));
    private static final Set<Qualifier> QUALIFIERS = Set.of();
    private static final TypeName SCOPE = TypeName.create("io.helidon.inject.service.Injection.Singleton");
    /**
     * Injection point dependency for ConfigProducer(Supplier configSources), parameter configSources.
     */
    public static final Ip IP_PARAM_0 = Ip.builder()
            .typeName(TYPE_0)
            .name("configSources")
            .service(SERVICE_TYPE)
            .descriptor(INFO_TYPE)
            .field("IP_PARAM_0")
            .contract(TYPE_1)
            .build();
    private static final List<Ip> DEPENDENCIES = List.of(IP_PARAM_0);

    /**
     * Constructor with no side effects
     */
    protected ConfigProducer__ServiceDescriptor() {
    }

    @Override
    public TypeName serviceType() {
        return SERVICE_TYPE;
    }

    @Override
    public TypeName infoType() {
        return INFO_TYPE;
    }

    @Override
    public Set<TypeName> contracts() {
        return CONTRACTS;
    }

    @Override
    public List<Ip> dependencies() {
        return DEPENDENCIES;
    }

    @Override
    public ConfigProducer instantiate(InjectionContext ctx__helidonInject, InterceptionMetadata interceptMeta__helidonInject) {
        Supplier<List<ConfigSource>> configSources = ctx__helidonInject.param(IP_PARAM_0);

        return new ConfigProducer(configSources);
    }

    @Override
    public TypeName scope() {
        return SCOPE;
    }

}
```

* As you can see from above example, the <i>ServiceDescriptors</i> are describing the service, and providing calls, so no reflection is required at runtime. types are placed in the same package as your class(es). Helidon Injection avoids reflection. This means that only public, protected, and package private injection points are supported. <b>private</b> and <b>static</b> injection points are not supported by the Injection framework.

* If an annotation in your service is meta-annotated with <i>InterceptedTrigger</i>, then an extra service type is created that will trigger interceptor service code generation. For example, if FooImpl was found to have one such annotation then FooImpl__Intercepted would also be created along with a descriptor for that interceptor. The intercepted type would be used by the generated service descriptor when a new instance is created, and the intercepted type takes care of all method interceptions.  If constructor should be intercepted, it is handled by the service descriptor directly.

* The [maven-plugin](./maven-plugin) can optionally be used to avoid Injection lookup resolutions at runtime within each service activation. At startup the Injection  framework will attempt to first use the <i>Application</i> to avoid lookups. The best practice is to apply the <i>maven-plugin</i> to <i>create-application</i> on your maven assembly - this is usually your "final" application module that depends upon every other service / module in your entire deployed application. Here is the <i>Injection__Application</i> from [examples/inject/application](../examples/inject/application):

```java
@Generated(value = "io.helidon.inject.maven.plugin.ApplicationCreator", trigger = "io.helidon.inject.maven.plugin.ApplicationCreator")
public class Injection__Application implements Application {

    /**
     * Constructor only for use by {@link java.util.ServiceLoader}.
     *
     * @deprecated to be used by Java Service Loader only
     */
    @Deprecated
    public Injection__Application() {
    }

    @Override
    public String name() {
        return "unnamed/io.helidon.examples.inject.application";
    }

    @Override
    public void configure(ServiceInjectionPlanBinder binder) {
        binder.interceptors();

        binder.bindTo(ConfigProducer__ServiceDescriptor.INSTANCE)
                .bindSupplierOfList(ConfigProducer__ServiceDescriptor.IP_PARAM_0)
                .commit();

        binder.bindTo(ToolBox__ServiceDescriptor.INSTANCE)
                .bindSupplierOfList(ToolBox__ServiceDescriptor.IP_PARAM_0,
                                    NailGun__ServiceDescriptor.INSTANCE,
                                    Hammer__ServiceDescriptor.INSTANCE,
                                    AngleGrinderSaw__ServiceDescriptor.INSTANCE,
                                    CircularSaw__ServiceDescriptor.INSTANCE,
                                    HandSaw__ServiceDescriptor.INSTANCE,
                                    TableSaw__ServiceDescriptor.INSTANCE,
                                    BigHammer__ServiceDescriptor.INSTANCE,
                                    LittleHammer__ServiceDescriptor.INSTANCE,
                                    Drill__ServiceDescriptor.INSTANCE)
                .bindOptional(ToolBox__ServiceDescriptor.IP_PARAM_1, LittleHammer__ServiceDescriptor.INSTANCE)
                .bind(ToolBox__ServiceDescriptor.IP_PARAM_2, BigHammer__ServiceDescriptor.INSTANCE)
                .commit();

        binder.bindTo(Drill__ServiceDescriptor.INSTANCE)
                .bind(Drill__ServiceDescriptor.IP_PARAM_0, DrillConfig__ConfigBean__ServiceDescriptor.INSTANCE)
                .commit();

        binder.bindTo(DrillConfig__ConfigBean__ServiceDescriptor.INSTANCE)
                .bind(DrillConfig__ConfigBean__ServiceDescriptor.IP_PARAM_0, ConfigProducer__ServiceDescriptor.INSTANCE)
                .commit();

        binder.bindTo(AngleGrinderSaw__ServiceDescriptor.INSTANCE)
                .bindOptional(AngleGrinderSaw__ServiceDescriptor.IP_PARAM_0, BladeProvider__ServiceDescriptor.INSTANCE)
                .commit();

        binder.bindTo(CircularSaw__ServiceDescriptor.INSTANCE)
                .bindOptional(CircularSaw__ServiceDescriptor.IP_PARAM_0, BladeProvider__ServiceDescriptor.INSTANCE)
                .commit();

        binder.bindTo(HandSaw__ServiceDescriptor.INSTANCE)
                .bindOptional(HandSaw__ServiceDescriptor.IP_PARAM_0, BladeProvider__ServiceDescriptor.INSTANCE)
                .commit();

        binder.bindTo(NailGun__ServiceDescriptor.INSTANCE)
                .bindSupplier(NailGun__ServiceDescriptor.IP_PARAM_0, NailProvider__ServiceDescriptor.INSTANCE)
                .commit();

        binder.bindTo(TableSaw__ServiceDescriptor.INSTANCE)
                .bindOptional(TableSaw__ServiceDescriptor.IP_PARAM_0, BladeProvider__ServiceDescriptor.INSTANCE)
                .commit();

        binder.bindTo(RequestScopeControlImpl__ServiceDescriptor.INSTANCE)
                .bind(RequestScopeControlImpl__ServiceDescriptor.IP_PARAM_0, ServiceSpiImpl__ServiceDescriptor.INSTANCE)
                .commit();

        binder.bindTo(ServiceSpiImpl__ServiceDescriptor.INSTANCE)
                .bind(ServiceSpiImpl__ServiceDescriptor.IP_PARAM_0, Services__ServiceDescriptor.INSTANCE)
                .commit();
    }
}
```

* The <i>maven-plugin</i> can additionally be used to create the DI supporting types (ServiceDescriptor, Modules, Interceptors, Applications, etc.) from introspecting an external jar. This is for example used in our own [TCK tests for JSR-330](tests/tck-jsr330)

That is basically all there is to know to get started and become productive using Injection.

## Special Notes to Providers & Contributors
Helidon Injection aims to provide an extensible, mechanism. There are many ways Injection can be overridden, or extended. Of course, you can also contribute directly by becoming a committer. However, if you are looking to fork the implementation then you are strongly encouraged to honor the "spirit of this framework" and follow this as a high-level guide:

* Providers are encouraged to fail-fast during compile time - this implies a sophisticated set of tooling that can and should be applied to create and validate the integrity of the dependency graph at compile time instead of at runtime.
* Providers are encouraged to avoid reflection completely at runtime.
