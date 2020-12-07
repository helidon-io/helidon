# Helidon Neo4j support proposal

## Proposal

Currently, there is no proper integration between Neo4j and Helidon.
Neo4j is a de facto standard for graph databases worldwide. Supporting it out of the box will be a huge benefit for Helidon.

## From the Helidon side:

For Helidon MP and SE there are three projects:
* Support: Contains SE integration with the main idea to expose a configured driver. There is also the CDI extension that delegates all the initialization and configuration to SE support.
* Health: health checks for the Neo4j based on the driver. Provided as a separate module.   
* Metrics: metrics exposition. Provides a wrapper over Neo4j metrics and exposes them for SE. They are available as MP Metrics as well. Provided as a separate module. 

### Usage for SE
```java
Neo4JSupport neo4j = Neo4JSupport.builder()
        .config(config)
        .helper(Neo4JMetricsSupport.create()) //optional support for Neo4j Metrics
        .helper(Neo4JHealthSupport.create()) //optional support for Neo4j Health checks
        .build();

 Routing.builder()
        .register(health)                   // Health at "/health"
        .register(metrics)                  // Metrics at "/metrics"
        .register(movieService)
        .build();
```

### Usage for MP
Just inject the driver:
```java
@Inject
Driver driver;
```

### Configuration

May be done in application.yaml:

```yaml
neo4j:
  uri: bolt://localhost:7687
  authentication:
    username: neo4j
    password: secret
  pool:
    metricsEnabled: true #should be explicitly enabled in Neo4j driver 
```
or via MicroProfile Config:

```properties
neo4j.uri=bolt://localhost:7687
neo4j.authentication.username=neo4j
neo4j.authentication.password: secret
neo4j.pool.metricsEnabled: true #should be explicitly enabled in Neo4j driver 

```


At this point there is no need to create or use any kind of Object Mapping. Exposing the driver, providing health checks 
and metrics should be considered as enough. 