# http-status-count-mp

This Helidon MP project illustrates a filter which updates a family of counters based on the HTTP status returned in each response.

The addition of the single filter class `HttpStatusMetricFilter` is the only difference from the Helidon MP QuickStart project.

## Incorporating status metrics into your own application
Use this example for inspiration in writing your own filter or just use the filter directly in your own application by copying and pasting the `HttpStatusMetricFilter` class into your application, adjusting the package declaration as needed. Helidon MP discovers and uses your filter automatically.

## Build and run

```bash
mvn package
java -jar target/http-status-count-mp.jar
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
