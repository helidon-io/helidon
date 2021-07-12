# Running the LRA TCKs

### Mock coordinator

Run the TCKs with Mock Coordinator, `mock-coordinator` maven profile is enabled by default.

```shell
mvn test
```

### Narayana coordinator

#### Download and run Narayana coordinator
```shell
wget https://search.maven.org/remotecontent?filepath=org/jboss/narayana/rts/lra-coordinator-quarkus/5.11.1.Final/lra-coordinator-quarkus-5.11.1.Final-runner.jar \
-O narayana-coordinator.jar \
&& java -Dquarkus.http.port=8070 -jar narayana-coordinator.jar
```

Run the TCKs with external Narayana like coordinator on port `8070` by setting custom `mock-coordinator` maven
profile.

#### Run tests
```shell
mvn test -P \!mock-coordinator -Dtck.lra.coordinator.url=http://localhost:8070/lra-coordinator
```