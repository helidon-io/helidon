#GraalVM native image integration test MP-3
_____

This is a manual (for the time being) test of integration with native-image.

To run this test:

```shell script
export GRAALVM_HOME=${path.to.graal.with.native-image}
mvn clean package -Pnative-image
./target/helidon-tests-native-image-mp-3
```  

Requires at least GraalVM 20.0.0

This test validates that Quickstart can run on native image with minimal number of changes - eventually with no changes.

To run this test using module path:
```shell script
java --module-path target/libs:target/helidon-tests-native-image-mp-3.jar --add-modules helidon.tests.nimage.quickstartmp -m io.helidon.microprofile.cdi/io.helidon.microprofile.cdi.Main
```
