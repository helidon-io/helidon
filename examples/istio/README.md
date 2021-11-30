# Helidon Istio

This example showcases how you can setup Helidon microservices inside Istio service mesh and setup communication amongst services inside mesh as well as access a service from outside the mesh.

![Helidon Istio](helidon-istio.png?raw=true "Helidon Istio")

# Environment Setup

Setup Kubernetes cluster (1.21.5) on your machine. We used docker-for-desktop (4.2.0) for this example. See for more details https://docs.docker.com/desktop/kubernetes/

Install Istio (1.12.0) following https://istio.io/latest/docs/setup/getting-started/#download. You don't need to follow "Deploy the sample application" onwards.

Following instructions have been verified with Istio 1.12 on Kubernetes 1.21

# MySQL Setup

For the purpose of this example, we are running MySQL in a container outside Kubernetes using docker-for-desktop.

```
docker run --name mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=<root-password> -e MYSQL_USER=user -e MYSQL_PASSWORD=<user-password> -e MYSQL_DATABASE=helloworld mysql:8
```

Please refer to https://dev.mysql.com/doc/mysql-installation-excerpt/8.0/en/docker-mysql-getting-started.html for detailed instructions.

# helidon-config application

helidon-config is a Helidon MP project that showcases how to load helidon config from a kubernetes configmap.

```
cd helidon-config
```

## Build the Docker Image

```
docker build -f Dockerfile -t helidon-config .
```

## Deploy the application to Kubernetes

```
kubectl cluster-info                         # Verify which cluster
kubectl get pods                             # Verify connectivity to cluster
kubectl apply -f app.yaml                    # Deploy application
kubectl get pods                             # Wait for quickstart pod to be RUNNING
kubectl get service helidon-config-np        # Verify deployed service
```

Note the PORTs. You can now exercise the application as following but use the second
port number (the NodePort) instead of 7001.

## Exercise the application

```
curl -X GET http://localhost:<NodePort>/first
Hello
```

# helidon-jps application

helidon-jps is a Helidon MP project that showcases how to invoke another microservice using RestClient as well as JPA integration (esp. stored procedure call)

```
cd ../helidon-jpa
```

## Build the Docker Image

Update MySQL related properties in `microprofile-config.properties`. Host IP is usually the IP of your laptop and port would be 3306, if you used the command provided above.

```
docker build -f Dockerfile -t helidon-jpa .
```

## Deploy the application to Kubernetes

Let's first make external MySQL available to application. Update MySQL related properties in `istio-mysql-se.yaml`. Host IP is usually the IP of your laptop and port would be 3306, if you used the command provided above.

```
kubectl apply -f istio-mysql-se.yaml        # Creates ServiceEntry inside the mesh
```

Now deploy helidon-jpa application
```
kubectl apply -f app.yaml                   # Deploy application
kubectl get pods                            # Wait for quickstart pod to be RUNNING
```

Expose helidon-jpa application to outside world
```
kubectl apply -f istio-gateway-vs.yaml      # Creates Gateway associated with Istio's ingressgeteway and VirtualService
```

## Exercise the application

In a different Terminal, create the stored procedure in MySQL that will be used by the application.
```
docker exec -it mysql bash
mysql -u root -p
<provide the root password that was used when starting MySQL container>
USE helloworld;
Delimiter //
Create Procedure getAllPersons()
   -> BEGIN
   -> Select * from Person;     # Person table is created when we run the application.
   -> END//
Delimiter ;
Call getAllPersons;             # Verify that stored procedure works
```

Now verify simple greeting:
```
curl -X GET http://localhost/greet
```
returned response should be:
```
{"message":"Hello World!"}
```
We are using localhost above, as it is the EXTERNAL-IP for `istio-ingressgateway` service.

Add new person to the database
```
curl -X POST -H "Content-Type: application/json" \
     -d '{"nick":"bob","name":"Bobby Fischer"}' \
     http://localhost/greet
```
returned response should be:
```
{"nick":"bob","name":"Bobby Fischer"}
```

Greet new person:
```
curl -X GET http://localhost/greet/bob
```
returned response should be:
```
{"message":"Hello Bobby Fischer!"}
```

# Let's enable security

We are going to enable access based on a JSON Web Token (JWT). Please refer to https://istio.io/latest/docs/tasks/security/authorization/authz-jwt/ for detailed instructions.

The following command creates `ingress-jwt-auth` request authentication policy for all `ingressgateway` workload. This policy accepts a JWT issued by `testing@secure.istio.io`
```
kubectl apply -f istio-request-auth.yaml 
```

Verify that a request with an invalid JWT is denied:
```
curl --header "Authorization: Bearer invalidToken" -X GET http://localhost/greet
```
returned response should be:
```
Jwt is not in the form of Header.Payload.Signature with two dots and 3 sections
```

Verify that a request without a JWT is allowed because there is no authorization policy:
```
curl -X GET http://localhost/greet
```
returned response should be:
```
{"message":"Hello World!"}
```

The following command creates `ingress-jwt-must` authorization policy for all `ingressgateway` workload. The policy requires all requests to have a valid JWT.
```
kubectl apply -f istio-auth-policy.yaml 
```

Get the JWT for `testing@secure.istio.io`
```
TOKEN=$(curl https://raw.githubusercontent.com/istio/istio/release-1.12/security/tools/jwt/samples/demo.jwt -s)
```

Verify that a request with a valid JWT is allowed:
```
curl --header "Authorization: Bearer $TOKEN" -X GET http://localhost/greet
```
returned response should be:
```
{"message":"Hello World!"}
```

Verify that a request without a JWT is denied:
```
curl -X GET http://localhost/greet
```
returned response should be:
```
RBAC: access denied
```

# After you’re done, cleanup.

```
kubectl delete -f istio-auth-policy.yaml 
kubectl delete -f istio-request-auth.yaml 
kubectl delete -f istio-gateway-vs.yaml
kubectl delete -f app.yaml
kubectl delete -f istio-mysql-se.yaml
cd ../helidon-config
kubectl delete -f app.yaml
```