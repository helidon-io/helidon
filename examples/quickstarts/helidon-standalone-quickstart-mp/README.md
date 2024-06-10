# Helidon Examples Standalone Quickstart MP

This example implements a simple Hello World REST service using MicroProfile
 with a standalone Maven pom.

## Build and run

```shell
mvn package
java -jar target/helidon-standalone-quickstart-mp.jar
```

## Exercise the application

```shell
curl -X GET http://localhost:8080/greet
#Output: {"message":"Hello World!"}

curl -X GET http://localhost:8080/greet/Joe
#Output: {"message":"Hello Joe!"}

curl -X PUT -H "Content-Type: application/json" -d '{"message" : "Hola"}' http://localhost:8080/greet/greeting

curl -X GET http://localhost:8080/greet/Jose
#Output: {"message":"Hola Jose!"}
```

## Try health and metrics

```shell
curl -s -X GET http://localhost:8080/health
#Output: {"outcome":"UP",...

# Prometheus Format
curl -s -X GET http://localhost:8080/metrics
# TYPE base:gc_g1_young_generation_count gauge

# JSON Format
curl -H 'Accept: application/json' -X GET http://localhost:8080/metrics
#Output: {"base":...
```

## Build the Docker Image

```shell
docker build -t helidon-standalone-quickstart-mp .
```

## Start the application with Docker

```shell
docker run --rm -p 8080:8080 helidon-standalone-quickstart-mp:latest
```

Exercise the application as described above

## Deploy the application to Kubernetes

```shell
kubectl cluster-info                         # Verify which cluster
kubectl get pods                             # Verify connectivity to cluster
kubectl create -f app.yaml               # Deploy application
kubectl get service helidon-standalone-quickstart-mp  # Verify deployed service
```

## Build a native image with GraalVM

GraalVM allows you to compile your programs ahead-of-time into a native
 executable. See https://www.graalvm.org/docs/reference-manual/aot-compilation/
 for more information.

You can build a native executable in 2 different ways:
* With a local installation of GraalVM
* Using Docker

### Local build

Download Graal VM at https://www.graalvm.org/downloads.

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
./target/helidon-quickstart-mp
```

### Multi-stage Docker build

Build the "native" Docker Image

```shell
docker build -t helidon-standalone-quickstart-mp-native -f Dockerfile.native .
```

Start the application:

```shell
docker run --rm -p 8080:8080 helidon-standalone-quickstart-mp-native:latest
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
./target/helidon-standalone-quickstart-mp-jri/bin/start
```

### Multi-stage Docker build

Build the JRI as a Docker Image

```shell
docker build -t helidon-standalone-quickstart-mp-jri -f Dockerfile.jlink .
```

Start the application:

```shell
docker run --rm -p 8080:8080 helidon-standalone-quickstart-mp-jri:latest
```

See the start script help:

```shell
docker run --rm helidon-standalone-quickstart-mp-jri:latest --help
```