# Helidon Config API design changes proposal

## Summary

Summary table:

| Area              | Impact on API | Impact on behavior | Impact on SPI | Urgency |
| ----------------- | ------------- | ------------------ | ------------- | ------- |
| Too many methods  | High          | Low or None        | Low           | High    |
| Source types      | Low or None   | High               | Medium        | Medium  |
| Change support    | Medium        | Medium             | High          | High    |
| Polling strategies| Medium        | Low                | Medium        | Low     |
| Debugging         | None          | None               | None          | Medium  |
| FileDetector SPI  | None          | None               | None          | High    |
| Java Beans        | None          | Low                | None          | High    |
| No reflection as()| None          | High               | None          | High    |
| Remove ConfigMapper| Medium       | None               | Medium        | High    |

### Too Many Methods
Too many methods are part of public API - this makes it very complicated to test, maintain and (sometimes) use

This is caused by:
1. each primitive type has its own method + methods for any type and methods for map, list and nodes 
    1. boolean
    2. int
    3. long
    4. Double
    5. String
    5. Map
    5. List
    5. Config
    5. Class<?>
1. supporting too many paradigms 
    1. returning T, Optional<T>, Supplier<T>, Supplier<Optional<T>>
    1. for each of these methods (except for Optional) supporting a method without and with a default value
    1. this means that we have 6 methods for each type (using boolean as an example):
        1. asBoolean()
        1. asBoolean(boolean default)
        1. asBooleanSupplier()
        1. asBooleanSupplier(boolean default)
        1. asOptionalBoolean()
        1. asOptionalBooleanSupplier()
        
#### Proposal
Create a typed config value `Config.Value<T>` (requires the @Value to be moved as part of "Java Beans") that 
would be returned by typed methods. 
This would leave config with the following accessor methods:
1. Required: 
    1. `Value<T> as(Class<? extends T> type) throws ConfigMappingException`
    2. `Value<List<T>> asList(Class<? extends T> type) throws ConfigMappingException`
    3. `Value<Map<String, String>> asMap()`
2. Optional (shortcut):
    1. `T as(Class<? extends T> type, T defaultValue) throws ConfigMappingException`
    2. `Value<Config> asNode()`
    3. `Value<List<Config>> asNodeList()`
    4. `Value<Boolean> asBoolean() throws ConfigMappingException`
    5. other shortcut methods for primitive types
    
The `Value` interface would have the following methods to access typed value (as supported in original API):
1. `Optional<T> value()`
2. `T get() throws MissingValueException`
2. `T get(T defaultValue)`
3. `Supplier<T> asSupplier()`
4. `Supplier<T> asSupplier(T defaultValue)`
5. `Supplier<Optional<T>> asOptionalSupplier()`
6. and a shortcut method `void ifPresent(Consumer<? super T> consumer)`

Example:
```java
// unchanged for String, as Config implements Value<String>
config.get("client-id").value().ifPresent(this::clientId);

// current for primitive type
config.get("proxy-port").asOptionalInt().ifPresent(this::proxyPort);
// new for primitive type
config.get("proxy-port").as(Integer.class).ifPresent(this::proxyPort);

// current for type with a mapper
config.get("identity-uri").asOptional(URI.class).ifPresent(this::identityUri);
// new for type with a mapper
config.get("identity-uri").as(URI.class).ifPresent(this::identityUri);

// current for type with a factory method
config.get("oidc-config").asOptional(OidcConfig.class).ifPresent(this::oidcConfig);
// new for type with a factory method (part of "No reflection as()" problem
config.get("oidc-config").as(OidcConfig::create).ifPresent(this::oidcConfig);

// current using response value
int port = config.get("proxy-port").asInt(7001);
// new using response value (if we decide to have shortcuts for primitives)
int port = config.get("proxy-prot").asInt().get(7001);
// new otherwise
int port = config.get("proxy-port").as(Integer.class).get(7001);

```
    
### Source types

#### Lazy config sources
We do not support config sources that require lazy access to values (using term "lazy sources" in the text)

_This may be sources that cannot list the keys, or where listing the keys is not feasible (speed, memory consumption etc.)_

To support such source types, we need to change approach to our configuration tree:
1. Each node created from known keys has to keep reference to all sources
2. Whenever a node value is requested, lazy sources must be queried for value (according to priority)
3. If a value is provided, it is cached forever

Other changes:
1. Notifications for changes can be provided only for cached keys
2. Config Source SPI will have to be updated, so a source can mark itself as lazy
3. Methods that traverse the tree (asMap, traverse, nodeList etc.) would only return
    the known key
    1. We must refactor our own usages of config to use direct key access wherever possible
    2. We should look into integrations to see if we can support lazy loading of configuration properties
        1. Jersey
        2. Weld
4. Behavior must be clearly documented

#### Mutable sources with no notification support
Some of our config source are mutable, yet do not support notifications.
We should change all config sources to support notifications if so chosen by the user.
If need be, these should be polled regularly and compared with previous version. 

Currently known sources that should be refactored:
1. System properties 

### Change support
Current change support is too complex and uses APIs not suitable for the purpose:
1. Flow API should not be used in Config API
2. method "onChange" should be "onChange(Consumer<Config>)" - current expects a function with undefined behavior for returned 
    boolean
3. Remove dependency on SubmissionPublisher (and on project Reactor transitively)

### Polling strategies
1. Check if these can be simplified, as current API and SPI is not easy to use.
2. Make sure one thing can be achieved only one way - e.g why do we have
    polling and watching both available for File config sources?
    
### Debugging
Provide better support for debugging:
1. Keep information about a config source that provided a value of a node
2. This may be accessible only in a debugger (e.g. no need to change API or SPI)

### File Detector SPI
Currently the FileDetector service does not work consistently in all environments.
Known problems:
1. when running JDK9+ using maven exec plugin (test with yaml config)
2. when running in some docker images (need to find the failing image)

### Java Beans
Separate java beans support into a different module (including annotations @Value and @Transient).
The current support can build instances from config using reflection. This is complicated
part of the code that should not be part of SE Config by default.
Add SPI to allow for such (more complex) config.as(AClass.class) transformation

### No reflection as()
Do not use reflection in T Config.as(Class<T> type) method. Currently there is a complicated
code that introspects the class to find suitable constructor or factory method.
Create a new method `T as(Function<Config, T> factoryMethod)`.

We can then use:
```java
config.as(OidcConfig::create);
config.as(SomeClass::new);
```

### Remove ConfigMapper
Remove ConfigMapper interface, as it is in fact a Function<Config, T>.