# Helidon DB Client mongoDB Example

This example shows how to run Helidon DB over mongoDB.


## Build

```
mvn package
```

## Run

This example requires a mongoDB database, start it using docker:
`docker run --rm --name mongo -p 27017:27017 mongo`

Then run the `io.helidon.examples.dbclient.mongo.MongoDbExampleMain` class

## Exercise

The application has the following endpoints:

- http://localhost:8079/db - the main business endpoint (see `curl` commands below)
- http://localhost:8079/metrics - the metrics endpoint (query adds application metrics)
- http://localhost:8079/health - has a custom database health check

Application also connects to zipkin on default address.
The query operation adds database trace.

`curl` commands:

- `curl http://localhost:8079/db` - list all Pokemon in the database
- `curl http://localhost:8079/db/id/2` - get a single pokemon by id
- `curl http://localhost:8079/db/name/Squirtle` - get a single pokemon by name
- `curl -i -X POST -d '{"id":6,"name":"Pidgey"}' http://localhost:8079/db/pokemon` - add a new pokemon Pidgey
- `curl -i -X POST http://localhost:8079/db/type/6/1` - add Normal type to pokemon Pidgey
- `curl -i -X POST http://localhost:8079/db/type/6/3` - add Flying type to pokemon Pidgey
- `curl -i -X PUT http://localhost:8079/db/pokemon/2/Charmeleon` - rename pokemon with id 2 to Charmeleon
- `curl -i -X DELETE http://localhost:8079/db/id/3` - delete pokemon with id 2
- `curl -i -X DELETE http://localhost:8079/db` - delete all pokemons

The application also supports update and delete - see `PokemonService.java` for bound endpoints.

### Proxy
Make sure that `localhost` is not being accessed trough proxy when proxy is configured on your system:
```
export NO_PROXY='localhost'
```
