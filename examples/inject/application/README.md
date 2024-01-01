# Helidon Injection Application Example

This example shows how a multi-module application can be created using Helidon Injection. The
[Main.java](./src/main/java/io/helidon/examples/inject/application/Main.java) class shows:

* multi-module usage (i.e., this module amalgamates [basics](../basics), [providers](../providers), [configdriven](../configdriven), and [interceptors](../interceptors) ).
* compile-time generation of the DI model for the entire multi-module project using the _inject-maven-plugin_ (see [pom.xml](./pom.xml)).
* TestingSupport in [ApplicationTest](src/test/java/io/helidon/examples/inject/application/InjectionApplicationTest.java)

## Build and run

```bash
mvn package
java -jar target/helidon-examples-inject-application.jar
```

Expected Output:
```
Startup service providers (ranked according to weight, pre-activated): [ToolBox:INIT, CircularSaw:INIT, NailGun:INIT, TableSaw:INIT]
Highest weighted service provider: NailGun:INIT
-----
Nail Gun: (nail provider=NailProvider:INIT); initialized
Highest weighted service provider (after activation): io.helidon.examples.inject.providers.NailGun@7cbd9d24
-----
Preferred Big Tool: Big Hammer
Optional Little Hammer: Optional[Little Hammer]
-----
ToolBox Contents:
Hammer:INIT
BigHammer:ACTIVE
LittleHammer:ACTIVE
Drill{root}:PENDING
AngleGrinderSaw:INIT
CircularSaw:INIT
HandSaw:INIT
NailGun:ACTIVE
TableSaw:INIT
-----
io.helidon.examples.inject.providers.CircularSaw::<init> will be injected with Optional.empty
Circular Saw: (blade=null); initialized
io.helidon.examples.inject.providers.TableSaw::<init> will be injected with Optional[LARGE Blade]
Table Saw: (blade=LARGE Blade); initialized
All service providers (after all activations): [ToolBox:ACTIVE, CircularSaw:ACTIVE, NailGun:ACTIVE, TableSaw:ACTIVE]
-----
Service lookup count: 2
```

While the output of this example may look similar to the previous [providers](../providers) example, the implementation is different since this example builds (at compile time) [Application.java](target/generated-sources/annotations/io/helidon/examples/inject/application/Injection$$Application.java). This establishes direct bindings to each and every injection point in your application avoiding runtime resolution with the exception for truly dynamic runtime providers (i.e., anything that is config-driven services or _Provider_ type implementations).

Note that the lookup count is 2 based upon the direct lookup calls used in the delegated [Main](../basics/src/main/java/io/helidon/examples/inject/basics/Main.java).
