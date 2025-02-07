# JPA Integration Test MySQL

To run this test:
```shell
mvn clean verify
```

Start the database:
```shell
docker run -d \
    --name mysql \
    -e MYSQL_DATABASE=test \
    -e MYSQL_USER=test \
    -e MYSQL_PASSWORD=mysql123 \
    -p 3306:3306 \
    container-registry.oracle.com/mysql/community-server:latest
```
