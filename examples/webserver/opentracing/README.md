# Opentracing Example Application

## Start Zipkin

With Docker:
```shell
docker run --name zipkin -d -p 9411:9411 openzipkin/zipkin
```

With Java 8+:
```shell
curl -sSL https://zipkin.io/quickstart.sh | bash -s
java -jar zipkin.jar
```

## Build and run

With Docker:
```shell
docker build -t helidon-webserver-opentracing-example .
docker run --rm -d --link zipkin --name helidon-webserver-opentracing-example \
    -p 8080:8080 helidon-webserver-opentracing-example:latest
```

With Java 8+:
```shell
mvn package
java -jar target/helidon-examples-webserver-opentracing.jar
```

Try the endpoint:
```shell
curl http://localhost:8080/test
```

Then check out the traces at http://localhost:9411.

Stop the docker containers:
```shell
docker stop zipkin helidon-webserver-opentracing-example
```
