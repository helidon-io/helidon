# Helidon Config Mapping Example

This example shows how to implement mappers that convert configuration
to POJOs.

1. [`BuilderExample.java`](./src/main/java/io/helidon/config/examples/mapping/BuilderExample.java)
shows how you can add a `builder()` method to a POJO. That method returns a `Builder` 
object which the config system uses to update with various key settings and then,
finally, invoke `build()` so the builder can instantiate the POJO with the
assigned values.
2. [`DeserializationExample.java`](./src/main/java/io/helidon/config/examples/mapping/DeserializationExample.java)
uses the config system's support for automatic mapping to POJOs that have bean-style
setter methods.
3. [`FactoryMethodExample.java`](./src/main/java/io/helidon/config/examples/mapping/FactoryMethodExample.java)
illustrates how you can add a static factory method `create` to a POJO to tell the config
system how to construct a POJO instance.

## Build and run

```bash
mvn package
java -jar target/helidon-examples-config-mapping.jar
```
