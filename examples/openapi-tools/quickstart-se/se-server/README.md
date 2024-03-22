# Helidon SE Server with OpenAPI

## Build and run

```shell
mvn package
java -jar target/openapi-se-server.jar
```

## Exercise the application

```shell
curl -X GET http://localhost:8080/greet
curl -X GET http://localhost:8080/greet/{name}
curl -X PUT -H "Content-Type: application/json" -d '{"greeting" : "Howdy"}'  http://localhost:8080/greet/greeting

```

## Try health and metrics

```shell
curl -s -X GET http://localhost:8080/health
#{"outcome":"UP",...
#. . .

# Prometheus Format
curl -s -X GET http://localhost:8080/metrics
# TYPE base:gc_g1_young_generation_count gauge
#. . .

# JSON Format
curl -H 'Accept: application/json' -X GET http://localhost:8080/metrics
#{"base":...
#. . .
```