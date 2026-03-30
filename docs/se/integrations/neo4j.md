# Neo4j

## Contents

- [Overview](#overview)
- [Maven Coordinates](#maven-coordinates)
- [Usage](#usage)
- [Configuration](#configuration)
- [Examples](#examples)
- [Additional Information](#additional-information)
- [References](#references)

## Overview

Neo4j is a graph database management system developed by Neo4j, Inc. It is an ACID-compliant transactional database with native graph storage and processing. Neo4j is available in a GPL3-licensed open-source “community edition”.

## Maven Coordinates

To enable Neo4j, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

``` xml
<dependency>
   <groupId>io.helidon.integrations.neo4j</groupId>
   <artifactId>helidon-integrations-neo4j</artifactId>
</dependency>
```

> [!NOTE]
> Check [Neo4j Metrics propagation](#neo4j-metrics-propagation) and [Neo4j Health Checks](#neo4j-health-checks) for additional dependencies for *Neo4j* `Metrics` and `Health Checks` integration.

## Usage

The support for Neo4j is implemented in Neo4j driver level. Just add the dependency, add configuration in `application.yaml` file and Neo4j driver will be configured by Helidon and can be used with `Neo4j` support object.

First describe Neo4j connection properties:

``` yaml
neo4j:
 uri: bolt://localhost:7687
 authentication:
   username: neo4j
   password: secret
 pool:
   metricsEnabled: true
```

Then just get the driver:

``` java
Neo4j neo4j = Neo4j.create(config.get("neo4j"));
Driver neo4jDriver = neo4j.driver();
```

The driver can be used according to the [Neo4j documentation](https://neo4j.com/developer/java/).

## Configuration

### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a3b4f6-authentication-enabled"></span> `authentication-enabled` | `VALUE` | `Boolean` | `true` | Enable authentication |
| <span id="a3b383-certificate"></span> `certificate` | `VALUE` | `Path` |   | Set certificate path |
| <span id="aab80e-connection-acquisition-timeout"></span> `connection-acquisition-timeout` | `VALUE` | `Duration` | `PT1M` | Set connection acquisition timeout |
| <span id="a05ed9-encrypted"></span> `encrypted` | `VALUE` | `Boolean` |   | Enable encrypted field |
| <span id="a53ff4-hostname-verification-enabled"></span> `hostname-verification-enabled` | `VALUE` | `Boolean` |   | Enable hostname verification |
| <span id="afe517-idle-time-before-connection-test"></span> `idle-time-before-connection-test` | `VALUE` | `Duration` | `PT1MS` | Set idle time |
| <span id="af0aa4-log-leaked-sessions"></span> `log-leaked-sessions` | `VALUE` | `Boolean` |   | Enable log leaked sessions |
| <span id="acbb81-max-connection-lifetime"></span> `max-connection-lifetime` | `VALUE` | `Duration` | `PT5H` | Set max life time |
| <span id="a3e142-max-connection-pool-size"></span> `max-connection-pool-size` | `VALUE` | `Integer` | `100` | Set pool size |
| <span id="afefd3-metrics-enabled"></span> `metrics-enabled` | `VALUE` | `Boolean` |   | Enable metrics |
| <span id="a74536-password"></span> `password` | `VALUE` | `String` |   | Create password |
| <span id="abfa44-trust-strategy"></span> [`trust-strategy`](../../config/io_helidon_integrations_neo4j_Neo4j_Builder_TrustStrategy.md) | `VALUE` | `i.h.i.n.N.B.TrustStrategy` |   | Set trust strategy |
| <span id="a2db98-uri"></span> `uri` | `VALUE` | `String` |   | Create uri |
| <span id="a98d22-username"></span> `username` | `VALUE` | `String` |   | Create username |

## Examples

This example implements a simple Neo4j REST service using MicroProfile. For this example a working Neo4j database is required. The Neo4j Movie database is used for this example.

Bring up a Neo4j instance via Docker

``` bash
docker run --publish=7474:7474 --publish=7687:7687 -e 'NEO4J_AUTH=neo4j/secret'  neo4j:latest
```

Go to the Neo4j browser and play the first step of the movies graph: [`:play movies`](http://localhost:7474/browser/?cmd=play&arg=movies)

Now go to the `pom.xml` and add the following dependencies:

``` xml
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

``` yaml
neo4j:
 uri: bolt://localhost:7687
 authentication:
   username: neo4j
   password: secret
 pool:
   metricsEnabled: true
```

This includes both connection information and enables Neo4j metrics propagation.

Finally, we are able to use the `Neo4j` driver.

``` java
record MovieRepository(Driver driver) { 

    List<Movie> findAll() { 
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
                    return new Actor(v.get("name").asString(), v.get("roles").asList(Value::asString));
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

- Constructor with `Neo4j` driver parameter
- Use `Neo4j` driver to extract all Movies

Movies can now be returned as JSON objects:

``` java
record MovieService(MovieRepository movieRepository) implements HttpService {

    @Override
    public void routing(HttpRules rules) {
        rules.get("/api/movies", this::findMovies);
    }

    void findMovies(ServerRequest request, ServerResponse response) {
        response.send(this.movieRepository.findAll());
    }
}
```

To use the service, as well as to add metrics and health support the following routing should be created:

``` java
Neo4j neo4j = Neo4j.create(config.get("neo4j"));
Driver driver = neo4j.driver(); 

Neo4jMetricsSupport.builder()
        .driver(driver)
        .build()
        .initialize(); 

ObserveFeature observeFeature = ObserveFeature.builder()
        .addObserver(HealthObserver.builder()
                             .addCheck(Neo4jHealthCheck.create(driver))
                             .build())
        .build(); 

WebServer server = WebServer.builder()
        .addFeature(observeFeature)
        .routing(it -> it.register(new MovieService(new MovieRepository(driver)))) 
        .build()
        .start();

System.out.println("WEB server is up! http://localhost:" + server.port() + "/api/movies");
```

- Use of `Neo4j` support object to initialise and configure the driver.
- Use of `Neo4jMetricsSupport` to add *Neo4j* metrics to `/metrics` output.
- Use of `Neo4jHealthCheck` to add *Neo4j* health support.
- Register `MovieService` in *Routing*.

Now build and run.

``` bash
mvn package
java -jar target/helidon-examples-integration-neo4j.jar
```

Exercise the application:

``` bash
curl -X GET http://localhost:8080/movies

# Try health
curl -s -X GET http://localhost:8080/health

# Try metrics in Prometheus Format
curl -s -X GET http://localhost:8080/metrics

# Try metrics in JSON Format
curl -H 'Accept: application/json' -X GET http://localhost:8080/metrics
```

Full example code is available in [Helidon Examples Repository](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/integrations/neo4j).

## Additional Information

### Neo4j Metrics propagation

Neo4j’s metrics can be propagated to the user as `MicroProfile` metrics. This is implemented in a separate Maven module. Just add:

``` xml
<dependency>
   <groupId>io.helidon.integrations.neo4j</groupId>
   <artifactId>helidon-integrations-neo4j-metrics</artifactId>
</dependency>
```

> [!NOTE]
> Works with *Neo4j Integration* main dependency described in [Maven Coordinates](#maven-coordinates).

To enable metrics in Neo4j, add the following property to `application.yaml`:

``` yaml
pool:
   metricsEnabled: true
```

Finally, to initialize metrics run:

``` java
Neo4jMetricsSupport.builder()
        .driver(driver)
        .build()
        .initialize();
```

Neo4j’s metrics will be automatically added to the output of the `/metrics` endpoint.

### Neo4j Health Checks

If your application is highly dependent on Neo4j database, health and liveness checks are essential for this application to work correctly.

`MicroProfile` Health checks for Neo4j are implemented in a separate Maven module:

``` xml
<dependency>
   <groupId>io.helidon.integrations.neo4j</groupId>
   <artifactId>helidon-integrations-neo4j-health</artifactId>
</dependency>
```

> [!NOTE]
> Works with *Neo4j Integration* main dependency described in [Maven Coordinates](#maven-coordinates).

To enable health checks run the following code:

``` java
ObserveFeature observeFeature = ObserveFeature.builder()
        .addObserver(HealthObserver.builder()
                             .addCheck(Neo4jHealthCheck.create(driver))
                             .build())
        .build();
```

Health checks for Neo4j will be included in `/health` endpoint output.

## References

- [Neo4j official website](https://neo4j.com/)
- [Neo4j Java developer guide](https://neo4j.com/developer/java/)
