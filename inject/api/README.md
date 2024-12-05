This module contains all the API and SPI types that are applicable to a Helidon Injection based application.

The API can logically be broken up into two categories - declarative types and imperative/programmatic types. The declarative form is the most common approach for using Service.

The declarative API is small and based upon annotations. This is because most of the supporting annotation types actually come directly from both of the standard javax/jakarta inject and javax/jakarta annotation modules. These standard annotations are supplemented with these proprietary annotation types offered here from Injection:

* [@Contract](src/main/java/io/helidon/inject/api/Contract.java)
* [@ExteralContracts](src/main/java/io/helidon/inject/api/ExternalContracts.java)
* [@RunLevel](src/main/java/io/helidon/inject/api/RunLevel.java)

The programmatic API is typically used to manually lookup and activate services (those that are typically annotated with <i>@jakarta.inject.Singleton</i> for example) directly. The main entry points for programmatic access can start from one of these two types:

* [InjectionServices](src/main/java/io/helidon/inject/api/InjectionServices.java)
* [Services](src/main/java/io/helidon/inject/api/Services.java)

Note that this module only contains the common types for a Helidon Injection service provider. See the [runtime](../runtime) module for the default reference implementation for this API / SPI.

## Declaring a Service

### Singleton
In this example the service is declared to be one-per JVM. Also note that <i>ApplicationScoped</i> is effectively the same as <i>Singleton</i> scoped services in a (micro)services framework such as Helidon.

```java
@jakarta.inject.Singleton
class MySingletonService implements ServiceContract {
}

```

Also note that in the above example <i>ServiceContract</i> is typically the <i>Contract</i> or <i>ExternalContract</i> definition - which is a way to signify lookup capabilities within the <i>Services</i> registry.

### Provider
Provider extends the <i>Singleton</i> to delegate dynamic behavior to service creation. In other frameworks this would typically be called a <i>Factory</i>, <i>Producer</i>, or <i>PerLookup</i>.

```java
@jakarta.inject.Singleton
class MySingletonProvider implements jakarta.inject.Provider<ServiceContract> {
    @Override
    ServiceContract get() {
        ...
    }
}
```

Helidon Injection delegates the cardinality of to the provider implementation for which instance to return to the caller. However, note that the instances returned are not "owned" by the Injection framework - unless those instances are looked up out of the <i>Services</i> registry.

### InjectionPointProvider
Here the standard <i>jakarta.inject.Provider<></i> from above is extended to support contextual knowledge of "who is asking" to be injected with the service. In this way the provider implementation can provide the "right" instance based upon the caller's context.

```java
@Singleton
@Named("*")
public class BladeProvider implements InjectionPointProvider<AbstractBlade> {
    @Override
    public Optional<AbstractBlade> first(
            ContextualServiceQuery query) {
        ServiceInfoCriteria criteria = query.serviceInfoCriteria();

        AbstractBlade blade;
        if (criteria.qualifiers().contains(all) || criteria.qualifiers().contains(coarse)) {
            blade = new CoarseBlade();
        } else if (criteria.qualifiers().contains(fine)) {
            blade = new FineBlade();
        } else {
            assert (criteria.qualifiers().isEmpty());
            blade = new DullBlade();
        }

        return Optional.of(blade);
    }
}
```

## Injectable Constructs
Any service can declare field, method, or constructor injection points. The only caveat is that these injectable elements must either be public or package private. Generally speaking, it is considered a best practice to (a) use only an injectable constructor, and (b) only inject <i>Provider</i> instances. Here is an example for best practice depicting all possible usages for injection types supported by Helidon Service.

```java
@Singleton
public class MainToolBox implements ToolBox {

    // generally not recommended
    @Inject
    Provider<Hammer> anyHammerProvider;

    // the best practice is to generally to use only constructor injection with Provider-wrapped types
    @Inject
    MainToolBox(
            @Preferred Screwdriver screwdriver, // the qualifier restricts to the "preferred" screwdriver
            List<Provider<Tool>> allTools,      // all tools in proper weighted/ranked order
            @Named("big") Provider<Hammer> bigHammerProvider, // only the hammer provider qualified with name "big"
            List<Provider<Hammer>> allHammers,  // all hammers in proper weighted/ranked order
            Optional<Chisel> maybeAChisel)      // optionally a Chisel, activated
    {
    }

    // generally not recommended
    @Inject
    void setScrewdriver(Screwdriver screwdriver) {
    }

    // called after construction by Injection's lifecycle startup management
    @PostConstruct
    void postConstruct() {
    }

    // called before shutdown by Injection's lifecycle shutdown management
    @PreDestroy
    void preDestroy() {
    }
}

```
