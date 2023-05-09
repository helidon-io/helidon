# Helidon Pico Providers Example

This example shows how providers can be leveraged to develop using Helidon Pico. The
[Main.java](./src/main/java/io/helidon/examples/pico/providers/Main.java) class shows:

* programmatic lookup of services in Pico's Services registry in [Main](./src/main/java/io/helidon/examples/pico/basics/Main.java).
* declarative injection in [ToolBox.java](./src/main/java/io/helidon/examples/pico/basics/ToolBox.java).
* lifecycle via <b>PostConstruct</b> and <b>RunLevel</b> in [Main](./src/main/java/io/helidon/examples/pico/basics/Main.java).

## Build and run

```bash
mvn package
java -jar target/helidon-examples-pico-providers.jar
```

Expected Output:
```
Startup service providers (ranked according to weight, pre-activated): [ToolBox:INIT, CircularSaw:INIT, TableSaw:INIT]
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
Table Saw: blade = null; initialized
All service providers (after all activations): [ToolBox:ACTIVE, CircularSaw:ACTIVE, TableSaw:ACTIVE]
-----
```
