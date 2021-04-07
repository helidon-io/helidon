# Helidon Metrics Exemplar SE Example

This project implements a simple Hello World REST service using Helidon SE and demonstrates the 
optional metrics exemplar support.

## Start Zipkin (optional)
If you do not start Zipkin, the example app will still function correctly but it will log a warning 
when it cannot contact the Zipkin server to report the tracing spans. Even so, the metrics output 
will contain valid exemplars.

With Docker:
```bash
docker run --name zipkin -d -p 9411:9411 openzipkin/zipkin
```

## Build and run

With JDK11+
```bash
mvn package
java -jar target/helidon-examples-metrics-exemplar.jar
```

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

## Retrieve application metrics

```
# Prometheus format with exemplars

curl -s -X GET http://localhost:8080/metrics/application
# TYPE application_counterForPersonalizedGreetings_total counter
# HELP application_counterForPersonalizedGreetings_total 
application_counterForPersonalizedGreetings_total 2 # {trace_id="78e61eed351f4c9d"} 1 1617812495.016000
. . .
# TYPE application_timerForGets_mean_seconds gauge
application_timerForGets_mean_seconds 0.005772598385062112 # {trace_id="b22f13c37ba8b879"} 0.001563945 1617812578.687000
# TYPE application_timerForGets_max_seconds gauge
application_timerForGets_max_seconds 0.028018165 # {trace_id="a1b127002725143c"} 0.028018165 1617812467.524000
```
The examplars contain `trace_id` values tying them to specific samples.
Note that the exemplar for the counter refers to the most recent update to the counter. 

For the timer, the value for the `max` is exactly the same as the value for its exemplar, 
because the `max` value has to come from at least one sample. 
In contrast, Helidon calculates the `mean` value from possibly multiple samples. The exemplar for 
`mean` is a sample with value as close as that of other samples to the mean. 

## Browse the Zipkin traces
If you started the Zipkin server, visit `http://localhost:9411` and click `Run Query` to see all 
the spans your Helidon application reported to Zipkin.
You can compare the trace IDs in the Zipkin display to those in the metrics output.
