# Helidon Pico Providers Example

This example shows how interceptors can be leveraged to develop using Helidon Pico. The
[Main.java](./src/main/java/io/helidon/examples/pico/providers/Main.java) class shows:

* Interception basics of Pico.

## Build and run

```bash
mvn package
java -jar target/helidon-examples-pico-interceptors.jar
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
ScrewDriver$$Pico$$Interceptor:INIT
BigHammer:ACTIVE
LittleHammer:ACTIVE
-----
Highest weighted service provider (after activation): ToolBox
-----
All service providers (after all activations): [ToolBox:ACTIVE]
-----
Screw Driver (1st turn): 
Screw Driver turning right
Screw Driver (2nd turn): 
Screw Driver turning right
```
