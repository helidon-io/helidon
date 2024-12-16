# Packaging Integration Test MP2

This test makes sure the following helidon modules can be compiled into native image:
- JPA in MicroProfile app
- JTA in MicroProfile app

---

To run this test:
```shell
mvn clean verify
mvn clean verify -Pnative-image
mvn clean verify -Pjlink-image
```  

---

Start the database manually:
```shell
docker run -d \
  -name oracledb \
  -e ORACLE_PWD=oracle123 \
  -v ./src/test/resources/init.sql:/opt/oracle/scripts/startup/init.sql \
  -p 1521:1521 \
  container-registry.oracle.com/database/express:latest
```

Start the application:
```shell
java -jar target/helidon-tests-integration-packaging-mp-2.jar
```
