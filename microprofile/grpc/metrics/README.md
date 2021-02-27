# gRPC Metrics

## Adding support for a new metric type
From time to time, the metrics spec evolves and new metrics appear. 
To add support for a new metric to gRPC, follow these steps.

### Update `GrpcMetrics`
The class contains a very simple method for each metric type like this:
```java
public static GrpcMetrics concurrentGauge() {...}
```
Just add a new method following the same pattern for the new metric type.

### Update `MetricsConfigurer`
Update the map near the top of the file to add the new metric annotation. 
The rest of the logic uses the map rather than hard-coded methods.

### Update `GrpcMetricsCdiExtension`

On the `registerMetrics` method, add the new metrics annotation(s) to the `@WithAnnotation` list of 
metrics 
annotations.

### Add `CoverageTestBeanXXX` test class
where `XXX` is the simple name of the new metrics annotation (e.g., `Counted`).
Use one of the existing classes as a pattern, removing the existing metrics annotation from the 
method and adding the new metrics annotation in its place.

## Testing
To help make sure all known metrics annotations (from the `microprofile/metrics` artifact) are 
handled, a test-time CDI extension finds all the coverage test beans and adds them to CDI. 
It also examines those classes during the normal CDI extension life cycle to make sure the 
actual gRPC extension processes them as expected.

Several tests make sure that all the metrics annotations are represented and processed, listing any annotations that were not processed as expected.

