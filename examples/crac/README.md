# Helidon MP on CRaC 
[Coordinated Restore at Checkpoint](https://wiki.openjdk.org/display/crac)


## Runtime CRaC
Standard docker build doesn't support privileged access to the host machine kernel,
therefore CRaC checkpoint needs to be created in runtime.

```bash
mvn clean package
docker build -t crac-runtime-helloworld . -f Dockerfile.runtime_crac
# First time ran, checkpoint is created, stop with Ctrl-C
docker run --privileged -p 7001:7001 --name crac-runtime-helloworld crac-runtime-helloworld
# Second time starting from checkpoint, stop with Ctrl-C
docker start -i crac-runtime-helloworld
```

## Buildtime CRaC
Docker buildx ...

[//]: # (TODO docker buildx with privileged access?)

### Exercise the app
```
curl -X GET http://localhost:7001/helloworld
curl -X GET http://localhost:7001/helloworld/earth
curl -X GET http://localhost:7001/another
```