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
docker build -t helidon-examples-grpc-opentracing -f ./Dockerfile ..
docker run --rm -d -p 1408:1408 \
    --link zipkin \
    --name helidon-examples-grpc-opentracing \
    helidon-examples-grpc-opentracing:latest
```

With Java 8+:
```bash
mvn -f ../pom.xml -pl common/security package
java -jar target/helidon-examples-grpc-opentracing.jar
```

Try the endpoint:
```bash
java -cp target/helidon-examples-grpc-opentracing.jar io.helidon.grpc.examples.common.GreetClient
java -cp target/helidon-examples-grpc-opentracing.jar io.helidon.grpc.examples.common.StringClient
```

Then check out the traces at http://localhost:9411.

Stop the docker containers:
```bash
docker stop zipkin helidon-examples-grpc-opentracing
```