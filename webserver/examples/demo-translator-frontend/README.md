Translator Frontend Example Application
======================================

Running locally
---------------
Prerequisites:
1. Requirements: JDK9, Maven, Docker (optional)
2. Add following lines to `/etc/hosts`
    ```
    127.0.0.1 zipkin
    127.0.0.1 helidon-webserver-translator-backend
    127.0.0.1 helidon-webserver-translator-frontend
    ```
3. Run Zipkin: <br/>
    In Docker:
    ```
    docker run -d -p 9411:9411 openzipkin/zipkin
    ```
    or with Java 8:
    ```
    wget -O zipkin.jar 'https://search.maven.org/remote_content?g=io.zipkin.java&a=zipkin-server&v=LATEST&c=exec'
    java -jar zipkin.jar
    ```

Build and run:
```
mvn clean install -pl examples/demo-translator-frontend
mvn exec:java -pl examples/demo-translator-frontend
curl "http://helidon-webserver-translator-frontend:8080/translator?q=cloud"
```
Check out the traces at: ```http://zipkin:9411```


Aura
----

### Deployment
In the parent `examples/` directory, execute:
```
export KUBECONFIG=/path/to/your/kubeconfig
ls *translator*/**/*.yaml | while read FILE; do kubectl create -f <(istioctl kube-inject -f $FILE) & done
```
### Accessing
```

# Setup port-forwarding
kubectl port-forward $(kubectl get pod -l component=aura-istio-zipkin -o jsonpath='{.items[0].metadata.name}') 9411:9411 &
kubectl port-forward $(kubectl get pod -l component=aura-istio-grafana -o jsonpath='{.items[0].metadata.name}') 3000:3000 &
kubectl port-forward $(kubectl get pod -l component=aura-istio-ingress -o jsonpath='{.items[0].metadata.name}') 30080:80 &

# Call the FE
watch -n 0.1 curl 'http://localhost:30080/translator?q=cloud'

# Break a random BE pod
watch -n 10 curl 'http://localhost:30080/translator/break' -X POST
# or alternatively break a specific one by execing into a specific pod
kubectl exec -c proxy "$(kubectl get pod -l app=helidon-webserver-translator-backend -o jsonpath='{.items[0].metadata.name}')" -i -t -- /bin/bash -c "watch -n 10 curl 'http://localhost:9080/translator/break' -X POST"

# Fix a random BE pod
watch -n 10 curl 'http://localhost:30080/translator/fix' -X POST

# See Grafana
open http://localhost:3000

# See Zipkin
open http://localhost:9411
```
### Uninstallation
```
export KUBECONFIG=/path/to/your/kubeconfig
ls *translator*/**/*.yaml | while read FILE; do kubectl delete -f $FILE & done

```

Running in MiniKube
-------------------

Prerequisites
1. Running Minikube and Zipkin: check out instructions at [WebServer Examples OpenTracing](../opentracing/README.md)

Running Translator Frontend app
```
eval $(minikube docker-env)

mvn clean deploy -Dmaven.deploy.skip=true -Drelease.enforce.no-snapshosts.phase=none -P release

\# Without Istio
kubectl create -f examples/demo-translator-frontend/k8s/frontend.yaml
\# With Istio
kubectl create -f <(istioctl kube-inject -f examples/demo-translator-frontend/k8s/frontend.yaml)
kubectl create -f examples/demo-translator-frontend/k8s/gateway.yaml

\# Without Istio
curl "$(minikube service helidon-webserver-translator-frontend --url)/translator?q=cloud"

\# With Istio
curl "$(minikube service -l istio=ingress --url)/translator?q=cloud"
```

Check out Zipkin traces:
```
minikube service zipkin

```

