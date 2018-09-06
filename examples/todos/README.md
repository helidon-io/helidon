TODOs Demo Application
=====================

If you want to run behind a proxy, you need to configure the following in application.yaml of both services (find appropriate
existing google-login provider configuration):
```yaml
providers:
    - google-login:
        proxy-host: "proxy.host"
        client-id: "1048216952820-6a6ke9vrbjlhngbc0al0dkj9qs9tqbk2.apps.googleusercontent.com"
```

Build and start the applications:
```bash
mvn clean install
mvn -f demo-frontend/pom.xml  generate-resources docker:build
mvn -f demo-backend/pom.xml  generate-resources docker:build
mvn -f demo-backend/cassandra/pom.xml generate-resources docker:build
```
and then
```bash
docker run -d --name zipkin -p 9411:9411 openzipkin/zipkin
docker run -d --name helidon-todos-cassandra -p 9042:9042 --link zipkin helidon.demos/io/helidon/demo/helidon-todos-cassandra
docker run -d --name helidon-todos-backend -p 8854:8854 --link zipkin --link helidon-todos-cassandra helidon.demos/io/helidon/demo/helidon-todos-backend
docker run -d --name helidon-todos-frontend -p 8080:8080 --link zipkin --link helidon-todos-backend helidon.demos/io/helidon/demo/helidon-todos-frontend
```
or
```bash
docker-compose up
```

Link map:

| URL | Description |
| --- | ----------- |
| http://localhost:8080/index.html | Main page of the demo |
| http://localhost:8080/metrics | Prometheus metrics (frontend metrics) |
| http://localhost:8080/env | Environment name (from configuration) |
| http://localhost:9411/zipkin/ | Tracing page for Zipkin (served from docker started above) |

Kubernetes setup: This assumes that the cluster is running DNS for services and pods. This
is required for pods to find services by name as shown in the configuration.

```bash
export KUBECONFIG=./path/to/admin.conf

# From application root
cd k8s

# Create or update deployments and services for demo
kubectl apply -f k8s-deployment.yml
```

It takes a few minutes for all containers to start when running in Kubernetes. After all 
containers are started, use these URLs:

| URL | Description |
| --- | ----------- |
| http://localhost:30080/index.html | Main page of the demo |
| http://localhost:30080/metrics | Prometheus metrics (frontend metrics) |
| http://localhost:30080/env | Environment name (from configuration) |
| http://localhost:30011/zipkin | Tracing page for Zipkin (served from docker started above) |
