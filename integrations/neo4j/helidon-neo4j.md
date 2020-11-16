# Helidon Neo4j proposal

## Proposal

Currently, there is no proper integration between Neo4j and Helidon.
Neo4j is a de facto standard for graph databases worldwide. Supporting it out of the box will be a huge benefit for Helidon.

## from Helidon side

For Helidon MP there are three projects:
* cdi - an extension for driver initialization;
* health – health check
* metrics – metrics exposition  

At this point there is no need to create or use any kind of Object Mapping. Exposing the driver, providing health checks 
and metrics should be considered as enough. 