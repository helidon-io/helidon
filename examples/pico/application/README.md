# Helidon Pico Providers Example

This example shows how providers can be leveraged to develop using Helidon Pico. The
[Main.java](./src/main/java/io/helidon/examples/pico/providers/Main.java) class shows:

* multi-module usage (i.e., this example extends [basics](../basics), [providers](../providers), and [configdriven](../configdriven) ).
* compile-time generation for the entire multi-module project using the pico-maven-plugin (see [pom.xml](./pom.xml)).
* TestingSupport

## Build and run

```bash
mvn package
java -jar target/helidon-examples-pico-providers.jar
```

Expected Output:
```
```
