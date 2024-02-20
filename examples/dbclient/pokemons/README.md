# Helidon DB Client Pokémon Example with JDBC

This example shows how to run Helidon DB Client over JDBC.

Application provides REST service endpoint with CRUD operations on Pokémons
database.

## Database

Database model contains two tables:

**Types**

| Column | Type    | Integrity   |
|--------|---------|-------------|
| id     | integer | Primary key |
| name   | varchar | &nbsp;      |

**Pokemons**

| Column  | Type    | Integrity   |
|---------|---------|-------------|
| id      | integer | Primary key |
| name    | varchar | &nbsp;      |
| id_type | integer | Type(id)    |

with 1:N relationship between *Types* and *Pokémons*

Examples are given for H2, Oracle, or MySQL databases (note that MySQL is currently not supported for GraalVM native image)

To switch between JDBC drivers:

- Uncomment the appropriate dependency in `pom.xml`
- Uncomment the configuration section in `application.yaml` and comment out the current one

## Build

To build a jar file
```
mvn package
```

To build a native image (supported only with Oracle, MongoDB, or H2 databases)
```
mvn package -Pnative-image
```

## Database
This example can run with any JDBC supported database.
In the `pom.xml` and `application.yaml` we provide configuration needed for Oracle database, MySQL and H2 database.
Start your database before running this example.

Example docker commands to start databases in temporary containers: 

Oracle:
```
docker run --rm --name xe -p 1521:1521 -p 8888:8080 wnameless/oracle-xe-11g-r2
```
For details on an Oracle Docker image, see https://github.com/oracle/docker-images/tree/master/OracleDatabase/SingleInstance

H2:
```
docker run --rm --name h2 -p 9092:9082 -p 8082:8082 nemerosa/h2
```
For details, see http://www.h2database.com/html/cheatSheet.html

MySQL:
```
docker run --rm --name mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root \
       -e MYSQL_DATABASE=pokemon -e MYSQL_USER=user -e MYSQL_PASSWORD=password  mysql:5.7
```


## Run

Then run the `io.helidon.examples.dbclient.pokemons.Main` class:
```
java -jar target/helidon-examples-dbclient-pokemons.jar
```

Or run the native image:
```
./target/helidon-examples-dbclient-pokemons
```

### Run with MongoDB

It's possible to run example with MongoDB database. Start it using docker:
```
docker run --rm --name mongo -p 27017:27017 mongo
```

Then run the `io.helidon.examples.dbclient.pokemons.Main` class with `mongo` argument:
```
java -jar target/helidon-examples-dbclient-pokemons.jar mongo
```

## Test Example

The application has the following endpoints:

- http://localhost:8080/db - the main business endpoint (see `curl` commands below)
- http://localhost:8080/metrics - the metrics endpoint (query adds application metrics)
- http://localhost:8080/health - has a custom database health check

Application also connects to zipkin on default address.
The query operation adds database trace.

```
# List all Pokémon
curl http://localhost:8080/db/pokemon

# List all Pokémon types
curl http://localhost:8080/db/type

# Get a single Pokémon by id
curl http://localhost:8080/db/pokemon/2

# Get a single Pokémon by name
curl http://localhost:8080/db/pokemon/name/Squirtle

# Add a new Pokémon Rattata
curl -i -X POST -H 'Content-type: application/json' -d '{"id":7,"name":"Rattata","idType":1}' http://localhost:8080/db/pokemon

# Rename Pokémon with id 7 to Raticate
curl -i -X PUT -H 'Content-type: application/json' -d '{"id":7,"name":"Raticate","idType":2}' http://localhost:8080/db/pokemon

# Delete Pokémon with id 7
curl -i -X DELETE http://localhost:8080/db/pokemon/7
```

### Proxy

Make sure that `localhost` is not being accessed trough proxy when proxy is configured on your system:
```
export NO_PROXY='localhost'
```

---

Pokémon, and Pokémon character names are trademarks of Nintendo.
