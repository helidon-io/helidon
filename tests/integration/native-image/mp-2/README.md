#GraalVM native image integration test MP-2
_____

This is a manual (for the time being) test of integration with native-image.

To run this test:

```shell script
export GRAALVM_HOME=${path.to.graal.with.native-image}
mvn clean package -Pnative-image
./target/helidon-tests-native-image-mp-2
```  

Requires at least GraalVM 20.0.0

This test validates that JPA integration (using Hibernate) and JTA
integration work in native image.

This test requires a running h2 database on `jdbc:h2:tcp://localhost:9092/test` 
with the following table and data:

`CREATE TABLE GREETING (FIRSTPART VARCHAR NOT NULL, SECONDPART VARCHAR NOT NULL, PRIMARY KEY (FIRSTPART))`

`INSERT INTO GREETING (FIRSTPART, SECONDPART) VALUES ('Jack', 'The Ripper')`


