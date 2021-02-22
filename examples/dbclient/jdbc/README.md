# Helidon DB Client JDBC Example

This example shows how to run Helidon DB Client over JDBC.

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
 
## Exercise

The application has the following endpoints:

- http://localhost:8079/db - the main business endpoint (see `curl` commands below)
- http://localhost:8079/metrics - the metrics endpoint (query adds application metrics)
- http://localhost:8079/health - has a custom database health check

Application also connects to zipkin on default address.
The query operation adds database trace.

`curl` commands:

- `curl http://localhost:8079/db` - list all Pokemon in the database
- `curl -i -X PUT -d '{"name":"Squirtle","type":"water"}' http://localhost:8079/db` - add a new pokemon
- `curl http://localhost:8079/db/Squirtle` - get a single pokemon
- `curl http://localhost:8079/blockigdb` - list all Pokemon in the database in a blocking way
- `curl -i -X DELETE http://localhost:8079/blockingdb` - delete all pokemon in a blocking way

The application also supports update and delete - see `PokemonService.java` for bound endpoints.

---

Pokémon, and Pokémon character names are trademarks of Nintendo.
