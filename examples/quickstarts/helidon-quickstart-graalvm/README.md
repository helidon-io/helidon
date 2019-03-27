
# Helidon Example: quickstart-graalvm

This example implements a simple Hello World REST service.

## Prerequisites

1. Maven 3.5 or newer
2. Graal VM 1.0.0-rc14 or newer as JAVA_HOME, ''$JAVA_HOME/bin/native-image' must exist 
3. Docker 17 or newer to build and run docker images
4. Kubernetes minikube v0.24 or newer to deploy to Kubernetes (or access to a K8s 1.7.4 or newer cluster)
5. Kubectl 1.7.4 or newer to deploy to Kubernetes

Verify prerequisites
```
java -version
mvn --version
docker --version
minikube version
kubectl version --short
```

## Configure `pom.xml`

- `graalvm.home`: Graal VM home directory. This directory shall contain `bin/native-image` executable.

## Build and run

### Build application JAR
```
mvn package
```
### Start the application using Graal VM
```
mvn exec:exec
```
### Build Docker Image
```
mvn package -Pnative-image-docker
```
### Start Docker container with the application using Graal VM
```
docker run -d --rm --name helidon-native -p 8080:8080 helidon/example-graal:1.0-SNAPSHOT
```

## Exercise the application

```
curl -X GET http://localhost:8080/greet
{"message":"Hello World!"}

curl -X GET http://localhost:8080/greet/Joe
{"message":"Hello Joe!"}

curl -X PUT -H "Content-Type: application/json" -d '{"greeting" : "Hola"}' http://localhost:8080/greet/greeting

curl -X GET http://localhost:8080/greet/Jose
{"message":"Hola Jose!"}
```

## Try health and metrics

```
curl -s -X GET http://localhost:8080/health
{"outcome":"UP",...
. . .

# Prometheus Format
curl -s -X GET http://localhost:8080/metrics
# TYPE base:gc_g1_young_generation_count gauge
. . .

# JSON Format
curl -H 'Accept: application/json' -X GET http://localhost:8080/metrics
{"base":...
. . .

```
