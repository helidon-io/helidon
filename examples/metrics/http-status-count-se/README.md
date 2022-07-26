# http-status-count-se

This Helidon SE project illustrates a service which updates a family of counters based on the HTTP status returned in each response.

The main source in this example is identical to that in the Helidon SE QuickStart application except in these ways:
* The `HttpStatusMetricService` class creates and updates the status metrics.
* The `Main` class has a two small enhancements:
   * The `createRouting` method instantiates `HttpStatusMetricService` and sets up routing for it.
   * The `startServer` method has an additional variant to simplify a new unit test.

## Incorporating status metrics into your own application
Use this example for inspiration in writing your own service or just use the `HttpStatusMetricService` directly in your own application.

1. Copy and paste the `HttpStatusMetricService` class into your application, adjusting the package declaration as needed.
2. Register routing for an instance of `HttpStatusMetricService`, as shown here:
   ```java
   Routing.Builder builder = Routing.builder()
                ...
                .register(HttpStatusMetricService.create()
                ...
   ```

## Build and run


With JDK17+
```bash
mvn package
java -jar target/http-status-count-se.jar
```

## Exercise the application
```bash
curl -X GET http://localhost:8080/simple-greet
```
```listing
{"message":"Hello World!"}
```

```bash
curl -X GET http://localhost:8080/greet
```
```listing
{"message":"Hello World!"}
```
```bash
curl -X GET http://localhost:8080/greet/Joe
```
```listing
{"message":"Hello Joe!"}
```
```bash
curl -X PUT -H "Content-Type: application/json" -d '{"greeting" : "Hola"}' http://localhost:8080/greet/greeting

curl -X GET http://localhost:8080/greet/Jose
```
```listing
{"message":"Hola Jose!"}
```

## Try metrics
```bash
# Prometheus Format
curl -s -X GET http://localhost:8080/metrics/application
```

```listing
...
# TYPE application_httpStatus_total counter
# HELP application_httpStatus_total Counts the number of HTTP responses in each status category (1xx, 2xx, etc.)
application_httpStatus_total{range="1xx"} 0
application_httpStatus_total{range="2xx"} 5
application_httpStatus_total{range="3xx"} 0
application_httpStatus_total{range="4xx"} 0
application_httpStatus_total{range="5xx"} 0
...
```
# JSON Format

```bash
curl -H "Accept: application/json" -X GET http://localhost:8080/metrics
```
```json
{
...
    "httpStatus;range=1xx": 0,
    "httpStatus;range=2xx": 5,
    "httpStatus;range=3xx": 0,
    "httpStatus;range=4xx": 0,
    "httpStatus;range=5xx": 0,
...
```

## Try health

```bash
curl -s -X GET http://localhost:8080/health
```
```listing
{"outcome":"UP",...

```



## Building a Native Image

Make sure you have GraalVM locally installed:

```
$GRAALVM_HOME/bin/native-image --version
```

Build the native image using the native image profile:

```
mvn package -Pnative-image
```

This uses the helidon-maven-plugin to perform the native compilation using your installed copy of GraalVM. It might take a while to complete.
Once it completes start the application using the native executable (no JVM!):

```
./target/http-status-count-se
```

Yep, it starts fast. You can exercise the application’s endpoints as before.


## Build the Docker Image
```
docker build -t http-status-count-se .
```
                                

## Building a Custom Runtime Image

Build the custom runtime image using the jlink image profile:

```
mvn package -Pjlink-image
```

This uses the helidon-maven-plugin to perform the custom image generation.
After the build completes it will report some statistics about the build including the reduction in image size.

The target/http-status-count-se-jri directory is a self contained custom image of your application. It contains your application,
its runtime dependencies and the JDK modules it depends on. You can start your application using the provide start script:

```
./target/http-status-count-se-jri/bin/start
```

Class Data Sharing (CDS) Archive
Also included in the custom image is a Class Data Sharing (CDS) archive that improves your application’s startup
performance and in-memory footprint. You can learn more about Class Data Sharing in the JDK documentation.

The CDS archive increases your image size to get these performance optimizations. It can be of significant size (tens of MB).
The size of the CDS archive is reported at the end of the build output.

If you’d rather have a smaller image size (with a slightly increased startup time) you can skip the creation of the CDS
archive by executing your build like this:

```
mvn package -Pjlink-image -Djlink.image.addClassDataSharingArchive=false
```

For more information on available configuration options see the helidon-maven-plugin documentation.
                                
