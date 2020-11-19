# Helidon Neo4j proposal

## Proposal

Currently, there is no proper integration between Neo4j and Helidon.
Neo4j is a de facto standard for graph databases worldwide. Supporting it out of the box will be a huge benefit for Helidon.

## from Helidon side

For Helidon MP and SE there are three projects:
* Support: Contains SE integration with the main idea to expose a configured driver. There is also the CDI extension that delegates all the initialization and configuration to SE support.
* Health: health checks for the Neo4j based on the driver. Provided as a separate module.   
* Metrics: metrics exposition. Provides a wrapper over Neo4j mectrics and exposes them for SE. They are available as MP Metrics as well. Provided as a separate module. 

Usage:
```Java
Neo4JSupport neo4j = Neo4JSupport.builder()
        .config(config)
        .helper(Neo4JMetricsSupport.create()) //optional support for Neo4j Metrics
        .helper(Neo4JHealthSupport.create()) //optional support for Neo4j Health checks
        .build();
```


At this point there is no need to create or use any kind of Object Mapping. Exposing the driver, providing health checks 
and metrics should be considered as enough. 