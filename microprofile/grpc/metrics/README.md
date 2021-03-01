# gRPC Metrics

## Adding support for a new metric type
From time to time, the metrics spec evolves and new metrics appear. 
To add support for a new metric to gRPC, follow these steps.

### Update `GrpcMetrics`
#### Add `xxx` metric factory method
The class contains a very simple method for each metric type like this:
```java
public static GrpcMetrics concurrentGauge() {...}
```
Just add a new method following the same pattern for the new metric type.

#### Add `xxxServerCall` method
The class also contains a method per metrics like this:
```java
private class ConcurrentGaugeServerCall<ReqT, RespT>
            extends MetricServerCall<ReqT, RespT, ConcurrentGauge> {...}
```
Add a new method following the same pattern for the new metric type. The new method will need to 
update its metric in a way that varies among the different metrics types. 

#### Update the `interceptCall` method
Add a new `case` for the new metric to the `switch` statement. 

### Update `GrpcMetricsInterceptorIT`
Add a test for the new metric:
```java
public void shouldUseConcurrentGaugeMetric() throws Exception {...}
```
Follow the pattern set by the methods for other metrics, changing the names and using a 
metric-specific check to make sure the metric was correctly updated.
### Update `MetricsConfigurer`
Update the map near the top of the file to add the new metric annotation. 
The rest of the logic uses the map rather than hard-coded methods.

### Update `GrpcMetricsCdiExtension`

Add the new metric and its corresponding annotation to the `METRICS_ANNOTATIONS` `EnumMap` 
initialization.

On the `registerMetrics` method, add the new metrics annotation(s) to the `@WithAnnotation` list of 
metrics 
annotations.

### Update `MetricsConfigurerTest`
#### Update `ServiceOne` inner class
Add an empty method for the new metric type.
```java
@Unary
@ConcurrentGauge
public void concurrentGauge(String request, StreamObserver<String> response) {}
```

#### Update `ServiceTwo` interface
Add a method for the new metric type:
```java
@Unary
@ConcurrentGauge
void concurrentGauge(String request, StreamObserver<String> response);
```

#### Update `ServiceTwoImpl` class
Add a method for the new metric type:
```java
@Override
public void concurrentGauge(String request, StreamObserver<String> response) {
}
```

Add an invocation of the new method to the `update` method:
```java
    ...
    .unary("concurrentGauge", this::concurrentGauge);
```
#### Add tests
##### Add a test to make sure the metric is updated

Follow the pattern:
```java
@Test
public void shouldAddConcurrentGaugeMetricFromClassAnnotation() {...}
```
##### Add a test to make sure the new metric annotation on the interface is ignored

Follow the pattern:
```java
@Test
public void shouldIgnoreConcurrentGaugeMetricFromInterfaceAnnotation {...}
```
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

