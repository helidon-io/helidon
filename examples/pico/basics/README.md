# Helidon Pico Basic Example

This example shows the basics of using Helidon Pico. The
[Main.java](./src/main/java/io/helidon/examples/pico/basics/Main.java) class shows:

* programmatic lookup of services in Pico's Services registry in [Main](./src/main/java/io/helidon/examples/pico/basics/Main.java).
* declarative injection in [ToolBox.java](./src/main/java/io/helidon/examples/pico/basics/ToolBox.java).
* lifecycle via <b>PostConstruct</b> and <b>RunLevel</b> in [Main](./src/main/java/io/helidon/examples/pico/basics/Main.java).
* annotation processing and source code generation (see [pom.xml](pom.xml) and [generated-sources](./target/generated-sources/annotations/io/helidon/examples/pico/basics)).

## Build and run

```bash
mvn package
java -jar target/helidon-examples-pico-basics.jar
```

Expected Output:
```
Startup service providers (ranked according to weight, pre-activated): [ToolBox:INIT]
Highest weighted service provider: ToolBox:INIT
Preferred (highest weighted) 'Big' Tool: Big Hammer
Optional 'Little' Hammer: Optional[Little Hammer]
Tools in the virtual ToolBox:
 tool: Hammer:INIT
 tool: BigHammer:ACTIVE
 tool: LittleHammer:ACTIVE
Highest weighted service provider (after activation): ToolBox
All service providers (after all activations): [ToolBox:ACTIVE]
```
