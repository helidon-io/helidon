# Helidon MP Tutorial

This tutorial describes how to build a Helidon MicroProfile (MP) application from scratch including JSON REST endpoints, metrics, health check, and configuration.

## What You Need

For this 30 minute tutorial, you will need the following:

|  |  |
|----|----|
| [Java SE 21](https://www.oracle.com/technetwork/java/javase/downloads) ([Open JDK 21](http://jdk.java.net)) | Helidon requires Java 21+ (25+ recommended). |
| [Maven 3.8+](https://maven.apache.org/download.cgi) | Helidon requires Maven 3.8+. |
| [Docker 18.09+](https://docs.docker.com/install/) | If you want to build and run Docker containers. |
| [Kubectl 1.16.5+](https://kubernetes.io/docs/tasks/tools/install-kubectl/) | If you want to deploy to Kubernetes, you need `kubectl` and a Kubernetes cluster (you can [install one on your desktop](../../about/kubernetes.md)). |
| [curl](https://curl.se/download.html) | (Optional) for testing |

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

## Create the Maven Project

This tutorial demonstrates how to create the application from scratch, without using the Maven archetypes as a quickstart.

Create a new empty directory for the project (for example, `helidon-mp-tutorial`). Change into this directory.

Create a new Maven POM file (called `pom.xml`) and add the following content:

*Initial Maven POM file*

``` xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent> 
        <groupId>io.helidon.applications</groupId>
        <artifactId>helidon-mp</artifactId>
        <version>4.4.0-SNAPSHOT</version>
        <relativePath/>
    </parent>

    <groupId>io.helidon.examples</groupId>
    <artifactId>helidon-mp-tutorial</artifactId> 
    <name>${project.artifactId}</name>

    <properties>
        <mainClass>io.helidon.examples.Main</mainClass> 
    </properties>

    <dependencies>
        <dependency>
            <groupId>io.helidon.microprofile.bundles</groupId>
            <artifactId>helidon-microprofile</artifactId> 
        </dependency>
    </dependencies>

    <build> 
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-dependency-plugin</artifactId>
                <executions>
                    <execution>
                        <id>copy-libs</id>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>io.smallrye</groupId>
                <artifactId>jandex-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <id>make-index</id>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

The POM file contains the basic project information and configurations needed to get started and does the following:

- Includes the Helidon MP application parent pom. This parent pom contains dependency and plugin management to keep your application’s pom simple and clean.
- Establishes the Maven coordinates for the new project.
- Sets the `mainClass` which will be used later when building a JAR file. The class will be created later in this tutorial.
- Adds a dependency for the MicroProfile bundle which allows the use of MicroProfile features in the application. The helidon-mp parent pom includes dependency management, so you don’t need to include a version number here. You will automatically use the version of Helidon that matches the version of the parent pom (4.4.0-SNAPSHOT in this case).
- Adds plugins to be executed during the build. The `maven-dependency-plugin` is used to copy the runtime dependencies into your target directory. The `jandex-maven-plugin` builds an index of your class files for faster loading. The Helidon parent pom handles the details of configuring these plugins. But you can modify the configuration here.

> [!TIP]
> MicroProfile contains features like Metrics, Health Check, Streams Operators, Open Tracing, OpenAPI, REST client, and fault tolerance. You can find detailed information about MicroProfile on the [Eclipse MicroProfile](https://projects.eclipse.org/projects/technology.microprofile) site.

With this `pom.xml`, the application can be built successfully with Maven:

``` bash
mvn clean package
```

This will create a JAR file in the `target` directory.

> [!TIP]
> The warning message `JAR will be empty - no content was marked for inclusion!` can be ignored for now because there is no actual content in the application yet.

## Start Implementing the MicroProfile Application

The actual application logic can be created now. Create a directory for your source code, and then create directories for the package hierarchy:

*Create directories for source code*

``` bash
mkdir -p src/main/java/io/helidon/examples
```

The application will be a simple REST service that will return a greeting to the caller. The first iteration of the application will contain a resource class and a Main class which will be used to start up the Helidon server and the application.

> [!TIP]
> Technically, your own main class is not needed unless you want to control the startup sequence. You can set the `mainClass` property to `io.helidon.microprofile.cdi.Main` and it will use Helidon’s default main class.

The `GreetResource` is defined in the `GreetResource.java` class as shown below:

*src/main/java/io/helidon/examples/GreetResource.java*

``` java
@Path("/greet") 
@RequestScoped 
public class GreetResource {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject getDefaultMessage() { 
        return JSON.createObjectBuilder()
                .add("message", "Hello World")
                .build(); 
    }

}
```

- This class is annotated with `Path` which sets the path for this resource as `/greet`.
- The `RequestScoped` annotation defines that this bean is request scoped. The request scope is active only for the duration of one web service invocation, and it is destroyed at the end of that invocation. You can learn more about scopes and contexts, and how they are used from the [Specification](https://jakarta.ee/specifications/cdi/4.0/jakarta-cdi-spec-4.0.html).
- A `public JsonObject getDefaultMessage()` method is defined which is annotated with `GET`, meaning it will accept the HTTP GET method. It is also annotated with `Produces(MediaType.APPLICATION_JSON)` which declares that this method will return JSON data.
- The method body creates a JSON object containing a single object named "message" with the content "Hello World". This method will be expanded and improved later in the tutorial.

> [!TIP]
> So far this is just a JAX-RS application, with no Helidon or MicroProfile specific code in it. There are many JAX-RS tutorials available if you want to learn more about this kind of application.

A main class is also required to start up the server and run the application. If you don’t use Helidon’s built-in main class you can define your own:

*src/main/java/io/helidon/examples/Main.java*

``` java
public final class Main {

    private Main() {
    } 

    public static void main(final String[] args) throws IOException {
        Server server = startServer();
        System.out.println("http://localhost:" + server.port() + "/greet");
    }

    static Server startServer() {
        return Server.create().start(); 
    }

}
```

In this class, a `main` method is defined which starts the Helidon MP server and prints out a message with the listen address.

- Notice that this class has an empty no-args constructor to make sure this class cannot be instantiated.
- The MicroProfile server is started with the default configuration.

Helidon MP applications also require a `beans.xml` resource file to tell Helidon to use the annotations discussed above to discover Java beans in the application.

Create a `beans.xml` in the `src/main/resources/META-INF` directory with the following content:

*src/main/resources/META-INF/beans.xml*

``` xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://xmlns.jcp.org/xml/ns/javaee"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee
                           http://xmlns.jcp.org/xml/ns/javaee/beans_2_0.xsd"
       version="2.0"
       bean-discovery-mode="annotated"> 
</beans>
```

- The `bean-discovery-mode` tells Helidon to look for the annotations to discover Java beans in the application.

## Build the Application

Helidon MP applications are packaged into a JAR file and the dependencies are copied into a `libs` directory.

    You can now build the application.

*Build the Application*

``` bash
mvn package
```

This will build the application jar and save all runtime dependencies in the `target/libs` directory. This means you can easily start the application by running the application jar file:

*Run the application*

``` bash
java -jar target/helidon-mp-tutorial.jar
```

At this stage, the application is a very simple "Hello World" greeting service. It supports a single GET request for generating a greeting message. The response is encoded using JSON. For example:

*Try the Application:*

``` bash
curl -X GET http://localhost:7001/greet
```

*JSON response:*

``` json
{"message":"Hello World!"}
```

In the output you can see the JSON output from the `getDefaultMessage()` method that was discussed earlier. The server has used a default port `7001`. The application can be stopped cleanly by pressing Ctrl+C.

## Configuration

Helidon MP applications can use the `META-INF/microprofile-config.properties` file to specify configuration data. This file (resource) is read by default if it is present on the classpath. Create this file in `src/main/resources/META-INF` with the following content:

*Initial microprofile-config.properties*

``` bash
# Microprofile server properties
server.port=8080
server.host=0.0.0.0
```

Rebuild the application and run it again. Notice that it now uses port 8080 as specified in the configuration file.

> [!TIP]
> You can learn more about options for configuring the Helidon Server on the [Server Configuration](../server.md) page.

In addition to predefined server properties, application-specific configuration information can be added to this file. Add the `app.greeting` property to the file as shown below. This property will be used to set the content of greeting message.

*Updated META-INF/microprofile-config.properties*

``` bash
# Microprofile server properties
server.port=8080
server.host=0.0.0.0

# Application properties
app.greeting=Hello
```

Add a new "provider" class to read this property and make it available to the application. The class will be called `GreetingProvider.java` and have the following content:

*src/main/java/io/helidon/examples/GreetingProvider.java*

``` java
@ApplicationScoped 
public class GreetingProvider {
    private final AtomicReference<String> message = new AtomicReference<>(); 

    @Inject 
    public GreetingProvider(@ConfigProperty(name = "app.greeting") String message) {
        this.message.set(message);
    }

    String getMessage() {
        return message.get();
    }

    void setMessage(String message) {
        this.message.set(message);
    }
}
```

- This class also has the `ApplicationScoped` annotation, so it will persist for the life of the application.
- The class contains an `AtomicReference` to a `String` where the greeting will be stored. The `AtomicReference` provides lock-free thread-safe access to the underlying `String`.
- The `public GreetingProvider(…​)` constructor is annotated with `Inject` which tells Helidon to use Contexts and Dependency Injection to provide the needed values. In this case, the `String message` is annotated with `ConfigProperty(name = "app.greeting")` so Helidon will inject the property from the configuration file with the key `app.greeting`. This method demonstrates how to read configuration information into the application. A getter and setter are also included in this class.

The `GreetResource` must be updated to use this value instead of the hard coded response. Make the following updates to that class:

*Updated GreetResource class*

``` java
@Path("/greet")
@RequestScoped
public class GreetResource {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());
    private final GreetingProvider greetingProvider;

    @Inject 
    public GreetResource(GreetingProvider greetingConfig) {
        this.greetingProvider = greetingConfig;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject getDefaultMessage() {
        return createResponse("World"); 
    }

    private JsonObject createResponse(String who) { 
        String msg = String.format("%s %s!", greetingProvider.getMessage(), who);
        return JSON.createObjectBuilder()
                .add("message", msg)
                .build();
    }
}
```

- This updated class adds a `GreetingProvider` and uses constructor injection to get the value from the configuration file.
- The logic to create the response message is refactored into a `createResponse` method and the `getDefaultMessage()` method is updated to use this new method.
- In `createResponse()` the message is obtained from the `GreetingProvider` which in turn got it from the configuration files.

Rebuild and run the application. Notice that it now uses the greeting from the configuration file. Change the configuration file and restart the application, notice that it uses the changed value.

> [!TIP]
> To learn more about Helidon MP configuration please see the [Config](../config/introduction.md) section of the documentation.

## Extending the Application

In this section, the application will be extended to add a PUT resource method which will allow users to update the greeting and a second GET resource method which will accept a parameter.

Here are the two new methods to add to `GreetResource.java`:

*New methods for GreetResource.java*

``` java
@Path("/{name}")
@GET
@Produces(MediaType.APPLICATION_JSON)
public JsonObject getMessage(@PathParam("name") String name) { 
    return createResponse(name);
}

@Path("/greeting")
@PUT
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public Response updateGreeting(JsonObject jsonObject) { 

    if (!jsonObject.containsKey("greeting")) {
        JsonObject entity = Json.createObjectBuilder()
                .add("error", "No greeting provided")
                .build();
        return Response.status(Response.Status.BAD_REQUEST).entity(entity).build();
    }

    String newGreeting = jsonObject.getString("greeting");

    greetingProvider.setMessage(newGreeting);
    return Response.status(Response.Status.NO_CONTENT).build();
}
```

- The first of these two methods implements a new HTTP GET service that returns JSON, and it has a path parameter. The `Path` annotation defines the next part of the path to be a parameter named `name`. In the method arguments the `PathParam("name")` annotation on `String name` has the effect of passing the parameter from the URL into this method as `name`.
- The second method implements a new HTTP PUT service which produces and consumes JSON, note the `Consumes` and `PUT` annotations. It also defines a path of "/greeting". Notice that the method argument is a `JsonObject`. Inside the method body there is code to check for the expected JSON, extract the value and update the message in the `GreetingProvider`.

Rebuild and run the application. Test the new services using curl commands similar to those shown below:

*Testing the new services*

``` bash
curl -X GET http://localhost:8080/greet
{"message":"Hello World!"}

curl -X GET http://localhost:8080/greet/Joe
{"message":"Hello Joe!"}

curl -X PUT -H "Content-Type: application/json" -d '{"greeting" : "Hola"}' http://localhost:8080/greet/greeting

curl -X GET http://localhost:8080/greet/Jose
{"message":"Hola Jose!"}
```

Helidon MP provides many other features which can be added to the application.

## Logging

The application logging can be customized. The default logging provider is `java.util.logging`, however it is possible to use other providers. In this tutorial the default provider is used.

Create a `logging.properties` file in `src/main/resources` with the following content:

*Example logging.properties file*

``` properties
# Send messages to the console
handlers=io.helidon.logging.jul.HelidonConsoleHandler 

# HelidonConsoleHandler uses a SimpleFormatter subclass that replaces "!thread!" with the current thread
java.util.logging.SimpleFormatter.format=%1$tY.%1$tm.%1$td %1$tH:%1$tM:%1$tS %4$s %3$s !thread!: %5$s%6$s%n 

# Global logging level. Can be overridden by specific loggers
.level=INFO 
```

- The Helidon console logging handler is configured. This handler writes to `System.out`, does not filter by level and uses a custom `SimpleFormatter` that supports thread names.
- The format string is set using the standard options to include the timestamp, thread name and message.
- The global logging level is set to `INFO`.

The Helidon MicroProfile server will detect the new `logging.properties` file and configure the LogManager for you.

Rebuild and run the application and notice the new logging format takes effect.

*Log output*

``` bash
// before
Aug 22, 2019 11:10:11 AM io.helidon.webserver.LoomWebServer lambda$start$8
INFO: Channel '@default' started: [id: 0xd0afba31, L:/0:0:0:0:0:0:0:0:8080]
Aug 22, 2019 11:10:11 AM io.helidon.microprofile.server.ServerImpl lambda$start$10
INFO: Server started on http://localhost:8080 (and all other host addresses) in 182 milliseconds.
http://localhost:8080/greet

// after
2019.08.22 11:24:42 INFO io.helidon.webserver.LoomServer Thread[main,5,main]: Version: 1.2.0
2019.08.22 11:24:42 INFO io.helidon.webserver.LoomServer Thread[nioEventLoopGroup-2-1,10,main]: Channel '@default' started: [id: 0x8f652dfe, L:/0:0:0:0:0:0:0:0:8080]
2019.08.22 11:24:42 INFO io.helidon.microprofile.server.ServerImpl Thread[nioEventLoopGroup-2-1,10,main]: Server started on http://localhost:8080 (and all other host addresses) in 237 milliseconds.
http://localhost:8080/greet
```

## Metrics

Helidon provides built-in support for metrics endpoints.

*Metrics in Prometheus Format*

``` bash
curl -s -X GET http://localhost:8080/metrics
```

*Metrics in JSON Format*

``` bash
curl -H 'Accept: application/json' -X GET http://localhost:8080/metrics
```

It is possible to disable metrics by adding properties to the `microprofile-config.properties` file, for example:

*Disable a metric*

``` properties
metrics.base.classloader.currentLoadedClass.count.enabled=false
```

Call the metrics endpoint before adding this change to confirm that the metric is included, then add the property to disable the metric, rebuild and restart the application and check again:

*Checking metrics before and after disabling the metric*

``` bash
# before
curl -s http://localhost:8080/metrics | grep classloader_current
# TYPE base:classloader_current_loaded_class_count counter
# HELP base:classloader_current_loaded_class_count Displays the number of classes that are currently loaded in the Java virtual machine.
base:classloader_current_loaded_class_count 7936

# after
curl -s http://localhost:8080/metrics | grep classloader_current
# (no output)
```

Helidon also support custom metrics. To add a new metric, annotate the JAX-RS resource with one of the metric annotations as shown in the example below:

> [!TIP]
> You can find details of the available annotations in the [MicroProfile Metrics Specification](https://download.eclipse.org/microprofile/microprofile-metrics-5.1.1/microprofile-metrics-spec-5.1.1.html)

*Updated GreetResource.java with custom metrics*

``` java
@GET
@Produces(MediaType.APPLICATION_JSON)
@Timed 
public JsonObject getDefaultMessage() {
    return createResponse("World");
}
```

- The `Timed` annotation is added to the `getDefaultMessage()` method.

Rebuild and run the application. Make some calls to the endpoint (<http://localhost:8080/greet>) so there will be some data to report. Then obtain the application metrics as follows:

*Checking the application metrics*

``` bash
curl -H "Accept: application/json" http://localhost:8080/metrics/application
{
  "io.helidon.examples.GreetResource.getDefaultMessage": {
    "count": 2,
    "meanRate": 0.036565171873527716,
    "oneMinRate": 0.015991117074135343,
    "fiveMinRate": 0.0033057092356765017,
    "fifteenMinRate": 0.0011080303990206543,
    "min": 78658,
    "max": 1614077,
    "mean": 811843.8728029992,
    "stddev": 766932.8494434259,
    "p50": 78658,
    "p75": 1614077,
    "p95": 1614077,
    "p98": 1614077,
    "p99": 1614077,
    "p999": 1614077
  }
}
```

Learn more about using Helidon and MicroProfile metrics in the [Metrics Guide](metrics.md).

## Health Check

Helidon provides built-in support for health check endpoints. Obtain the built-in health check using the following URL:

*Health check*

``` bash
curl -s -X GET http://localhost:8080/health
{
  "outcome": "UP",
  "status": "UP",
  "checks": [
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
        "free": "381.23 GB",
        "freeBytes": 409340088320,
        "percentFree": "43.39%",
        "total": "878.70 GB",
        "totalBytes": 943491723264
      }
    },
    {
      "name": "heapMemory",
      "state": "UP",
      "status": "UP",
      "data": {
        "free": "324.90 MB",
        "freeBytes": 340682920,
        "max": "3.46 GB",
        "maxBytes": 3715629056,
        "percentFree": "97.65%",
        "total": "408.00 MB",
        "totalBytes": 427819008
      }
    }
  ]
}
```

Endpoints for readiness and liveness checks are also provided by default. Obtain the default results using these URLs, which return the same result as the previous example.:

*Default readiness and liveness endpoints*

``` bash
# readiness
curl -i  -X GET http://localhost:8080/health/ready

# liveness
curl -i  -X GET http://localhost:8080/health/live
```

Helidon allows the addition of custom health checks to applications. Create a new class `GreetHealthcheck.java` with the following content:

*src/main/java/io/helidon/examples/GreetHealthcheck.java*

``` java
@Liveness 
@ApplicationScoped 
public class GreetHealthcheck implements HealthCheck {

    private GreetingProvider provider;

    @Inject 
    public GreetHealthcheck(GreetingProvider provider) {
        this.provider = provider;
    }

    @Override
    public HealthCheckResponse call() { 
        String message = provider.getMessage();
        return HealthCheckResponse.named("greeting") 
                .status("Hello".equals(message))
                .withData("greeting", message)
                .build();
    }
}
```

- This class has the MicroProfile `Liveness` annotation which tells Helidon that this class provides a custom health check. You can learn more about the available annotations in the [MicroProfile Health Protocol and Wireformat](https://download.eclipse.org/microprofile/microprofile-health-4.0/microprofile-health-spec-4.0.html##_protocol_and_wireformat) document.
- This class also has the `ApplicationScoped` annotation, as seen previously.
- The `GreetingProvider` is injected using Context and Dependency Service. This example will use the greeting to determine whether the application is healthy, this is a contrived example for demonstration purposes.
- Health checks must implement the `HealthCheck` functional interface, which includes the method `HealthCheckResponse call()`. Helidon will invoke the `call()` method to verify the healthiness of the application.
- In this example, the application is deemed to be healthy if the `GreetingProvider,getMessage()` method returns the string `"Hello"` and unhealthy otherwise.

Rebuild the application, make sure that the `mp.conf` has the `greeting` set to something other than `"Hello"` and then run the application and check the health:

*Custom health check reporting unhealthy state*

``` bash
curl -i -X GET http://localhost:8080/health/live
HTTP/1.1 503 Service Unavailable 
Content-Type: application/json
Date: Fri, 23 Aug 2019 10:07:23 -0400
transfer-encoding: chunked
connection: keep-alive

{"outcome":"DOWN","status":"DOWN","checks":[{"name":"deadlock","state":"UP","status":"UP"},{"name":"diskSpace","state":"UP","status":"UP","data":{"free":"381.08 GB","freeBytes":409182306304,"percentFree":"43.37%","total":"878.70 GB","totalBytes":943491723264}},{"name":"greeting","state":"DOWN","status":"DOWN","data":{"greeting":"Hey"}},{"name":"heapMemory","state":"UP","status":"UP","data":{"free":"243.81 MB","freeBytes":255651048,"max":"3.46 GB","maxBytes":3715629056,"percentFree":"98.58%","total":"294.00 MB","totalBytes":308281344}}]} 
```

- The HTTP return code is now 503 Service Unavailable.
- The status is reported as "DOWN" and the custom check is included in the output.

Now update the greeting to `"Hello"` using the following request, and then check health again:

*Update the greeting and check health again*

``` bash
# update greeting
curl -i -X PUT -H "Content-Type: application/json" -d '{"greeting": "Hello"}' http://localhost:8080/greet/greeting
HTTP/1.1 204 No Content 
Date: Thu, 22 Aug 2019 13:29:57 -0400
connection: keep-alive

# check health
curl -i -X GET http://localhost:8080/health/live
HTTP/1.1 200 OK 
Content-Type: application/json
Date: Fri, 23 Aug 2019 10:08:09 -0400
connection: keep-alive
content-length: 536

{"outcome":"UP","status":"UP","checks":[{"name":"deadlock","state":"UP","status":"UP"},{"name":"diskSpace","state":"UP","status":"UP","data":{"free":"381.08 GB","freeBytes":409179811840,"percentFree":"43.37%","total":"878.70 GB","totalBytes":943491723264}},{"name":"greeting","state":"UP","status":"UP","data":{"greeting":"Hello"}},{"name":"heapMemory","state":"UP","status":"UP","data":{"free":"237.25 MB","freeBytes":248769720,"max":"3.46 GB","maxBytes":3715629056,"percentFree":"98.40%","total":"294.00 MB","totalBytes":308281344}}]} 
```

- The PUT returns an HTTP 204.
- The health check now returns an HTTP 200.
- The status is now reported as "UP" and the details are provided in the checks.

Learn more about health checks in the [Health Check Guide](health.md).

## Build a Docker Image

To run the application in Docker (or Kubernetes), a `Dockerfile` is needed to build a Docker image. To build the Docker image, you need to have Docker installed and running on your system.

Add a new `Dockerfile` in the project root directory with the following content:

*Dockerfile content*

``` bash
FROM container-registry.oracle.com/java/openjdk:21 as build 

# Install maven
WORKDIR /usr/share
RUN set -x && \
    curl -O https://archive.apache.org/dist/maven/maven-3/3.8.4/binaries/apache-maven-3.8.4-bin.tar.gz && \
    tar -xvf apache-maven-*-bin.tar.gz  && \
    rm apache-maven-*-bin.tar.gz && \
    mv apache-maven-* maven && \
    ln -s /usr/share/maven/bin/mvn /bin/

WORKDIR /helidon

ADD pom.xml .
RUN mvn package -DskipTests 

ADD src src
RUN mvn package -DskipTests 
RUN echo "done!"

FROM container-registry.oracle.com/java/openjdk:21
WORKDIR /helidon

COPY --from=build /helidon/target/helidon-mp-tutorial.jar ./ 
COPY --from=build /helidon/target/libs ./libs

CMD ["java", "-jar", "helidon-mp-tutorial.jar"] 
EXPOSE 8080
```

- This Dockerfile uses Docker’s multi-stage build feature. The `FROM` keyword creates the first stage. In this stage, the base container has the build tools needed to build the application. These are not required to run the application, so the second stage uses a smaller container.
- Add the `pom.xml` and running an "empty" maven build will download all the dependencies and plugins in this layer. This will make future builds faster because they will use this cached layer rather than downloading everything again.
- Add the source code and do the real build.
- Copy the binary and libraries from the first stage.
- Set the initial command and expose port 8080.

To create the Docker image, use the following command:

*Docker build*

``` bash
docker build -t helidon-mp-tutorial .
```

Make sure the application is shutdown if it was still running locally so that port 8080 will not be in use, then start the application in Docker using the following command:

*Run Docker Image*

``` bash
docker run --rm -p 8080:8080 helidon-mp-tutorial:latest
```

Try the application as before.

*Try the application*

``` bash
curl http://localhost:8080/greet/bob
{"message":"Howdee bob!"}

curl http://localhost:8080/health/ready
{"outcome":"UP","status":"UP","checks":[]}
```

## Deploy the application to Kubernetes

If you don’t have access to a Kubernetes cluster, you can [install one on your desktop](../../about/kubernetes.md). Then deploy the example:

*Verify connectivity to cluster*

``` bash
kubectl cluster-info
kubectl get nodes
```

To deploy the application to Kubernetes, a Kubernetes YAML file that defines the deployment and associated resources is needed. In this case all that is required is the deployment and a service.

Create a file called `app.yaml` in the project’s root directory with the following content:

*Kubernetes YAML file*

``` yaml
---
kind: Service 
apiVersion: v1
metadata:
  name: helidon-mp-tutorial
  labels:
    app: helidon-mp-tutorial
spec:
  type: NodePort 
  selector:
    app: helidon-mp-tutorial
  ports:
    - port: 8080
      targetPort: 8080
      name: http
---
kind: Deployment 
apiVersion: apps/v1
metadata:
  name: helidon-mp-tutorial
spec:
  replicas: 1 
  selector:
    matchLabels:
      app: helidon-mp-tutorial
  template:
    metadata:
      labels:
        app: helidon-mp-tutorial
        version: v1
    spec:
      containers:
        - name: helidon-mp-tutorial
          image: helidon-mp-tutorial 
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 8080
```

- Define a Service to provide access to the application.
- Define a NodePort to expose the application outside the Kubernetes cluster.
- Define a Deployment of the application.
- Define how many replicas of the application to run.
- Define the Docker image to use - this must be the one that was built in the previous step. If the image was built on a different machine to the one where Kubernetes is running, or if Kubernetes is running on multiple machines (worker nodes) then the image must either be manually copied to each node or otherwise pushed to a Docker registry that is accessible to the worker nodes.

This Kubernetes YAML file can be used to deploy the application to Kubernetes:

*Deploy the application to Kubernetes*

``` bash
kubectl create -f app.yaml
kubectl get pods # Wait for quickstart pod to be RUNNING
```

> [!TIP]
> Remember, if Kubernetes is running on a different machine, or inside a VM (as in Docker for Desktop) then the Docker image must either be manually copied to the Kubernetes worker nodes or pushed to a Docker registry that is accessible to those worker nodes. Update the `image` entry in the example above to include the Docker registry name. If the registry is private a Docker registry secret will also be required.

The step above created a service that is exposed using any available node port. Kubernetes allocates a free port. Lookup the service to find the port.

*Lookup the service*

``` bash
kubectl get service helidon-mp-tutorial
```

Note the PORTs. The application can be exercised as before but use the second port number (the NodePort) instead of 8080. For example:

*Access the application*

``` bash
curl -X GET http://localhost:31431/greet
```

If desired, the Kubernetes YAML file can also be used to remove the application from Kubernetes as follows:

*Remove the application from Kubernetes*

``` bash
kubectl delete -f app.yaml
```

## Summary

This tutorial demonstrated how to build a new Helidon MP application, how to use Helidon and MicroProfile configuration, logging, metrics, and health checks. It also demonstrated how to package the application in a Docker image and run it in Kubernetes.

There were several links to more detailed information included in the tutorial. These links are repeated below and can be explored to learn more details about Helidon application development.

## Related links

- [Eclipse MicroProfile](https://projects.eclipse.org/projects/technology.microprofile)
- [Contexts and Dependency Injection Specification](https://jakarta.ee/specifications/cdi/4.0/jakarta-cdi-spec-4.0.html)
- [Server Configuration](../server.md)
- [Config](../config/introduction.md)
- [MicroProfile Metrics Specification](https://download.eclipse.org/microprofile/microprofile-metrics-5.1.1/microprofile-metrics-spec-5.1.1.html)
- [Metrics Guide](metrics.md)
- [MicroProfile Health Protocol and Wireformat](https://download.eclipse.org/microprofile/microprofile-health-4.0/microprofile-health-spec-4.0.html##_protocol_and_wireformat)
- [Install Kubernetes on your desktop](../../about/kubernetes.md)
