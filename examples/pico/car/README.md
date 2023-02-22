# pico-examples-car

This example compares Pico to Google's Dagger 2. It was taken from an example found on the internet (see references below). The example is fairly trivial, but it is sufficient to compare the similarities between the two.

[dagger2](dagger2) contains the Dagger2 application module.
[pico](pico) contains the Pico application module.

Unlike the [logger](../logger) example, this example replicates the entire set of classes for each application module. This was done for a few reasons. The primary reason is that while Pico offers the ability to generate the DI supporting module on an external jar (as the <i>logger example</i> demonstrates), Dagger does not provide such an option - the DI module for dagger
only provides for an annotation processor mechanism, thereby requiring the developer to inject the Dagger annotation processor
into the build of the project to generate the DI module at compilation time. The other reason why this was done was to demonstrate
a few variations in the way developers can use Pico for more complicated use cases.

Review the code to see the similarities and differences. Note that the generated source code is found under
"./target/generated-sources" for each sub application module. Summaries of similarities and differences are listed below.

# Building and Running
```
> mvn clean install
> ./run.sh
```

# Notable

1. Pico supports jakarta.inject as well as javax.inject packaging. Dagger 2 (at the time of this writing - see https://github.com/google/dagger/issues/2058) only supports javax.inject. This example uses <i>javax.inject</i> for the Dagger app, and uses <i>jakarta.inject</i> for the Pico app. Functionally, however, both are the same.

2. The API programming model between Pico and Dagger is fairly different. Pico strives to closely follow the jsr-330 specification and relies heavily on annotation processing to generate *all* of the supporting DI classes, and those classes are hidden as an implementation detail not directly exposed to the (typical) developer.  This can be seen by observing Dagger's [VehiclesComponent.java](./dagger2/src/main/java/io/helidon/examples/pico/car/dagger2/VehiclesComponent.java) and [VehiclesModule.java](./dagger2/src/main/java/io/helidon/examples/pico/car/dagger2/VehiclesModule.java) classes - notice the imports as well as the code the developer is expected to write.

On the Pico side, you will notice that the only <i>#import</i> of Pico is found on the [Vehicle.java](./pico/src/main/java/io/helidon/examples/pico/car/pico/Vehicle.java) class. The <i>@Contract</i> annotation is used to demarcate the <i>Vehcile</i> iterface that <i>Car</i> implements/advertises. All the other imports are using standard javax/jakarta annotations. Pico actually offers an option to advertise all interfaces as contracts (see the pom.xml snippet below). Turning on this switch will allow the <i>@Contract</i> to be removed as well, thereby using 100% standard javax/jakarta types.

```
                        <!-- turn this on to avoid the use of @Contract in the example --> 
<!--                    <compilerArgs>-->
<!--                        <arg>-Apico.autoAddNonContractInterfaces=true</arg>-->
<!--                    </compilerArgs>-->
```

There are a few other "forced" examples under Pico which demonstrate additional options available. For example, the [BrandProvider.java](./pico/src/main/java/io/helidon/examples/pico/car/pico/BrandProvider.java) class is the standard means to produce an instance of a <i>Brand</i>. The implementation can choose the cardinality of the instances created.  At times, it might be convenient to "know" the injection point consumer requesting the <i>Brand</i> instance, in order to change the cardinality or somehow make it dependent in scope.  This option is demonstrated in the [BrandProvider.java](./pico/src/main/java/io/helidon/examples/pico/car/pico/EngineProvider.java) class.

3. Both Dagger and Pico are using compile-time to generate the DI supporting classes as mentioned above. The code generated between the two, however is a little different. Let's have a closer look:

The Dagger generated <i>Car_Factory</i>:
```java
@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class Car_Factory implements Factory<Car> {
  private final Provider<Engine> engineProvider;

  private final Provider<Brand> brandProvider;

  public Car_Factory(Provider<Engine> engineProvider, Provider<Brand> brandProvider) {
    this.engineProvider = engineProvider;
    this.brandProvider = brandProvider;
  }

  @Override
  public Car get() {
    return newInstance(engineProvider.get(), brandProvider.get());
  }

  public static Car_Factory create(Provider<Engine> engineProvider, Provider<Brand> brandProvider) {
    return new Car_Factory(engineProvider, brandProvider);
  }

  public static Car newInstance(Engine engine, Brand brand) {
    return new Car(engine, brand);
  }
}
```

The Pico generated <i>Car$$picoActivator</i>:
```java
/**
 * Activator for {@link io.helidon.examples.pico.car.pico.Car}.
 */
// @Singleton 
@SuppressWarnings("unchecked")
@Generated(value = "io.helidon.pico.tools.DefaultActivatorCreator", comments = "version=1")
public class Car$$picoActivator
        extends io.helidon.pico.services.AbstractServiceProvider<Car> {
   private static final DefaultServiceInfo serviceInfo =
           DefaultServiceInfo.builder()
                   .serviceTypeName(io.helidon.examples.pico.car.pico.Car.class.getName())
                   .addContractsImplemented(io.helidon.examples.pico.car.pico.Vehicle.class.getName())
                   .activatorTypeName(Car$$picoActivator.class.getName())
                   .addScopeTypeName(jakarta.inject.Singleton.class.getName())
                   .build();

   /**
    * The global singleton instance for this service provider activator.
    */
   public static final Car$$picoActivator INSTANCE = new Car$$picoActivator();

   /**
    * Default activator constructor.
    */
   protected Car$$picoActivator() {
      serviceInfo(serviceInfo);
   }

   /**
    * The service type of the managed service.
    *
    * @return the service type of the managed service
    */
   public Class<?> serviceType() {
      return io.helidon.examples.pico.car.pico.Car.class;
   }

   @Override
   public DependenciesInfo dependencies() {
      DependenciesInfo deps = Dependencies.builder(io.helidon.examples.pico.car.pico.Car.class.getName())
              .add(CONSTRUCTOR, io.helidon.examples.pico.car.pico.Engine.class, ElementKind.CONSTRUCTOR, 2, Access.PUBLIC).elemOffset(1)
              .add(CONSTRUCTOR, io.helidon.examples.pico.car.pico.Brand.class, ElementKind.CONSTRUCTOR, 2, Access.PUBLIC).elemOffset(2)
              .build();
      return Dependencies.combine(super.dependencies(), deps);
   }

   @Override
   protected Car createServiceProvider(Map<String, Object> deps) {
      io.helidon.examples.pico.car.pico.Engine c1 = (io.helidon.examples.pico.car.pico.Engine) get(deps, "io.helidon.examples.pico.car.pico.<init>|2(1)");
      io.helidon.examples.pico.car.pico.Brand c2 = (io.helidon.examples.pico.car.pico.Brand) get(deps, "io.helidon.examples.pico.car.pico.<init>|2(2)");
      return new io.helidon.examples.pico.car.pico.Car(c1, c2);
   }

}
```

Here is the main difference:

* Pico attempts to model each service in terms of the contracts/interfaces each service offers, as well as the dependencies (other contracts/interfaces) that it requires. A more elaborate dependency model would additionally include the qualifiers (such as @Named), whether the services are optional, list-based, etc. This model extends down to mention each element (for methods or constructors for example). All of this is generated at compile-time.  In this way the Pico <i>Services</i> registry has knowledge of every available service and what it offers and what it requires.  These services are left to be lazily activated on-demand.

4. Pico provides the ability (as demonstrated in the [pom.xml](./pico/pom.xml)) to take the injection model, analyze and validate it, and ultimately bind to the <b>final</b> injection model at assembly time. Using this option provides several key benefits including deterministic behavior, speed & performance enhancements, and helps to ensure the completeness & validity of the entire application's dependency graph at compile time.  When this option is applied the <i>picoApplication</i> is generated. Here is what it looks like for this example:

```java
@Generated(value = "io.helidon.pico.maven.plugin.ApplicationCreatorMojo", comments = "version=1")
@Singleton @Named(picoApplication.NAME)
public class picoApplication implements Application {
   static final String NAME = "io.helidon.examples.pico.car.pico";

   @Override
   public Optional<String> named() {
      return Optional.of(NAME);
   }

   @Override
   public String toString() {
      return NAME + ":" + getClass().getName();
   }

   @Override
   public void configure(ServiceInjectionPlanBinder binder) {
      /**
       * In module name "io.helidon.examples.pico.car.pico".
       * @see {@link io.helidon.examples.pico.car.pico.BrandProvider }
       */
      binder.bindTo(io.helidon.examples.pico.car.pico.BrandProvider$$picoActivator.INSTANCE)
              .commit();

      /**
       * In module name "io.helidon.examples.pico.car.pico".
       * @see {@link io.helidon.examples.pico.car.pico.Car }
       */
      binder.bindTo(io.helidon.examples.pico.car.pico.Car$$picoActivator.INSTANCE)
              .bind("io.helidon.examples.pico.car.pico.<init>|2(1)",
                    io.helidon.examples.pico.car.pico.EngineProvider$$picoActivator.INSTANCE)
              .bind("io.helidon.examples.pico.car.pico.<init>|2(2)",
                    io.helidon.examples.pico.car.pico.BrandProvider$$picoActivator.INSTANCE)
              .commit();

      /**
       * In module name "io.helidon.examples.pico.car.pico".
       * @see {@link io.helidon.examples.pico.car.pico.EngineProvider }
       */
      binder.bindTo(io.helidon.examples.pico.car.pico.EngineProvider$$picoActivator.INSTANCE)
              .commit();

   }
}
```

At initialization time (and using the default configuration) Pico will use the service loader to attempt to find the <i>Application<i> instance and use that instead of resolving the dependency graph at runtime. Generating the application is optional but recommended for production scenarios.

5. The Dagger application is considerably smaller in terms of disk and memory footprints. This makes sense considering that the primary driver for Dagger 2 is to be consumed by Android developers who naturally require a liter footprint. Pico, while still small and lite compared to many other options on the i-net, offers more features including: interceptor/interception capabilities, flexible service registry search & resolution semantics, service meta information as described above, lazy activation, circular dependency detection, extensibility, configuration, etc.  The default configuration assumes a production use case where the services registry is assumed to be non-dynamic/static in nature.

On the topic of extensibility - Pico is centered around extensibility of its tooling. The templates use replaceable [handlebars](https://github.com/jknack/handlebars.java), and the generators use service loader. Anyone can either override Pico's reference implementation, or else write an entirely new implementation based upon the API & SPI that Pico provides. 

6. Pico requires less coding as compared to Dagger. In this example the <i>BrandProvider<i> and <i>EngineProvider<i> were contrived in order to demonstrate a nuances of the approach. Generally speaking most of the time the <i>@Singleton</i> annotation (or lack thereof) is all that is needed, depending upon the injection scope required. 

7. Pico offers lifecycle support (see jakarta.annotation.@PostConstruct, jakarta.annotation.@PreDestroy, Pico's @RunLevel
   annotations and PicoServices#shutdown()).  

8. Pico generates a suggested <i>module-info.java</i> based upon analysis of your injection/dependency model (see ./target/classes/module-info.java.pico).

```./target/classes/module-info.java.pico
// @Generated(value = "io.helidon.pico.tools.DefaultActivatorCreator", comments = "version=1")
module io.helidon.examples.pico.car.pico {
    exports io.helidon.examples.pico.car.pico;
    // pico module - Generated(value = "io.helidon.pico.tools.DefaultActivatorCreator", comments = "version=1")
    provides io.helidon.pico.Module with io.helidon.examples.pico.car.pico.picoModule;
    // pico external contract usage - Generated(value = "io.helidon.pico.tools.DefaultActivatorCreator", comments = "version=1")
    uses jakarta.inject.Provider;
    uses io.helidon.pico.InjectionPointProvider;
    // pico services - Generated(value = "io.helidon.pico.tools.DefaultActivatorCreator", comments = "version=1")
    requires transitive io.helidon.pico;
}
```

9. Pico can optionally generate the activators (i.e., the DI supporting classes) on an external jar module. See the [logger](../logger) example for details.

# References
* https://www.baeldung.com/dagger-2
