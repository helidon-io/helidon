# MySQL Integration Example

## Overview

This example shows a trivial Helidon MicroProfile application that
uses the MySQL CDI integration.  It also shows how to run MySQL in a
Docker container and connect to it using the application.

## Notes

To run MySQL's `mysql:8` Docker image in a Docker container named
`mysql` that publishes its port 3306 to the host machine's port 3306
and uses `tiger` as the MySQL root password and that will
automatically be removed when it is stopped:

```sh
docker container run --rm -d -p 3306:3306 \
    --env MYSQL_ROOT_PASSWORD=tiger \
    --name mysql \
    mysql:8
```

(Note that in the `3306:3306` option value above the first port number
is the port number on the host (i.e. your physical machine running
`docker`) and the second number (after the colon) is the port number
on the Docker container.)

To ensure that the sample application is configured to talk to MySQL
running in this Docker container, verify that the following lines
(among others) are present in
`src/main/resources/META-INF/microprofile-config.properties`:

```sh
javax.sql.DataSource.example.dataSourceClassName=com.mysql.cj.jdbc.MysqlDataSource
javax.sql.DataSource.example.dataSource.url = jdbc:mysql://localhost:3306
javax.sql.DataSource.example.dataSource.user = root
javax.sql.DataSource.example.dataSource.password = tiger
```


## Build and run

```bash
mvn package
java -jar target/helidon-integrations-examples-datasource-hikaricp-mysql.jar
```

Try the endpoint:
```sh
curl http://localhost:8080/tables
```

Stop the docker container:
```bash
docker stop mysql
```

## References

- [MySQL Docker documentation](https://hub.docker.com/_/mysql?tab=description)
