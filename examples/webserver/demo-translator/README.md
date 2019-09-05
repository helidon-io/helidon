# Translator Example Application

This application demonstrates a pseudo application composed of two microservices
 implemented with Helidon SE.

## Start Zipkin

In Docker:
```bash
docker run --name zipkin -d -p 9411:9411 openzipkin/zipkin
```

With Java 8+:
```bash
curl -sSL https://zipkin.io/quickstart.sh | bash -s
java -jar zipkin.jar
```

With Kubernetes:
```bash
kubectl apply -f ingress.yaml -f zipkin.yaml
```

## Build and run

With Docker:
```bash
docker build -t helidon-examples-webserver-translator-backend backend/
docker build -t helidon-examples-webserver-translator-frontend frontend/
docker run --rm -d -p 9080:9080 \
    --link zipkin \
    --name helidon-examples-webserver-translator-backend \
     helidon-examples-webserver-translator-backend:latest
docker run --rm -d -p 8080:8080 \
    --link zipkin \
    --link helidon-examples-webserver-translator-backend \
    --name helidon-examples-webserver-translator-frontend \
     helidon-examples-webserver-translator-frontend:latest
```

With Java 8+:
```bash
mvn package
java -jar backend/target/helidon-examples-webserver-translator-backend.jar &
java -jar frontend/target/helidon-examples-webserver-translator-frontend.jar
```

Make an HTTP request to application:
```bash
curl "http://localhost:8080?q=cloud&lang=czech"
```

Then check out the traces at http://localhost:9411.

## Run with Kubernetes (docker for desktop)

```bash
docker build -t helidon-examples-webserver-translator-backend backend/
docker build -t helidon-examples-webserver-translator-frontend frontend/
kubectl apply -f backend/app.yaml -f frontend/app.yaml
```

Make an HTTP request to application:
```bash
curl "http://localhost/translator?q=cloud&lang=czech"
```

Then check out the traces at http://localhost/zipkin.
