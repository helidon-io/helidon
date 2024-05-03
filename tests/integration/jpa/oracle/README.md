# JPA Integration Test Oracle

To run this test:
```shell
mvn clean verify
```

Start the database:
```shell
docker run -d \
  -name oracledb \
  -e ORACLE_PWD=oracle123 \
  -p 1521:1521 \
  container-registry.oracle.com/database/express:latest
```
