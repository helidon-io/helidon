# Helidon MP Metrics Guide

This guide describes how to create a sample Helidon MicroProfile (MP) project that can be used to run some basic examples using both built-in and custom metrics with Helidon.

## What You Need

For this 30 minute tutorial, you will need the following:

|  |  |
|----|----|
| [Java SE 21](https://www.oracle.com/technetwork/java/javase/downloads) ([Open JDK 21](http://jdk.java.net)) | Helidon requires Java 21+ (25+ recommended). |
| [Maven 3.8+](https://maven.apache.org/download.cgi) | Helidon requires Maven 3.8+. |
| [Docker 18.09+](https://docs.docker.com/install/) | If you want to build and run Docker containers. |
| [Kubectl 1.16.5+](https://kubernetes.io/docs/tasks/tools/install-kubectl/) | If you want to deploy to Kubernetes, you need `kubectl` and a Kubernetes cluster (you can [install one on your desktop](../../about/kubernetes.md)). |
| [Helm](https://github.com/helm/helm) | To manage Kubernetes applications. |

*Verify Prerequisites*

``` bash
java -version
mvn --version
docker --version
kubectl version
```

*Setting JAVA_HOME*

``` bash
# On Mac
export JAVA_HOME=`/usr/libexec/java_home -v 21`

# On Linux
# Use the appropriate path to your JDK
export JAVA_HOME=/usr/lib/jvm/jdk-21
```

### Create a Sample Helidon MP Project

Use the Helidon MP Maven archetype to create a simple project that can be used for the examples in this guide.

*Run the Maven archetype*

``` bash
mvn -U archetype:generate -DinteractiveMode=false \
    -DarchetypeGroupId=io.helidon.archetypes \
    -DarchetypeArtifactId=helidon-quickstart-mp \
    -DarchetypeVersion=4.4.0-SNAPSHOT \
    -DgroupId=io.helidon.examples \
    -DartifactId=helidon-quickstart-mp \
    -Dpackage=io.helidon.examples.quickstart.mp
```

### Using the Built-In Metrics

Helidon provides three built-in scopes of metrics: base, vendor, and application. Here are the metric endpoints:

1.  `/metrics?scope=base` - Base metrics as specified by the MicroProfile Metrics specification
2.  `/metrics?scope=vendor` - Helidon-specific metrics
3.  `/metrics?scope=application` - Application-specific metrics data.

Applications can add their own custom scopes as well simply by specifying a custom scope name when registering a metric.

> [!NOTE]
> The `/metrics` endpoint returns data for all scopes.

The built-in metrics fall into these categories:

1.  JVM behavior (in the base scope), and
2.  basic key performance indicators for request handling (in the vendor scope).

A later section describes the [key performance indicator metrics](#collecting-basic-and-extended-key-performance-indicator-kpi-metrics) in detail.

The following example demonstrates how to use the other built-in metrics. All examples are executed from the root directory of your project (helidon-quickstart-mp).

*Build the application and then run it:*

``` bash
mvn package
java -jar target/helidon-quickstart-mp.jar
```

> [!NOTE]
> Metrics output can be returned in either text format (the default), or JSON. The text format uses OpenMetrics (Prometheus) Text Format, see <https://prometheus.io/docs/instrumenting/exposition_formats/#text-format-details>.

*Verify the metrics endpoint in a new terminal window:*

``` bash
curl http://localhost:8080/metrics
```

*Text response (partial):*

``` text
# HELP classloader_loadedClasses_count Displays the number of classes that are currently loaded in the Java virtual machine.
# TYPE classloader_loadedClasses_count gauge
classloader_loadedClasses_count{mp_scope="base",} 4878.0
# HELP classloader_unloadedClasses_total Displays the total number of classes unloaded since the Java virtual machine has started execution.
# TYPE classloader_unloadedClasses_total counter
classloader_unloadedClasses_total{mp_scope="base",} 0.0
# HELP classloader_loadedClasses_total Displays the total number of classes that have been loaded since the Java virtual machine has started execution.
# TYPE classloader_loadedClasses_total counter
classloader_loadedClasses_total{mp_scope="base",} 4878.0
# HELP vthreads_submitFailures Virtual thread submit failures since metrics start-up
# TYPE vthreads_submitFailures gauge
vthreads_submitFailures{mp_scope="base",} 0.0
# HELP vthreads_pinned Number of pinned virtual threads since metrics start-up
# TYPE vthreads_pinned gauge
vthreads_pinned{mp_scope="base",} 0.0
```

You can get the same data in JSON format.

*Verify the metrics endpoint with an HTTP accept header:*

``` bash
curl -H "Accept: application/json"  http://localhost:8080/metrics
```

*JSON response (partial):*

``` json
{
  "application": {
    "personalizedGets": 0,
    "allGets": {
      "count": 0,
      "elapsedTime": 0,
      "max": 0,
      "mean": 0
    }
  },
  "vendor": {
    "requests.count": 2
  },
  "base": {
    "gc.total;name=G1 Concurrent GC": 2,
    "cpu.systemLoadAverage": 10.3388671875,
    "classloader.loadedClasses.count": 8224,
    "thread.count": 19,
    "vthreads.pinned": 0,
    "classloader.unloadedClasses.total": 0,
    "jvm.uptime": 36.8224,
    "vthreads.submitFailures": 0
  }
}
```

You can get a single metric by specifying the scope and name as query parameters in the URL.

*Get the Helidon `requests.count` metric:*

``` bash
curl -H "Accept: application/json"  'http://localhost:8080/metrics?scope=vendor&name=requests.count'
```

*JSON response:*

``` json
{
  "requests.count": 6
}
```

The `base` metrics illustrated above provide some insight into the behavior of the JVM in which the server runs.

The `vendor` metric shown above gives an idea of the request traffic the server is handling. See the [later section](#collecting-basic-and-extended-key-performance-indicator-kpi-metrics) for more information on the basic and extended key performance indicator metrics.

### Controlling Metrics Behavior

By adding a `metrics` section to your application configuration you can control how the Helidon metrics subsystem behaves in any of several ways.

- [Disable metrics subsystem entirely](#disabling-metrics-subsystem-entirely).
- [Control `REST.request` metrics.](#controlling-restrequest-metrics)
- Select whether to collect [extended key performance indicator metrics](#collecting-basic-and-extended-key-performance-indicator-kpi-metrics).
- Control reporting of [virtual threads metrics](#configuring-virtual-threads-metrics).

#### Disabling Metrics Subsystem Entirely

You can disable the metrics subsystem entirely using configuration:

*Configuration properties file disabling metrics*

``` properties
metrics.enabled=false
```

With metrics processing disabled, Helidon never updates any metrics and the `/metrics` endpoints respond with `404`.

#### Collecting Basic and Extended Key Performance Indicator (KPI) Metrics

Any time you include the Helidon metrics module in your application, Helidon tracks a basic performance indicator metric: a `Counter` of all requests received (`requests.count`).

Helidon MP also includes additional, extended KPI metrics which are disabled by default:

- current number of requests in-flight - a `Gauge` (`requests.inFlight`) of requests currently being processed
- long-running requests - a `Counter` (`requests.longRunning`) measuring the total number of requests which take at least a given amount of time to complete; configurable, defaults to 10000 milliseconds (10 seconds)
- load - a `Counter` (`requests.load`) measuring the number of requests worked on (as opposed to received)
- deferred - a `Gauge` (`requests.deferred`) measuring delayed request processing (work on a request was delayed after Helidon received the request)

You can enable and control these metrics using configuration:

*Configuration properties file controlling extended KPI metrics*

``` properties
metrics.key-performance-indicators.extended = true
metrics.key-performance-indicators.long-running.threshold-ms = 2000
```

#### Controlling Meters Related to Virtual Threads Behavior

Helidon optionally maintains several metrics related to virtual threads as summarized in the next table. Helidon might rely on Java Flight Recorder (JFR) events and JMX MBeans in computing the metric values. Be aware that limitations or changes in the values provided by these sources are outside the control of Helidon.

For performance reasons Helidon does not report virtual thread metrics unless you enable them using configuration.

| Metric name | Usage | Source |
|----|----|----|
| `vthreads.count` | Current number of active virtual threads. | JFR `jdk.virtualThreadStart` and `jdk.virtualThreadEnd` events |
| `vthreads.pinned` | Number of times virtual threads have been pinned. | JFR `jdk.virtualThreadPinned` event |
| `vthreads.recentPinned` | Distribution of the duration of thread pinning. <sup>1</sup> | JFR `jdk.virtualThreadPinned` event |
| `vthreads.started` | Number of virtual threads started. | JFR `jdk.virtualThreadStart` event |
| `vthreads.submitFailed` | Number of times submissions of a virtual thread to a platform carrier thread failed. | JFR `jdk.virtualThreadSubmitFailed` event |

Metrics for Virtual Threads

<sup>1</sup> Distribution summaries can discard stale data, so the `recentPinned` summary might not reflect all thread pinning activity. <sup>1</sup> Distribution summaries can discard stale data, so the `recentPinned` summary might not reflect all thread pinning activity.

#### Configuring Virtual Threads Metrics

##### Enabling Virtual Threads Metrics

Gathering data to compute the metrics for virtual threads is designed to be as efficient as possible, but doing so still imposes a load on the server and by default Helidon does not report metrics related to virtual threads.

To enable the metrics describing virtual threads include a config setting as shown in the following example.

*Enabling virtual thread metrics*

``` properties
metrics.virtual-threads.enabled = true
```

##### Controlling Measurements of Pinned Virtual Threads

Helidon measures pinned virtual threads only when the thread is pinned for a length of time at or above a threshold. Control the threshold as shown in the example below.

*Setting virtual thread pinning threshold to 100 ms*

``` properties
metrics.virtual-threads.pinned.threshold=PT0.100S
```

The threshold value is a `Duration` string, such as `PT0.100S` for 100 milliseconds.

#### Controlling `REST.request` Metrics

Helidon MP implements the optional family of metrics, all with the name `REST.request`, as described in the [MicroProfile Metrics specification](https://download.eclipse.org/microprofile/microprofile-metrics-5.1.1/microprofile-metrics-spec-5.1.1.html#_optional_rest). Each instance is a `Timer` with tags `class` and `method` identifying exactly which REST endpoint Java method that instance measures.

By default, Helidon MP does *not* enable this feature. Enable it by editing your application configuration to set `metrics.rest-request.enabled` to `true`.

Note that the applications you generate using the full Helidon archetype *do* enable this feature in the generated config file. You can see the results in the sample output shown in earlier example runs.

### Metrics Metadata

Each metric has associated metadata that includes:

1.  name: The name of the metric.
2.  units: The unit of the metric such as time (seconds, milliseconds), size (bytes, megabytes), etc.
3.  a description of the metric.

You can get the metadata for any scope, such as `/metrics?scope=base`, as shown below:

*Get the metrics metadata using HTTP OPTIONS method:*

``` bash
 curl -X OPTIONS -H "Accept: application/json"  'http://localhost:8080/metrics?scope=base'
```

*JSON response (truncated):*

``` json
{
   "classloader.loadedClasses.count": {
      "type": "gauge",
      "description": "Displays the number of classes that are currently loaded in the Java virtual machine."
    },
   "jvm.uptime": {
      "type": "gauge",
      "unit": "seconds",
      "description": "Displays the start time of the Java virtual machine in milliseconds. This attribute displays the approximate time when the Java virtual machine started."
    },
   "memory.usedHeap": {
      "type": "gauge",
      "unit": "bytes",
      "description": "Displays the amount of used heap memory in bytes."
    }
}
```

### Application-Specific Metrics Data

You can create application-specific metrics and integrate them with Helidon using CDI. To add a new metric, simply annotate the JAX-RS resource with one of the metric annotations. Metrics can be injected at the class, method, and field-levels. This document shows examples of all three.

Helidon will automatically create and register annotated application metrics and store them in the application `MetricRegistry`, which also contains the metric metadata. The metrics will exist for the lifetime of the application. Each metric annotation has mandatory and optional fields. The name field, for example, is optional.

#### Method Level Metrics

There are two metrics that you can use by annotating a method:

1.  `@Counted` - Register a `Counter` metric
2.  `@Timed` - Register a `Timer` metric

The following example will demonstrate how to use the `@Counted` annotation to track the number of times the `/cards` endpoint is called.

*Create a new class `GreetingCards` with the following code:*

``` java
@Path("/cards") 
@RequestScoped 
public class GreetingCards {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Counted(name = "any-card")  
    public JsonObject anyCard() throws InterruptedException {
        return createResponse("Here are some random cards ...");
    }

    private JsonObject createResponse(String msg) {
        return JSON.createObjectBuilder().add("message", msg).build();
    }
}
```

- This class is annotated with `Path` which sets the path for this resource as `/cards`.
- The `@RequestScoped` annotation defines that this bean is request scoped. The request scope is active only for the duration of one web service invocation, and it is destroyed at the end of that invocation.
- The annotation `@Counted` will register a `Counter` metric for this method, creating it if needed. The counter is incremented each time the anyCards method is called. The `name` attribute is optional.

*Build and run the application, then invoke the application endpoints below:*

``` bash
curl http://localhost:8080/cards
curl http://localhost:8080/cards
curl -H "Accept: application/json"  'http://localhost:8080/metrics?scope=application'
```

*JSON response (partial):*

``` json
{
  "io.helidon.examples.quickstart.mp.GreetingCards.any-card":2 
}
```

- The any-card count is two, since you invoked the endpoint twice.

> [!NOTE]
> Notice the counter is fully qualified. You can remove the package prefix by using the `absolute=true` field in the `@Counted` annotation. You must use `absolute=false` for class-level annotations.

#### Additional Method Level Metrics

The `@Timed` annotation can also be used with a method. For the following example. you can just annotate the same method with `@Timed`. These metrics collect significant information about the measured methods, but at a cost of some overhead and more complicated output.

Note that when using multiple annotations on a method, you **must** give the metrics different names as shown below.

*Replace the `GreetingCards` class with the following code:*

``` java
@Path("/cards")
@RequestScoped
public class GreetingCards {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Counted(name = "cardCount", absolute = true) 
    @Timed(name = "cardTimer", absolute = true, unit = MetricUnits.MILLISECONDS) 
    public JsonObject anyCard() {
        return createResponse("Here are some random cards ...");
    }

    private JsonObject createResponse(String msg) {
        return JSON.createObjectBuilder().add("message", msg).build();
    }
}
```

- Specify a custom name for the `Counter` metric and set `absolute=true` to remove the path prefix from the name.
- Add the `@Timed` annotation to get a `Timer` metric.

*Build and run the application, then invoke the application endpoints below:*

``` bash
curl http://localhost:8080/cards
curl http://localhost:8080/cards
curl -H "Accept: application/json"  'http://localhost:8080/metrics?scope=application'
```

*JSON response (partial):*

``` json
{
  "cardTimer": {
    "count": 2,
    "max": 0.002921992,
    "mean": 0.0014682555,
    "elapsedTime": 0.002936511,
    "p0.5": 1.4336e-05,
    "p0.75": 0.003014144,
    "p0.95": 0.003014144,
    "p0.98": 0.003014144,
    "p0.99": 0.003014144,
    "p0.999": 0.003014144
  },
  "cardCount": 2
}
```

#### Reusing Metrics

You can share a metric across multiple endpoints simply by specifying the same metric annotation as demonstrated below.

*Replace the `GreetingCards` class with the following code:*

``` java
@Path("/cards")
@RequestScoped
public class GreetingCards {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Counted(name = "anyCard", absolute = true)
    public JsonObject anyCard() throws InterruptedException {
        return createResponse("Here are some cards ...");
    }

    @GET
    @Path("/birthday")
    @Produces(MediaType.APPLICATION_JSON)
    @Counted(name = "specialEventCard", absolute = true)  
    public JsonObject birthdayCard() throws InterruptedException {
        return createResponse("Here are some birthday cards ...");
    }

    @GET
    @Path("/wedding")
    @Produces(MediaType.APPLICATION_JSON)
    @Counted(name = "specialEventCard", absolute = true)  
    public JsonObject weddingCard() throws InterruptedException {
        return createResponse("Here are some wedding cards ...");
    }

    private JsonObject createResponse(String msg) {
        return JSON.createObjectBuilder().add("message", msg).build();
    }
}
```

- The `/birthday` endpoint uses a `Counter` metric, named `specialEventCard`.
- The `/wedding` endpoint uses the same `Counter` metric, named `specialEventCard`.

*Build and run the application, then invoke the following endpoints:*

``` bash
curl  http://localhost:8080/cards/wedding
curl  http://localhost:8080/cards/birthday
curl  http://localhost:8080/cards
curl -H "Accept: application/json"  'http://localhost:8080/metrics?scope=application'
```

*JSON response (partial):*

``` json
{
  "anyCard": 1,
  "specialEventCard": 2 
}
```

- Notice that `specialEventCard` count is two, since you accessed `/cards/wedding` and `/cards/birthday`.

#### Class Level Metrics

You can collect metrics at the class-level to aggregate data from all methods in that class using the same metric. The following example introduces a metric to count all card queries. In the following example, the method-level metrics are not needed to aggregate the counts, but they are left in the example to demonstrate the combined output of all three metrics.

*Replace the `GreetingCards` class with the following code:*

``` java
@Path("/cards")
@RequestScoped
@Counted(name = "totalCards") 
public class GreetingCards {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Counted(absolute = true) 
    public JsonObject anyCard() throws InterruptedException {
        return createResponse("Here are some random cards ...");
    }

    @Path("/birthday")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Counted(absolute = true) 
    public JsonObject birthdayCard() throws InterruptedException {
        return createResponse("Here are some birthday cards ...");
    }

    private JsonObject createResponse(String msg) {
        return JSON.createObjectBuilder().add("message", msg).build();
    }
}
```

- This class is annotated with `@Counted`, which aggregates count data from all the method that have a `Count` annotation.
- Use `absolute=true` to remove path prefix for method-level annotations.
- Add a method with a `Counter` metric to get birthday cards.

*Build and run the application, then invoke the following endpoints:*

``` bash
curl http://localhost:8080/cards
curl http://localhost:8080/cards/birthday
curl -H "Accept: application/json"  'http://localhost:8080/metrics?scope=application'
```

*JSON response (partial):*

``` json
{
  "anyCard": 1,
  "birthdayCard": 1,
  "io.helidon.examples.quickstart.mp.totalCards.GreetingCards": 2 
}
```

- The `totalCards` count is a total of all the method-level `Counter` metrics. Class level metric names are always fully qualified.

#### Field Level Metrics

Field level metrics can be injected into managed objects, but they need to be updated by the application code. This annotation can be used on fields of type `Timer`, `Counter`, and `Histogram`.

The following example shows how to use a field-level `Counter` metric to track cache hits.

*Replace the `GreetingCards` class with the following code:*

``` java
@Path("/cards")
@RequestScoped
@Counted(name = "totalCards")
public class GreetingCards {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

    @Inject
    @Metric(name = "cacheHits", absolute = true) 
    private Counter cacheHits;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Counted(absolute = true)
    public JsonObject anyCard() throws InterruptedException {
        updateStats(); 
        return createResponse("Here are some random cards ...");
    }

    @Path("/birthday")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Counted(absolute = true)
    public JsonObject birthdayCard() throws InterruptedException {
        updateStats();  
        return createResponse("Here are some birthday cards ...");
    }

    private JsonObject createResponse(String msg) {
        return JSON.createObjectBuilder().add("message", msg).build();
    }

    private void updateStats() {
        if (new Random().nextInt(3) == 1) {
            cacheHits.inc(); 
        }
    }
}
```

- A `Counter` metric field, `cacheHits`, is automatically injected by Helidon.
- Call `updateStats()` to update the cache hits.
- Call `updateStats()` to update the cache hits.
- Randomly increment the `cacheHits` counter.

*Build and run the application, then invoke the following endpoints:*

``` bash
curl http://localhost:8080/cards
curl http://localhost:8080/cards
curl http://localhost:8080/cards/birthday
curl http://localhost:8080/cards/birthday
curl http://localhost:8080/cards/birthday
curl -H "Accept: application/json"  'http://localhost:8080/metrics?scope=application'
```

*JSON response (partial):*

``` json
{
  "anyCard": 2,
  "birthdayCard": 3,
  "cacheHits": 2, 
  "io.helidon.examples.quickstart.mp.totalCards.GreetingCards": 5
}
```

- The cache was hit two times out of five queries.

#### Gauge Metric

The `Gauge` metric measures a value that is maintained by code outside the metrics subsystem. As with other metrics, the application explicitly registers a gauge. When the `/metrics` endpoint is invoked, Helidon retrieves the value of each registered `Gauge`.

The following example demonstrates how to use a `Gauge` to track application up-time.

*Create a new `GreetingCardsAppMetrics` class with the following code:*

``` java
@ApplicationScoped 
public class GreetingCardsAppMetrics {

    private AtomicLong startTime = new AtomicLong(0); 

    public void onStartUp(@Observes @Initialized(ApplicationScoped.class) Object init) {
        startTime = new AtomicLong(System.currentTimeMillis()); 
    }

    @Gauge(unit = "TimeSeconds")
    public long appUpTimeSeconds() {
        return Duration.ofMillis(System.currentTimeMillis() - startTime.get()).getSeconds();  
    }
}
```

- This managed object must be application scoped to properly register and use the `Gauge` metric.
- Declare an `AtomicLong` field to hold the start time of the application.
- Initialize the application start time.
- Return the application `appUpTimeSeconds` metric, which will be included in the application metrics.

*Update the `GreetingCards` class with the following code to simplify the metrics output:*

``` java
@Path("/cards")
@RequestScoped
public class GreetingCards {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Counted(name = "cardCount", absolute = true)
    public JsonObject anyCard() throws InterruptedException {
        return createResponse("Here are some random cards ...");
    }

    private JsonObject createResponse(String msg) {
        return JSON.createObjectBuilder().add("message", msg).build();
    }
}
```

*Build and run the application, then invoke the application metrics endpoint:*

``` bash
curl -H "Accept: application/json"  http://localhost:8080/metrics/application
```

*JSON response from `/metrics/application`:*

``` json
{
  "cardCount": 0,
  "io.helidon.examples.quickstart.mp.GreetingCardsAppMetrics.appUpTimeSeconds": 6 
}
```

- The application has been running for 6 seconds.

### Integration with Kubernetes and Prometheus

#### Kubernetes Integration

The following example shows how to integrate the Helidon MP application with Kubernetes.

*Stop the application and build the docker image:*

``` bash
docker build -t helidon-metrics-mp .
```

*Create the Kubernetes YAML specification, named `metrics.yaml`, with the following content:*

``` yaml
kind: Service
apiVersion: v1
metadata:
  name: helidon-metrics 
  labels:
    app: helidon-metrics
  annotations:
    prometheus.io/scrape: "true" 
spec:
  type: NodePort
  selector:
    app: helidon-metrics
  ports:
    - port: 8080
      targetPort: 8080
      name: http
---
kind: Deployment
apiVersion: apps/v1
metadata:
  name: helidon-metrics
spec:
  replicas: 1 
  selector:
    matchLabels:
      app: helidon-metrics
  template:
    metadata:
      labels:
        app: helidon-metrics
        version: v1
    spec:
      containers:
        - name: helidon-metrics
          image: helidon-metrics-mp
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 8080
```

- A service of type `NodePort` that serves the default routes on port `8080`.
- An annotation that will allow Prometheus to discover and scrape the application pod.
- A deployment with one replica of a pod.

*Create and deploy the application into Kubernetes:*

``` bash
kubectl apply -f ./metrics.yaml
```

*Get the service information:*

``` bash
kubectl get service/helidon-metrics
```

``` bash
NAME             TYPE       CLUSTER-IP      EXTERNAL-IP   PORT(S)          AGE
helidon-metrics   NodePort   10.99.159.2   <none>        8080:31143/TCP   8s 
```

- A service of type `NodePort` that serves the default routes on port `31143`.

*Verify the metrics endpoint using port `30116`, your port will likely be different:*

``` bash
curl http://localhost:31143/metrics
```

> [!NOTE]
> Leave the application running in Kubernetes since it will be used for Prometheus integration.

#### Prometheus Integration

The metrics service that you just deployed into Kubernetes is already annotated with `prometheus.io/scrape:`. This will allow Prometheus to discover the service and scrape the metrics. This example shows how to install Prometheus into Kubernetes, then verify that it discovered the Helidon metrics in your application.

*Install Prometheus and wait until the pod is ready:*

``` bash
helm install stable/prometheus --name metrics
export POD_NAME=$(kubectl get pods --namespace default -l "app=prometheus,component=server" -o jsonpath="{.items[0].metadata.name}")
kubectl get pod $POD_NAME
```

You will see output similar to the following. Repeat the `kubectl get pod` command until you see `2/2` and `Running`. This may take up to one minute.

``` bash
metrics-prometheus-server-5fc5dc86cb-79lk4   2/2     Running   0          46s
```

*Create a port-forward, so you can access the server URL:*

``` bash
kubectl --namespace default port-forward $POD_NAME 7090:9090
```

Now open your browser and navigate to `http://localhost:7090/targets`. Search for helidon on the page, and you will see your Helidon application as one of the Prometheus targets.

#### Final Cleanup

You can now delete the Kubernetes resources that were just created during this example.

*Delete the Prometheus Kubernetes resources:*

``` bash
helm delete --purge metrics
```

*Delete the application Kubernetes resources:*

``` bash
kubectl delete -f ./metrics.yaml
```

### Summary

This guide demonstrated how to use metrics in a Helidon MP application using various combinations of metrics and scopes.

- Access metrics for all three scopes: base, vendor, and application
- Configure application metrics at the class, method, and field-level
- Integrate Helidon metrics with Kubernetes and Prometheus

Refer to the following references for additional information:

- [MicroProfile Metrics specification](https://download.eclipse.org/microprofile/microprofile-metrics-5.1.1/microprofile-metrics-spec-5.1.1.html)
- [MicroProfile Metrics Javadoc](https://download.eclipse.org/microprofile/microprofile-metrics-5.1.1/apidocs)
- Helidon Javadoc at /apidocs/index.html?overview-summary.html
