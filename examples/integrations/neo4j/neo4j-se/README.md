# Helidon SE integration with Neo4J example

## Build and run

Bring up a Neo4j instance via Docker

```shell
docker run --publish=7474:7474 --publish=7687:7687 -e 'NEO4J_AUTH=neo4j/secret'  neo4j:4.0
```

Goto the Neo4j browser and play the first step of the movies graph: [`:play movies`](http://localhost:7474/browser/?cmd=play&arg=movies).

Build and run with With JDK11+
```shell
mvn package -DskipTests
java -jar target/helidon-examples-integration-neo4j-se.jar  
```

Then access the rest API like this:

```shell
curl localhost:8080/api/movies
```

#Health and metrics

Heo4jSupport provides health checks and metrics reading from Neo4j.

To enable them add to routing:
```java
// metrics
Neo4jMetricsSupport.builder()
        .driver(neo4j.driver())
        .build()
        .initialize();
// health checks
HealthSupport health = HealthSupport.builder()
                .addLiveness(HealthChecks.healthChecks())   // Adds a convenient set of checks
                .addReadiness(Neo4jHealthCheck.create(neo4j.driver()))
                .build();

return Routing.builder()
        .register(health)                   // Health at "/health"
        .register(metrics)                  // Metrics at "/metrics"
        .register(movieService)
        .build();
```
and enable them in the driver:
```yaml
  pool:
    metricsEnabled: true
```


```shell
curl localhost:8080/health
```

```shell
curl localhost:8080/metrics
```



## Build the Docker Image

```shell
docker build -t helidon-integrations-heo4j-se .
```

## Start the application with Docker

```shell
docker run --rm -p 8080:8080 helidon-integrations-heo4j-se:latest
```

Exercise the application as described above

## Deploy the application to Kubernetes

```shell
kubectl cluster-info                                 # Verify which cluster
kubectl get pods                                     # Verify connectivity to cluster
kubectl create -f app.yaml                           # Deply application
kubectl get service helidon-integrations-heo4j-se    # Get service info
```

## Build a native image with GraalVM

GraalVM allows you to compile your programs ahead-of-time into a native
 executable. See https://www.graalvm.org/docs/reference-manual/aot-compilation/
 for more information.

You can build a native executable in 2 different ways:
* With a local installation of GraalVM
* Using Docker

### Local build

Download Graal VM at https://www.graalvm.org/downloads. We recommend
version `20.1.0` or later.

```shell
# Setup the environment
export GRAALVM_HOME=/path
# build the native executable
mvn package -Pnative-image
```

You can also put the Graal VM `bin` directory in your PATH, or pass
 `-DgraalVMHome=/path` to the Maven command.

See https://github.com/oracle/helidon-build-tools/tree/master/helidon-maven-plugin#goal-native-image
 for more information.

Start the application:

```shell
./target/helidon-integrations-heo4j-se
```

### Multi-stage Docker build

Build the "native" Docker Image

```shell
docker build -t helidon-integrations-heo4j-se-native -f Dockerfile.native .
```

Start the application:

```shell
docker run --rm -p 8080:8080 helidon-integrations-heo4j-se-native:latest
```

## Build a Java Runtime Image using jlink

You can build a custom Java Runtime Image (JRI) containing the application jars and the JDK modules 
on which they depend. This image also:

* Enables Class Data Sharing by default to reduce startup time. 
* Contains a customized `start` script to simplify CDS usage and support debug and test modes. 
 
You can build a custom JRI in two different ways:
* Local
* Using Docker


### Local build

```shell
# build the JRI
mvn package -Pjlink-image
```

See https://github.com/oracle/helidon-build-tools/tree/master/helidon-maven-plugin#goal-jlink-image
 for more information.

Start the application:

```shell
./target/helidon-integrations-heo4j-se-jri/bin/start
```

### Multi-stage Docker build

Build the JRI as a Docker Image

```shell
docker build -t helidon-integrations-heo4j-se-jri -f Dockerfile.jlink .
```

Start the application:

```shell
docker run --rm -p 8080:8080 helidon-integrations-heo4j-se-jri:latest
```

See the start script help:

```shell
docker run --rm helidon-integrations-heo4j-se-jri:latest --help
```
