
# Helidon WebServer Basic Example

This example consists of various methods that can be selected
at runtime. Each method illustrates a different WebServer concept.
See the comments in `Main.java` for a description of the various
methods.

## Build

```
mvn package
```

## Run

To see the list of methods that are available run:

```
mvn -DexampleName=help exec:java
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
mvn -DexampleName=firstRouting exec:java
```

This will start the Helidon SE WebServer using the method indicated.

