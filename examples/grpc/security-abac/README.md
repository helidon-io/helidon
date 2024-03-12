# Helidon gRPC Security ABAC Example

An example gRPC server for attribute based access control.

## Build and run
Build:
```shell
mvn -f ../pom.xml -pl common,security-abac package
```

Run using programmatic ABAC setup(see [AbacServer.java](src/main/java/io/helidon/grpc/examples/security/abac/AbacServer.java)):
```shell
java -jar target/helidon-examples-grpc-security-abac.jar
```

Run using ABAC config setup (see [application.yaml](src/main/resources/application.yaml)):
```shell
java -cp target/helidon-examples-grpc-security-abac.jar \
    io.helidon.grpc.examples.security.abac.AbacServerFromConfig
```

Exercise the example using SecureStringClient:
```shell
java -cp target/helidon-examples-grpc-security-abac.jar \
    io.helidon.grpc.examples.security.abac.SecureStringClient
```

The client will only fail if parameters are not within the ABAC attributes setup. For example, below failed
because the request was made outside the `time-of-day` attribute range: 
```shell
Jul 20, 2022 12:27:39 PM io.helidon.security.DefaultAuditProvider lambda$logEvent$1
FINEST: FAILURE authz.authorize 71e94b20-961c-4123-8f8b-ad8c365b8f80:1  io.helidon.common.context.Contexts runInContext Contexts.java 117 :: "Path Optional[StringService/Upper]. Provider io.helidon.security.providers.abac.AbacProvider, Description io.helidon.security.AuthorizationClientImpl@186478ad, Request Optional[Subject:    Principal: Principal{properties=BasicAttributes{registry={name=user, id=user}}, name='user', id='user'}   Principal: role:user_role        Principal: scope:calendar_read  Principal: scope:calendar_edit ]. Subject FATAL: 12:27:38 is in neither of allowed times: [08:15 - 12:00, 12:30 - 17:30] at io.helidon.security.abac.time.TimeValidator@72486851"
Jul 20, 2022 12:27:39 PM io.helidon.security.DefaultAuditProvider lambda$logEvent$1
FINEST: FAILURE grpcRequest 71e94b20-961c-4123-8f8b-ad8c365b8f80:1  io.helidon.security.integration.grpc.GrpcSecurityHandler processAudit GrpcSecurityHandler.java 442 :: "PERMISSION_DENIED StringService/Upper grpc grpc requested by Subject:   Principal: Principal{properties=BasicAttributes{registry={name=user, id=user}}, name='user', id='user'}         Principal: role:user_role       Principal: scope:calendar_read  Principal: scope:calendar_edit "
```
