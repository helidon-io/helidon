# Helidon Health Basic Example

This example shows the basics of using Helidon SE Health. It uses the
set of built-in health checks that Helidon provides plus defines a
custom health check.

## Build and run

Start the application:

```shell
mvn package
java -jar target/helidon-examples-health-basics.jar
```

Note the port number reported by the application.

Probe the health endpoints:

```shell
curl -i -X GET http://localhost:8080/observe/health/ready
curl -i -X GET http://localhost:8080/observe/health/
```
