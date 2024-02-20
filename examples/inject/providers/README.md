# Helidon Injection Providers Example

This example shows how providers can be leveraged to develop using Helidon Injection. The
[Main.java](./src/main/java/io/helidon/examples/inject/providers/Main.java) class shows:

* multi-module usage (i.e., this example extends [basics](../basics)).
* [standard Providers](src/main/java/io/helidon/examples/inject/providers/NailProvider.java).
* [InjectionPoint Providers](src/main/java/io/helidon/examples/inject/providers/BladeProvider.java).
* additional lifecycle examples via <b>PostConstruct</b> and <b>RunLevel</b>.

## Build and run

```shell
mvn package
java -jar target/helidon-examples-inject-providers.jar
```

Expected Output:
```
Startup service providers (ranked according to weight, pre-activated): [ToolBox:INIT, CircularSaw:INIT, NailGun:INIT, TableSaw:INIT]
Preferred (highest weighted) 'Big' Tool: Big Hammer
Optional 'Little' Hammer: Optional[Little Hammer]
Tools in the virtual ToolBox:
 tool: Hammer:INIT
 tool: BigHammer:ACTIVE
 tool: LittleHammer:ACTIVE
 tool: AngleGrinderSaw:INIT
 tool: CircularSaw:INIT
 tool: HandSaw:INIT
 tool: NailGun:INIT
 tool: TableSaw:INIT
io.helidon.examples.inject.providers.CircularSaw::<init> will be injected with Optional.empty
Circular Saw: (blade=null); initialized
Nail Gun: (nail provider=NailProvider:INIT); initialized
io.helidon.examples.inject.providers.TableSaw::<init> will be injected with Optional[LARGE Blade]
Table Saw: (blade=LARGE Blade); initialized
All service providers (after all activations): [ToolBox:ACTIVE, CircularSaw:ACTIVE, NailGun:ACTIVE, TableSaw:ACTIVE]
```
