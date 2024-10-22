# Packaging Integration Test MP1

This test makes sure the following helidon modules can be compiled into native image:
- Microprofile CDI
- Microprofile Server
- Microprofile Tracing
- Jaeger Tracer
- Microprofile Access Log
- Microprofile Fault Tolerance
- Microprofile Metrics
- Microprofile Health Check
- Microprofile Rest Client
- Microprofile Config
- YAML configuration
- JSON-P
- JSON-B
- Microprofile JWT-Auth

To run this test:
```shell
mvn clean verify
mvn clean verify -Pnative-image
mvn clean verify -Pjlink-image
```

---

To build native image using Helidon feature tracing (with maven):
Add a file `META-INF/native-image/native-image.properties` with the following content:

```properties
Args=-Dhelidon.native.reflection.trace=true -Dhelidon.native.reflection.trace-parsing=true
```

To build native image using Helidon feature tracing (without maven):
```shell
native-image \
    -Dhelidon.native.reflection.trace-parsing=true \
    -Dhelidon.native.reflection.trace=true \
    -H:Path=./target \
    -H:Name=helidon-tests-packaging-mp-1 \
    -H:+ReportExceptionStackTraces \
    -jar ./target/helidon-tests-packaging-mp-1.jar
```
