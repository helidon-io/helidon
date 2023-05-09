# Helidon Pico Providers Example

This example shows how providers can be leveraged to develop using Helidon Pico. The
[Main.java](./src/main/java/io/helidon/examples/pico/providers/Main.java) class shows:

* multi-module usage (i.e., this example extends [basics](../basics)).
* [standard Providers](src/main/java/io/helidon/examples/pico/providers/NailProvider.java).
* [InjectionPoint Providers](src/main/java/io/helidon/examples/pico/providers/BladeProvider.java).
* additional lifecycle examples via <b>PostConstruct</b> and <b>RunLevel</b>.

## Build and run

```bash
mvn package
java -jar target/helidon-examples-pico-providers.jar
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
-----
```
