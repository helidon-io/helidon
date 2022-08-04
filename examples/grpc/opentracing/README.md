# Opentracing gRPC Server Example Application

## Start Zipkin

With Docker:
```bash
docker run --name zipkin -d -p 9411:9411 openzipkin/zipkin
```

```bash
curl -sSL https://zipkin.io/quickstart.sh | bash -s
java -jar zipkin.jar
```

## Build and run
```bash
mvn -f ../pom.xml -pl common,opentracing package
java -jar target/helidon-examples-grpc-opentracing.jar
```

Exercise the gRPC endpoint with GreeClient and StringClient:
```bash
java -cp target/helidon-examples-grpc-opentracing.jar io.helidon.grpc.examples.common.GreetClient
java -cp target/helidon-examples-grpc-opentracing.jar io.helidon.grpc.examples.common.StringClient
```

Then check out the traces at http://localhost:9411 from a browser.

Stop zipkin if run with the docker container:
```bash
docker stop zipkin && docker rm zipkin
```
