# Helidon SE WebClient Guide

This guide describes how to create a sample Helidon SE project that can be used
to run some basic examples using WebClient.

## What you need

For this 15 minute tutorial, you will need the following:

| Requirement                                     | Description                                                                       |
|-------------------------------------------------|-----------------------------------------------------------------------------------|
| [Java 21][java-21] ([Open JDK 21][open-jdk-21]) | Helidon requires Java 21+ (25+ recommended).                                      |
| [Maven 3.8+][maven-3-8]                         | Helidon requires Maven 3.8+.                                                      |
| [Docker 18.09+][docker-18-09]                   | If you want to build and run Docker containers.                                   |
| [Kubectl 1.16.5+][kubectl-1-16-5]               | If you want to deploy to Kubernetes, you need `kubectl` and a Kubernetes cluster. |

Prerequisite product versions for Helidon 4.4.0-SNAPSHOT

Verify Prerequisites:

```shell [Terminal]
java -version
mvn --version
docker --version
kubectl version
```

Setting JAVA_HOME:

```shell [Terminal]
# On Mac
export JAVA_HOME=`/usr/libexec/java_home -v 21`

# On Linux
# Use the appropriate path to your JDK
export JAVA_HOME=/usr/lib/jvm/jdk-21
```

- [WebClient features](#webclient-features)
- [WebClient usage](#webclient-usage)
- [WebClient Metrics](#webclient-metrics)

### WebClient Features

Helidon’s WebClient is used to perform HTTP REST requests to target endpoints
and handle their responses.

WebClient provides the following features:

- **User-friendly**: Every client and request is created by a builder pattern,
  so it improves readability and code maintenance.
- **Following redirects**: The WebClient is able to follow the redirect chain
  and perform requests on the correct endpoint for you. You no longer have to
  point your client to the correct/final endpoint.
- **Tracing, metrics and security propagation**: When you configure the Helidon
  WebServer to use tracing, metrics and security, the settings are automatically
  propagated to the WebClient and used during request/response.

For more information about the `WebClient`, please refer to the [WebClient
Introduction](../webclient.md).

### WebClient Usage

#### Create a sample SE project

Generate the project sources using the Helidon SE Maven archetype. The result is
a simple project that can be used for the examples in this guide.

Run the Maven archetype:

```shell [Terminal]
mvn -U archetype:generate -DinteractiveMode=false \
    -DarchetypeGroupId=io.helidon.archetypes \
    -DarchetypeArtifactId=helidon-quickstart-se \
    -DarchetypeVersion=4.4.0-SNAPSHOT \
    -DgroupId=io.helidon.examples \
    -DartifactId=helidon-quickstart-se \
    -Dpackage=io.helidon.examples.quickstart.se
```

You should now have a directory called `helidon-quickstart-se`.

Open this directory:

```shell [Terminal]
cd helidon-quickstart-se
```

The Helidon quickstart is a greeting application supporting several HTTP
requests such as GET and PUT. Using it will be time-saving for this exercise as
it will allow us to modify the project to demonstrate some of the WebClient
features and usability, rather than start from scratch.

The quickstart example utilizes `WebClient` solely for testing purposes, with
the dependency configured under the test scope. To use `WebClient` within your
application, remove the test scope from the dependency in the `pom.xml`.

Remove the test scope from WebClient dependency:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webclient</groupId>
  <artifactId>helidon-webclient</artifactId>
</dependency>
```

#### Add ClientExample class

In `io.helidon.examples.quickstart.se` package, create a new class named
ClientExample. This class will use the WebClient to send request to the greeting
application.

Create ClientExample class:

```java
public class ClientExample {

    public static void main(String[] args) {

    }
}
```

Add the following code to the main method to create a WebClient instance. The
builder approach allows you to create the WebClient with specific settings and
improves the readability and simplicity of the code.

Add WebClient instance to the main method:

<!--@mdc ::code-callout -->
```java
WebClient webClient = WebClient.builder()
        .baseUri("http://localhost:8080") // <1>
        .build();
```
1. The base URI of the outbound requests.
<!--@mdc :: -->
some reason the host name or port number of the quickstart application is
changed, make sure that the baseURI is also modified to reflect that change.
Once built, the WebClient can be used to send a GET request to the greeting
application.

Send a GET request to the target endpoint:

<!--@mdc ::code-callout -->
```java
ClientResponseTyped<String> response = webClient.get() // <1>
        .path("/greet") // <2>
        .request(String.class); // <3>
String entityString = response.entity(); // <4>
System.out.println(entityString);
```
1. Create an HTTP GET request.
2. Target endpoint path.
3. Execute the request
4. Return response entity handled as a String.
<!--@mdc :: -->
request URI becoming `http://localhost:8080/greet`. The received response entity
will be a greeting message and will be automatically handled as a String. If no
specific type is set in the method request(), `HttpClientResponse` will be
returned by default. This `HttpClientResponse` object contains response code,
headers and entity.

#### Run the application

Build the quickstart:

```shell [Terminal]
mvn package
```

This command will create helidon-quickstart-se.jar in the target folder.

Run the greeting application:

```shell [Terminal]
java -cp target/helidon-quickstart-se.jar io.helidon.examples.quickstart.se.Main
```

Open a new command prompt or terminal and run the ClientExample class you just
created.

Run the client application:

```shell [Terminal]
java -cp target/helidon-quickstart-se.jar io.helidon.examples.quickstart.se.ClientExample
```

```json [Response]
{"message":"Hello World!"}
```

When the ClientExample finishes its execution, you can stop the Main class by
pressing `CTRL+C`.

#### Discover other WebClient functionality

In practice, String is not the most useful return type, since it usually needs
some more handling. In this case, it could be more interesting to return an
object of another type like a JSON object. One way to process a JSON object is
by enabling Helidon’s built-in JSON-P support and this can be simply achieved by
adding its dependency in the project’s pom.xml:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.http.media</groupId>
  <artifactId>helidon-http-media-jsonp</artifactId>
</dependency>
```

Once the dependency is added, the feature will be automatically loaded as a
service allowing the response methods to easily parse the JSON object.

Replace String with JsonObject:

<!--@mdc ::code-callout -->
```java
ClientResponseTyped<JsonObject> response = webClient.get()
        .path("/greet/David")
        .request(JsonObject.class); // <1>
String value = response.entity().getString("message"); // <2>
System.out.println(value);
```
1. Request a JsonObject as return value.
2. Extract the value of the JsonObject with name of `message`.
<!--@mdc :: -->
the application to greet someone.

Output:

```shell [Terminal]
Hello David!
```

It is also possible to change the greeting word by using a PUT request to
`/greet/greeting` path. The request also needs to include a body with JSON type
and using a structure like `{"greeting" : "value"}`.

Modify the application greeting:

<!--@mdc ::code-callout -->
```java
JsonObject entity = Json.createObjectBuilder() // <1>
        .add("greeting", "Bonjour")
        .build();
webClient.put() // <2>
        .path("/greet/greeting")
        .submit(entity); // <3>
ClientResponseTyped<JsonObject> response = webClient.get() // <4>
        .path("/greet/David")
        .request(JsonObject.class);
String entityString = response.entity().getString("message"); // <5>
System.out.println(entityString);
```
1. Create a JsonObject with key `greeting` and value `bonjour`.
2. Create a PUT request.
3. Submit the JsonObject created earlier.
4. Execute a GET call to verify that the greeting has been changed.
5. Retrieve the greeting message from the JSON object
<!--@mdc :: -->
has been changed.

Output:

```shell [Terminal]
Bonjour David!
```

### WebClient Metrics

WebClient, like other Helidon components, supports Metrics. The following
example introduces a counter metric that can be used to measure WebClient
request activity. There are two ways to set up metrics, programmatically on the
WebClient instance or manually using the configuration file.

#### Add metrics dependency

To enable support for this feature, the `helidon-webclient-metrics` dependency
needs to be added .

Add the following dependency to pom.xml:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webclient</groupId>
  <artifactId>helidon-webclient-metrics</artifactId>
</dependency>
```

#### Set up metrics on WebClient instance

Metrics can be registered on the WebClient directly. The following example shows
how a `Counter` metric can be defined, created and monitored.

Example of metric creation:

<!--@mdc ::code-callout -->
```java
MeterRegistry METER_REGISTRY = Metrics.globalRegistry();

String metricName = "counter.GET.localhost"; // <1>

Counter counter = METER_REGISTRY.getOrCreate(Counter.builder(metricName)); // <2>
System.out.println(metricName + ": " + counter.count());

WebClientService clientServiceMetric = WebClientMetrics.counter()
        .methods(Method.GET)                // OPTIONAL
        .success(true)                      // OPTIONAL
        .errors(true)                       // OPTIONAL
        .description("Metric Description")  // OPTIONAL
        .nameFormat("counter.%1$s.%2$s") // <3>
        .build(); // <4>
```
1. Specify the metric name.
2. From the `MeterRegistry`, create a Counter metric using the specified metric
   name.
3. Specify how the name of the metric will be generated using the `nameFormat`.
4. Build a WebClient Metric Service that can count number of GET requests made.
<!--@mdc :: -->
requests executed on the `localhost`. The format strings in the parameter value
of `nameFormat` method will identify how the name of a metric will get
generated:

- `%1$s` = Request method
- `%2$s` = Request host
- `%3$s` = Response status

So for example, if the `nameFormat` value is `metric.%1$s.%2$s.%3$s` and a
request uses a GET method, targeting a URL with localhost as the hostname, and
got a response code of 200, that the final metric will get created with a name
of metric.GET.localhost.200.

To register the metric service, simply use the `addService` method and pass in
the created WebClient Metric Service as a parameter.

Add the metric service to the WebClient:

<!--@mdc ::code-callout -->
```java
WebClient webClient = WebClient.builder()
        .baseUri("http://localhost:8080")
        .addService(clientServiceMetric) // <1>
        .build();

webClient.get().path("/greet").request(); // <2>
```
1. Register the metric service to the webclient.
2. Send an HTTP GET request
<!--@mdc :: -->
the end of the main method.

Print the metric count:

```java
System.out.println(metricName + ": " + counter.count());
```

This will result to an output showing that a metric with the name of
`counter.GET.localhost` was created with a count value of 1 indicating that it
correctly measured the request that was just made.

Output:

```shell [Terminal]
counter.GET.localhost: 1
```

#### Set up metrics with configuration files

Using the configuration file can reduce the code complexity and make the metrics
simpler to use. With this approach, it eliminates the need to modify the source
code for scenarios where the metric settings have to be changed. The
`application.yaml` file is the default configuration file for Helidon and can be
used to set up metrics settings.

Example of metric configuration:

```yaml
client:
  services:
    metrics:
      - type: COUNTER
        methods: ["GET"]
        description: "Metric Description"
        name-format: "counter.%1$s.%2$s"
```

In the example configuration definition above, the metrics configuration are
located under `client.services.metrics`. The metric setting can start either by
its `type` or `methods`. The configuration file uses the same keywords as the
programmatic way. For example, `type` defines the kind of metric and `methods`
identifies the http methods that will be measured.

Add the metric service to the WebClient via the Configuration:

<!--@mdc ::code-callout -->
```java
MeterRegistry METER_REGISTRY = Metrics.globalRegistry();

String counterName = "counter.GET.localhost"; // <1>

Counter counter = METER_REGISTRY.getOrCreate(Counter.builder(counterName)); // <2>
System.out.println(counterName + ": " + counter.count());

Config config = Config.create(); // <3>

WebClient webClient = WebClient.builder()
        .baseUri("http://localhost:8080")
        .config(config.get("client")) // <4>
        .build();
webClient.get().path("/greet").request(); // <5>
System.out.println(counterName + ": " + counter.count()); // <6>
```
1. Choose the metric name.
2. Create counter metric from `MeterRegistry`.
3. Create a Helidon Config instance from default config file `application.yaml`.
4. Configure the WebClient using the `client` section from `application.yaml`.
5. Send an HTTP GET request
6. Print out the metric result
<!--@mdc :: -->
in the source code. For more information about metrics, see the [Helidon Metrics
Guide](metrics.md).

[java-21]: https://www.oracle.com/technetwork/java/javase/downloads
[open-jdk-21]: http://jdk.java.net
[maven-3-8]: https://maven.apache.org/download.cgi
[docker-18-09]: https://docs.docker.com/install/
[kubectl-1-16-5]: https://kubernetes.io/docs/tasks/tools/install-kubectl/
