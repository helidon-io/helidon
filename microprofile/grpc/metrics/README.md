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

## Testing
To help make sure all known metrics annotations (from the `microprofile/metrics` artifact) are 
handled, 
during 
testing an 
annotation processor generates test classes, one per known metrics annotation, each with a 
method that the normal gRPC CDI extension will process. 

Several tests make sure 
that all the known metrics annotations are covered in the areas listed above. 
Any test failures should list the metrics annotations that seem not to be processed correctly.