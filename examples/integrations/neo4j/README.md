# Helidon SE integration with Neo4J example

## Build and run

Bring up a Neo4j instance via Docker

```shell
docker run --publish=7474:7474 --publish=7687:7687 -e 'NEO4J_AUTH=neo4j/secret'  neo4j:4.0
```

Goto the Neo4j browser and play the first step of the movies graph: [`:play movies`](http://localhost:7474/browser/?cmd=play&arg=movies).

Build and run with JDK20
```shell
mvn package
java -jar target/helidon-examples-integration-neo4j.jar  
```

Then access the rest API like this:

````shell
curl localhost:8080/api/movies
````

# Health and metrics

Neo4jSupport provides health checks and metrics reading from Neo4j.

Enable them in the driver:
```yaml
  pool:
    metricsEnabled: true
```

```shell
curl localhost:8080/observe/health
curl localhost:8080/observe/metrics
```
