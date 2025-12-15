## Tracing

### Set up Zipkin

First, you need to run the Zipkin tracer. Helidon will communicate with this tracer at runtime.

Run Zipkin within a docker container, then check the Zipkin server health:
```
docker run -d --name zipkin -p 9411:9411 openzipkin/zipkin
```
Run the Zipkin docker image named openzipkin/zipkin.
Check the Zipkin server health:
```
curl http://localhost:9411/health
```
Invoke the Zipkin REST API to check the Zipkin server health.
```
Response body
{
  "status": "UP",
  "zipkin": {
    "status": "UP",
    "details": {
      "InMemoryStorage{}": {
        "status": "UP"
      }
    }
  }
}
```
All status fields should be UP.

### View Tracing Using Zipkin REST API

Run the curl command and check the response:
```
curl http://localhost:9411/api/v2/services
```
Response body
```
["helidon-mp-1"]
```

### View Tracing Using Zipkin UI

Zipkin provides a web-based UI at http://localhost:9411/zipkin, where you can see a visual representation of 
the same data and the relationship between spans within a trace.
