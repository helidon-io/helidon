# TODO Demo Application

This application implements todomvc[http://todomvc.com] with two microservices
implements with Helidon MP and Helidon SE.

## HTTP proxy

If you want to run behind a proxy, you need to configure the config key
 `security.providers.google-login.proxy-host`. You can do that by updating
 `frontend/src/main/resources/application.yaml` and
 `backend/src/main/resources/application.yaml` with the following content:
```yaml
security:
  providers:
    - google-login:
        proxy-host: "proxy.host"
```

## Start Zipkin

With Docker:
```bash
docker run --name zipkin -d -p 9411:9411 openzipkin/zipkin
```

With Kubernetes:
```bash
kubectl apply \
  -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/ingress-nginx-3.15.2/deploy/static/provider/cloud/deploy.yaml \
  -f ../k8s/zipkin.yaml
```

## Build and run

### With Docker:
```bash
docker build -t helidon-examples-todo-cassandra cassandra/
docker build -t helidon-examples-todo-backend backend/
docker build -t helidon-examples-todo-frontend frontend/
docker run --rm -d -p 9042:9042 \
    --link zipkin \
    --name helidon-examples-todo-cassandra \
    helidon-examples-todo-cassandra
docker run --rm -d -p 8854:8854 \
    --link zipkin \
    --link helidon-examples-todo-cassandra \
    --name helidon-examples-todo-backend \
    helidon-examples-todo-backend
docker run --rm -d -p 8080:8080 \
    --link zipkin \
    --link helidon-examples-todo-backend \
    --name helidon-examples-todo-frontend \
    helidon-examples-todo-frontend
```

Open http://localhost:8080 in your browser, add some TODO entries, then check
 out the traces at http://localhost:9411.

### With Kubernetes (docker for desktop)

```bash
docker build -t helidon-examples-todo-cassandra cassandra/
docker build -t helidon-examples-todo-backend backend/
docker build -t helidon-examples-todo-frontend frontend/
kubectl apply -f cassandra.yaml -f backend/app.yaml -f frontend/app.yaml
```

Open http://localhost/todo/ in your browser, add some TODO entries, then
 check out the traces at http://localhost/zipkin.

Stop the docker containers:
```bash
docker stop zipkin \
    helidon-examples-todo-backend \
    helidon-examples-todo-frontend
```

Delete the Kubernetes resources:
```bash
kubectl delete \
    -f cassandra.yaml \
    -f backend/app.yaml \
    -f frontend/app.yaml
```