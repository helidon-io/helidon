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