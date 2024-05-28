# Helidon MP on CRaC 
[Coordinated Restore at Checkpoint](https://wiki.openjdk.org/display/crac)


## Runtime CRaC
Standard docker build doesn't support privileged access to the host machine kernel,
therefore CRaC checkpoint needs to be created in runtime.

```bash
mvn clean package
docker build -t crac-helloworld . -f Dockerfile.crac
# First time ran, checkpoint is created, stop with Ctrl-C
docker run --privileged --network host --name crac-helloworld crac-helloworld
# Second time starting from checkpoint, stop with Ctrl-C
docker start -i crac-helloworld
```

### Exercise the app
```
curl -X GET http://localhost:7001/helloworld
curl -X GET http://localhost:7001/helloworld/earth
curl -X GET http://localhost:7001/another
```

## Kubernetes CRaC

```shell
minikube start
bash deploy-minikube.sh
curl $(minikube service crac-helloworld -n crac-helloworld --url)/helloworld/earth | jq
```

```shell
kubectl get pods
# Check first start - leghtly checkpoint creation
kubectl logs --previous --tail=100 -l app=crac-helloworld
# Check restart - fast checkpoint restoration
kubectl logs -l app=crac-helloworld
# Scale-up quickly 
kubectl scale --replicas=3 deployment/crac-helloworld
```