# Jedis Integration Example

## Start Redis

```bash
docker run --rm --name redis -d -p 6379:6379 redis
```

## Build and run

With Docker:
```bash
docker build -t helidon-examples-integrations-cdi-jedis .
docker run --rm -d \
    --link redis
    --name helidon-examples-integrations-cdi-jedis \
    -p 8080:8080 helidon-examples-integrations-cdi-jedis:latest
```

With Java 8+:
```bash
mvn package
java -jar target/helidon-examples-integrations-cdi-jedis.jar
```

Try the endpoint:
```bash
curl -X PUT  -H "Content-Type: text/plain" http://localhost:8080/jedis/foo -d 'bar'
curl http://localhost:8080/jedis/foo
```

## Run with Kubernetes (docker for desktop)

```bash
docker build -t helidon-examples-integrations-cdi-jedis .
kubectl apply -f ../../k8s/ingress.yaml -f app.yaml
```

Try the endpoint:
```bash
curl -X PUT -H "Content-Type: text/plain" http://localhost/helidon-cdi-jedis/jedis/foo -d 'bar'
curl http://localhost/helidon-cdi-jedis/jedis/foo
```

Stop the docker containers:
```bash
docker stop redis helidon-examples-integrations-cdi-jedis
```

Delete the Kubernetes resources:
```bash
kubectl delete -f ../../k8s/ingress.yaml -f app.yaml
```