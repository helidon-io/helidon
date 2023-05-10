# Helidon Pico Application Example

This example shows how a multi-module application can be created using Helidon Pico. The
[Main.java](./src/main/java/io/helidon/examples/pico/application/Main.java) class shows:

* multi-module usage (i.e., this example extends [basics](../basics), [providers](../providers), and [configdriven](../configdriven) ).
* compile-time generation for the entire multi-module project using the _pico-maven-plugin_ (see [pom.xml](./pom.xml)).
* TestingSupport

## Build and run

```bash
mvn package
java -jar target/helidon-examples-pico-application.jar
```

Expected Output:
```
```
