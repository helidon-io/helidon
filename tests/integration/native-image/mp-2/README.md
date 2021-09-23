#GraalVM native image integration test MP-2
_____

This is a manual (for the time being) test of integration with native-image.

To run this test:

```shell script
export GRAALVM_HOME=${path.to.graal.with.native-image}
mvn clean package -Pnative-image
./target/helidon-tests-native-image-mp-2
```  

Requires at least GraalVM 21.1.0

This test validates that JPA integration (using Hibernate) and JTA
integration work in native image.

This test requires a running oracle database on `jdbc:oracle:thin:@localhost:1521/XE`
    username `system`, password `oracle` (or modify `microprofile-config.properties` to match your DB) 
with the following table and data:

```sql
CREATE TABLE GREETING (FIRSTPART VARCHAR2(100), SECONDPART VARCHAR2(100), PRIMARY KEY (FIRSTPART))
INSERT INTO GREETING (FIRSTPART, SECONDPART) VALUES ('Jack', 'The Ripper')
```

Once the test is done against Oracle DB database, switch to h2:

Make sure the table exists and contains expected data:
```sql
CREATE TABLE GREETING (FIRSTPART VARCHAR NOT NULL, SECONDPART VARCHAR NOT NULL, PRIMARY KEY (FIRSTPART))
INSERT INTO GREETING (FIRSTPART, SECONDPART) VALUES ('Jack', 'The Ripper')
```


1. Comment out `ojdbc` and uncomment `h2` in `pom.xml`
2. Comment out `org.hibernate.dialect.Oracle10gDialect"` and uncomment `org.hibernate.dialect.H2Dialect` in `persistence.xml`
3. Comment out all oracle driver properties and uncomment all h2 driver properties in `microprofile-config.properties`
