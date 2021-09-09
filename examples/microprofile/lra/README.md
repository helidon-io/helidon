# Helidon LRA Example

## Build and run

With JDK11+
```bash
mvn install && java -jar ./target/helidon-examples-microprofile-lra.jar
```

### Coordinator
Narayana like coordinator is expected to be running on the port `8070`, url can be changed in application.yaml
```yaml
mp.lra.coordinator.url: http://localhost:8070/lra-coordinator
```

#### Build and run LRA coordinator
> :warning: **Experimental feature**: Helidon LRA coordinator is an experimental tool, running it in production is not advised

```shell
docker build -t helidon/lra-coordinator https://github.com/oracle/helidon.git#:lra/coordinator/server
docker run -dp 8070:8070 --name lra-coordinator --network="host" helidon/lra-coordinator
```

### Test LRA resource
Then call for completed transaction:
```bash
curl -X PUT -d 'lra rocks' http://localhost:7001/example/start-example
```
And observe processing success in the output followed by complete called by LRA coordinator:
```
Data lra rocks processed 🏭
LRA id: f120a842-88da-429b-82d9-7274ee9ce8f6 completed 🎉 
```

For compensated transaction:
```bash
curl -X PUT -d BOOM http://localhost:7001/example/start-example
```
Observe exception in the output followed by compensation called by LRA coordinator:
```
java.lang.RuntimeException: BOOM 💥
	at io.helidon.microprofile.example.lra.LRAExampleResource.startExample(LRAExampleResource.java:56)
...
LRA id: 3629421b-b2a4-4fc4-a2f0-941cbf3fa8ad compensated 🚒 
```

Or compensated transaction timeout:
```bash
curl -X PUT -d TIMEOUT http://localhost:7001/example/start-example
```