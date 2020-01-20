# Helidon DB JDBC Example

This example shows how to run Helidon DB over JDBC.


## Build

```
mvn package
```

## Run

This example requires a MySQL database, start it using docker:
`docker run --rm --name mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=pokemon -e MYSQL_USER=user -e MYSQL_PASSWORD=password  mysql:5.7`

Then run the `io.helidon.examples.db.jdbc.Main` class
 
## Exercise

The application has the following endpoints:

- http://localhost:8080/db - the main business endpoint (see `curl` commands below)
- http://localhost:8080/metrics - the metrics endpoint (query adds application metrics)
- http://localhost:8080/health - has a custom database health check

Application also connects to zipkin on default address.
The query operation adds database trace.

`curl` commands:

- `curl http://localhost:8080/db` - list all Pokemon in the database
- `curl -i -X POST -d '{"name":"Squirtle","type":"water"}' http://localhost:8080/db` - add a new pokemon
- `curl http://localhost:8080/db/Squirtle` - get a single pokemon

The application also supports update and delete - see `PokemonService.java` for bound endpoints.
