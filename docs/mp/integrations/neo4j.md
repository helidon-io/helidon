<!--@frontmatter
description: "Helidon Neo4j Integration"
-->
# Neo4j

## Overview

Neo4j is a graph database management system developed by Neo4j, Inc. It is an
ACID-compliant transactional database with native graph storage and processing.
Neo4j is available in a GPL3-licensed open-source “community edition”.

## Maven Coordinates

To enable Neo4j, add the following dependency to your project’s `pom.xml` (see
[Managing Dependencies](../../dependency-management.md)).

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.integrations.neo4j</groupId>
  <artifactId>helidon-integrations-neo4j</artifactId>
</dependency>
```

> [!NOTE]
> Check [Neo4j Metrics propagation](#neo4j-metrics-propagation) and [Neo4j
> Health Checks](#neo4j-health-checks) for additional dependencies for *Neo4j*
> `Metrics` and `Health Checks` integration.

## Usage

The support for Neo4j is implemented in Neo4j driver level. Just add the
dependency, add configuration in `microprofile-config.properties` file and Neo4j
driver will be configured by Helidon and can be injected using CDI.

First describe Neo4j connection properties:

```properties [microprofile-config.properties]
# Neo4j settings
neo4j.uri=bolt://localhost:7687
neo4j.authentication.username=neo4j
neo4j.authentication.password=secret
neo4j.pool.metricsEnabled=true
```

Then just inject the driver:

```java
@Inject
public MovieRepository(Driver driver) {
    this.driver = driver;
}
```

The driver can be used according to the [Neo4j documentation][neo4j-documentat].

## Configuration

### Configuration options

<!--@include ../../config/io.helidon.integrations.neo4j.Neo4j.md#configuration-options delim=--- offset=1 collapseTables=10 -->
See [Configuration options][io-helidon-integ].
<!--/include-->


## Examples

This example implements a simple Neo4j REST service using MicroProfile. For this
example a working Neo4j database is required. The Neo4j Movie database is used
for this example.

Bring up a Neo4j instance via Docker

```shell [Terminal]
docker run --publish=7474:7474 --publish=7687:7687 -e 'NEO4J_AUTH=neo4j/secret'  neo4j:latest
```

Go to the Neo4j browser and play the first step of the movies graph: [`:play
movies`][play-movies]

Now go to the `pom.xml` and add the following dependencies:

```xml [pom.xml]
<dependencies>
  <dependency>
    <groupId>io.helidon.integrations.neo4j</groupId>
    <artifactId>helidon-integrations-neo4j</artifactId>
  </dependency>
  <dependency>
    <groupId>io.helidon.integrations.neo4j</groupId>
    <artifactId>helidon-integrations-neo4j-metrics</artifactId>
  </dependency>
  <dependency>
    <groupId>io.helidon.integrations.neo4j</groupId>
    <artifactId>helidon-integrations-neo4j-health</artifactId>
  </dependency>
</dependencies>
```

Next add the connection configuration properties for Neo4j:

```properties [microprofile-config.properties]
# Neo4j settings
neo4j.uri=bolt://localhost:7687
neo4j.authentication.username=neo4j
neo4j.authentication.password=secret
neo4j.pool.metricsEnabled=true

# Enable the optional MicroProfile Metrics REST.request metrics
metrics.rest-request.enabled=true
```

This includes both connection information and enables Neo4j metrics propagation.

Finally, we are able to inject and use the `Neo4j` driver.

<!--@mdc ::code-callout{collapsed} -->
```java
@ApplicationScoped
public class MovieRepository {

    private final Driver driver;

    @Inject
    public MovieRepository(Driver driver) { // <1>
        this.driver = driver;
    }

    List<Movie> findAll() { // <2>
        try (var session = driver.session()) {
            var query = """
                        match (m:Movie)
                        match (m) <- [:DIRECTED] - (d:Person)
                        match (m) <- [r:ACTED_IN] - (a:Person)
                        return m, collect(d) as directors, collect({name:a.name, roles: r.roles}) as actors
                        """;

            return session.executeRead(tx -> tx.run(query).list(r -> {
                var movieNode = r.get("m").asNode();

                var directors = r.get("directors").asList(v -> {
                    var personNode = v.asNode();
                    return new Person(personNode.get("born").asInt(), personNode.get("name").asString());
                });

                var actors = r.get("actors").asList(v -> {
                    return new Actor(v.get("name").asString(), v.get("roles").asList(
                            Value::asString));
                });

                return new Movie(
                        movieNode.get("title").asString(),
                        movieNode.get("tagline").asString(),
                        movieNode.get("released").asInt(),
                        directors,
                        actors);
            }));
        }
    }
}
```
1. `Neo4j` driver constructor injection
2. Use of `Neo4j` driver to extract all Movies
<!--@mdc :: -->

Movies can now be returned as JSON objects:

```java
@GET
@Produces(MediaType.APPLICATION_JSON)
public List<Movie> getAllMovies() {
    return movieRepository.findAll();
}
```

Now build and run.

```shell [Terminal]
mvn package
java -jar target/helidon-examples-integration-neo4j-mp.jar
```

Exercise the application:

```shell [Terminal]
curl -X GET http://localhost:8080/movies

# Try health
curl -s -X GET http://localhost:8080/health

# Try metrics in Prometheus Format
curl -s -X GET http://localhost:8080/metrics

# Try metrics in JSON Format
curl -H 'Accept: application/json' -X GET http://localhost:8080/metrics
```

## Additional Information

### Neo4j Metrics propagation

Neo4j’s metrics can be propagated to the user as `MicroProfile` metrics. This is
implemented in a separate Maven module. Just add

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.integrations.neo4j</groupId>
  <artifactId>helidon-integrations-neo4j-metrics</artifactId>
</dependency>
```

> [!NOTE]
> Works with *Neo4j Integration* main dependency described in [Maven
> Coordinates](#maven-coordinates).

To enable metrics in Neo4j, add the following property to
`microprofile-config.properties`:

```properties [microprofile-config.properties]
neo4j.pool.metricsEnabled=true
```

By applying these two actions, Neo4j metrics will be automatically added to the
output of the `/metrics` endpoint.

### Neo4j Health Checks

If your application is highly dependent on Neo4j database, health and liveness
checks are essential for this application to work correctly.

`MicroProfile` Health checks for Neo4j are implemented in a separate Maven
module:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.integrations.neo4j</groupId>
  <artifactId>helidon-integrations-neo4j-health</artifactId>
</dependency>
```

> [!NOTE]
> Works with *Neo4j Integration* main dependency described in [Maven
> Coordinates](#maven-coordinates).

Health checks for Neo4j will be included in `/health` endpoint output.

## Reference

- [Neo4j official website](https://neo4j.com/)
- [Neo4j Java developer guide][neo4j-documentat]

[neo4j-documentat]: https://neo4j.com/developer/java/
[play-movies]: http://localhost:7474/browser/?cmd=play&arg=movies
[io-helidon-integ]: ../../config/io.helidon.integrations.neo4j.Neo4j.md#configuration-options
