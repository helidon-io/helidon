# Helidon Injection Providers Example

This example shows how providers can be leveraged to develop using Helidon Injection. The
[Main.java](./src/main/java/io/helidon/examples/inject/providers/Main.java) class shows:

* multi-module usage (i.e., this example extends [basics](../basics)).
* [standard Providers](src/main/java/io/helidon/examples/inject/providers/NailProvider.java).
* [InjectionPoint Providers](src/main/java/io/helidon/examples/inject/providers/BladeProvider.java).
* additional lifecycle examples via <b>PostConstruct</b> and <b>RunLevel</b>.

## Build and run

```bash
mvn package
java -jar target/helidon-examples-inject-providers.jar
```

Expected Output:
```
Startup service providers (ranked according to weight, pre-activated): [io.helidon.examples.inject.providers.NailGun, io.helidon.examples.inject.basics.ToolBox, io.helidon.examples.inject.providers.CircularSaw, io.helidon.examples.inject.providers.TableSaw]
Nail Gun: (nail provider=io.helidon.examples.inject.providers.NailProvider); initialized
Preferred (highest weighted) 'Big' Tool: Big Hammer
Optional 'Little' Hammer: Optional[Little Hammer]
--------------------------------
- Initializing all tools       -
--------------------------------
Angle Grinder Saw: (blade=null); initialized
io.helidon.examples.inject.providers.CircularSaw::blade will be injected with Optional.empty
Circular Saw: (blade=null); initialized
io.helidon.examples.inject.providers.HandSaw::blade will be injected with Optional.empty
Hand Saw: (blade=null); initialized
Table Saw: (blade=null); initialized
--------------------------------
- Tools in the virtual ToolBox -
--------------------------------
 tool: Nail Gun: (nail provider=io.helidon.examples.inject.providers.NailProvider)
 tool: Hammer
 tool: Allen Wrench
 tool: Angle Grinder Saw: (blade=null)
 tool: Circular Saw: (blade=null)
 tool: Hand Saw: (blade=null)
 tool: Table Saw: (blade=null)
 tool: Big Hammer
 tool: Little Hammer
```
