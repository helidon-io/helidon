# Hikari Connection Pool Integration Example

## Overview

This example shows a trivial Helidon MicroProfile application that
uses the Hikari connection pool CDI integration.  It also shows how to
run the Oracle database in a Docker container and connect the
application to it.

## Prerequisites

You'll need an Oracle account in order to log in to the Oracle
Container Registry.  The Oracle Container Registry is where the Docker
image housing the Oracle database is located.  To set up an Oracle
account if you don't already have one, see
[the Oracle account creation website](https://profile.oracle.com/myprofile/account/create-account.jspx).

## Notes

To log in to the Oracle Container Registry (which you will need to do
in order to download Oracle database Docker images from it):

```bash
docker login -u username -p password container-registry.oracle.com
```

For more information on the Oracle Container Registry, please visit
its [website](https://container-registry.oracle.com/).

To run Oracle's `database/standard` Docker image in a Docker container
named `oracle` that publishes ports 1521 and 5500 to
the host while relying on the defaults for all other settings:

```bash
docker container run -d -it -p 1521:1521 -p 5500:5500 --shm-size=3g \
    --name oracle \
    container-registry.oracle.com/database/standard:latest
```

It will take about ten minutes before the database is ready.

For more information on the Oracle database image used by this
example, you can visit the relevant section of the
 [Oracle Container Registry website](https://container-registry.oracle.com/).

To ensure that the sample application is configured to talk to the
Oracle database running in this Docker container, verify that the
following lines (among others) are present in
`src/main/resources/META-INF/microprofile-config.properties`:

```properties
javax.sql.DataSource.example.dataSourceClassName=oracle.jdbc.pool.OracleDataSource
javax.sql.DataSource.example.dataSource.url = jdbc:oracle:thin:@localhost:1521:ORCL
javax.sql.DataSource.example.dataSource.user = sys as sysoper
javax.sql.DataSource.example.dataSource.password = Oracle
```

## Build and run

With Docker:
```bash
docker build -t helidon-examples-integrations-datasource-hikaricp .
docker run --rm -d \
    --link oracle \
    -e javax_sql_DataSource_example_dataSource_url="jdbc:oracle:thin:@oracle:1521:ORCL" \
    --name helidon-examples-integrations-datasource-hikaricp \
    -p 8080:8080 helidon-examples-integrations-datasource-hikaricp:latest
```
OR

With Maven:
```bash
mvn package
java -jar target/helidon-examples-integrations-datasource-hikaricp.jar
```

Try the endpoint:
```bash
curl http://localhost:8080/tables
```

Stop the docker containers:
```bash
docker stop oracle helidon-examples-integrations-datasource-hikaricp
```
