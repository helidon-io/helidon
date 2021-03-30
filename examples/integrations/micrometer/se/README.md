# Helidon SE Micrometer Example

This example shows a simple greeting application, similar to the one from the 
Helidon SE QuickStart, but enhanced with Micrometer support to:

* time all accesses to the two `GET` endpoints, and

* count the accesses to the `GET` endpoint which returns a personalized
  greeting.

The Helidon Micrometer integration creates a Micrometer `MeterRegistry` automatically for you.
The `registry()` instance method on `MicrometerSupport` returns that meter registry.
  
The `Main` class
1. Uses `MicrometerSupport` to obtain the Micrometer `MeterRegistry` which Helidon SE 
   automatically provides.
   
1. Uses the `MeterRegistry` to: 
   * Create a Micrometer `Timer` for recording accesses to all `GET` endpoints.
   * Create a Micrometer `Counter` for counting accesses to the `GET` endpoint that
     returns a personalized greeting. 

1. Registers the built-in support for the `/micrometer` endpoint.
    
1. Passes the `Timer` and `Counter` to the `GreetingService` constructor.

The `GreetingService` class
1. Accepts in the constructor the `Timer` and `Counter` and saves them.
1. Adds routing rules to:
   * Update the `Timer` with every `GET` access.
   * Increment `Counter` (in addition to returning a personalized greeting) for every 
     personalized `GET` access.


## Build and run

With JDK11+
```bash
mvn package
java -jar target/helidon-examples-integrations-micrometer-se.jar
```

## Using the app endpoints as with the "classic" greeting app

These normal greeting app endpoints work just as in the original greeting app:

```bash
curl -X GET http://localhost:8080/greet
{"message":"Hello World!"}

curl -X GET http://localhost:8080/greet/Joe
{"message":"Hello Joe!"}

curl -X PUT -H "Content-Type: application/json" -d '{"greeting" : "Hola"}' http://localhost:8080/greet/greeting

curl -X GET http://localhost:8080/greet/Jose
{"message":"Hola Jose!"}
```

## Using Micrometer

Access the `/micrometer` endpoint which reports the newly-added timer and counter.
```bash
curl http://localhost:8080/micrometer
```

Because we created the `Timer` with a histogram,
the actual timer output includes a lengthy histogram (only part of which is shown below). 
Your output might show the `personalizedGets` output before the `allGets` output, 
rather than after as shown here.

```
curl http://localhost:8080/micrometer
# HELP allGets_seconds_max  
# TYPE allGets_seconds_max gauge
allGets_seconds_max 0.04341847
# HELP allGets_seconds  
# TYPE allGets_seconds histogram
allGets_seconds_bucket{le="0.001",} 0.0
...
allGets_seconds_bucket{le="30.0",} 3.0
allGets_seconds_bucket{le="+Inf",} 3.0
allGets_seconds_count 3.0
allGets_seconds_sum 0.049222592
# HELP personalizedGets_total  
# TYPE personalizedGets_total counter
personalizedGets_total 2.0

```