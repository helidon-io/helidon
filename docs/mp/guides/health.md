# Helidon MP Health Check Guide

This guide describes how to create a sample MicroProfile (MP) project that can be used to run some basic examples using both built-in and custom health checks with Helidon MP.

## What You Need

For this 15 minute tutorial, you will need the following:

|  |  |
|----|----|
| [Java SE 21](https://www.oracle.com/technetwork/java/javase/downloads) ([Open JDK 21](http://jdk.java.net)) | Helidon requires Java 21+ (25+ recommended). |
| [Maven 3.8+](https://maven.apache.org/download.cgi) | Helidon requires Maven 3.8+. |
| [Docker 18.09+](https://docs.docker.com/install/) | If you want to build and run Docker containers. |
| [Kubectl 1.16.5+](https://kubernetes.io/docs/tasks/tools/install-kubectl/) | If you want to deploy to Kubernetes, you need `kubectl` and a Kubernetes cluster (you can [install one on your desktop](../../about/kubernetes.md)). |

Prerequisite product versions for Helidon 4.4.0-SNAPSHOT

*Verify Prerequisites*

```bash
java -version
mvn --version
docker --version
kubectl version
```

*Setting JAVA_HOME*

```bash
# On Mac
export JAVA_HOME=`/usr/libexec/java_home -v 21`

# On Linux
# Use the appropriate path to your JDK
export JAVA_HOME=/usr/lib/jvm/jdk-21
```

### Create a Sample MP Project

Generate the project sources using the Helidon MP Maven archetype. The result is a simple project that can be used for the examples in this guide.

*Run the Maven archetype:*

```bash
mvn -U archetype:generate -DinteractiveMode=false \
    -DarchetypeGroupId=io.helidon.archetypes \
    -DarchetypeArtifactId=helidon-quickstart-mp \
    -DarchetypeVersion=4.4.0-SNAPSHOT \
    -DgroupId=io.helidon.examples \
    -DartifactId=helidon-quickstart-mp \
    -Dpackage=io.helidon.examples.quickstart.mp
```

### Using the Built-In Health Checks

Helidon has a set of built-in health checks:

- deadlock detection
- available disk space
- available heap memory

The following example will demonstrate how to use the built-in health checks. These examples are all executed from the root directory of your project (helidon-quickstart-mp).

*Include dependency for the built-in health checks*

```xml
<dependency>
    <groupId>io.helidon.health</groupId>
    <artifactId>helidon-health-checks</artifactId>
</dependency>
```

*Build the application then run it:*

```bash
mvn package
java -jar target/helidon-quickstart-mp.jar
```

*Verify the health endpoint in a new terminal window:*

```bash
curl http://localhost:8080/health
```

*JSON response:*

```json
{
  "status": "UP",
  "checks": [
    {
      "name": "deadlock",
      "status": "UP"
    },
    {
      "name": "diskSpace",
      "status": "UP",
      "data": {
        "free": "325.54 GB",
        "freeBytes": 349543358464,
        "percentFree": "69.91%",
        "total": "465.63 GB",
        "totalBytes": 499963174912
      }
    },
    {
      "name": "heapMemory",
      "status": "UP",
      "data": {
        "free": "230.87 MB",
        "freeBytes": 242085696,
        "max": "3.56 GB",
        "maxBytes": 3817865216,
        "percentFree": "98.90%",
        "total": "271.00 MB",
        "totalBytes": 284164096
      }
    }
  ]
}
```

### Custom Liveness Health Checks

You can create application-specific custom health checks and integrate them with Helidon using CDI. The following example shows how to add a custom liveness health check.

*Create a new `GreetLivenessCheck` class with the following content:*

```java
@Liveness 
@ApplicationScoped 
public class GreetLivenessCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("LivenessCheck")  
                .up()
                .withData("time", System.currentTimeMillis())
                .build();
    }
}
```

- Annotation indicating this is a liveness health check.
- Annotation indicating there is a single liveness `HealthCheck` object during the lifetime of the application.
- Build the HealthCheckResponse with status `UP` and the current time.

*Build and run the application, then verify the custom liveness health endpoint*

```bash
curl http://localhost:8080/health/live
```

*JSON response:*

```json
{
  "status": "UP",
  "checks": [
    {
      "name": "LivenessCheck",
      "status": "UP",
      "data": {
        "time": 1566338255331
      }
    }
  ]
}
```

### Custom Readiness Health Checks

You can add a readiness check to indicate that the application is ready to be used. In this example, the server will wait five seconds before it becomes ready.

*Create a new `GreetReadinessCheck` class with the following content:*

```java
@Readiness 
@ApplicationScoped
public class GreetReadinessCheck implements HealthCheck {
    private final AtomicLong readyTime = new AtomicLong(0);

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("ReadinessCheck")  
                .status(isReady())
                .withData("time", readyTime.get())
                .build();
    }

    public void onStartUp(
            @Observes @Initialized(ApplicationScoped.class) Object init) {
        readyTime.set(System.currentTimeMillis()); 
    }

    /**
     * Become ready after 5 seconds
     *
     * @return true if application ready
     */
    private boolean isReady() {
        return Duration.ofMillis(System.currentTimeMillis() - readyTime.get()).getSeconds() >= 5;
    }
}
```

- Annotation indicating that this is a readiness health check.
- Build the `HealthCheckResponse` with status `UP` after five seconds, else `DOWN`.
- Record the time at startup.

*Build and run the application. Issue the curl command with -v within five seconds, and you will see that the application is not ready:*

```bash
curl -v  http://localhost:8080/health/ready
```

*HTTP response status*

```text
< HTTP/1.1 503 Service Unavailable 
```

- The HTTP status is `503` since the application is not ready.

*Response body*

```json
{
  "status": "DOWN",
  "checks": [
    {
      "name": "ReadinessCheck",
      "status": "DOWN",
      "data": {
        "time": 1566399775700
      }
    }
  ]
}
```

*After five seconds you will see the application is ready:*

```bash
curl -v http://localhost:8080/health/ready
```

*HTTP response status*

```text
< HTTP/1.1 200 OK 
```

- The HTTP status is `200` indicating that the application is ready.

*Response body*

```json
{
  "status": "UP",
  "checks": [
    {
      "name": "ReadinessCheck",
      "status": "UP",
      "data": {
        "time": 1566399775700
      }
    }
  ]
}
```

### Custom Startup Health Checks

You can add a startup check to indicate if the application is initialized to the point that the other health checks make sense. In this example, the server will wait eight seconds before it declares itself started.

*Create a new `GreetStartedCheck` class with the following content:*

```java
@Startup 
@ApplicationScoped
public class GreetStartedCheck implements HealthCheck {
    private final AtomicLong readyTime = new AtomicLong(0);

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("StartedCheck")  
                .status(isStarted())
                .withData("time", readyTime.get())
                .build();
    }

    public void onStartUp(
            @Observes @Initialized(ApplicationScoped.class) Object init) {
        readyTime.set(System.currentTimeMillis()); 
    }

    /**
     * Become ready after 5 seconds
     *
     * @return true if application ready
     */
    private boolean isStarted() {
        return Duration.ofMillis(System.currentTimeMillis() - readyTime.get()).getSeconds() >= 8;
    }
}
```

- Annotation indicating that this is a startup health check.
- Build the `HealthCheckResponse` with status `UP` after eight seconds, else `DOWN`.
- Record the time at startup of Helidon; the application will declare itself as started eight seconds later.

*Build and run the application. Issue the curl command with -v within five seconds, and you will see that the application has not yet started:*

```bash
curl -v  http://localhost:8080/health/started
```

*HTTP response status*

```text
< HTTP/1.1 503 Service Unavailable 
```

- The HTTP status is `503` since the application has not started.

*Response body*

```json
{
  "status": "DOWN",
  "checks": [
    {
      "name": "StartedCheck",
      "status": "DOWN",
      "data": {
        "time": 1566399775700
      }
    }
  ]
}
```

*After eight seconds you will see the application has started:*

```bash
curl -v http://localhost:8080/health/started
```

*HTTP response status*

```text
< HTTP/1.1 200 OK 
```

- The HTTP status is `200` indicating that the application is started.

*Response body*

```json
{
  "status": "UP",
  "checks": [
    {
      "name": "StartedCheck",
      "status": "UP",
      "data": {
        "time": 1566399775700
      }
    }
  ]
}
```

When using the health check URLs, you can get the following health check data:

- liveness only - <http://localhost:8080/health/live>
- readiness only - <http://localhost:8080/health/ready>
- startup checks only - <http://localhost:8080/health/started>
- all health check data - <http://localhost:8080/health>

*Get all the health check data, including custom data:*

```bash
curl http://localhost:8080/health
```

*JSON response:*

```json
{
  "status": "UP",
  "checks": [
    {
      "name": "LivenessCheck",
      "status": "UP",
      "data": {
        "time": 1566403431536
      }
    },
    {
      "name": "ReadinessCheck",
      "status": "UP",
      "data": {
        "time": 1566403280639
      }
    },
    {
      "name": "StartedCheck",
      "status": "UP",
      "data": {
        "time": 1566403280639
      }
    },
    {
      "name": "deadlock",
      "state": "UP",
      "status": "UP"
    },
    {
      "name": "diskSpace",
      "state": "UP",
      "status": "UP",
      "data": {
        "free": "325.50 GB",
        "freeBytes": 349500698624,
        "percentFree": "69.91%",
        "total": "465.63 GB",
        "totalBytes": 499963174912
      }
    },
    {
      "name": "heapMemory",
      "state": "UP",
      "status": "UP",
      "data": {
        "free": "231.01 MB",
        "freeBytes": 242235928,
        "max": "3.56 GB",
        "maxBytes": 3817865216,
        "percentFree": "98.79%",
        "total": "275.00 MB",
        "totalBytes": 288358400
      }
    }
  ]
}
```

### Custom Health Root Path and Port

You can specify a custom port and root context for the root health endpoint path. However, you cannot use different ports, such as <http://localhost:8080/myhealth> and <http://localhost:8081/myhealth/live>. Likewise, you cannot use different paths, such as <http://localhost:8080/health> and <http://localhost:8080/probe/live>.

The example below will change the root path.

*Create a file named `application.yaml` in the `resources` directory with the following contents:*

```yaml
health:
  endpoint: "/myhealth" 
```

- The `endpoint` settings specifies the root path for the health endpoint.

*Build and run the application, then verify that the health endpoint is using the new `/myhealth` root:*

```bash
curl http://localhost:8080/myhealth
curl http://localhost:8080/myhealth/live
curl http://localhost:8080/myhealth/ready
curl http://localhost:8080/myhealth/started
```

The following example will change the root path and the health port.

*Update application.yaml to use a different port and root path for the health endpoint:*

```yaml
server:
  port: 8080 
  sockets:
    - name: "admin" 
      port: 8081 
  features:
    observe:
      sockets: "admin" 
health:
  endpoint: "/myhealth" 
```

- The default port for the application.
- The name of the new socket, it can be any name, this example uses `admin`.
- The port for the `admin` socket.
- The health endpoint, as part of Helidon’s observability support, uses the socket `admin`.

*Build and run the application, then verify the health endpoint using port `8081` and `/myhealth`:*

```bash
curl http://localhost:8081/myhealth
curl http://localhost:8081/myhealth/live
curl http://localhost:8081/myhealth/ready
curl http://localhost:8081/myhealth/started
```

### Using Liveness, Readiness, and Startup Health Checks with Kubernetes

The following example shows how to integrate the Helidon health check API with an application that implements health endpoints for the Kubernetes liveness, readiness, and startup probes.

**Delete the contents of `application.yaml` so that the default health endpoint path and port are used.**

*Rebuild and start the application, then verify the health endpoint:*

```bash
curl http://localhost:8080/health
```

*Stop the application and build the docker image:*

```bash
docker build -t helidon-quickstart-mp .
```

*Create the Kubernetes YAML specification, named `health.yaml`, with the following content:*

```yaml
kind: Service
apiVersion: v1
metadata:
  name: helidon-health 
  labels:
    app: helidon-health
spec:
  type: NodePort
  selector:
    app: helidon-health
  ports:
    - port: 8080
      targetPort: 8080
      name: http
---
kind: Deployment
apiVersion: apps/v1
metadata:
  name: helidon-health 
spec:
  replicas: 1
  selector:
    matchLabels:
      app: helidon-health
  template:
    metadata:
      labels:
        app: helidon-health
        version: v1
    spec:
      containers:
        - name: helidon-health
          image: helidon-quickstart-mp
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 8080
          livenessProbe:
            httpGet:
              path: /health/live 
              port: 8080
            initialDelaySeconds: 5 
            periodSeconds: 10
            timeoutSeconds: 3
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /health/ready 
              port: 8080
            initialDelaySeconds: 5 
            periodSeconds: 2
            timeoutSeconds: 3
          startupProbe:
            httpGet:
              path: /health/started 
              port: 8080
            initialDelaySeconds: 8 
            periodSeconds: 10
            timeoutSeconds: 3
            failureThreshold: 3
---
```

- A service of type `NodePort` that serves the default routes on port `8080`.
- A deployment with one replica of a pod.
- The HTTP endpoint for the liveness probe.
- The liveness probe configuration.
- The HTTP endpoint for the readiness probe.
- The readiness probe configuration.
- The HTTP endpoint for the startup probe.
- The startup probe configuration.

*Create and deploy the application into Kubernetes:*

```bash
kubectl apply -f ./health.yaml
```

*Get the service information:*

```bash
kubectl get service/helidon-health
```

```bash
NAME             TYPE       CLUSTER-IP      EXTERNAL-IP   PORT(S)          AGE
helidon-health   NodePort   10.107.226.62   <none>        8080:30116/TCP   4s 
```

- A service of type `NodePort` that serves the default routes on port `30116`.

*Verify the health endpoints using port '30116', your port may be different. The JSON response will be the same as your previous test:*

```bash
curl http://localhost:30116/health
```

*Delete the application, cleaning up Kubernetes resources:*

```bash
kubectl delete -f ./health.yaml
```

### Summary

This guide demonstrated how to use health checks in a Helidon MP application as follows:

- Access the default health checks
- Create and use custom readiness, liveness, and startup checks
- Customize the health check root path and port
- Integrate Helidon health check API with Kubernetes

Refer to the following references for additional information:

- [MicroProfile health check specification](https://download.eclipse.org/microprofile/microprofile-health-4.0/microprofile-health-spec-4.0.html)
- [MicroProfile health check Javadoc](https://download.eclipse.org/microprofile/microprofile-health-4.0/apidocs)
- [Helidon Javadoc](/apidocs/index.html?overview-summary.html)
