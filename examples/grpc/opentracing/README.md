# Opentracing gRPC Server Example Application

## Start Zipkin

With Docker:
```bash
docker run --name zipkin -d -p 9411:9411 openzipkin/zipkin
```

With Java 8+:
```bash
curl -sSL https://zipkin.io/quickstart.sh | bash -s
java -jar zipkin.jar
```

## Build and run

With Docker:
```bash
docker build -t helidon-examples-grpc-opentracing .
docker run --rm -d --link zipkin --name helidon-examples-grpc-opentracing \
    -p 8080:8080 helidon-examples-grpc-opentracing:latest
```

With Java 8+:
```bash
mvn package
java -jar target/helidon-examples-grpc-opentracing.jar
```

Try the endpoint:
```bash
curl http://localhost:8080/test
```

Then check out the traces at http://localhost:9411.