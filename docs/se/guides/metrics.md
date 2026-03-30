# Helidon SE Metrics Guide

This guide describes how to create a sample Helidon {h1-prefix} project that can be used to run some basic examples using both built-in and custom meters with Helidon.

## What You Need

For this 30 minute tutorial, you will need the following:

|  |  |
|----|----|
| [Java SE 21](https://www.oracle.com/technetwork/java/javase/downloads) ([Open JDK 21](http://jdk.java.net)) | Helidon requires Java 21+ (25+ recommended). |
| [Maven 3.8+](https://maven.apache.org/download.cgi) | Helidon requires Maven 3.8+. |
| [Docker 18.09+](https://docs.docker.com/install/) | If you want to build and run Docker containers. |
| [Kubectl 1.16.5+](https://kubernetes.io/docs/tasks/tools/install-kubectl/) | If you want to deploy to Kubernetes, you need `kubectl` and a Kubernetes cluster (you can [install one on your desktop](../../about/kubernetes.md)). |
| [Helm](https://github.com/helm/helm) | To manage Kubernetes applications. |

Verify Prerequisites

``` bash
java -version
mvn --version
docker --version
kubectl version
```

Setting JAVA_HOME

``` bash
# On Mac
export JAVA_HOME=`/usr/libexec/java_home -v 21`

# On Linux
# Use the appropriate path to your JDK
export JAVA_HOME=/usr/lib/jvm/jdk-21
```

### Create a Sample Helidon SE Project

Use the Helidon SE Maven archetype to create a simple project that can be used for the examples in this guide.

Run the Maven archetype

``` bash
mvn -U archetype:generate -DinteractiveMode=false \
    -DarchetypeGroupId=io.helidon.archetypes \
    -DarchetypeArtifactId=helidon-quickstart-se \
    -DarchetypeVersion=4.4.0-SNAPSHOT \
    -DgroupId=io.helidon.examples \
    -DartifactId=helidon-quickstart-se \
    -Dpackage=io.helidon.examples.quickstart.se
```

### Using the Built-In Meters

Helidon provides three built-in scopes of metrics: base, vendor, and application. Here are the metric endpoints:

1.  `/observe/metrics?scope=base` - Base meters
2.  `/observe/metrics?scope=vendor` - Helidon-specific meters
3.  `/observe/metrics?scope=application` - Application-specific metrics data.

Applications can add their own custom scopes as well simply by specifying a custom scope name when registering a meter.

> [!NOTE]
> The `/observe/metrics` endpoint returns data for all scopes.

The built-in meters fall into these categories:

1.  JVM behavior (in the base scope), and
2.  basic key performance indicators for request handling (in the vendor scope).

A later section describes the [key performance indicator meters](#collecting-basic-and-extended-key-performance-indicator-kpi-metrics) in detail.

The following example demonstrates how to use the other built-in meters. All examples are executed from the root directory of your project (helidon-quickstart-se).

The generated source code is already configured for both metrics and health checks, but the following example removes health checks.

Metrics dependencies in the generated `pom.xml`:

``` xml
<dependencies>
    <dependency>
        <groupId>io.helidon.webserver.observe</groupId>
        <artifactId>helidon-webserver-observe-metrics</artifactId> <!--(1)-->
    </dependency>
    <dependency>
        <groupId>io.helidon.metrics</groupId>
        <artifactId>helidon-metrics-system-meters</artifactId>     <!--(2)-->
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

1.  Includes the Helidon observability component for metrics and, as transitive dependencies, the Helidon neutral metrics API and a full-featured implementation of the API.
2.  Includes the built-in meters.

With these dependencies in your project, Helidon’s auto-discovery of webserver features automatically finds and runs the metrics subsystem. You do not need to change any of the generated source code.

Build the application and then run it:

``` bash
mvn package
java -jar target/helidon-quickstart-se.jar
```

> [!NOTE]
> Metrics output can be returned in either text format (the default), or JSON.
> The text format uses OpenMetrics (Prometheus) Text Format, see https://prometheus.io/docs/instrumenting/exposition_formats/#text-format-details

Verify the metrics endpoint in a new terminal window:

``` bash
curl http://localhost:8080/observe/metrics
```

Text response (partial):

``` text
# HELP classloader_loadedClasses_count Displays the number of classes that are currently loaded in the Java virtual machine.
# TYPE classloader_loadedClasses_count gauge
classloader_loadedClasses_count{scope="base",} 4878.0
# HELP classloader_unloadedClasses_total Displays the total number of classes unloaded since the Java virtual machine has started execution.
# TYPE classloader_unloadedClasses_total counter
classloader_unloadedClasses_total{scope="base",} 0.0
# HELP classloader_loadedClasses_total Displays the total number of classes that have been loaded since the Java virtual machine has started execution.
# TYPE classloader_loadedClasses_total counter
classloader_loadedClasses_total{scope="base",} 4878.0
# HELP vthreads_submitFailures Virtual thread submit failures since metrics start-up
# TYPE vthreads_submitFailures gauge
vthreads_submitFailures{scope="base",} 0.0
# HELP vthreads_pinned Number of pinned virtual threads since metrics start-up
# TYPE vthreads_pinned gauge
vthreads_pinned{scope="base",} 0.0
```

You can get the same data in JSON format.

Verify the metrics endpoint with an HTTP accept header:

``` bash
curl -H "Accept: application/json"  http://localhost:8080/observe/metrics
```

JSON response:

``` json
{
  "base": {
    "gc.total;name=G1 Young Generation": 2,
    "cpu.systemLoadAverage": 11.0546875,
    "classloader.loadedClasses.count": 5124.0,
    "thread.count": 23.0,
    "classloader.unloadedClasses.total": 0,
    "vthreads.recentPinned": {
      "count": 0,
      "max": 0.0,
      "mean": 0.0,
      "elapsedTime": 0.0,
      "p0.5": 0.0,
      "p0.75": 0.0,
      "p0.95": 0.0,
      "p0.98": 0.0,
      "p0.99": 0.0,
      "p0.999": 0.0
    },
    "jvm.uptime": 138.233,
    "gc.time;name=G1 Young Generation": 0,
    "memory.committedHeap": 541065216,
    "thread.max.count": 26.0,
    "vthreads.pinned": 0,
    "cpu.availableProcessors": 8.0,
    "classloader.loadedClasses.total": 5124,
    "thread.daemon.count": 20.0,
    "memory.maxHeap": 8589934592,
    "memory.usedHeap": 2.774652E+7,
    "thread.starts": 28.0,
    "vthreads.submitFailures": 0
  },
  "vendor": {
    "requests.count": 3
  }

}
```

You can get a single metric by specifying the scope and name as query parameters in the URL.

Get the Helidon `requests.count` meter:

``` bash
curl -H "Accept: application/json"  'http://localhost:8080/observe/metrics?scope=vendor&name=requests.count'
```

JSON response:

``` json
{
  "requests.count": 6
}
```

The `base` meters illustrated above provide some insight into the behavior of the JVM in which the server runs.

The `vendor` meter shown above gives an idea of the request traffic the server is handling. See the [later section](#collecting-basic-and-extended-key-performance-indicator-kpi-metrics) for more information on the basic and extended key performance indicator meters.

### Controlling Metrics Behavior

By adding a `metrics` section to your application configuration you can control how the Helidon metrics subsystem behaves in any of several ways.

- [Disable metrics subsystem entirely](#disabling-metrics-subsystem-entirely).
- Select whether to collect [extended key performance indicator meters](#collecting-basic-and-extended-key-performance-indicator-kpi-metrics).
- Control reporting of [virtual threads meters](#configuring-virtual-threads-meters).

Your Helidon SE application can also control metrics processing programmatically as described in the following sections.

#### Disabling Metrics Subsystem Entirely

You can disable the metrics subsystem entirely using configuration:

Configuration properties file disabling metrics

``` yaml
server:
  features:
    observe:
      observers:
        metrics:
          enabled: false
```

A Helidon SE application can disable metrics processing programmatically.

Disable all metrics behavior

``` java
ObserveFeature observe = ObserveFeature.builder()   // (1)
        .addObserver(MetricsObserver.builder() // (2)
                             .enabled(false) // (3)
                             .build()) // (4)
        .build(); // (5)

WebServer server = WebServer.builder() // (6)
        .config(Config.global().get("server"))
        .addFeature(observe)
        .routing(Main::routing)
        .build()
        .start();
```

1.  Begin preparing the `ObserveFeature`.
2.  Begin preparing the `MetricsObserver`.
3.  Disable metrics.
4.  Complete the `MetricsObserver`.
5.  Complete the `ObserveFeature`.
6.  Create and start the `WebServer` with the `ObserveFeature` (and other settings).

These builders and interfaces also have methods which accept `Config` objects representing the `metrics` node from the application configuration.

With metrics processing disabled, Helidon never updates any meters and the `/observe/metrics` endpoints respond with `404`.

#### Collecting Basic and Extended Key Performance Indicator (KPI) Metrics

Any time you include the Helidon metrics module in your application, Helidon tracks a basic performance indicator meter: a `Counter` of all requests received (`requests.count`).

Helidon SE also includes additional, extended KPI metrics which are disabled by default:

- current number of requests in-flight - a `Gauge` (`requests.inFlight`) of requests currently being processed
- long-running requests - a `Counter` (`requests.longRunning`) measuring the total number of requests which take at least a given amount of time to complete; configurable, defaults to 10000 milliseconds (10 seconds)
- load - a `Counter` (`requests.load`) measuring the number of requests worked on (as opposed to received)
- deferred - a `Gauge` (`requests.deferred`) measuring delayed request processing (work on a request was delayed after Helidon received the request)

You can enable and control these meters using configuration:

``` yaml
server:
  features:
    observe:
      observers:
        metrics:
          key-performance-indicators:
            extended: true
            long-running:
              threshold-ms: 2000
```

Your Helidon SE application can also control the KPI settings programmatically.

Assign KPI metrics behavior from code

``` java
KeyPerformanceIndicatorMetricsConfig kpiConfig =
        KeyPerformanceIndicatorMetricsConfig.builder() // (1)
                .extended(true) // (2)
                .longRunningRequestThreshold(Duration.ofSeconds(4)) // (3)
                .build();

MetricsObserver metrics = MetricsObserver.builder()
        .metricsConfig(MetricsConfig.builder() // (4)
                               .keyPerformanceIndicatorMetricsConfig(kpiConfig)) // (5)
        .build();

ObserveFeature observe = ObserveFeature.builder()
        .config(config.get("server.features.observe"))
        .addObserver(metrics) // (6)
        .build();

WebServer server = WebServer.builder() // (7)
        .config(config.get("server"))
        .addFeature(observe)
        .routing(Main::routing)
        .build()
        .start();
```

1.  Create a [`KeyPerformanceIndicatorMetricsConfig` instance (via its](/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/KeyPerformanceIndicatorMetricsConfig.html) [`Builder`](/apidocs/io.helidon.metrics.api/io/helidon/metrics/api/KeyPerformanceIndicatorMetricsConfig.Builder.html)) with non-default values.
2.  Enabled extended KPI meters.
3.  Set the long-running request threshold.
4.  Prepare the metrics observer’s builder.
5.  Update the metrics observer’s builder using the just-prepared KPI metrics config.
6.  Add the metrics observer to the `ObserveFeature`.
7.  Add the `ObserveFeature` to the `WebServer`.

#### Controlling Meters Related to Virtual Threads Behavior

Helidon optionally maintains several meters related to virtual threads as summarized in the next table. Helidon might rely on Java Flight Recorder (JFR) events and JMX MBeans in computing the meter values. Be aware that limitations or changes in the values provided by these sources are outside the control of Helidon.

For performance reasons Helidon does not report virtual thread meters unless you enable them using configuration.

| Meter name | Usage | Source |
|----|----|----|
| `vthreads.count` | Current number of active virtual threads. | JFR `jdk.virtualThreadStart` and `jdk.virtualThreadEnd` events |
| `vthreads.pinned` | Number of times virtual threads have been pinned. | JFR `jdk.virtualThreadPinned` event |
| `vthreads.recentPinned` | Distribution of the duration of thread pinning. <sup>1</sup> | JFR `jdk.virtualThreadPinned` event |
| `vthreads.started` | Number of virtual threads started. | JFR `jdk.virtualThreadStart` event |
| `vthreads.submitFailed` | Number of times submissions of a virtual thread to a platform carrier thread failed. | JFR `jdk.virtualThreadSubmitFailed` event |

Table 1. Meters for Virtual Threads {.tableblock .frame-all .grid-all .stretch}

<sup>1</sup> Distribution summaries can discard stale data, so the `recentPinned` summary might not reflect all thread pinning activity. <sup>1</sup> Distribution summaries can discard stale data, so the `recentPinned` summary might not reflect all thread pinning activity.

#### Configuring Virtual Threads Meters

##### Enabling Virtual Threads Meters

Gathering data to compute the meters for virtual threads is designed to be as efficient as possible, but doing so still imposes a load on the server and by default Helidon does not report meters related to virtual threads.

To enable the meters describing virtual threads include a config setting as shown in the following example.

Enabling virtual thread meters

``` yaml
metrics:
  virtual-threads:
    enabled: true
```

##### Controlling Measurements of Pinned Virtual Threads

Helidon measures pinned virtual threads only when the thread is pinned for a length of time at or above a threshold. Control the threshold as shown in the example below.

Setting virtual thread pinning threshold to 100 ms

``` yaml
metrics:
  virtual-threads:
    pinned:
      threshold: PT0.100S
```

The threshold value is a `Duration` string, such as `PT0.100S` for 100 milliseconds.

### Metrics Metadata

Each meter has associated metadata that includes:

1.  name: The name of the meter.
2.  units: The unit of the meter such as time (seconds, milliseconds), size (bytes, megabytes), etc.
3.  a description of the meter.

You can get the metadata for any scope, such as `/observe/metrics?scope=base`, as shown below:

Get the metrics metadata using HTTP OPTIONS method:

``` bash
 curl -X OPTIONS -H "Accept: application/json"  'http://localhost:8080/observe/metrics?scope=base'
```

JSON response (truncated):

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

This section demonstrates how to use application-specific meters and integrate them with Helidon, starting from a Helidon SE QuickStart application.

It is the application’s responsibility to create and update the meters at runtime. The application has complete control over when and how each meter is used. For example, an application may use the same counter for multiple methods, or one counter per method. Helidon maintains a single meter registry which holds all meters.

In all of these examples, the code uses a meter builder specific to the type of meter needed to register a new meter or locate a previous-registered meter.

#### Counter Meter

The `Counter` meter is a monotonically increasing number. The following example demonstrates how to use a `Counter` to track the number of times the `/cards` endpoint is called.

Create a new class named `GreetingCards` with the following code:

``` java
public class GreetingCards implements HttpService {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

    private final Counter cardCounter; // (1)

    GreetingCards() {
        cardCounter = Metrics.globalRegistry()
                .getOrCreate(Counter.builder("cardCount")
                                     .description("Counts card retrievals")); // (2)
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/", this::getDefaultMessageHandler);
    }

    private void getDefaultMessageHandler(ServerRequest request, ServerResponse response) {
        cardCounter.increment(); // (3)
        sendResponse(response, "Here are some cards ...");
    }

    private void sendResponse(ServerResponse response, String msg) {
        JsonObject returnObject = JSON.createObjectBuilder().add("message", msg).build();
        response.send(returnObject);
    }
}
```

1.  Declare a `Counter` member field.
2.  Create and register the `Counter` meter in the global meter registry\`. This `Counter` will exist for the lifetime of the application.
3.  Increment the count.

Update the `routing` method in the main class as follows:

``` java
static void routing(HttpRouting.Builder routing) {
    routing
            .register("/greet", new GreetService())
            .register("/cards", new GreetingCards()) // (1)
            .get("/simple-greet", (req, res) -> res.send("Hello World!"));
}
```

1.  Add the `GreetingCards` service to the routing. Helidon routes any REST requests with the `/cards` root path to the `GreetingCards` service.

Build and run the application, then invoke the endpoints below:

``` bash
curl http://localhost:8080/cards
curl -H "Accept: application/json" 'http://localhost:8080/observe/metrics?scope=application'
```

JSON response:

``` json
{
  "cardCount": 1 // (1)
}
```

1.  The count value is one since the method was called once.

#### Timer Meter

The `Timer` meter aggregates durations.

In the following example, a `Timer` meter measures the duration of a method’s execution. Whenever the REST `/cards` endpoint is called, the code updates the `Timer` with additional timing information.

Replace the `GreetingCards` class with the following code:

``` java
public class GreetingCards implements HttpService {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());
    private final Timer cardTimer; // (1)

    GreetingCards() {
        cardTimer = Metrics.globalRegistry()
                .getOrCreate(Timer.builder("cardTimer") // (2)
                                     .description("Times card retrievals"));
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/", this::getDefaultMessageHandler);
    }

    private void getDefaultMessageHandler(ServerRequest request, ServerResponse response) {
        Timer.Sample timerSample = Timer.start(); // (3)
        response.whenSent(() -> timerSample.stop(cardTimer)); // (4)
        sendResponse(response, "Here are some cards ...");
    }

    private void sendResponse(ServerResponse response, String msg) {
        JsonObject returnObject = JSON.createObjectBuilder().add("message", msg).build();
        response.send(returnObject);
    }
}
```

1.  Declare a `Timer` member field.
2.  Create and register the `Timer` metric in the global meter registry.
3.  Create a timer sample which, among other things, automatically records the starting time.
4.  Arrange for the timer sample to be stopped and applied to the `cardTimer` once Helidon sends the response to the client.

Build and run the application, then invoke the endpoints below:

``` bash
curl http://localhost:8080/cards
curl http://localhost:8080/cards
curl -H "Accept: application/json"  'http://localhost:8080/observe/metrics?scope=application'
```

JSON response:

``` json
{
  "cardTimer": {
    "count": 2,
    "max": 0.01439681,
    "mean": 0.0073397075,
    "elapsedTime": 0.014679415,
    "p0.5": 0.000278528,
    "p0.75": 0.01466368,
    "p0.95": 0.01466368,
    "p0.98": 0.01466368,
    "p0.99": 0.01466368,
    "p0.999": 0.01466368
  }
}
```

Helidon updated the timer statistics for each of the two accesses to the `/cards` endpoint.

#### Distribution Summary Meters

The `DistributionSummary` meter calculates the distribution of a set of values within ranges. This meter does not relate to time at all. The following example records a set of random numbers in a `DistributionSummary` meter when the `/cards` endpoint is invoked.

Replace the `GreetingCards` class with the following code:

``` java
public class GreetingCards implements HttpService {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());
    private final DistributionSummary cardSummary; // (1)

    GreetingCards() {
        cardSummary = Metrics.globalRegistry()
                .getOrCreate(DistributionSummary.builder("cardDist")
                                     .description("random card distribution")); // (2)
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/", this::getDefaultMessageHandler);
    }

    private void getDefaultMessageHandler(ServerRequest request, ServerResponse response) {
        Random r = new Random(); // (3)
        for (int i = 0; i < 1000; i++) {
            cardSummary.record(1 + r.nextDouble());
        }
        sendResponse(response, "Here are some cards ...");
    }

    private void sendResponse(ServerResponse response, String msg) {
        JsonObject returnObject = JSON.createObjectBuilder().add("message", msg).build();
        response.send(returnObject);
    }
}
```

1.  Declare a `DistributionSummary` member field.
2.  Create and register the `DistributionSummary` meter in the global meter registry
3.  Update the distribution summary with a random number multiple times for each request.

Build and run the application, then invoke the endpoints below:

``` bash
curl http://localhost:8080/cards
curl -H "Accept: application/json"  'http://localhost:8080/observe/metrics?scope=application'
```

JSON response:

``` json
{
  "cardDist": {
    "count": 1000,
    "max": 1.999805150914427,
    "mean": 1.4971440362723523,
    "total": 1497.1440362723522,
    "p0.5": 1.4375,
    "p0.75": 1.6875,
    "p0.95": 1.9375,
    "p0.98": 1.9375,
    "p0.99": 1.9375,
    "p0.999": 1.9375
  }
}
```

The `DistributionSummary.Builder` allows your code to configure other aspects of the summary, such as bucket boundaries and percentiles to track.

#### Gauge Metric

The `Gauge` meter measures a value that is maintained by code outside the metrics subsystem. As with other meters, the application explicitly registers a gauge. When the `/observe/metrics` endpoint is invoked, Helidon retrieves the value of each registered `Gauge`. The following example demonstrates how a `Gauge` is used to get the current temperature.

Replace the `GreetingCards` class with the following code:

``` java
public class GreetingCards implements HttpService {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

    GreetingCards() {
        Random r = new Random();
        Metrics.globalRegistry()
                .getOrCreate(Gauge.builder("temperature",
                                           () -> r.nextDouble(100.0))
                                     .description("Ambient temperature")); // (1)
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/", this::getDefaultMessageHandler);
    }

    private void getDefaultMessageHandler(ServerRequest request, ServerResponse response) {
        sendResponse(response, "Here are some cards ...");
    }

    private void sendResponse(ServerResponse response, String msg) {
        JsonObject returnObject = JSON.createObjectBuilder().add("message", msg).build();
        response.send(returnObject);
    }
}
```

1.  Register the `Gauge`, passing a `Supplier<Double>` which furnishes a random temperature from 0 to 100.0 each time the metrics system interrogates the gauge.

Build and run the application, then invoke the endpoint below:

``` bash
curl -H "Accept: application/json"  'http://localhost:8080/observe/metrics?scope=application
```

JSON response:

``` json
{
  "temperature": 46.582132737739066 // (1)
}
```

1.  The current (random) temperature. Accessing the endpoint again returns a different value.

### Integration with Kubernetes and Prometheus

#### Kubernetes Integration

The following example shows how to integrate the Helidon SE application with Kubernetes.

Stop the application and build the docker image:

``` bash
docker build -t helidon-metrics-se .
```

Create the Kubernetes YAML specification, named `metrics.yaml`, with the following content:

``` yaml
kind: Service
apiVersion: v1
metadata:
  name: helidon-metrics # (1)
  labels:
    app: helidon-metrics
  annotations:
    prometheus.io/scrape: "true" # (2)
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
  replicas: 1 # (3)
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
          image: helidon-metrics-se
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 8080
```

1.  A service of type `NodePort` that serves the default routes on port `8080`.
2.  An annotation that will allow Prometheus to discover and scrape the application pod.
3.  A deployment with one replica of a pod.

Create and deploy the application into Kubernetes:

``` bash
kubectl apply -f ./metrics.yaml
```

Get the service information:

``` bash
kubectl get service/helidon-metrics
```

``` text
NAME             TYPE       CLUSTER-IP      EXTERNAL-IP   PORT(S)          AGE
helidon-metrics   NodePort   10.99.159.2   <none>        8080:31143/TCP   8s # (1)
```

1.  A service of type `NodePort` that serves the default routes on port `31143`.

Verify the metrics endpoint using port `30116`, your port will likely be different:

``` bash
curl http://localhost:31143/metrics
```

> [!NOTE]
> Leave the application running in Kubernetes since it will be used for Prometheus integration.

#### Prometheus Integration

The metrics service that you just deployed into Kubernetes is already annotated with `prometheus.io/scrape:`. This will allow Prometheus to discover the service and scrape the metrics. This example shows how to install Prometheus into Kubernetes, then verify that it discovered the Helidon metrics in your application.

Install Prometheus and wait until the pod is ready:

``` bash
helm install stable/prometheus --name metrics
export POD_NAME=$(kubectl get pods --namespace default -l "app=prometheus,component=server" -o jsonpath="{.items[0].metadata.name}")
kubectl get pod $POD_NAME
```

You will see output similar to the following. Repeat the `kubectl get pod` command until you see `2/2` and `Running`. This may take up to one minute.

``` text
metrics-prometheus-server-5fc5dc86cb-79lk4   2/2     Running   0          46s
```

Create a port-forward, so you can access the server URL:

``` bash
kubectl --namespace default port-forward $POD_NAME 7090:9090
```

Now open your browser and navigate to <a href="http://localhost:7090/targets" class="bare"><code>http://localhost:7090/targets</code></a>. Search for helidon on the page, and you will see your Helidon application as one of the Prometheus targets.

#### Final Cleanup

You can now delete the Kubernetes resources that were just created during this example.

Delete the Prometheus Kubernetes resources:

``` bash
helm delete --purge metrics
```

Delete the application Kubernetes resources:

``` bash
kubectl delete -f ./metrics.yaml
```

### Summary

This guide demonstrated how to use metrics in a Helidon SE application using various combinations of meters and scopes.

- Access meters for all three built-in scopes: base, vendor, and application
- Configure meters that are updated by the application when an application REST endpoint is invoked
- Configure a `Gauge` meter
- Integrate Helidon metrics with Kubernetes and Prometheus

Refer to the following references for additional information:

- [Helidon Javadoc](/apidocs/index.html?overview-summary.html)
