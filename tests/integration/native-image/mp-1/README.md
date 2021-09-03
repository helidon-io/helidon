#GraalVM native image integration test
_____

This is a manual (for the time being) test of integration with native-image.

To run this test:

```shell script
mvn clean package -Dnative.image=$path_to_native_image
./target/native-image
```  

There are a tests run from within the application.
Tests:
1. Injection of Config


To run using module-path:
```shell script
java --module-path target/libs:target/helidon-tests-native-image-mp-1.jar -m helidon.tests.nimage.mp/io.helidon.tests.integration.nativeimage.mp1.Mp1Main
```

To build native image using Helidon feature tracing (with maven):
Add a file `META-INF/native-image/native-image.properties` with the following content:

```properties
Args=-Dhelidon.native.reflection.trace=true -Dhelidon.native.reflection.trace-parsing=true
```

To build native image using Helidon feature tracing (without maven):
```shell script
${GRAALVM_HOME}/bin/native-image -Dhelidon.native.reflection.trace-parsing=true \
    -Dhelidon.native.reflection.trace=true \
    -H:Path=./target \
    -H:Name=helidon-tests-native-image-mp-1 \
    "-H:IncludeResources=logging.properties|meta-config.yaml|web/resource.txt|web/welcome.txt|verify-jwk.json|META-INF/native-image/tests/mp-1/resource-config.json|META-INF/beans.xml|META-INF/microprofile-config.properties|sign-jwk.json" \
    -H:+ReportExceptionStackTraces \
    --no-server \
    -jar ./target/helidon-tests-native-image-mp-1.jar
```

 