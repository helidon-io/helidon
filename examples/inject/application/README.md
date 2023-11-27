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

Expected Output (redacted):
```
--------------------------------
- Initialize services          -
--------------------------------
A few lines with details from injected objects
--------------------------------
- Initializing all tools       -
--------------------------------
A few lines with details from injected objects in tools
--------------------------------
- Tools in the virtual ToolBox -
--------------------------------
 tool: Nail Gun: (nail provider=io.helidon.examples.inject.providers.NailProvider)
 tool: Hammer
 tool: Angle Grinder Saw: (blade=SMALL Blade)
 tool: Circular Saw: (blade=null)
 tool: Hand Saw: (blade=null)
 tool: Table Saw: (blade=LARGE Blade)
 tool: Big Hammer
 tool: Little Hammer
 tool: Hand Drill
 tool: Impact Drill
--------------------------------
- Programmatic lookup          -
--------------------------------
All services in RunLevel.STARTUP (ranked according to weight):
  io.helidon.examples.inject.providers.NailGun
  io.helidon.examples.inject.basics.ToolBox
  io.helidon.examples.inject.providers.CircularSaw
  io.helidon.examples.inject.providers.TableSaw
Highest weighted service provider: io.helidon.examples.inject.providers.NailGun
Service lookup count: 2 (expected to be 2, as we lookup twice in basics.Main)
```

While the output of this example may look similar to the previous [providers](../providers) example, the implementation is different since this example builds (at compile time) [Injection__Application.java](target/generated-sources/annotations/io/helidon/examples/inject/application/Injection__Application.java). This establishes direct bindings to each and every injection point in your application avoiding runtime resolution with the exception for truly dynamic runtime providers (i.e., anything that is config-driven services or _Provider_ type implementations).

Note that the lookup count is 2 based upon the direct lookup calls used in the delegated [Main](../basics/src/main/java/io/helidon/examples/inject/basics/Main.java).
