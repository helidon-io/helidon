# Helidon SE Micrometer Example

This example shows a simple greeting application, similar to the one from the 
Helidon SE QuickStart, enhanced with Micrometer support.

The `Main` class
1. Uses `MicrometerSupport` to create a Micrometer `Counter` for counting the number of `getXXX` endpoint accesses.
1. Passes the `Counter` to the `GreetingService` constructor.
1. Registers the `MicrometerSupport` instance as a web service so the server will respond to the `/micrometer` endpoint.

The `GreetingService`
1. Accepts in the constructor the Micrometer `Counter` and saves it.
1. Adds a routing rule so every `get` endpoint access increments the counter (as well as routing 
   requests to the correct `GreetingResourcd` method).

Helidon Micrometer support creates a Micrometer `MeterRegistry` automatically for you. 
The `registry()` 
instance method on `MicrometerSupport` returns that meter registry.

## Build and run

With JDK11+
```bash
mvn package
java -jar helidon-examples-micrometer.jar
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

Access the `/micrometer` endpoint which reports the newly-added counter.

```bash
curl http://localhost:8080/micrometer
# HELP gets_total  
# TYPE gets_total counter
gets_total 3.0
```
