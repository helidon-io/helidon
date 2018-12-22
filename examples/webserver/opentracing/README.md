Opentracing Example Application
===============================

Running locally
---------------
Prerequisites:
1. Requirements: JDK9, Maven, Docker (optional)
2. Add following lines to `/etc/hosts`
    ```
    127.0.0.1 zipkin
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
mvn clean install -pl examples/opentracing
mvn exec:java -pl examples/opentracing
curl "http://localhost:8080/test"
```
Check out the traces at: ```http://zipkin:9411```


Running in Minikube
-------------------

### Preparing the infrastructure ###
Starting Minikube

```
% minikube start

% kubectl version
Client Version: version.Info{Major:"1", Minor:"6", GitVersion:"v1.6.2", GitCommit:"477efc3cbe6a7effca06bd1452fa356e2201e1ee", GitTreeState:"clean", BuildDate:"2017-04-19T22:51:36Z", GoVersion:"go1.8.1", Compiler:"gc", Platform:"darwin/amd64"}
Server Version: version.Info{Major:"1", Minor:"6", GitVersion:"v1.6.0", GitCommit:"fff5156092b56e6bd60fff75aad4dc9de6b6ef37", GitTreeState:"dirty", BuildDate:"2017-04-07T20:46:46Z", GoVersion:"go1.7.3", Compiler:"gc", Platform:"linux/amd64"}

% minikube dashboard
  Waiting, endpoint for service is not ready yet...
  Opening kubernetes dashboard in default browser...

```

Running Zipkin in K8S
```
% kubectl run zipkin --image=openzipkin/zipkin --port=9411
deployment "zipkin" created

% kubectl expose deployment zipkin --type=NodePort
service "zipkin" exposed

% kubectl get pod
NAME                      READY     STATUS              RESTARTS   AGE
zipkin-2596933303-bccnw   0/1       ContainerCreating   0          14s

% kubectl get pod
NAME                      READY     STATUS    RESTARTS   AGE
zipkin-2596933303-bccnw   1/1       Running   0          16s

% minikube service zipkin
Opening kubernetes service default/zipkin in default browser...
```

Running opentracing app
```
% eval $(minikube docker-env)
% mvn clean install -pl examples/opentracing docker:build

% kubectl run helidon-webserver-opentracing-example --image=mic.docker.oraclecorp.com/helidon-webserver-opentracing-example:0.1.0-SNAPSHOT --port=8080 --image-pull-policy=Never
deployment "helidon-webserver-opentracing-example" created

% kubectl expose deployment helidon-webserver-opentracing-example --type=NodePort
service "helidon-webserver-opentracing-example" exposed

% curl $(minikube service helidon-webserver-opentracing-example --url)/test
Hello World!%
```


