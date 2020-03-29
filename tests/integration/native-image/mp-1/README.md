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
