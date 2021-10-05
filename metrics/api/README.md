# Helidon Metrics API module

## Intended audience
This informal document is intended for developers of Helidon components who are converting  a 
metrics-dependent component to be metrics-capable instead.

Developers of new metrics-capable components should see the Helidon SE metrics guide (part 2) on developing metrics-capable compoments.

## Overview
Helidon SE provides two related metrics components:
* `helidon-metrics` - The full-featured implementation of Helidon SE metrics.
* `helidon-metrics-api` - 
  New component, containing the public interface to Helidon SE metrics for Helidon SE apps and 
  components.
  This component also includes "no-op" implementations of those interfaces which allow 
  apps and components to create, register, look-up, and remove metrics in metrics registries.
  The implementations of metric types (counters, timers, etc.) in this component _do not update_.

The new module contains interfaces for `RegistryFactory` and 
`MetricsSupport` which are API-compatible with their counterpart classes in `helidon-metrics`. 
With minimal coding changes, components can be converted to metrics-capable.

## Step-by-step Conversion

See `docs/se/guides/_metrics-capable-components.adoc` (the public documentation).

### Change `pom.xml`
Change the `pom.xml` to depend on `helidon-metrics-api` instead of `helidon-metrics`.
    
   ```
   <dependency>
       <groupId>io.helidon.metrics</groupId>
       <artifactId>helidon-metrics-api</artifactId>
   </dependency>
   ```
### Change imports
Change imports of `io.helidon.metrics.RegistryFactory` to `io.helidon.metrics.api.
   RegistryFactory`.

The interfaces in the new module are API-compatible with their counterparts in `helidon-metrics` so existing code that works with the existing `RegistryFactory` should compile and run just the same as before.

### Update the builder for your component

1. Initialize
   ```
   private CompomentMetricsSettings.Builder componentMetricsSettingsBuilder = 
                   CompomentMetricsSettings.builder();
   ``` 
2. Add a setter for that field.
3. Add or augment a setter for `Config` so it includes this or the equivalent:
   ```
   componentConfig
                .get(ComponentMetricsSettings.Builder.METRICS_CONFIG_KEY)
                .as(ComponentMetricsSettings::create)
                .ifPresent(this::componentMetricsSettings);
   ```

### Update the constructor   
1. Get the correct `RegistryFactory` implementation:
   ```
   RegistryFactory rf = 
           RegistryFactory.getInstance(builder.componentMetricsSettingsBuilder.build());
   ```
2. For whichever registry type or types your component uses, get and save the instances using (for example)
   ```
   MetricRegistry appRegistry = rf.getRegistry(MetricRegistry.Type.APPLICATION);
   ...
   ```
### Use the saved registries   
Expose those registries to the rest of your component and use them however your component requires.

You do not have to change any of the component code that registers, looks up, removes, or updates metrics.

## Other topics
### Component documentation
Consider enhancing your component's documentation to cover these topics.

1. Configuration
   
   Many components have their own settings, assignable via the builder and configuration. Add to or create a config format--and document it--for your component that includes a `metrics` section. This corresponds to the new `ComponentMetricsSettings` interface.
    
    Explain in your documentation how applications that depend on your component or users who package or deploy it can control whether your component's metrics features are enabled or disabled, for example using the config setting `utilComponent.metrics.enabled=false`.

2. Packaging

   Your component cannot update live metrics unless the Helidon metrics implementation in `helidon-metrics` is on the path at runtime. Your own component depends on the API, not the implementation. Whoever uses your component in an app or packages and deploys your component needs to be aware that--if they want metrics to work--they need to make sure the metrics  implementation is available at runtime. As long as _some_ Maven artifact in the stack has a runtime or stronger dependency on `helidon-metrics`, then metrics will work. 

  The public doc contains a section about this. Your component's doc can link to that.
