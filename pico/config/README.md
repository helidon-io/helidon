# pico-config (aka config-driven services)

Configuration is optional in the Pico-based framework. But if your application requires service types to be configured in some way then we think you will find <i>pico-config</i> to be a natural extension to the base framework and very easy to adopt and extend. In order to use it, though, you need to understand that (a) it is only designed to work with the pico base API/SPI, (b) all types used are defined are proprietary to Pico since there are no DI/config specifications or standards (e.g., jsr-330) that apply in this space, and (c) you must include additional modules as dependencies - specifically the <i>pico-config-api</i> and <i>pico-config</i> modules at compile and runtime in your application. Additionally, you will need to include the <i>pico-config-processor</i> in your annotation processor classpath.

## Guiding Principles
* There is a separation of logical and physical configuration models. The API is logical and consumer facing, while the SPI is physical and is provider implementation facing. The config model used by each is different and both will be explained below.
* Many applications today are designed in such a way that the underlying service components are first started, and then they fetch their configuration as part of their startup sequence. Pico inverts this. In Pico the presence of configuration causes service components to be created and activated. This is why Pico's configuration approach is commonly referred to as "config-driven services". If the configuration for the service is introduced via it's declared "config bean", then that service is spawned and injected with that config bean during Pico's startup sequence. And this is not limited to just one service starting - it is based upon the number of config beans that are introduced. A new service instance will be created and activated for each config bean introduced.
* Services registered in the Pico services registry are lazy. The way services are activated in Pico is by demand, activating those lazy services. Demand originates from one of the following techniques:
  * The <i>RunLevel</i> annotation, in conjunction with using PicoServices.get() (as described elsewhere).
  * The use of the standard <i>Inject</i> annotation that is used as part of a service that is itself being activated, perhaps occurring in a iterative & recursive manner across a mesh of services (i.e., A injects/demands B, B injects/demands C, etc.). This is why <i>Provider<></i> injection is often preferred since it will avoid (or defer) the activation until when the injected service type is actually needed at runtime when the get() method is first called on it.  
  * Configuration (as described herein).

## Consumer-facing API
* The consumer-facing API deals exclusively with configuration in the form of Config (java) bean interfaces, and are typically annotated with <i>@ConfigBean</i>. Here is an example:
```java
@ConfigBean
public interface MyConfigBean {
    String getName();
    boolean isEnabled();
    int getPort();
}
```
* From this example we see that there is no reference to <b>how or where</b> the configuration beans come from. This is the logical API point of view for configuration. How and where these come from will be explained later in the [provider-facing SPI](#provider-facing-spi) section of this document.
* The configuration bean interface aggregates and encapsulates the complete set of attributes into beans that are needed and consumed by 0..N service(s) that are said to be "driven" or configured by that configuration bean. Here is an example where <i>MyConfiguredService</i> is configured by (aka driven by) <i>MyConfigBean</i>:
```java
@ConfiguredBy(MyConfigBean.class)
public class MyConfiguredService {
    private final MyConfigBean cfg;
    
    @Inject
    public MyConfiguredService(MyConfigBean cfg) {
        this.cfg = cfg;
    }
    
    public int getPort() {
        return cfg.getPort();
    }
}
```
* When we say that <i>MyConfiguredService</i> <b>is driven by</b> <i>MyConfigBean</i> we mean that a new instance of the service is spawned for each new instance of a bean that is found in "the configuration bean registry". The service's scope is therefore said to be dependent upon its configuration bean. The configuration registry is an SPI provider-facing construct and will be explained later. Configuration beans can only be injected via the constructor.
* Configuration beans must represent an aggregation of pure value objects with only getter methods, and can only use types that are restricted to what is deemed permissible and described later in this document. See the javadoc for <i>ConfigBean</i> for additional usages to indicate the policy that the annotation/API can restrict on its configuration at the provider level (e.g., @ConfigBean(repeatable=true, aliases={"my-config-bean-alt-name"}, pluralAliases="my-config-bean-alt-names", isDynamicInContent=true, isDynamicInLifecycle=true, requiredAttributes={"sister", "brother"), securitySensitiveAttributes="password", defaultConfigBean=<SingletonClassImplementingConfigBeanIface>). The usage of List and Map will be explained later. Here is another example that includes a richer set of attributes in order to illustrate what is canonically possible from the usage point of view:

```java
@ConfigBean
@Named("my-config")
public interface MyRichConfigBean extends BaseConfigBean, MyConfigBean {
    @Named("single")
    MyConfigBean getSingleConfigBean();
    MyRichConfigBean getSister();
    MyRichConfigBean getBrother();
    List<MyConfigBean> getListOfSomeOtherConfigBeans();
    Map<String, MyConfigBean> getMapOfSomeOtherConfigBeans();
    URI getUri();
    char[] getPassword();
    LocalDateTime getNextMaintenanceTime();
}
```
* There is a naming convention used to impart special meaning to certain attribute names. By extending your bean class definition to <i>... extends BaseConfigBean</i> you can access additional meta attributes that are available. Only extend from the <i>BaseConfigBean</i> it is necessary to access these additional attributes from your application code. These special attributes include the following: 
  * "_name" - String getName() - all beans either explicitly or else implicitly will have a name alias assigned to them. If the name is not explicitly specified by the backing provider then the name will be defaulted to its zero-based instance id, starting with zero and monotonically incremented by each config bean instance found in the config bean registry.
  * "_instanceId" - int getInstanceId() - this is the monotonically increasing instance id assigned. Note that there is no guarantee that the same instance id will be used for the same backing config between each process runtime. If consistency is required between runtime processes and over time then a name should be explicitly given to the config. More will be discussed on this in the provider section later. 
  * "_enabled" - boolean isEnabled() - all beans by default are considered enabled unless they are explicitly disabled using this attribute name. When a bean is disabled it will not drive any configured service to be created/activated.
  * "_hidden" - boolean isHidden() - default is false, but if set will hide the bean from the public bean registry and will avoid driving configured services for this bean. These hidden beans are mainly used for composing lists and maps of child configuration bean instances.
  * "get*AsMap"  Map<String, ?> - all attributes and child beans of this configuration bean represented in a java.util.Map.
```java
class BaseConfigBean {
    String getName();
    int getInstanceId();
    boolean isEnabled();
    boolean isHidden();
    // all "asMap" getters use the beans name (perhaps inferred/generated) as the key
    Map<String, ? extends TypedAttribute> getAttributesAsMap();
    Map<String, ? extends BaseConfigBean> getChildBeansAsMap(Class<T> optionalFilteredType);
    Map<String, Object> getAllAsMap();
}
```
* It is NOT possible to be a "normal" service that is injected with a config bean. Only services that are declared to be configured-by a config bean are injected with their config bean exclusively in its constructor - these only are eligible for receiving configuration beans in Pico. Any attempt to inject config beans in any other way or for any configuration will be detected at compile-time and will result in a build-time failure. If your application is interested in seeing/using/subscribing to configuration in some other way then look at the next few sections.
* <i>ConfigBean</i>s are code generated into <i>picoConfigBean</i> counterparts in order to avoid reflection at runtime. The Pico config annotation framework and annotation processor will handle the implementation details for this aspect. 

### Advanced Configuration API Usages
* In some cases your application may need to support dynamism in the way it uses configuration. An example of this is if your application must be reconfigured at runtime to support a new SSL certificate, etc. Dynamism is considered opt-in and comes in two styles allowing you to use one or the other or both. Every bean that is passed to your service is always 100% immutable, so you can count on it never changing. When dynamic changes occur (assuming they are declared to allow dynamic behavior) the result will be in a new bean that is created and then given to your service to process. The way this happens will be explained later. First, lets take a look at how dynamic configuration is supported:
  * Dynamic in content - If a bean is declared to be dynamic in content then the attributes for those values might change over time. When this occurs a new bean for the new updated configuration will be created and passed to your service if your service implements one of the <i>ConfigListener</i>, <i>ConfigParticipant</i>, or <i>ConfigAware</i> interfaces. Depending on which implementation is chosen, one can either listen to any config changes, specific bean type config changes, or even have the option to participate in prepare-apply-commit|rollback strategy to handle the new configuration bean change(s) in the system in a transaction-like manner. All of this is more complicated from the provider-side of this equation since the provider implementation needs to handle all kinds of failure handling scenarios, etc. But from the consumer API point of view it is intended to present a very clear and concise approach for handling configuration changes. Suffice it to say that if your service does not implement any of these interfaces then a warning will be logged and your service will just remain blissfully unaware of the configuration changes that might occur. 
  * Dynamic in lifecycle - If a bean is declared to be dynamic in lifecycle then new bean instances can come into existence post-startup/initialization, or can go away post-startup/initialization. When a new configuration bean comes into existence then it will spawn all services that are declared to be configured (aka driven) by that configuration bean type. When a configuration bean is removed from the backing provider config registry then the service that was configured by that bean instance will be destroyed and removed from the main Pico services registry. Note that once again the <i>ConfigListener</i> is used to inform all parties about pending and completed changes in the config bean registry, so they too can react appropriately depending upon how your application is written. The service itself can notice it is being destroyed by implementing the standard <i>PreDestroy</i> annotation/method. 
* A built-in <i>PicoSystemConfig</i> is provided as a singleton-type config bean that offers getters to other system-level attributes that might be of interest to your application, including:
  * "startupTime" - long getStartup() - this is the time (i.e., System.currentTimeMillis) that the pico configuration sub-system was initially started (which is typically early in startup when Pico is first bootstrapped).
  * "uniqueJvmId" - String getUniqueJvmId() - this is a unique (but not necessarily UUID level unique) id that represents this runtime JVM instance (e.g., metadata.uid from K8S).
```java
public interface PicoSystemConfig {
    long getStartupTime();
    String getUniqueJvmId();
    boolean isDynamicInContent();
    boolean isDynamicInLifecycle();
    default boolean isDynamic() {
        return isDynamicInContent() || isDynamicInLifecycle();
    }
}
```

### Important Notes on how Configured and Non-Configured services are realized in the Pico Service Registry
One principle that Pico strives to achieve is to help your application be as deterministic as possible from a runtime behavioral perspective, ideally determined (and to a limited extend validated) at compile-time (see application generation elsewhere in the documentation if this part seems unclear). Obviously this goal is not completely achievable for an application when configuration is involved and especially when that configuration supports dynamism of any manner since the configuration will affect your application's behavior and thereby make it less deterministic in nature post compilation. This is why there is no substitute for integration and functional testing while still applying your configuration(s) to those testing scenarios. Additionally, though, it is important to understand how "normal" (i.e., non-configured) services and configured services handle injection and lifecycle since there are differences that might appear to be a bit surprising.

This section describes some basic points from the API consumer point of view. The provider section later in this document will embellish upon this and describe in more detail how everything works under the hood.
* At Pico startup, the Pico configuration subsystem and (various) configuration providers are loaded (assuming the proper classes are on the class/module path; e.g. the pico-config module is used etc). At startup is when the <i>PicoSystemConfig</i> singleton is initialized. Remember that Pico startup is at the time <i>PicoServices.getPicoServices()</i> is first invoked. 
* During startup there is an initial round where configuration bean attributes are gathered from the available configuration providers. The <i>PicoSystemConfig</i> along with all the gathered configuration beans from those providers are then placed into Pico's config bean registry.
* Once all the config beans are gathered, then distinct <i>ServiceProvider</i> instances will be created for each config bean and then bound into the service registry in the INIT (pre-activated) state.
* Pico will continue to bootstrap, loading the <i>Application</i>(s) and/or <i>Module</i>(s) as described elsewhere in Pico. This process continues to populate Pico's service registry. 
* At the end of this phase of startup a Pico service registry is built with a combination of config-driven and other non-config-driven services.  Assuming the default configuration (see <i>PicoServicesConfig</i>) is used then that implies "dynamic" will be set to false, meaning that these services are what are used as the domain of services available for injection. Standard rules apply:
  * If there is an '@Inject List<Provider<?>>' being used then this list will (a) include configured and non-configured services that match the qualifiers/criteria for injection, (b) be ordered in <i>Weight</i> order, and (c) be immutable for the lifespan of the JVM process.
  * If there is a non-list '@Inject X' then X is selecting essentially what would have been the head of the list from the previous bullet point.
* Assuming dynamic changes are allowed from what has already been described above, post-startup phase all config changes will come in via the config interfaces. It is left up to your application how to deal with reconciling the injection points that were initially injected vs what comes in via the configuration interfaces as dynamic changes occur. Note, however, that <i>Services.lookup()</i> can still be used to get the "latest" (post-config-change) set of services that are in use, but generally it is suggested to minimize the use of the <i>spi</i> packages in your application code and therefore try to avoid its use whenever possible. The Pico service registry will be dynamically updated to reflect config changes that are happening at the provider level.

## Provider-facing SPI
Pico config providers are solely and collectively responsible for managing the <i>ConfigBean</i>s that form Pico's config bean registry --- BUT they manage it indirectly instead of directly. This will progressively become clear, but we will instead start with some basic key points: 

* The set of config providers are loaded at startup and is thereafter fixed and immutable. The standard ServiceLoader is used to load providers.
* The framework requires all configuration flows in via its set of config providers. No other agent can inject or render config beans into the system.
* The config providers are responsible for supplying configuration in a flattened "map form" (described later) when the provider is queried for its configuration. Querying for configuration in this manner typically occurs only during the Pico startup processing phase when the initial bean registry is being formed.
* If the system is configured to allow dynamism then these providers will also be given a callback they can subsequently use to notify of config (map) changes. Config providers are completely responsible for determining how config changes are monitored.
* Providers are not expected to know how the "map form" of its attributes form into <i>ConfigBean</i>, but loosely speaking will need to ensure that the schema dictated by the config annotations are in agreement with the data it feeds in.
* Providers can dictate the name for the config beans they supply, but they cannot dictate the instance id for the config bean instance. If the instance id must be controlled then the provider will need to force the name to be the desired instance id. The instance id is assigned by the framework and is subject to the ordering of how all config providers feed in config and is therefore not guaranteed to be the same from run to run.
* The attribute map fed to the provider must be all represented with values types as described below.
* Dots/periods (.) are used as delimiters in the underlying system and should not be used as part of the base key/name for an attribute. It is recommended to use dashes (-) instead. The dot is what is used to represent the "map form" that will be explained in the next section. Note that the semantics are similar to [hocon properties](https://github.com/lightbend/config/blob/main/HOCON.md#java-properties-mapping) but are not the same in Pico.
* Leading underscores (at 0 offset position, or following any dot in a dotted name) on config attribute keys have special meaning and should not be used in the base key/name for an attribute.
* The prefix for a config attribute value of "->" is used to represent a "by reference" attribute value. The value that comes after the "->" is the key/name for another named attribute. 
* It is highly recommended for providers to use lower-case attribute key/names. The framework is case-sensitive and will not force lower case for key/names.
* The flattened map of attributes can be fed by multiple providers feeding into a single config bean instance. In other words, the flattened map is an amalgamation of all config attributes fed from all providers. In cases where two providers conflict with a particular key-value pair, the resolution will be as follows:
  * The last one to set the value chronologically wins.
  * The initial set of attributes are ordered according to <i>Weight</i> of the config providers, where the highest weighted provider wins.

### Flattened "Map Form"
Providers are responsible for feeding either the full map of attributes they gather or, during post startup processing, a delta map representing the subset of attributes that are in transition. The javadoc for the SPI will provide the details on this. It is important to understand the canonical layout for the configuration map. Let's start with an example. Taking the "MyRichConfigBean" example from above - here is what the configuration map would need to look like in order to realize two config beans in the Pico config registry:

```
my-config.0.single = my-config.tom
my-config.0.brother = my-config._jerry
my-config.0.uri = http://helidon.io
my-config.0.password = password
my-config.tom.uri = http://localhost
my-config.tom.someRandomConfig = whatever
my-config._jerry.uri = http://127.0.0.1
```

The astute observer will notice that three beans are being referenced here instead of two. The bean registry exposes only the beans that are named without a leading underscore - this translates to the bean named "0" and the bean named "tom" being created in the bean registry. Also notice how the beans are sparsely populated. The framework will honor the policy as established on the <i>ConfigBean</i> descriptor, or via the configuration that was pre-established by the maven-plugin (described elsewhere). Since configuration assumes optional/nullable as the default we can sparsely populate our example here. Whenever a bean is used in config it needs to refer to it by name, and it needs to be referencing either an exposed or hidden bean. The bean, however, must be resolvable in the internal bean registry. Any bean name reference that is not resolvable will be rejected and logged as an error.

Now that we have an example, lets describe the canonical attribute key/name convention:
```
    <bean-type-name>.<bean-name>.["_" (iff an internal attribute key)]<attribute-name>[.<offset (iff list reference)>][.["_"]<attribute-name (iff bean reference)>]
```
Where:
* bean-type-name - the @Named value for the <i>ConfigBean</i> if present, or the simple class name for the bean type. That means either "my-config" can be used or else "MyRichConfigBean" can be used interchangeably in the example above.
* bean-name - the named instance of the bean; again if providers need to force this to be an ordinal then it can be named 0, 1, ... In the example above we have three names in use: {0, tom, _jerry}. The instance id will depend upon how many beans might have been created, destroyed, etc. In this example once should therefore NOT assume the instance id will be 0,1,2.  It might look like that at startup if only this one provider is used, but unlikely to be the case in other common scenarios.
* attribute-name = the @Named value of the getter method if present, or the bean method name. In this example the attribute "sister" or "singleConfigBean" would be acceptable.

Additionally, notice how the "tom" bean refers to "someRandomConfig". This attribute will be carried in, but only be accessible for callers using the get*AsMap() method.

It is also acceptable to avoid using underscore named beans, and instead use nesting. When this style is used the <bean-type-name>.<bean-name> is defaulted by the framework. Here is another way to express the same map of two bean values in the bean config registry that was shown in the above example, but now switching to use nesting "by value" instead of "by reference".

```
my-config.0.single.name = tom
my-config.0.single.uri = http://localhost
my-config.0.single.someRandomConfig = whatever
my-config.0.brother.uri = http://127.0.0.1
my-config.0.uri = http://helidon.io
my-config.0.password = password
```

The config attribute value follows this convention:
* Literal value (as shown in the above example), or
* Referenced value, as shown below. If a referenced bean or attribute does not exist, it will be set to null and a log message will be issued by the framework.
```
# bean level reference 
my-config.0.single = -> my-config.0
# attribute level reference 
my-config.0.single.uri = -> my-config.0.single.uri
```

## Providers & Configuration Types
The implementation provider for Pico's configuration is Helidon's config module (see [Helidon's config/config](../../config) for details).

## Q & A
* Q: Can there be multiple services configured by the same config bean?
* A: Yes, you can have as many services configured by the same config bean type.
---
* Q: Can there be more than one config bean injected into the constructor of a configured service?
* A: No, only one config bean can be injected into that service - the configuration bean that drove the configured service to become spawned. This service can, however, be listed as a configuration listener or query the bean registry to find out about other bean configurations that are active.
---
* Q: Can the constructor of a configured service accept other injections other than the configured bean interface?
* A: Yes, any other "normal" contract injections can also occur using other constructor parameters.
---
* Q: Can the system support multiple configurations using the same name?
* A: No, for the same config bean type. If the config bean types are different for each then each can use the bean name uniquely once for that type.
---
* Q: Are encrypted or encoded attributes supported?
* A: Conditionally based upon the configuration providers in use.
