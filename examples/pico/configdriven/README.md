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
Preferred (highest weighted) 'Big' Tool: Big Hammer
Optional 'Little' Hammer: Optional[Little Hammer]
Tools in the virtual ToolBox:
 tool: Hammer:INIT
 tool: BigHammer:ACTIVE
 tool: LittleHammer:ACTIVE
 tool: Drill{Hand}:PENDING
 tool: Drill{Impact}:PENDING
```
