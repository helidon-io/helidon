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
