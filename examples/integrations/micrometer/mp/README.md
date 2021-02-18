# Helidon MP Micrometer Example

This example shows a simple greeting application, similar to the one from the Helidon MP 
QuickStart, but enhanced with Helidon MP Micrometer support to
* time all accesses to the two `GET` endpoints, and
  
* count the accesses to the `GET` endpoint which returns a personalized 
  greeting.
  
The example is similar to the one from the Helidon MP QuickStart with these differences:
* The `pom.xml` file contains this additional dependency on the Helidon Micrometer integration 
  module:
```xml
<dependency>
    <groupId>io.helidon.integrations</groupId>
    <artifactId>helidon-integrations-micrometer</artifactId>
</dependency>
```
* The `GreetingService` includes additional annotations:
    * `@Timed` on the two `GET` methods.
    * `@Counted` on the `GET` method that returns a personalized greeting.

## Build and run

With JDK11+
```bash
mvn package
java -jar target/helidon-examples-integrations-micrometer-mp.jar
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
Because the `@Timer` annotation specifies a histogram, 
the actual timer output includes a lengthy histogram (only part of which is shown below). 
Your output might show the `personalizedGets` output before the `allGets` output,
rather than after as shown here.

```
curl http://localhost:8080/micrometer
# HELP allGets_seconds_max Tracks all GET operations
# TYPE allGets_seconds_max gauge
allGets_seconds_max 0.004840005
# HELP allGets_seconds Tracks all GET operations
# TYPE allGets_seconds histogram
allGets_seconds_bucket{le="0.001",} 2.0
allGets_seconds_bucket{le="30.0",} 3.0
allGets_seconds_bucket{le="+Inf",} 3.0
allGets_seconds_count 3.0
allGets_seconds_sum 0.005098119
# HELP personalizedGets_total Counts personalized GET operations
# TYPE personalizedGets_total counter
personalizedGets_total 2.0
```
