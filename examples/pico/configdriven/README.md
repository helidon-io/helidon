# Helidon Pico Config-Driven Example

This example shows the basics of using Helidon Pico's Config-Driven Services. The
[Main.java](./src/main/java/io/helidon/examples/pico/configdriven/Main.java) class shows:

* setting up the bootstrap [configuration](./src/main/resources/application.yaml).
* [ConfigBean](src/main/java/io/helidon/examples/pico/configdriven/DrillConfig.java).
* [ConfiguredBy](src/main/java/io/helidon/examples/pico/configdriven/Drill.java) Services.
* annotation processing and source code generation (see [pom.xml](pom.xml) and [generated-sources](./target/generated-sources/annotations/io/helidon/examples/pico/configdriven)).

## Build and run

```bash
mvn package
java -jar target/helidon-examples-pico-configdriven.jar
```

Expected Output:
```
Startup service providers (ranked according to weight, pre-activated): [ToolBox:INIT]
Highest weighted service provider: ToolBox:INIT
-----
Preferred Big Tool: Big Hammer
Optional Little Hammer: Optional[Little Hammer]
-----
ToolBox Contents:
Hammer:INIT
BigHammer:ACTIVE
LittleHammer:ACTIVE
Drill{Hand}:PENDING
Drill{Impact}:PENDING
-----
Highest weighted service provider (after activation): ToolBox
-----
All service providers (after all activations): [ToolBox:ACTIVE]
-----
Hand; initialized
io.helidon.examples.pico.configdriven.Drill@c33b74f
Impact; initialized
io.helidon.examples.pico.configdriven.Drill@130161f7
Ending
-----
ToolBox Contents:
Hammer:INIT
BigHammer:ACTIVE
LittleHammer:ACTIVE
Drill{Hand}:ACTIVE
Drill{Impact}:ACTIVE
-----
```
