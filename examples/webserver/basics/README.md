
# Helidon WebServer Basic Example

This example consists of various methods that can be selected
at runtime. Each method illustrates a different WebServer concept.
See the comments in `Main.java` for a description of the various
methods.

## Build and run

```bash
mvn package
```

To see the list of methods that are available run:

```bash
java -DexampleName=help -jar target/helidon-examples-webserver-basics.jar
```

You should see output like:

```
Example method names:
    help
    h
    firstRouting
    startServer
    routingAsFilter
    parametersAndHeaders
    advancedRouting
    organiseCode
    readContentEntity
    filterAndProcessEntity
    supports
    errorHandling
```

You can then choose the method to execute by setting the `exampleName` system property:

```
java -DexampleName=firstRouting -jar target/helidon-examples-webserver-basics.jar
```

This will start the Helidon SE WebServer using the method indicated.
