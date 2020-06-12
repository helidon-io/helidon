# Helidon DB Client Pokemon Example

This example shows how to run Helidon DB Client.

Application provides REST service endpoint with CRUD operations on Pokemnons
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

with 1:N relationship between *Types* and *Pokemons*

Examples are given for H2, Oracle, or MySQL databases (note that MySQL is currently not supported for GraalVM native image)

Uncomment the appropriate dependencies in the pom.xml for the desired database (H2, Oracle, or MySQL) and insure others are commented.

Uncomment the appropriate configuration in the application.xml for the desired database (H2, Oracle, or MySQL) and insure others are commented.

## Build

```
mvn package
```

This example may also be run as a GraalVM native image in which case can be built using the following: 

```
mvn package -Pnative-image
```


## Run

This example requires a database. 

Instructions for H2 can be found here: http://www.h2database.com/html/cheatSheet.html

Instructions for Oracle can be found here: https://github.com/oracle/docker-images/tree/master/OracleDatabase/SingleInstance

MySQL can be run as a docker container with the following command:
```
docker run --rm --name mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=pokemon -e MYSQL_USER=user -e MYSQL_PASSWORD=password  mysql:5.7
```


Then run the application:

```
java -jar target/helidon-examples-dbclient-jdbc.jar
```
or in the case of native image
```
./target/helidon-examples-dbclient-jdbc
```
 

### Run with MongoDB

It's possible to run example with MongoDB database. Start it using docker:
```
docker run --rm --name mongo -p 27017:27017 mongo
```

Then run the `io.helidon.examples.dbclient.pokemons.PokemonMain` class with `mongo` argument:
```
cd <project_root>/examples/dbclient/pokemons
java -jar target/helidon-examples-dbclient-pokemons.jar mongo
```

## Test Example

The application has the following endpoints:

- http://localhost:8079/db - the main business endpoint (see `curl` commands below)
- http://localhost:8079/metrics - the metrics endpoint (query adds application metrics)
- http://localhost:8079/health - has a custom database health check

Application also connects to zipkin on default address.
The query operation adds database trace.

`curl` commands:

- `curl http://localhost:8079/db/type | json_pp` - list all pokemon types in the database
- `curl http://localhost:8079/db/pokemon | json_pp` - list all pokemons in the database
- `curl http://localhost:8079/db/pokemon/2 | json_pp` - get a single pokemon by id
- `curl http://localhost:8079/db/pokemon/name/Squirtle | json_pp` - get a single pokemon by name
- `curl -i -X POST -d '{"id":7,"name":"Rattata","idType":1}' http://localhost:8079/db/pokemon` - add a new pokemon Rattata
- `curl -i -X PUT -d '{"id":7,"name":"Raticate","idType":2}' http://localhost:8079/db/pokemon` - rename pokemon with id 7 to Raticate
- `curl -i -X DELETE http://localhost:8079/db/pokemon/7` - delete pokemon with id 7

### Proxy

Make sure that `localhost` is not being accessed trough proxy when proxy is configured on your system:
```
export NO_PROXY='localhost'
```
