# Helidon Metrics Key Performance Indicators SE Example

This project implements a simple Hello World REST service using Helidon SE and demonstrates 
support in Helidon for extended key performance indicator (KPI) metrics.

Your application can set up KPI metrics either programmatically or using configuration.
The `Main` class of this example shows both techniques, checking the system property `useConfig` to 
determine 
which to use.
You would typically write any given application to use only one of the approaches.

## Build and run

With JDK11+
```bash
mvn package
```
To use programmatic set-up:
```bash
java -jar target/helidon-examples-metrics-kpi.jar 
```
To use configuration:
```bash
java -DuseConfig=true -jar target/helidon-examples-metrics-kpi.jar
````

## Exercise the application

```
curl -X GET http://localhost:8080/greet
{"message":"Hello World!"}

curl -X GET http://localhost:8080/greet/Joe
{"message":"Hello Joe!"}

curl -X PUT -H "Content-Type: application/json" -d '{"greeting" : "Hola"}' http://localhost:8080/greet/greeting

curl -X GET http://localhost:8080/greet/Jose
{"message":"Hola Jose!"}

curl -X GET http://localhost:8080/greet          
{"message":"Hola World!"}
```

## Retrieve vendor metrics with key performance indicators

For brevity, the example output below shows only some of the KPI metrics. 

Note that, even though the `curl` commands above access `/greet` endpoints five times, 
the 
`load` and (not shown here) 
`count` and 
`meter` are `6` in the Prometheus output example and `7` in the JSON output example. 
Further, the `inFlight`
current value is `1`.

This is 
because Helidon tallies 
all
requests, even those 
to Helidon-provided services such as `/metrics` and `/health`, in the KPI metrics. 
The request 
to retrieve the metrics is the one that is in flight, and it contributes to the KPI metrics just 
as requests to application endpoints do.

Further, the request to `/metrics` is still in progress when Helidon prepares the 
output by getting the values of the KPI metrics at that moment. 
If _that_ request turns out to be long- running, Helidon would discover so only 
_after_ preparing the metrics output and completing the request. 
The `longRunning` `Meter` 
values in _that_ 
response 
could 
not reflect the fact that Helidon would subsequently conclude that _that_ request was 
long-running.

## Prometheus format
```
curl -s -X GET http://localhost:8080/metrics/vendor
...
# TYPE vendor_requests_inFlight_current concurrent gauge
# HELP vendor_requests_inFlight_current Measures the number of currently in-flight requests
vendor_requests_inFlight_current 1
# TYPE vendor_requests_inFlight_min concurrent gauge
vendor_requests_inFlight_min 0
# TYPE vendor_requests_inFlight_max concurrent gauge
vendor_requests_inFlight_max 1
# TYPE vendor_requests_load_total counter
# HELP vendor_requests_load_total Measures the total number of in-flight requests and rates at which they occur
vendor_requests_load_total 6
# TYPE vendor_requests_load_rate_per_second gauge
vendor_requests_load_rate_per_second 0.04932913209653636
# TYPE vendor_requests_load_one_min_rate_per_second gauge
vendor_requests_load_one_min_rate_per_second 0.025499793037824785
# TYPE vendor_requests_load_five_min_rate_per_second gauge
vendor_requests_load_five_min_rate_per_second 0.012963147773962286
# TYPE vendor_requests_load_fifteen_min_rate_per_second gauge
vendor_requests_load_fifteen_min_rate_per_second 0.005104944851522425
...
```
## JSON output


```bash
curl -s -X GET -H "Accept: application/json" http://localhost:8080/metrics/vendor
{
  ...
  "requests.inFlight": {
    "current": 1,
    "max": 1,
    "min": 0
  },
  "requests.load": {
    "count": 7,
    "meanRate": 0.01530869471741443,
    "oneMinRate": 0.00016123154886115814,
    "fiveMinRate": 0.005344110443653005,
    "fifteenMinRate": 0.004286243527303867
  },
  ...
}
```
