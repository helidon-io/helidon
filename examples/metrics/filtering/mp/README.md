# Helidon Metrics Filtering MP Example

This project implements a simple Hello World REST service using Helidon SE and demonstrates the
optional metrics exemplar support.

## Build and run

```shell
mvn package
java -jar target/helidon-examples-metrics-filtering-mp.jar
```

## Exercise the application

```shell
curl -X GET http://localhost:8080/greet
#Output: {"message":"Hello World!"}

curl -X GET http://localhost:8080/greet/Joe
#Output: {"message":"Hello Joe!"}

curl -X PUT -H "Content-Type: application/json" -d '{"message" : "Hola"}' http://localhost:8080/greet/greeting

curl -X GET http://localhost:8080/greet/Jose
#Output: {"message":"Hola Jose!"}

curl -X GET http://localhost:8080/greet          
#Output: {"message":"Hola World!"}
```

## Retrieve application metrics

```shell
# Prometheus format with exemplars

curl -s -X GET http://localhost:8080/metrics/application
```
```
# TYPE application_counterForPersonalizedGreetings_total counter
# HELP application_counterForPersonalizedGreetings_total 
application_counterForPersonalizedGreetings_total 2 # {trace_id="78e61eed351f4c9d"} 1 1617812495.016000
. . .
# TYPE application_timerForGets_mean_seconds gauge
application_timerForGets_mean_seconds 0.005772598385062112 # {trace_id="b22f13c37ba8b879"} 0.001563945 1617812578.687000
# TYPE application_timerForGets_max_seconds gauge
application_timerForGets_max_seconds 0.028018165 # {trace_id="a1b127002725143c"} 0.028018165 1617812467.524000
```
