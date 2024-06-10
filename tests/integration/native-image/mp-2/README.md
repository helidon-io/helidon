# GraalVM native image integration test MP-2
_____

## Build

```shell
mvn clean package -Pnative-image
```  

## Run

Start the database:
```shell
docker run -it --rm \
  -name oracledb \
  -e ORACLE_PWD=oracle \
  -v $PWD/init.sql:/opt/oracle/scripts/startup/init.sql \
  -p 1521:1521 \
  container-registry.oracle.com/database/express:latest
```

Run the application:
```shell
./target/helidon-tests-native-image-mp-2
```
