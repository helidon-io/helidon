# Helidon LRA Example

## Build and run

With JDK11+
```bash
mvn install && java -jar ./target/helidon-examples-microprofile-lra.jar
```

### Coordinator
Narayana like coordinator is expected to be running on the port `8070`, can be changed in application.yaml
```yaml
mp.lra.coordinator.url: http://localhost:8070/lra-coordinator
```

#### Download and run Narayana coordinator
```shell
wget https://search.maven.org/remotecontent?filepath=org/jboss/narayana/rts/lra-coordinator-quarkus/5.11.1.Final/lra-coordinator-quarkus-5.11.1.Final-runner.jar \
-O narayana-coordinator.jar \
&& java -Dquarkus.http.port=8070 -jar narayana-coordinator.jar
```

### Test LRA resource
Then call for completed transaction:
```bash
curl -X PUT -d 'lra rocks' http://localhost:7001/example/start-example
```

And for compensated transaction:
```bash
curl -X PUT -d BOOM http://localhost:7001/example/start-example
```

Or compensated transaction timeout:
```bash
curl -X PUT -d TIMEOUT http://localhost:7001/example/start-example
```