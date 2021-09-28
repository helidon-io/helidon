# Helidon Metrics API module

## Overview

### A _metrics-capable_ component author should:
* Declare a dependency on the new `helidon-metrics-api` component instead of on the 
  `helidon-metrics` component directly.
* Use  `instance(ComponentMetricsSettings)` to get a metrics `RegistryFactory`, then use that to get 
    `MetricsRegistry` instances (e.g., `APPLICATION`, `VENDOR`).
* Use the normal `MetricsRegistry` methods to register, look-up, and remove metrics, and use 
    the normal methods on metric instances to update them. 

### App developers can:
* Include or exclude a runtime dependency on `helidon-metrics`.

Without a runtime dependency on `helidon-metrics`, neither the app nor the Helidon components it 
includes can use or update full-featured metrics; they will have access to only the no-op 
implementations.

### Developers and deployers can:
* Control all of metrics using the `metrics.enabled` config key. (enabled by default)
* Control a specific component's use of metrics using `component-name.metrics.enabled`.  
  (enabled by default)
    
## A few details

This module provides the main access point to metrics for Helidon 
components. 
It provides a `RegistryFactory` interface which is API-compatible with and expands on the earlier 
`RegistryFactory` 
class in `helidon-metrics`. (The `helidon-metrics` `RegistryFactory` static methods now delegate 
to the `helidon-metrics-api` `RegistryFactory` to allow components to migrate to the new 
approach over time.)

_Metrics-capable_ Helidon components are ones which should create and update metrics when a 
metrics 
implementation is on the runtime path and the user configuration so indicates. These components 
need to 
compile and run even if a developer:
*  omits the metrics implementation altogether, so the application never supports 
   full-featured metrics, or
* includes the metrics implementation but uses configuration to disable 
metrics for individual components or for the app as a whole.

This `helidon-metrics-api` module allows the code in such components to use metrics classes and 
interfaces 
 regardless of whether metrics is or is not present on the path at build-time or runtime, and 
regardless of whether metrics is disabled for a particular run of the application. If metrics is 
present and enabled at runtime, then Helidon provides the component full-featured metrics; 
otherwise it furnishes a "no-op" implementation.

Key elements in this module for metrics-capable components:
* `RegistryFactory` interface
      
   Components use this interface instead of the 
  earlier `RegistryFactory` 
  class from `helidon-metrics` which components have used 
  previously.
* `ComponentMetricsSettings` interface and its `Builder` interface
  
   Attributes of metrics for 
  metrics-capable components (currently, 
  simply whether metrics are to be enabled for the component or not). 

## Converting a metrics-dependent component to a metrics-enabled one
1. Change `pom.xml` to depend on `helidon-metrics-api` instead of `helidon-metrics`.
2. Either:
   1. Change the service to extend `HelidonRestServiceSuport` if it does not already, or
   2. Update the service and the service's builder to handle a `ComponentMetricsSettings` object 
   and a `metrics` config section within the component's config. (see below)
3. Change invocations of static factory methods on the `helidon-metrics` `RegistryFactory` class to 
   invoke `io.
   helidon.metrics.api.RegistryFactory.instance(ComponentMetricsSettings)` instead.
   
   This method returns a `RegistryFactory` which automatically accounts for:
   * whether the metrics implementation is available at runtime,
   * whether the overall metrics configuration has enabled or disabled metrics in general, and
   * whether the component's metrics settings indicate to use the full-featured metrics
  implementation (assuming it is available).

Aside from that, leave the metrics-related code in metrics-capable components as-is.

For example, suppose the `helidon-xxx` component creates or updates some metrics. You have two 
options.


### Extend `HelidonRestServiceSupport`
If the `XXXSupport` class 
already extends `HelidonRestServiceSupport` then it can use these new or enhanced methods:
* `Builder Builder.componentMetricsSettings(ComponentMetricsSettings)`
    
    sets the component metric 
  settings to be used in creating the service's `RegistryFactory`
* `Builder Builder.config(Config)`
   
   now processes a `metrics` section in the component's config 
* `RegistryFactory metricsRegistryFactory()`
  
   returns the `RegistryFactory` for the 
  component to use, according to the component metrics settings
  
### Change the service and its `Builder`
If the component's `XXXSupport` class does not extend `HelidonRestServiceSupport`, strongly 
consider changing it so it does. Otherwise, you need to change the 
`XXXSupport` class 
in these ways:

1. `XXXSupport.Builder` adds:
    
    ```java
    private ComponentMetricsSettings componentMetricsSettings = ComponentMetricsSettings.DEFAULT;

    Builder componentMetricsSettings(ComponentMetricsSettings componentMetricsSettings) {
       this.componentMetricsSettings = componentMetricsSettings;
       return this;
    }
        
    Builder config(Config componentConfig) {
        ...
        componentConfig.get(ComponentMetricsSettings.METRICS_CONFIG_KEY)
              .as(ComponentMetricsSettings::create)
              .ifPresent(this::componentMetricsSettings);
        ...
    }
    ```
1. `XXXSupport` adds:
   ```java
   private final ComponentMetricsSettings componentMetricsSettings;
   
   private XXXSupport(Builder builder) {
       ...
       componentMetricsSettings = builder.componentMetricsSettings;
       ...
   }
   ```
1. Code inside `XXXSupport` would get a `MetricsRegistry` using
   ```java
   import io.helidon.metrics.api.RegistryFactory;
   ...
      RegistryFactory registryFactory = RegistryFactory
          .getInstance(componentMetricsSettings);
   ...
   ```
   Change other code that currently uses `RegistryFactory` to use this instance to get a 
   `MetricsRegistry`.

## Application developers' and deployers' choices
Users who develop and deploy Helidon apps have three levels of control over metrics:
1. Include or exclude `helidon-metrics` from the project dependencies.
2. With metrics on the runtime path, enable or disable metrics as a whole.
    
    Metrics are enabled by default. Configuring `metrics.enabled` to `false` disables all 
   metrics. In that case, even if metrics-capable components request a full-featured metrics 
   `RegistryFactory` 
   they will get a no-op one.
3. Enable or disable metrics for individual components.
   
   Metrics-capable components are enabled to use metrics by default. Configuring `xxx.metrics.
   enabled` to `false` disables metrics for the `XXX` component.
   
If overall metrics are not on the runtime path or are disabled, Helidon records no updates to metrics and
the `/metrics` endpoints respond with a `404` and an explanatory message.

For example, continuing the hypothetical example, applications 
would prepare `XXXSupport` using either its builder's `componentMetricSettings` method or its 
`config` method:
```java
Config topLevelConfig = // top-level config
XXXSupport xxxSupport = XXXSupport.builder()
        .config(topLevelConfig.get("xxx"))
        ...
```

Further, suppose another component `YYY` were enhanced similarly and the application's Helidon 
config 
contained
```yaml
...
server:
  port: 8080
xxx:
  some-setting: 1
yyy:
  metrics:
    enabled: false
...
```
`XXX` would use full-featured metrics if `helidon-metrics` were on the path and the no-op
implementation otherwise. `YYY` would always use the no-op implementation.

## Behavior of the no-op implementation
The no-op implementation accepts calls to the `MetricRegistry` methods which register metrics or create them. 
Although the registry records or looks up metrics normally, any metric it creates and 
returns is a no-op. 
For example, with the no-op `MetricsRegistry` if a component code creates a `Counter` the code can 
find 
that registered metric. If that code then 
increments that counter, the subsequently retrieved counter value is always 0.

The `remove` operations operate as normal, 
so a metrics-capable component can remove metrics it created for a  
transitory object 
(such as a connection to a database, etc.) normally. 

In short, metrics-capable components may create, register, and update metrics but should 
not depend on their values changing.
