# Opentracing Example Application

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
docker build -t helidon-webserver-opentracing-example .
docker run --rm -d --link zipkin --name helidon-webserver-opentracing-example \
    -p 8080:8080 helidon-webserver-opentracing-example:latest
```

With Java 8+:
```bash
mvn package
java -jar target/helidon-examples-webserver-opentracing.jar
```

Try the endpoint:
```bash
curl http://localhost:8080/test
```

Then check out the traces at http://localhost:9411.

Stop the docker containers:
```bash
docker stop zipkin helidon-webserver-opentracing-example
```
