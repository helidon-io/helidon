# Translator Example Application

This application demonstrates a pseudo application composed of two microservices
 implemented with Helidon SE.

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

With Kubernetes:
```bash
kubectl apply \
 -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/ingress-nginx-3.15.2/deploy/static/provider/cloud/deploy.yaml \
 -f ../k8s/zipkin.yaml
```

## Build and run

With Docker:
```bash
docker build -t helidon-examples-translator-backend backend/
docker build -t helidon-examples--translator-frontend frontend/
docker run --rm -d -p 9080:9080 \
    --link zipkin \
    --name helidon-examples-translator-backend \
     helidon-examples-translator-backend:latest
docker run --rm -d -p 8080:8080 \
    --link zipkin \
    --link helidon-examples-translator-backend \
    --name helidon-examples-translator-frontend \
     helidon-examples-translator-frontend:latest
```

With Java 8+:
```bash
mvn package
java -jar backend/target/helidon-examples-translator-backend.jar &
java -jar frontend/target/helidon-examples-translator-frontend.jar
```

Try the endpoint:
```bash
curl "http://localhost:8080?q=cloud&lang=czech"
curl "http://localhost:8080?q=cloud&lang=french"
curl "http://localhost:8080?q=cloud&lang=italian"
```

Then check out the traces at http://localhost:9411.

## Run with Kubernetes (docker for desktop)

```bash
docker build -t helidon-examples-translator-backend backend/
docker build -t helidon-examples-translator-frontend frontend/
kubectl apply -f backend/app.yaml -f frontend/app.yaml
```

Try the endpoint:
```bash
curl "http://localhost/translator?q=cloud&lang=czech"
curl "http://localhost/translator?q=cloud&lang=french"
curl "http://localhost/translator?q=cloud&lang=italian"
```

Then check out the traces at http://localhost/zipkin.

Stop the docker containers:
```bash
docker stop zipkin \
    helidon-examples-translator-backend \
    helidon-examples-translator-frontend
```

Delete the Kubernetes resources:
```bash
kubectl delete -f backend/app.yaml -f frontend/app.yaml
```