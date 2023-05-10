# Helidon Pico Application Example

This example shows how a multi-module application can be created using Helidon Pico. The
[Main.java](./src/main/java/io/helidon/examples/pico/application/Main.java) class shows:

* multi-module usage (i.e., this example extends [basics](../basics), [providers](../providers), and [configdriven](../configdriven) ).
* compile-time generation for the entire multi-module project using the _pico-maven-plugin_ (see [pom.xml](./pom.xml)).
* TestingSupport in [ApplicationTest](src/test/java/io/helidon/examples/pico/application/PicoApplicationTest.java)

## Build and run

```bash
mvn package
java -jar target/helidon-examples-pico-application.jar
```

Expected Output:
```
Startup service providers (ranked according to weight, pre-activated): [ToolBox:INIT, CircularSaw:INIT, NailGun:INIT, TableSaw:INIT]
Highest weighted service provider: ToolBox:INIT
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
NailGun:INIT
TableSaw:INIT
-----
Highest weighted service provider (after activation): ToolBox
-----
io.helidon.examples.pico.providers.CircularSaw::<init> will be injected with Optional.empty
Circular Saw: (blade=null); initialized
Nail Gun: (nail provider=NailProvider:INIT); initialized
io.helidon.examples.pico.providers.TableSaw::<init> will be injected with Optional[LARGE Blade]
Table Saw: (blade=LARGE Blade); initialized
All service providers (after all activations): [ToolBox:ACTIVE, CircularSaw:ACTIVE, NailGun:ACTIVE, TableSaw:ACTIVE]
```

While the output of this provider may look similar to the one from the previous [providers](../providers) example, the implementation is different. This module builds [Application.java](target/generated-sources/annotations/io/helidon/examples/pico/application/Pico$$Application.java) at compile-time - establishing direct binding to every injection point in your application that is not dynamic in nature (i.e., config-driven services and _Provider_ types).
