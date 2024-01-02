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
Startup service providers (ranked according to weight, pre-activated): [NailGun:INIT, ToolBox:INIT, CircularSaw:INIT, TableSaw:INIT]
Highest weighted service provider: NailGun:INIT
Nail Gun: (nail provider=NailProvider:INIT); initialized
Highest weighted service provider (after activation): io.helidon.examples.inject.providers.NailGun
Preferred (highest weighted) 'Big' Tool: Big Hammer
Optional 'Little' Hammer: Optional[Little Hammer]
--------------------------------
- Initializing all tools       -
--------------------------------
Angle Grinder Saw: (blade=null); initialized
io.helidon.examples.inject.providers.CircularSaw::blade will be injected with Optional.empty
Circular Saw: (blade=null); initialized
Hand Saw: (blade=null); initialized
Table Saw: (blade=null); initialized
Hand; initialized
--------------------------------
- Tools in the virtual ToolBox -
--------------------------------
 tool: Nail Gun: (nail provider=NailProvider:INIT)
 tool: Hammer
 tool: Angle Grinder Saw: (blade=null)
 tool: Circular Saw: (blade=null)
 tool: Hand Saw: (blade=null)
 tool: Table Saw: (blade=null)
 tool: Big Hammer
 tool: Little Hammer
 tool: Hand
 tool: Hand
All service providers (after all activations): [NailGun:ACTIVE, ToolBox:ACTIVE, CircularSaw:ACTIVE, TableSaw:ACTIVE]
Service lookup count: 3
```

While the output of this example may look similar to the previous [providers](../providers) example, the implementation is different since this example builds (at compile time) [Application.java](target/generated-sources/annotations/io/helidon/examples/inject/application/Injection$$Application.java). This establishes direct bindings to each and every injection point in your application avoiding runtime resolution with the exception for truly dynamic runtime providers (i.e., anything that is config-driven services or _Provider_ type implementations).

Note that the lookup count is 2 based upon the direct lookup calls used in the delegated [Main](../basics/src/main/java/io/helidon/examples/inject/basics/Main.java).
