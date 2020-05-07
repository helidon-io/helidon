
# Changelog

All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

For Helidon 1.x releases please see [Helidon 1.x CHANGELOG.md](https://github.com/oracle/helidon/blob/helidon-1.x/CHANGELOG.md)

## [Unreleased] 

### Notes

This is the third milestone release of Helidon 2.0. 

### Improvements

### Fixes

### Backward incompatible changes

#### Removal of processor-like operators
The formerly public `Flow.Processor` implementations performing common operations have been removed. 
Users should use the respective operators from `Single` and `Multi` instead:

```java
// before
Flow.Publisher<Integer> source = ...
MappingProcessor<Integer, String> mapper = new MappingProcessor<>(Integer::toString);
source.subscribe(mapper);
mapper.subscribe(subscriber);

// after
Flow.Publisher<Integer> source = ...
Multi.from(source)
     .map(Integer::toString)
     .subscribe(subscriber)
```

#### Removal of Flows
The class was providing basic `Flow.Publisher` implementations. Users should pick one of the static methods of 
`Single` or `Multi` instead, which provide the additional benefits of having fluent operators available to them for 
assembling reactive flows conveniently:
```java
// before
Flow.Publisher<Integer> just = Flows.singletonPublisher(1);
Flow.Publisher<Object> empty = Flows.emptyPublisher();

// after
Multi<Integer> just1 = Multi.singleton(1);
Single<Integer> just2 = Single.just(1);

Multi<Object> empty1 = Multi.empty();
Single<Object> empty2 = Single.empty();
```

#### MediaSupport refactored
The `MediaSupport` class has been used as holder object of media operator contexts. Now, the name has changed to `MediaContext`, 
and`MediaSupport` will be the name given to the interface which defines media support for given type (readers, writers etc.)  
The Classes `JsonProcessing`, `JsonBinding` and `Jackson` are now renamed to `JsonpSupport`, `JsonbSupport` and `JacksonSupport` 
and are implementing the `MediaSupport` interface.

```java
//before
JsonProcessing jsonProcessing = new JsonProcessing();
MediaSupport mediaSupport = MediaSupport.builder()
    .registerReader(jsonProcessing.newReader())
    .registerWriter(jsonProcessing.newWriter())
    .build();

WebServer.builder()
    .mediaSupport(mediaSupport)
    .build();

//after
WebServer.builder()
    .addMediaSupport(JsonpSupport.create()) //registers reader and writer for Json-P
    .build()
```

## [2.0.0-M2] 

### Notes

This is the second milestone release of Helidon 2.0. It contains significant new features,
enhancements and fixes. It also contains backward incompatible changes (see section below).
This milestone release is provided for customers that want early access to Helidon 2.0. It
should be considered unstable and is not intended for production use. APIs and features might
not be fully tested and are subject to change. Documentation is incomplete. Those looking for
a stable release should use a 1.4 release.

### Notable New Features

* Helidon Web Client
* MicroProfile Reactive Streams Operators
* MicroProfile Reactive Messaging
* Multi and Single extended with the set of new reactive operators
* Preliminary WebSocket support
* Preliminary jlink image support

### Changes

- Config: Configuration changes [1357](https://github.com/oracle/helidon/pull/1357)
- Config: Default config now loads without duplicates for MP [1369](https://github.com/oracle/helidon/pull/1369)
- Config: Fix merging of value nodes in config 2.0 [1488](https://github.com/oracle/helidon/pull/1488)
- Config: Removed null params and return types from Config API. [1345](https://github.com/oracle/helidon/pull/1345)
- Config: Resource from config refactoring. [1530](https://github.com/oracle/helidon/pull/1530)
- Config: Stopped executor service will not cause an error in polling strategy. [1484](https://github.com/oracle/helidon/pull/1484)
- Config: cache is not using SoftReference anymore. [1494](https://github.com/oracle/helidon/pull/1494)
- Config: change support refactoring [1417](https://github.com/oracle/helidon/pull/1417)
- DB Client: Mappers for database date/time/timestamps. [1485](https://github.com/oracle/helidon/pull/1485)
- Docker Image: Added libstdc++-6-dev to the image [1414](https://github.com/oracle/helidon/pull/1414)
- Examples: Remove MP quickstart GreetApplication#getClasses method for auto-discovery [1395](https://github.com/oracle/helidon/pull/1395)
- Examples: Gradle file cleanup. Fix deprecations. [1354](https://github.com/oracle/helidon/pull/1354)
- Examples: Add OpenAPI annotations to MP quickstart for input and responses on updateGreeting [1394](https://github.com/oracle/helidon/pull/1394)
- JAX-RS: Safeguard against JAX-RS app modifications after start. [1486](https://github.com/oracle/helidon/pull/1486)
- JAX-RS: Upgrade Jersey [1438](https://github.com/oracle/helidon/pull/1438)
- JPA: Resolves JTA/JPA transaction synchronization issues [1473](https://github.com/oracle/helidon/pull/1473)
- JPA: Ensures that JtaDataSource instances that are acquired when a transaction is already active have their Synchronizations registered appropriately [1497](https://github.com/oracle/helidon/pull/1497)
- JPA: Added further tests in TestJpaTransactionScopedSynchronizedEntityManager.java [1453](https://github.com/oracle/helidon/pull/1453)
- JPA: Added more JPA tests [1355](https://github.com/oracle/helidon/pull/1355)
- JPA: Added small test to verify database changes in existing JPA test [1471](https://github.com/oracle/helidon/pull/1471)
- Javadoc: Java 11 javadoc fixes. Turn on failOnError [1386](https://github.com/oracle/helidon/pull/1386)
- Media Support: New media support API. [1356](https://github.com/oracle/helidon/pull/1356)
- Media Support: Adds Config into MediaSupport#builder() method [1403](https://github.com/oracle/helidon/pull/1403)
- Messaging: MP Reactive Messaging impl [1287](https://github.com/oracle/helidon/pull/1287)
- Messaging: Fix completable queue and clean-up of messaging tests [1499](https://github.com/oracle/helidon/pull/1499)
- Metrics: Prometheus format problems (master) [1440](https://github.com/oracle/helidon/pull/1440)
- MicroProfile Server now properly fails when CDI is started externally. [1371](https://github.com/oracle/helidon/pull/1371)
- Native Image: JPA and JTA for native image [1478](https://github.com/oracle/helidon/pull/1478)
- OpenAPI: Openapi custom context root 2.x [1521](https://github.com/oracle/helidon/pull/1521)
- OpenAPI: Remove dependency on Jackson via SmallRye -2.x [1458](https://github.com/oracle/helidon/pull/1458)
- OpenAPI: Support multiple apps in OpenAPI document [1493](https://github.com/oracle/helidon/pull/1493)
- OpenAPI: Use CONFIG rather than FINE logging for jandex indexes used - 2.x [1405](https://github.com/oracle/helidon/pull/1405)
- OpenAPI: Use smallrye openapi 1.2.0 (in 2.x) [1422](https://github.com/oracle/helidon/pull/1422)
- Reactive: Add Helidon-Reactive Scrabble benchmark [1482](https://github.com/oracle/helidon/pull/1482)
- Reactive: Add JMH Benchmark baseline to reactive [1462](https://github.com/oracle/helidon/pull/1462)
- Reactive: Add Multi.range and Multi.rangeLong + TCK tests [1475](https://github.com/oracle/helidon/pull/1475)
- Reactive: Expand TestSubscriber's API, fix a bug in MultiFirstProcessor [1463](https://github.com/oracle/helidon/pull/1463)
- Reactive: Fix expected exception [1472](https://github.com/oracle/helidon/pull/1472)
- Reactive: Fix reactive mapper publisher tck test [1447](https://github.com/oracle/helidon/pull/1447)
- Reactive: Implement Multi.interval() + TCK tests [1526](https://github.com/oracle/helidon/pull/1526)
- Reactive: Implement Single.flatMapIterable + TCK tests [1517](https://github.com/oracle/helidon/pull/1517)
- Reactive: Implement defaultIfEmpty() + TCK tests [1520](https://github.com/oracle/helidon/pull/1520)
- Reactive: Implement defer() + TCK tests [1503](https://github.com/oracle/helidon/pull/1503)
- Reactive: Implement from(Stream) + TCK tests [1525](https://github.com/oracle/helidon/pull/1525)
- Reactive: Implement reduce() + TCK tests [1504](https://github.com/oracle/helidon/pull/1504)
- Reactive: Implement switchIfEmpty + TCK tests [1527](https://github.com/oracle/helidon/pull/1527)
- Reactive: Implement takeUntil + TCK tests [1502](https://github.com/oracle/helidon/pull/1502)
- Reactive: Implement timer() + TCK tests [1516](https://github.com/oracle/helidon/pull/1516)
- Reactive: Improve SingleJust + TCK [1410](https://github.com/oracle/helidon/pull/1410)
- Reactive: Move onXXX from Subscribable to Single + TCK [1477](https://github.com/oracle/helidon/pull/1477)
- Reactive: Reactive Streams impl [1282](https://github.com/oracle/helidon/pull/1282)
- Reactive: Reimplement ConcatPublisher + TCK tests [1452](https://github.com/oracle/helidon/pull/1452)
- Reactive: Reimplement Multi.dropWhile + TCK test [1464](https://github.com/oracle/helidon/pull/1464)
- Reactive: Reimplement Multi.first + TCK test [1466](https://github.com/oracle/helidon/pull/1466)
- Reactive: Reimplement Multi.flatMapIterable + TCK test [1467](https://github.com/oracle/helidon/pull/1467)
- Reactive: Reimplement Multi.just(T[]), add Multi.singleton(T) + TCK [1461](https://github.com/oracle/helidon/pull/1461)
- Reactive: Reimplement Single.flatMap(->Publisher) + TCK test [1465](https://github.com/oracle/helidon/pull/1465)
- Reactive: Reimplement Single.from + TCK tests [1481](https://github.com/oracle/helidon/pull/1481)
- Reactive: Reimplement Single.map + TCK test [1456](https://github.com/oracle/helidon/pull/1456)
- Reactive: Reimplement many operators + TCK tests [1442](https://github.com/oracle/helidon/pull/1442)
- Reactive: Reimplement onErrorResume[With] + TCK tests [1489](https://github.com/oracle/helidon/pull/1489)
- Reactive: Reimplement the Multi.map() operator + TCK test [1411](https://github.com/oracle/helidon/pull/1411)
- Reactive: Rewrite collect, add juc.Collector overload + TCK tests [1459](https://github.com/oracle/helidon/pull/1459)
- Security: public fields for IdcsMtRoleMapperProvider.MtCacheKey [1409](https://github.com/oracle/helidon/pull/1409)
- Security: Fail fast when policy validation fails because of setup/syntax. [1491](https://github.com/oracle/helidon/pull/1491)
- Security: PermitAll overridden by JWT [1359](https://github.com/oracle/helidon/pull/1359)
- WebClient: Webclient implementation (#1205) [1431](https://github.com/oracle/helidon/pull/1431)
- WebServer: Adds a default send(Throwable) method to ServerResponse.java as the first step in providing an easy facility for reporting exceptions during HTTP processing [1378](https://github.com/oracle/helidon/pull/1378)
- WebServer: SetCookie test for parse method [1529](https://github.com/oracle/helidon/pull/1529)
- WebSocket: Integration of WebSockets POC into Helidon 2.0 [1280](https://github.com/oracle/helidon/pull/1280)
- jlink: jlink-image support. [1398](https://github.com/oracle/helidon/pull/1398)
- jlink: Update standalone quickstarts to support jlink-image. [1424](https://github.com/oracle/helidon/pull/1424)

#### Thank You!

Thanks to community member David Karnok [akarnokd](https://github.com/akarnokd) for his
significant contributions to our reactive support.

### Backward incompatible changes

#### Resource class when loaded from Config
The configuration approach to `Resource` class was using prefixes which was not aligned with our approach to configuration.
All usages were refactored as follows:

1. The `Resource` class expects a config node `resource` that will be used to read it
2. The feature set remains unchanged - we support path, classpath, url, content as plain text, and content as base64
3. Classes using resources are changed as well, such as `KeyConfig` - see details below

##### OidcConfig
Configuration has been updated to use the new `Resource` approach:

1. `oidc-metadata.resource` is the new key for loading `oidc-metadata` from local resource
2. `sign-jwk.resource` is the new key for loading signing JWK resource

##### JwtProvider and JwtAuthProvider
Configuration has been updated to use the new `Resource` approach:

1. `jwk.resource` is the new key for loading JWK for verifying signatures
2. `jwt.resource` is also used for outbound as key for loading JWK for signing tokens

##### GrpcTlsDescriptor
Configuration has been updated to use the new `Resource` approach:

1. `tls-cert.resource` is the new key for certificate
2. `tls-key.resource` is the new key for private key
3. `tl-ca-cert` is the the new key for certificate 

##### KeyConfig

The configuration has been updated to have a nicer tree structure:

Example of a public key from keystore:
```yaml
ssl:
  keystore:
    cert.alias: "service_cert"
    resource.path: "src/test/resources/keystore/keystore.p12"
    type: "PKCS12"
    passphrase: "password"
```

Example of a private key from keystore:
```yaml
ssl:
  keystore:
    key:
      alias: "myPrivateKey"
      passphrase: "password"
    resource.path: "src/test/resources/keystore/keystore.p12"
    type: "PKCS12"
    passphrase: "password"
```

Example of a pem resource with private key and certificate chain:
```yaml
ssl:
  pem:
    key:
      passphrase: "password"
      resource.resource-path: "keystore/id_rsa.p8"
    cert-chain:
      resource.resource-path: "keystore/public_key_cert.pem"
```

## [2.0.0-M1] 

### Notes

This is the first milestone release of Helidon 2.0. It contains significant new
features, enhancements and fixes. It also contains backward incompatible changes
(see [section](#backward-incompatible-changes) below ). This milestone release
is provided for customers that want early access to Helidon 2.0. It should be
considered unstable and is not intended for production use. APIs and features
might not be fully tested and are subject to change. Documentation is incomplete.
Those looking for a stable release should use a 1.4 release.

Notable changes:

- New Helidon DB Client
- Preliminary GraalVM native-image support for Helidon MP (in addition
  to existing support for Helidon SE)
- Improved discovery and handling of JAX-RS applications
- Dropped Java 8 support and moved to Java 11 APIs

### Improvements

- Helidon DB Client [657](https://github.com/oracle/helidon/pull/657) [1329](https://github.com/oracle/helidon/pull/1329)
- Native image: Helidon MP support [1328](https://github.com/oracle/helidon/pull/1328) [1295](https://github.com/oracle/helidon/pull/1295) [1259](https://github.com/oracle/helidon/pull/1259)
- Config: Helidon Config now implements MicroProfile config, so you can cast between these two types
- Security: Basic auth and OIDC in MP native image [1330](https://github.com/oracle/helidon/pull/1330)
- Security: JWT and OIDC security providers now support groups claim. [1324](https://github.com/oracle/helidon/pull/1324)
- Support for Helidon Features [1240](https://github.com/oracle/helidon/pull/1240)
- Java 11: [1232](https://github.com/oracle/helidon/pull/1232) [1187](https://github.com/oracle/helidon/pull/1187) [1222](https://github.com/oracle/helidon/pull/1222)

### Fixes

- JAX-RS: Better recovery for invalid URLs and content types [1246](https://github.com/oracle/helidon/pull/1246)
- JAX-RS: Fix synthetic app creation + functional test. [1323](https://github.com/oracle/helidon/pull/1323)
- Config: replace reflection with service loader [1102](https://github.com/oracle/helidon/pull/1102)
- Config: now uses common media type module instead of FileTypeDetector. [1332](https://github.com/oracle/helidon/pull/1332)
- JPA: Permit non-XA DataSources to take part in JTA transactions [1316](https://github.com/oracle/helidon/pull/1316)
- JPA: Fixes an issue with multiple persistence units [1342](https://github.com/oracle/helidon/pull/1342)
- JPA: Address several rollback-related issues [1146](https://github.com/oracle/helidon/pull/1146)
- JPA: Relax requirements on container-managed persistence units which can now be resource-local [1243](https://github.com/oracle/helidon/pull/1243)
- OpenAPI: Support multiple jandex files on the classpath [1320](https://github.com/oracle/helidon/pull/1320)
- Security: Fixed issues with IDCS and OIDC provider. [1313](https://github.com/oracle/helidon/pull/1313)
- Security: Removed support for entity modification from security. [1263](https://github.com/oracle/helidon/pull/1263)
- Security: Allow usage of exceptions for security in Jersey. [1227](https://github.com/oracle/helidon/pull/1227)
- Security: Outbound with OIDC provider no longer causes an UnsupportedOperationE… [1226](https://github.com/oracle/helidon/pull/1226)
- gRPC: Minor changes [1299](https://github.com/oracle/helidon/pull/1299)
- Quickstart: Fix application jar class-path for SNAPSHOT versioned dependencies [1297](https://github.com/oracle/helidon/pull/1297)
- Use Helidon service loader. [1334](https://github.com/oracle/helidon/pull/1334)
- Fix optional files in bare archetypes. (#1250) [1321](https://github.com/oracle/helidon/pull/1321)
- Make Multi streams compatible with Reactive Streams [1260](https://github.com/oracle/helidon/pull/1260)
- Remove Valve [1279](https://github.com/oracle/helidon/pull/1279)
- IDE support: Missing requires of JAX-RS API [1271](https://github.com/oracle/helidon/pull/1271)
- Metrics: fix unable to find reusable metric with tags [1244](https://github.com/oracle/helidon/pull/1244)
- Support for lazy values and changed common modules to use it. [1228](https://github.com/oracle/helidon/pull/1228)
- Upgrade jacoco to version 0.8.5 to avoid jdk11 issue [1281](https://github.com/oracle/helidon/pull/1281)
- Upgrade tracing libraries [1230](https://github.com/oracle/helidon/pull/1230)
- Upgrade config libraries  [1159](https://github.com/oracle/helidon/pull/1159)
- Upgrade Netty to 4.1.45 [1309](https://github.com/oracle/helidon/pull/1309)
- Upgrade Google libraries for Google login provider. [1229](https://github.com/oracle/helidon/pull/1229)
- Upgrade H2, HikariCP, Jedis, OCI SDK versions [1198](https://github.com/oracle/helidon/pull/1198)
- Upgrade to FT 2.0.2 and Failsafe 2.2.3 [1204](https://github.com/oracle/helidon/pull/1204)


### Backward incompatible changes

In order to stay current with dependencies, and also refine our APIs we have 
introduced some backward incompatible changes in this release. Most of the changes
are mechanical in nature: changing package names, changing GAV coordinates, etc.
Here are the details:

#### Java Runtime

- Java 8 support has been dropped. Java 11 or newer is now required.

#### Common
- Removed `io.helidon.reactive.Flow`, please use `java.util.concurrent.Flow`
- Removed `io.helidon.common.CollectionsHelper`, please use factory methods of `Set`, `Map` and `List`
- Removed `io.helidon.common.OptionalHelper`, please use methods of `java.util.Optional`
- Removed `io.helidon.common.StackWalker`, please use `java.lang.StackWalker`
- Removed `io.helidon.common.InputStreamHelper`, please use `java.io.InputStream` methods
- Removed dependency on Project Reactor

#### Tracing
- We have upgraded to OpenTracing version 0.33.0 that is not backward compatible, the following breaking changes exist
    (these are OpenTracing changes, not Helidon changes):
    1. `TextMapExtractAdapter` and `TextMapInjectAdapter` are now `TextMapAdapter`
    2. module name changed from `opentracing.api` to `io.opentracing.api` (same for `noop` and `util`)
    3. `SpanBuilder` no longer has `startActive` method, you need to use `Tracer.activateSpan(Span)`
    4. `ScopeManager.activate(Span, boolean)` is replaced by `ScopeManager.activate(Span)` - second parameter is now always 
            `false`
    5. `Scope ScopeManager.active()` is removed - replaced by `Span Tracer.activeSpan()`
- If you use the `TracerBuilder` abstraction in Helidon and have no custom Spans, there is no change required    

#### Config

##### Helidon MP
When using MP Config through the API, there are no backward incompatible changes in Helidon.

##### Helidon SE Config Usage

The following changes are relevant when using Helidon Config:

1. File watching is now done through a `ChangeWatcher` - use of `PollingStrategies.watch()` needs to be refactored to
    `FileSystemWatcher.create()` and the method to configure it on config source builder has changed to 
    `changeWatcher(ChangeWatcher)`
2. Methods on `ConfigSources` now return specific builders (they use to return `AbstractParsableConfigSource.Builder` with
    a complex type declaration). If you store such a builder in a variable, either change it to the correct type, or use `var`
3. Some APIs were cleaned up to be aligned with the development guidelines of Helidon. When using Git config source, or etcd
    config source, the factory methods moved to the config source itself, and the builder now accepts all configuration
    options through methods
4. The API of config source builders has been cleaned, so now only methods that are relevant to a specific config source type
    can be invoked on such a builder. Previously you could configure a polling strategy on a source that did not support 
    polling
5. There is a small change in behavior of Helidon Config vs. MicroProfile config: 
    The MP TCK require that system properties are fully mutable (e.g. as soon as the property is changed, it
    must be used), so MP Config methods work in this manner (with a certain performance overhead).
    Helidon Config treats System properties as a mutable config source, with a (optional) time based polling strategy. So
    the change is reflected as well, though not immediately (this is only relevant if you use change notifications). 
6. `CompositeConfigSource` has been removed from `Config`. If you need to configure `MerginStrategy`, you can do it now on 
    `Config` `Builder`

##### Helidon SE Config Extensibility

1. Meta configuration has been refactored to be done through `ServiceLoader` services. If you created
a custom `ConfigSource`, `PollingStrategy` or `RetryPolicy`, please have a look at the new documentation.
2. To implement a custom config source, you need to choose appropriate (new) interface(s) to implement. This is the choice:
    From "how we obtain the source of data" point of view:
    * `ParsableSource` - for sources that provide bytes (used to be reader, now `InputStream`)
    * `NodeConfigSource` - for sources that provide a tree structure directly
    * `LazyConfigSource` - for sources that cannot read the full config tree in advance
    From mutability point of view (immutable config sources can ignore this):
    * `PollableSource` - a config source that is capable of identifying a change based on a data "stamp"
    * `WatchableSource` - a config source using a target that can be watched for changes without polling (such as `Path`)
    * `EventConfigSource` - a config source that can trigger change events on its own 
3. `AbstractConfigSource` and `AbstractConfigSourceBuilder` are now in package `io.helidon.config`
4. `ConfigContext` no longer contains method to obtain a `ConfigParser`, as this is no longer responsibility of 
    a config source
5.  Do not throw an exception when config source does not exist, just return
    an empty `Optional` from `load` method, or `false` from `exists()` method
6.  Overall change support is handled by the config module and is no longer the responsibility
    of the config source, just implement appropriate SPI methods if changes are supported,
    such as `PollableSource.isModified(Object stamp)`
 

#### Metrics
Helidon now supports only MicroProfile Metrics 2.x. Modules for Metrics 1.x were removed, and 
modules for 2.x were renamed from `metrics2` to `metrics`.

#### Security

- When OIDC provider is configured to use cookie (default configuration) to carry authentication information,
    the cookie `Same-Site` is now set to `Lax` (used to be `Strict`). This is to prevent infinite redirects, as 
    browsers would refuse to set the cookie on redirected requests (due to this setting).
    Only in case the frontend host and identity host match, we leave `Strict` as the default


#### Microprofile Bundles
We have removed all versioned MP bundles (i.e. `helidon-microprofile-x.x`, and introduced
unversioned core and full bundles:

- `io.helidon.microprofile.bundles:helidon-microprofile-core` - contains only MP Server
   and Config. Allows you to add only the specifications needed by your application.
- `io.helidon.microprofile.bundles:helidon-microprofile` - contains the latest full 
   MicroProfile version implemented by Helidon
   
You can find more information in this blog post:
[New Maven bundles](https://medium.com/helidon/microprofile-3-2-and-helidon-mp-1-4-new-maven-bundles-a9f2bdc1b5eb)

#### Helidon CDI and MicroProfile Server

- You cannot start CDI container yourself (this change is required so we can 
      support GraalVM `native-image`)
    - You can only run a single instance of Server in a JVM
    - If you use `SeContainerInitializer` you would get an exception
        - this can be worked around by configuration property `mp.initializer.allow=true`, and warning can be removed
            using `mp.initializer.no-warn=true`
        - once `SeContainerInitializer` is used, you can no longer use MP with `native-image` 
- You can no longer provide a `Context` instance, root context is now built-in
- `MpService` and `MpServiceContext` have been removed
    - methods from context have been moved to `JaxRsCdiExtension` and `ServerCdiExtension` that can be accessed
        from CDI extension through `BeanManager.getExtension`.
    - methods `register` can be used on current `io.helidon.context.Context`
    - `MpService` equivalent is a CDI extension. All Helidon services were refactored to CDI extension 
        (you can use these for reference)
- `Server.cdiContainer` is removed, use `CDI.current()` instead

#### Startup
New recommended option to start Helidon MP:
1. Use class `io.helidon.microprofile.cdi.Main`
2. Use meta configuration option when advanced configuration of config is required (e.g. `meta-config.yaml`)
3. Put `logging.properties` on the classpath or in the current directory to be automatically picked up to configure 
    Java util logging
    
`io.helidon.microprofile.server.Main` is still available, just calls `io.helidon.microprofile.cdi.Main` and is deprecated.
`io.helidon.microprofile.server.Server` is still available, though the features are much reduced


#### MicroProfile JWT-Auth
If a JAX-RS application exists that is annotated with `@LoginConfig` with value `MP-JWT`, the correct authentication
provider is added to security.
The startup would fail if the provider is required yet not configured.

#### Security in MP
If there is no authentication provider configured, authentication will always fail.
If there is no authorization provider configured, ABAC provider will be configured.
(original behavior - these were configured if there was no provider configured overall) 

### Other Behavior changes

- JAX-RS applications now work similar to how they work in application servers
    - if there is an `Application` subclass that returns anything from
      `getClasses` or `getSingletons`, it is used as is
    - if there is an `Application` subclass that returns empty sets from these methods,
      all available resource classes will be part of such an application
    - if there is no `Application` subclass, a synthetic application will be
      created with all available resource classes
    - `Application` subclasses MUST be annotated with `@ApplicationScoped`,
      otherwise they are ignored


[Unreleased]: https://github.com/oracle/helidon/compare/2.0.0-M2...HEAD
[2.0.0-M2]: https://github.com/oracle/helidon/compare/2.0.0-M1...2.0.0-M2
[2.0.0-M1]: https://github.com/oracle/helidon/compare/1.4.0...2.0.0-M1
