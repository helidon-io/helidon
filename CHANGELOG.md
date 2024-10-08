
# Changelog

All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

For Helidon 3.x releases please see [Helidon 3.x CHANGELOG.md](https://github.com/oracle/helidon/blob/helidon-3.x/CHANGELOG.md)

For Helidon 2.x releases please see [Helidon 2.x CHANGELOG.md](https://github.com/oracle/helidon/blob/helidon-2.x/CHANGELOG.md)

For Helidon 1.x releases please see [Helidon 1.x CHANGELOG.md](https://github.com/oracle/helidon/blob/helidon-1.x/CHANGELOG.md)

## [4.1.2]

This release contains important bugfixes and enhancements and is recommended for all users of Helidon 4. 

A minimum of Java 21 is required to use Helidon 4.

### CHANGES

- gRPC: Adds support to iterate over URIs when connecting to a gRPC service [9300](https://github.com/helidon-io/helidon/pull/9300)
- LRA: LRA testing feature [9320](https://github.com/helidon-io/helidon/pull/9320)
- Logging: JSON Formatter for JUL  [9301](https://github.com/helidon-io/helidon/pull/9301)
- Security: Policy validator configurable per endpoint in config (#9248) [9308](https://github.com/helidon-io/helidon/pull/9308)
- WebServer: Allows the webserver's write buffer size to be set to 0  [9314](https://github.com/helidon-io/helidon/pull/9314)
- WebServer: Fix DataReader.findNewLine with lone EOL character [9327](https://github.com/helidon-io/helidon/pull/9327)
- WebServer: Grouping Executors related methods into a single class [9298](https://github.com/helidon-io/helidon/pull/9298)
- WebServer: New implementation for SSE in webserver [9297](https://github.com/helidon-io/helidon/pull/9297)
- WebServer: Smart async writer in webserver [9292](https://github.com/helidon-io/helidon/pull/9292)
- Dependencies: Upgrade Jersey to 3.1.8  [9303](https://github.com/helidon-io/helidon/pull/9303)
- Dependencies: Upgrades protobuf to 3.25.5 [9299](https://github.com/helidon-io/helidon/pull/9299)
- Dependencies: Uptake build-tools 4.0.12 (fixes [9305](https://github.com/helidon-io/helidon/issues/9305)) [9323](https://github.com/helidon-io/helidon/pull/9323)
- Docs: Add emphasis on including an OTel exporter and configuring  [9312](https://github.com/helidon-io/helidon/pull/9312)
- Docs: Document work-around for maven archetype issue (#9316) [9324](https://github.com/helidon-io/helidon/pull/9324)
- Tests: Fix DbClient PostgreSQL tests [9293](https://github.com/helidon-io/helidon/pull/9293)

## [4.1.1]

This release contains important bugfixes and enhancements and is recommended for all users of Helidon 4. It is compatible with Helidon 4.0.X.

A minimum of Java 21 is required to use Helidon 4.

### Notable Changes

- Implement gRPC MP Client [9026](https://github.com/helidon-io/helidon/pull/9026)

### CHANGES

- CORS: Remove headers that do not affect CORS decision-making from request adapter logging output [9178](https://github.com/helidon-io/helidon/pull/9178)
- Codegen: Add support for additional modifiers [9201](https://github.com/helidon-io/helidon/pull/9201)
- Codegen: Fix generation of annotations, including lists, nested annotations etc. [9182](https://github.com/helidon-io/helidon/pull/9182)
- Codegen: Handling enum and type values in a consistent way in code generation. [9167](https://github.com/helidon-io/helidon/pull/9167)
- Codegen: Support for validation of Duration and URI default values. [9166](https://github.com/helidon-io/helidon/pull/9166)
- Codegen: Udpates to types and annotation processing [9168](https://github.com/helidon-io/helidon/pull/9168)
- Config: Replace manual casts on pattern with instanceof in HoconConfigParser [9209](https://github.com/helidon-io/helidon/pull/9209)
- LRA: Replace deprecated method Scheduling.fixedRateBuilder() [9098](https://github.com/helidon-io/helidon/pull/9098)
- Security: Required authorization propagated from the class level now [9137](https://github.com/helidon-io/helidon/pull/9137)
- Tracing: Allow users to direct Helidon to use an existing global `OpenTelemetry` instance rather than create its own [9205](https://github.com/helidon-io/helidon/pull/9205)
- WebServer: Allows the creation of empty SSE events [9207](https://github.com/helidon-io/helidon/pull/9207)
- WebServer: Increases default value of write-buffer-size to 4K [9190](https://github.com/helidon-io/helidon/pull/9190)
- WebServer: UncheckedIOException no longer a special case [9206](https://github.com/helidon-io/helidon/pull/9206)
- gRPC: Downgrades version of protobuf for backwards compatibility [9162](https://github.com/helidon-io/helidon/pull/9162)
- gRPC: Implements support for client gRPC channel injections [9155](https://github.com/helidon-io/helidon/pull/9155)
- gRPC: Implements the gRPC MP Client API [9026](https://github.com/helidon-io/helidon/pull/9026)
- gRPC: Renames package-private Grpc type to GrpcRouteHandler [9173](https://github.com/helidon-io/helidon/pull/9173)
- Build: Fix nightly script [9221](https://github.com/helidon-io/helidon/pull/9221)
- Build: Update release workflows [9210](https://github.com/helidon-io/helidon/pull/9210)
- Dependencies: Upgrade microprofile-cdi-tck to 4.0.13 [9141](https://github.com/helidon-io/helidon/pull/9141)
- Dependencies: Upgrade oci sdk to 3.46.1 [9179](https://github.com/helidon-io/helidon/pull/9179)
- Dependencies: Upgrade slf4j to 2.0.16 [9143](https://github.com/helidon-io/helidon/pull/9143)
- Deprecation: deprecate old injection integration for oci [9184](https://github.com/helidon-io/helidon/pull/9184)
- Docs: Clarify description of config profiles [9188](https://github.com/helidon-io/helidon/pull/9188)
- Docs: Documents gRPC MP Client API [9150](https://github.com/helidon-io/helidon/pull/9150)
- Tests: Builder tests that confidential options are not printed in toString() [9154](https://github.com/helidon-io/helidon/pull/9154)

## [4.1.0]

This release contains important bugfixes and enhancements and is recommended for all users of Helidon 4. It is compatible with Helidon 4.0.X.

A minimum of Java 21 is required to use Helidon 4.

### Notable Changes
 
- Support for MicroProfile 6.1 [8704](https://github.com/helidon-io/helidon/issues/8704)
- gRPC support [5418](https://github.com/helidon-io/helidon/issues/5418)
- Support for Java 22 and Java 23

### CHANGES

- Builders: Fixed configuration metadata of blueprints that are configured and provide a service [8891](https://github.com/helidon-io/helidon/pull/8891)
- Common: Convert `ConcurrentHashMap` which does service loading to `HashMap` with `ReentrantLock` [8977](https://github.com/helidon-io/helidon/pull/8977)
- Common: Fix SetCookie to work for client side as well [9029](https://github.com/helidon-io/helidon/pull/9029)
- Common: Improved parsing of HTTP/1 prologue and headers. [8890](https://github.com/helidon-io/helidon/pull/8890)
- Common: Introduction of HSON library to write and parse Helidon metadata files. [9050](https://github.com/helidon-io/helidon/pull/9050)
- Common: Mapper manager cache key fix [9121](https://github.com/helidon-io/helidon/pull/9121)
- Common: Methods to retrieve optional typed entity [8939](https://github.com/helidon-io/helidon/pull/8939)
- Common: Remove unused parameters from JsonpWriter [8979](https://github.com/helidon-io/helidon/pull/8979)
- Common: Replace deprecated method Header.value() on Header.get() [8873](https://github.com/helidon-io/helidon/pull/8873)
- Common: Update UriEncoding.decode to expose a decodeQuery method [9006](https://github.com/helidon-io/helidon/pull/9006)
- Common: Use Helidon metadata format (HSON) for service registry generated file. [9061](https://github.com/helidon-io/helidon/pull/9061)
- Common: Use Hson.Struct instead of Hson.Object to prevent confusion with java.lang.Object [9080](https://github.com/helidon-io/helidon/pull/9080)
- Common: Use System.Logger instead of JUL where applicable #7792 [8791](https://github.com/helidon-io/helidon/pull/8791)
- Common: Use string constructor of BigDecimal to avoid bad decimals in output. [9074](https://github.com/helidon-io/helidon/pull/9074)
- Config: Upgrade to MP Config 3.1 and fix an issue with profile specific properties [8757](https://github.com/helidon-io/helidon/pull/8757)
- DBClient: Add uses io.helidon.dbclient.jdbc.spi.JdbcConnectionPoolProvider to io.helidon.dbclient.jdbc module (#8237) [8850](https://github.com/helidon-io/helidon/pull/8850)
- DBClient: Consider missing named parameters values in the parameters Map as null [9035](https://github.com/helidon-io/helidon/pull/9035)
- DBClient: Fix DbClientService for Mongo DbClient [9102](https://github.com/helidon-io/helidon/pull/9102)
- FT: Fix confusing log message on breach of overallTimeout duration [8936](https://github.com/helidon-io/helidon/pull/8936)
- FT: Remove unused constructor parameter from io.helidon.faulttolerance.AsyncImpl [9020](https://github.com/helidon-io/helidon/pull/9020)
- FT: Use correct exception when retrying in FT [8983](https://github.com/helidon-io/helidon/pull/8983)
- gRPC: Client Implementation [8423](https://github.com/helidon-io/helidon/pull/8423)
- gRPC: MP Implementation [8878](https://github.com/helidon-io/helidon/pull/8878)
- JEP290: forward port of serial-config fix [8814](https://github.com/helidon-io/helidon/pull/8814)
- JTA: Refactors JtaConnection to allow status enforcement by JTA implementation [8479](https://github.com/helidon-io/helidon/pull/8479)
- JTA: Removes usage of ConcurrentHashMap in LocalXAResource.java to avoid thread pinning in JDKs of version 22 and lower [8900](https://github.com/helidon-io/helidon/pull/8900)
- Logging: Bugfixes log builder [9051](https://github.com/helidon-io/helidon/pull/9051)
- MDC: propagation without context [8957](https://github.com/helidon-io/helidon/pull/8957)
- Metrics: Add RW locking to better manage concurrency [8997](https://github.com/helidon-io/helidon/pull/8997)
- Metrics: Add deprecation logging and mention in Micrometer integration doc pages [9100](https://github.com/helidon-io/helidon/pull/9100)
- Metrics: MP Metrics 5.1 support [9032](https://github.com/helidon-io/helidon/pull/9032)
- Metrics: Mark deprecations for Micrometer integration component [9085](https://github.com/helidon-io/helidon/pull/9085)
- Metrics: Properly handle disabled metrics in MP [8908](https://github.com/helidon-io/helidon/pull/8908)
- Metrics: Update metrics config default for `rest-request-enabled` and add doc text explaining SE vs. MP defaults for some values [8912](https://github.com/helidon-io/helidon/pull/8912)
- Native image fixes (required for Java 22) [9028](https://github.com/helidon-io/helidon/pull/9028)
- Native image: Add required reflection configuration for EclipseLink [8871](https://github.com/helidon-io/helidon/pull/8871)
- Native image: to support latest dev release of GraalVM native image [8838](https://github.com/helidon-io/helidon/pull/8838)
- OCI: Add Imds data retriever as a service provider [8928](https://github.com/helidon-io/helidon/pull/8928)
- OCI: Oci integration fixes [8927](https://github.com/helidon-io/helidon/pull/8927)
- OCI: Service registry OCI integration update [8921](https://github.com/helidon-io/helidon/pull/8921)
- OCI: Support for OKE Workload identity in OCI integration for Service registry [8862](https://github.com/helidon-io/helidon/pull/8862)
- OCI: Update oci.auth-strategy values in generated OCI archetype to avoid UnsatisfiedResolutionException [9073](https://github.com/helidon-io/helidon/pull/9073)
- SSE mediaType comes null after first consumed event [8922](https://github.com/helidon-io/helidon/pull/8922)
- Security: ConcurrentHashMap guarding added [9114](https://github.com/helidon-io/helidon/pull/9114)
- Security: Correctly guard concurrent access to hash map [9031](https://github.com/helidon-io/helidon/pull/9031)
- Security: Fixed concurrent access to identity hash map with reentrant lock. [9030](https://github.com/helidon-io/helidon/pull/9030)
- Security: Jwt improvements [8865](https://github.com/helidon-io/helidon/pull/8865)
- Service Registry [8766](https://github.com/helidon-io/helidon/pull/8766)
- Tracing: Adopt MP Telemetry 1.1 [8984](https://github.com/helidon-io/helidon/pull/8984)
- Tracing: After retrieval check baggage entry for null before dereferencing it [8885](https://github.com/helidon-io/helidon/pull/8885)
- Tracing: Fix tracer information propagation across threads using Helidon context [8841](https://github.com/helidon-io/helidon/pull/8841)
- Tracing: Reorder checking of delegate vs. wrapper in OTel tracer unwrap [8855](https://github.com/helidon-io/helidon/pull/8855)
- Tracing: Replace deprecated method Span.baggage(key) on Span.baggage().get(key) [9042](https://github.com/helidon-io/helidon/pull/9042)
- WebClient: Attempt to read an unconsumed response entity to allow connection caching [8943](https://github.com/helidon-io/helidon/pull/8943)
- WebClient: Client connection properly returned to the cache [9115](https://github.com/helidon-io/helidon/pull/9115)
- WebClient: Fix multi-value query string parsing [8889](https://github.com/helidon-io/helidon/pull/8889)
- WebClient: Moves client protocol ID caching from HttpClientRequest to WebClient [8933](https://github.com/helidon-io/helidon/pull/8933)
- WebClient: Remove unnecessary field length from ContentLengthInputStream [8915](https://github.com/helidon-io/helidon/pull/8915)
- WebClient: not routing the requests through proxy configured using Proxy Builder. #9022 [9023](https://github.com/helidon-io/helidon/pull/9023)
- WebServer: Avoids running the encoders (such as GZIP) when no data is written [9117](https://github.com/helidon-io/helidon/pull/9117)
- WebServer: Fix problem where throwing an Error would close connection but send keep-alive [9014](https://github.com/helidon-io/helidon/pull/9014)
- WebServer: HTTP2-Settings needs to be encoded/decoded to Base64 with url dialect [8845](https://github.com/helidon-io/helidon/pull/8845)
- WebServer: Replaces ConcurrentHashMap to avoid potential thread pinning [8995](https://github.com/helidon-io/helidon/pull/8995)
- WebServer: Retrieve the correct requested URI info path value, indpt of the routing path used to locate the handler [8823](https://github.com/helidon-io/helidon/pull/8823)
- WebServer: Return correct status on too long prologue [9001](https://github.com/helidon-io/helidon/pull/9001)
- WebServer: Server TLS - Add path key description [8937](https://github.com/helidon-io/helidon/pull/8937)
- WebServer: Skips content encoding of empty entities [9000](https://github.com/helidon-io/helidon/pull/9000)
- WebServer: Update max-prologue-length from 2048 to 4096 to align with 3.x [9007](https://github.com/helidon-io/helidon/pull/9007)
- WebServer: improvement of header parsing error handling [8831](https://github.com/helidon-io/helidon/pull/8831)
- WebServer: register routing in weighted order of Server and HTTP Features [8826](https://github.com/helidon-io/helidon/pull/8826)
- WebSocket: Makes SocketContext available to a WsSession [8944](https://github.com/helidon-io/helidon/pull/8944)
- Archetype: Remove unused config property from generated code [8965](https://github.com/helidon-io/helidon/pull/8965)
- Archetype: fix Native image build for `quickstart` with `jackson` [8835](https://github.com/helidon-io/helidon/pull/8835)
- Archetype: fix database app-type typo [8963](https://github.com/helidon-io/helidon/pull/8963)
- Build: Add post pr merge workflow to support continuous snapshot deployments [8919](https://github.com/helidon-io/helidon/pull/8919) [8924](https://github.com/helidon-io/helidon/pull/8924) [8923](https://github.com/helidon-io/helidon/pull/8923)
- Build: Cleanup validate workflow [9108](https://github.com/helidon-io/helidon/pull/9108)
- Build: Fix release.sh [9087](https://github.com/helidon-io/helidon/pull/9087)
- Build: POM cleanups [9110](https://github.com/helidon-io/helidon/pull/9110)
- Build: Parallelized pipelines [9111](https://github.com/helidon-io/helidon/pull/9111)
- Build: ShellCheck [9078](https://github.com/helidon-io/helidon/pull/9078)
- Build: Uptake Helidon Build Tools v4.0.9 [9086](https://github.com/helidon-io/helidon/pull/9086)
- Dependencies: Bump up cron-utils [9120](https://github.com/helidon-io/helidon/pull/9120)
- Dependencies: GraphQL upgrade [9109](https://github.com/helidon-io/helidon/pull/9109)
- Dependencies: Java 22 support. Upgrade ASM, byte-buddy, and eclipselink [8956](https://github.com/helidon-io/helidon/pull/8956)
- Dependencies: Update eclipselink to 4.0.4 [9015](https://github.com/helidon-io/helidon/pull/9015)
- Dependencies: Upgrade oci-sdk to 3.45.0 [9083](https://github.com/helidon-io/helidon/pull/9083)
- Dependencies: Upgrade snakeyaml to 2.2 [9072](https://github.com/helidon-io/helidon/pull/9072)
- Dependencies: Upgrades gRPC dependencies to latest versions [9105](https://github.com/helidon-io/helidon/pull/9105)
- Dependencies: jakarta ee upgrades [9089](https://github.com/helidon-io/helidon/pull/9089)
- Docs: Add back and enhance the page describing OpenAPI generation for Helidon 4 [9052](https://github.com/helidon-io/helidon/pull/9052)
- Docs: Clarify javadoc for HealthCheckResponse.Builder.status(boolean) [9043](https://github.com/helidon-io/helidon/pull/9043)
- Docs: Cleanup prerequisites and use of prereq table [9063](https://github.com/helidon-io/helidon/pull/9063)
- Docs: Config reference documentation [9053](https://github.com/helidon-io/helidon/pull/9053)
- Docs: Correct the ordering of whenSent in doc snippet [8884](https://github.com/helidon-io/helidon/pull/8884)
- Docs: Doc for @AddConfigBlock #8807 [8825](https://github.com/helidon-io/helidon/pull/8825)
- Docs: Document supported GraalVM version for native-image [8938](https://github.com/helidon-io/helidon/pull/8938)
- Docs: Documents the gRPC MP server API [9123](https://github.com/helidon-io/helidon/pull/9123)
- Docs: Excluding generated service descriptors from javadoc plugin(s). [9082](https://github.com/helidon-io/helidon/pull/9082)
- Docs: Generate config docs during build [9103](https://github.com/helidon-io/helidon/pull/9103)
- Docs: Mocking documentation [8787](https://github.com/helidon-io/helidon/pull/8787)
- Docs: Update Keycloak version to 24 in OIDC guide [8868](https://github.com/helidon-io/helidon/pull/8868)
- Docs: Update generated config reference [8852](https://github.com/helidon-io/helidon/pull/8852)
- Docs: Update microprofile spec versions in docs [9095](https://github.com/helidon-io/helidon/pull/9095)
- Docs: Updates links to examples that are in documentation to point to the `helidon-examples` repository. [9094](https://github.com/helidon-io/helidon/pull/9094)
- Examples: Fix example to use the configured values. [8994](https://github.com/helidon-io/helidon/pull/8994)
- Examples: Skip test if InstancePrincipal UT if Imds is available [8985](https://github.com/helidon-io/helidon/pull/8985)
- Examples: Updates versions of beans.xml resources to 4.0 [9038](https://github.com/helidon-io/helidon/pull/9038)
- Examples: examples removal [9034](https://github.com/helidon-io/helidon/pull/9034)
- Test: add helidon-logging-jul as a test dependency to some modules #779 [8810](https://github.com/helidon-io/helidon/pull/8810)
- Test: Add `classesDirectory` configuration to failsafe plugin [9059](https://github.com/helidon-io/helidon/pull/9059)
- Test: DbClient IT tests job [9107](https://github.com/helidon-io/helidon/pull/9107)
- Test: Packaging Integration Tests [9106](https://github.com/helidon-io/helidon/pull/9106)
- Test: Re-add tck-fault-tolerance module in the reactor [9112](https://github.com/helidon-io/helidon/pull/9112)
- Test: Reenables failing JPA test [9037](https://github.com/helidon-io/helidon/pull/9037)
- Test: Refactor DbClient integration tests [9104](https://github.com/helidon-io/helidon/pull/9104)
- Test: Restored test TenantTest#test2 after changes in FT [8832](https://github.com/helidon-io/helidon/pull/8832)
- Test: Update microprofile tck artifact install [9077](https://github.com/helidon-io/helidon/pull/9077)
- Test: Use Hamcrest assertions instead of JUnit in common/buffers (#1749) [8883](https://github.com/helidon-io/helidon/pull/8883)
- Test: Use Hamcrest assertions instead of JUnit in dbclient/mongodb  (#1749) [8934](https://github.com/helidon-io/helidon/pull/8934)
- Test: Use Hamcrest assertions instead of JUnit in webclient/http1 (#1749) [8914](https://github.com/helidon-io/helidon/pull/8914)


## [4.0.11]

This release contains important bugfixes and is recommended for all users of Helidon 4. 

Java 21 is required to use Helidon 4

### CHANGES

- Common: Update UriEncoding.decode to expose a decodeQuery method [9009](https://github.com/helidon-io/helidon/pull/9009)
- JTA: Removes usage of ConcurrentHashMap in LocalXAResource.java  [8988](https://github.com/helidon-io/helidon/pull/8988)
- Metrics: Add RW locking to better manage concurrency [8999](https://github.com/helidon-io/helidon/pull/8999)
- Metrics: Properly handle disabled metrics in MP [8976](https://github.com/helidon-io/helidon/pull/8976)
- Observability: Convert `ConcurrentHashMap` which does service loading to `HashMap` with reentrant lock [8991](https://github.com/helidon-io/helidon/pull/8991)
- Tracing: After retrieval check baggage entry for null before dereferencing it [8975](https://github.com/helidon-io/helidon/pull/8975)
- WebClient: Attempt to read an unconsumed response entity to allow connection caching [8996](https://github.com/helidon-io/helidon/pull/8996)
- WebClient: Moves client protocol ID caching from HttpClientRequest to WebClient [8987](https://github.com/helidon-io/helidon/pull/8987)
- WebServer: Fix problem where throwing an Error would close connection but send keep-alive [9016](https://github.com/helidon-io/helidon/pull/9016)
- WebServer: Skips content encoding of empty entities.  [9008](https://github.com/helidon-io/helidon/pull/9008)
- WebServer: Update max-prologue-length from 2048 to 4096 to align with 3.x [9010](https://github.com/helidon-io/helidon/pull/9010)
- Dependencies: Update eclipselink to 4.0.4 [9017](https://github.com/helidon-io/helidon/pull/9017)
- Dependencies: Upgrade oci-sdk to 3.43.2 [8961](https://github.com/helidon-io/helidon/pull/8961)
- Examples: Archetype: Remove unused config property from generated code [8990](https://github.com/helidon-io/helidon/pull/8990)
- Examples: Archetype: fix database app-type typo (#8963) [8989](https://github.com/helidon-io/helidon/pull/8989)
- Testing: Skip test if InstancePrincipal UT if Imds is available [8992](https://github.com/helidon-io/helidon/pull/8992)

## [4.0.10]

This release contains important bugfixes and enhancements and is recommended for all users of Helidon 4. 

Java 21 is required to use Helidon 4.0.10.

### CHANGES

- Fault Tolerance: implement a new method caching strategy in fault tolerance. [8842](https://github.com/helidon-io/helidon/pull/8842)
- Tracing: Reorder checking of delegate vs. wrapper in OTel tracer unwrap ( [8859](https://github.com/helidon-io/helidon/pull/8859)
- Tracing: tracer information propagation across threads using Helidon context [8847](https://github.com/helidon-io/helidon/pull/8847)
- WebServer: HTTP2-Settings needs to be encoded/decoded to Base64 with url dialect [8853](https://github.com/helidon-io/helidon/pull/8853)
- WebServer: Fix handling of invalid end of line in HTTP header parsing. Added tests [8843](https://github.com/helidon-io/helidon/pull/8843)
- WebServer: Retrieve the correct requested URI info path value, indpt of the routing path used to locate the handler [8844](https://github.com/helidon-io/helidon/pull/8844)
- WebServer: register routing in weighted order of Server and HTTP Features [8840](https://github.com/helidon-io/helidon/pull/8840)
- Native Image: Updates to support latest dev release of GraalVM native image [8838](https://github.com/helidon-io/helidon/pull/8838)
- Security: JWT improvements [8865](https://github.com/helidon-io/helidon/pull/8865)

## [4.0.9]

This release contains important bugfixes and ehancements and is recommended for all users of Helidon 4. 

Java 21 is required to use Helidon 4.0.9.

### CHANGES

- Common: Parameters.first(String) generates java.lang.IndexOutOfB… [8723](https://github.com/helidon-io/helidon/pull/8723)
- Config: Cannot read config from environment with environment modules variables [8786](https://github.com/helidon-io/helidon/pull/8786)
- Config: LazyConfigSource is now queried when an unknown node is requested [8707](https://github.com/helidon-io/helidon/pull/8707)
- Config: Switched implementation of MpEnvironmentVariablesSource to use an LRU cache [8768](https://github.com/helidon-io/helidon/pull/8768)
- Config: fix `getOrdinal` for system property and environment variable config sources  [8744](https://github.com/helidon-io/helidon/pull/8744)
- gRPC: Improvements to gRPC server-side support [8765](https://github.com/helidon-io/helidon/pull/8765)
- MP Threading: New annotation @ExecuteOn  [8643](https://github.com/helidon-io/helidon/pull/8643)
- Native image: AbstractConfigurableExtension native-image fix [8771](https://github.com/helidon-io/helidon/pull/8771)
- Native image: Force hibernate to use no-op bytecode provider with native-image [8740](https://github.com/helidon-io/helidon/pull/8740)
- Native image: Register additional hibernate classes for reflection [8758](https://github.com/helidon-io/helidon/pull/8758)
- Native-image: Fix native-image properties layout. [8808](https://github.com/helidon-io/helidon/pull/8808)
- Native-image: Use native-image:compile-no-fork instead of native-image:compile [8802](https://github.com/helidon-io/helidon/pull/8802)
- OCI: Refactor OCI metrics library a bit [8745](https://github.com/helidon-io/helidon/pull/8745)
- Testing: @HelidonTest / @AddConfig* Provide a config parser by type #8718  [8721](https://github.com/helidon-io/helidon/pull/8721)
- Testing: Ability to Inject MockBeans in Helidon #7694 [8674](https://github.com/helidon-io/helidon/pull/8674)
- Testing: Add text block support [8655](https://github.com/helidon-io/helidon/pull/8655)
- Tracing: Associate tracer-level tags with Jaeger process level (instead of span level) [8764](https://github.com/helidon-io/helidon/pull/8764)
- Tracing: Fix problems with tracing data propagation [8742](https://github.com/helidon-io/helidon/pull/8742)
- Tracing: Harden WebClientSecurity against absent or disabled tracing [8809](https://github.com/helidon-io/helidon/pull/8809)
- Tracing: Use Helidon tracer, span builder, span types instead of OTel ones so we can trigger span listeners [8778](https://github.com/helidon-io/helidon/pull/8778)
- Build: Plugin updates [8687](https://github.com/helidon-io/helidon/pull/8687)
- Build: Update setup-java for snapshot workflow [8788](https://github.com/helidon-io/helidon/pull/8788)
- Build: cleanup helidon-bom and helidon-all [8783](https://github.com/helidon-io/helidon/pull/8783)
- Build: helidon-common sources JAR contains absolute paths #8761 [8762](https://github.com/helidon-io/helidon/pull/8762)
- Build: upgrade MacOS runner to 14 and fix protoc version [8717](https://github.com/helidon-io/helidon/pull/8717)
- Dependencies: Bump deploy plugin to 3.1.1 [8790](https://github.com/helidon-io/helidon/pull/8790)
- Dependencies: Upgrade microprofile rest client to 3.0.1 [8730](https://github.com/helidon-io/helidon/pull/8730)
- Dependencies: Upgrades to Jersey to 3.1.7 [8798](https://github.com/helidon-io/helidon/pull/8798)
- Docs: New document that describes the ExecuteOn annotation [8756](https://github.com/helidon-io/helidon/pull/8756)
- Docs: include SE upgrade guide in docs navbar [8795](https://github.com/helidon-io/helidon/pull/8795)
- Examples: Capitalized received message in io.helidon.examples.webserver.websocket.MessageBoardEndpoint (#8725) [8731](https://github.com/helidon-io/helidon/pull/8731)
- Examples: Update Dockerfiles to use jdk-no-fee-term instead of openjdk [8733](https://github.com/helidon-io/helidon/pull/8733)
- Examples: Use LevelChangePropagator in examples/logging/slf4j (#7737) [8656](https://github.com/helidon-io/helidon/pull/8656)
- Examples: Archetype - Add SLF4J dependency [8792](https://github.com/helidon-io/helidon/pull/8792)
- Tests: Add tests for building native-image for quickstart examples [8719](https://github.com/helidon-io/helidon/pull/8719)
- Tests: Fix tests/integration/native-image/mp-2 [8801](https://github.com/helidon-io/helidon/pull/8801)

## [4.0.8]

This release contains important bugfixes and ehancements and is recommended for all users of Helidon 4. 

Java 21 is required to use Helidon 4.0.8.

### CHANGES

- Builders: Generated builders honor service discovery from copied instance [8648](https://github.com/helidon-io/helidon/pull/8648)
- Common: Properly handles an empty input stream in GrowingBufferData [8694](https://github.com/helidon-io/helidon/pull/8694)
- DBClient: Closeable jdbc pool [8571](https://github.com/helidon-io/helidon/pull/8571)
- DBClient: Remove unnecessary comments [8658](https://github.com/helidon-io/helidon/pull/8658)
- Datasource: Addresses a shortcoming of the UCP pool creation logic in certain test scenarios [8642](https://github.com/helidon-io/helidon/pull/8642)
- Health: Preserve stability in details JSON output [8697](https://github.com/helidon-io/helidon/pull/8697)
- OCI: Fixes bug in OkeWorkloadIdentityAdpSupplier.available() method [8689](https://github.com/helidon-io/helidon/pull/8689)
- OCI: Retires dependency of ...cdi.OciExtension on ...runtime.OciExtension [8486](https://github.com/helidon-io/helidon/pull/8486)
- OIDC: double outbound fix [8680](https://github.com/helidon-io/helidon/pull/8680)
- OIDC: original uri resolving query fix [8701](https://github.com/helidon-io/helidon/pull/8701)
- Tracing: Clear default propagator when one is explicitly added [8695](https://github.com/helidon-io/helidon/pull/8695)
- Tracing: Support span event listeners [8619](https://github.com/helidon-io/helidon/pull/8619)
- WebClient: Allows transition from HEADERS to END in HTTP2 client stream [8702](https://github.com/helidon-io/helidon/pull/8702)
- WebClient: Client header duplication fixed [8628](https://github.com/helidon-io/helidon/pull/8628)
- WebClient: WsClient accept `Connection` header with `upgrade` value lowercase [8675](https://github.com/helidon-io/helidon/pull/8675)
- WebServer: Add shutdown handler to control shutdown sequence in a deterministic … [8684](https://github.com/helidon-io/helidon/pull/8684)
- WebServer: ErrorHandlers class swallows exception object if response can't be reset [8634](https://github.com/helidon-io/helidon/pull/8634)
- WebServer: Proper handling of invalid Accept types [8669](https://github.com/helidon-io/helidon/pull/8669)
- WebServer: Use delegation instead of inheritance from BufferedOutputStream [8662](https://github.com/helidon-io/helidon/pull/8662)
- Dependencies: Remove dependency on jakarta.activation-api as no longer needed [8650](https://github.com/helidon-io/helidon/pull/8650)
- Dependencies: Upgrade kafka-clients to 3.6.2 [8665](https://github.com/helidon-io/helidon/pull/8665)
- Deprecation: Add deprecation to helidon-inject modules in prep for service registry [8678](https://github.com/helidon-io/helidon/pull/8678)
- Docs: Fix wrong description for bean validation annotations (#8505) [8556](https://github.com/helidon-io/helidon/pull/8556)
- Docs: Move example WebClient code into a snippet [8699](https://github.com/helidon-io/helidon/pull/8699)
- Examples: Archetype - Add intellij configuration file for MP projects [8609](https://github.com/helidon-io/helidon/pull/8609)
- Examples: Archetype - Implement MP JSON and metrics combination [8629](https://github.com/helidon-io/helidon/pull/8629)
- Tests: Remove unused private methods from HelloWorldAsyncResponseWithRestRequestTest [8624](https://github.com/helidon-io/helidon/pull/8624)


## [4.0.7]

This release contains important bugfixes and ehancements and is recommended for all users of Helidon 4. 

Java 21 is required to use Helidon 4.0.7.

### CHANGES

- Builders: Avoid using replicated default values for Lists when creating from builder or instance [8428](https://github.com/helidon-io/helidon/pull/8428)
- CORS: Properly handle opaque origin ("null") in CORS processing [8537](https://github.com/helidon-io/helidon/pull/8537)
- JAX-RS: Fix failure with input stream obtained more than once for post with more than one JAX-RS app [8558](https://github.com/helidon-io/helidon/pull/8558)
- Metrics: Add `unwrap` support to metrics builders [8588](https://github.com/helidon-io/helidon/pull/8588)
- Observability: Observers now inherit weight of the ObserveFeature. [8554](https://github.com/helidon-io/helidon/pull/8554)
- Security: Disabled OidcFeature no longer throws an NPE. [8520](https://github.com/helidon-io/helidon/pull/8520)
- Security: If there is no configuration of oidc provider, it is considered disabled [8603](https://github.com/helidon-io/helidon/pull/8603)
- Security: Support for disabling security providers through configuration. [8521](https://github.com/helidon-io/helidon/pull/8521)
- TLS: Fixed method that compares SSLParameters for equality [8570](https://github.com/helidon-io/helidon/pull/8570)
- Tracing: Fix baggage propagation from current active span [8512](https://github.com/helidon-io/helidon/pull/8512)
- Tracing: Properly return Optional.empty() for current span if there is no current OTel span [8583](https://github.com/helidon-io/helidon/pull/8583)
- WebServer: Fix static content sending 304 with entity. [8599](https://github.com/helidon-io/helidon/pull/8599)
- WebServer: Make HttpRouting an interface [8523](https://github.com/helidon-io/helidon/pull/8523)
- WebServer: Validate that header has at least one value when list is used. [8489](https://github.com/helidon-io/helidon/pull/8489)
- Build: Add version check to release script [8481](https://github.com/helidon-io/helidon/pull/8481)
- Build: Upgrade upload-artifact to v4 [8610](https://github.com/helidon-io/helidon/pull/8610)
- Dependencies: Integrate Helidon Build Tools v4.0.6 [8476](https://github.com/helidon-io/helidon/pull/8476)
- Dependencies: Upgrade netty to 4.1.108.Final [8532](https://github.com/helidon-io/helidon/pull/8532)
- Dependencies: Upgrade oci-sdk to 3.39.0 [8611](https://github.com/helidon-io/helidon/pull/8611)
- Docs: Add MP observability page; discuss weight setting to resolve some routing conflicts [8580](https://github.com/helidon-io/helidon/pull/8580)
- Docs: Clarify ordinal definition of meta-config [8581](https://github.com/helidon-io/helidon/pull/8581)
- Docs: Fix SE Health endpoint path to match code snippet [8587](https://github.com/helidon-io/helidon/pull/8587)
- Docs: Fix wrong example for Config.onChange (#8607) [8608](https://github.com/helidon-io/helidon/pull/8608)
- Examples: Archetype - Add opens WEB to module info for multipart [8506](https://github.com/helidon-io/helidon/pull/8506)
- Examples: Archetype - Fix Json code duplication [8507](https://github.com/helidon-io/helidon/pull/8507)
- Examples: Archetype - Kubernetes uses `ClusterIP` instead of `NodePort` [8488](https://github.com/helidon-io/helidon/pull/8488)
- Examples: Update streaming example so it starts and downloads last uploaded file [8515](https://github.com/helidon-io/helidon/pull/8515)
- Examples: remove netty logging config from logging.properties [8534](https://github.com/helidon-io/helidon/pull/8534)
- Examples: threading example [8576](https://github.com/helidon-io/helidon/pull/8576)
- Tests: Avoid implementing the OCI Monitoring interface. [8474](https://github.com/helidon-io/helidon/pull/8474)
- Tests: Introducing a test to validate that combination of config annotations… [8491](https://github.com/helidon-io/helidon/pull/8491)
- Tests: Upgrade core profile tests to version 10.0.3 [8606](https://github.com/helidon-io/helidon/pull/8606)

## [4.0.6]

This release contains important bugfixes and ehancements and is recommended for all users of Helidon 4. 

Java 21 is required to use Helidon 4.0.6.

### CHANGES

- Builders: Fixes for problems discovered in service registry work [8403](https://github.com/helidon-io/helidon/pull/8403)
- Config: Config encryption improvements [8440](https://github.com/helidon-io/helidon/pull/8440)
- DBClient: Fix for #8344 - Incorrect SQL Type for null Parameters in JDBC Parameter Binding [8342](https://github.com/helidon-io/helidon/pull/8342)
- DataSource: Adds support for injecting UniversalConnectionPool instances [8378](https://github.com/helidon-io/helidon/pull/8378)
- DataSource: Installs a convenient default for connectionPoolName in UCPBackedDataSourceExtension when appropriate [8359](https://github.com/helidon-io/helidon/pull/8359)
- HTTP: Enhance Status.java with Additional Standard HTTP Status Codes [8444](https://github.com/helidon-io/helidon/pull/8444)
- LRA: Replace deprecated classInfo.classAnnotation(DotName) on classInfo.declaredAnnotation(DotName) [8417](https://github.com/helidon-io/helidon/pull/8417)
- Media: Multipart fixes (streaming, consume) [8375](https://github.com/helidon-io/helidon/pull/8375)
- Media: Replace deprecated MediaType.fullType() on MediaType.text() [8340](https://github.com/helidon-io/helidon/pull/8340)
- Metrics: More carefully handle re-creation of global meter registry [8389](https://github.com/helidon-io/helidon/pull/8389)
- Neo4J: Replace deprecated session.writeTransaction on session.executeWrite in Neo4jHealthCheck [8397](https://github.com/helidon-io/helidon/pull/8397)
- OCI: Generalize the creation of the `OciMetricsSupport` instance from CDI [8431](https://github.com/helidon-io/helidon/pull/8431)
- OIDC: OIDC hostname handling [8456](https://github.com/helidon-io/helidon/pull/8456)
- OIDC: OIDC updates [8387](https://github.com/helidon-io/helidon/pull/8387)
- OIDC: check file system support posix before setting permission [8296](https://github.com/helidon-io/helidon/pull/8296)
- OIDC: refresh token optionality fix [8336](https://github.com/helidon-io/helidon/pull/8336)
- Reactive: Replace deprecated  Awaitable.await(long, TimeUnit) on Awaitable.await(Duration) [8334](https://github.com/helidon-io/helidon/pull/8334)
- Tracing: Add baggage API and allow access to baggage via `SpanContext` (as well as `Span`) [8320](https://github.com/helidon-io/helidon/pull/8320)
- Tracing: Suppress Helidon handling of OpenTelemetry annotations if OTel Java agent is present [8360](https://github.com/helidon-io/helidon/pull/8360)
- Tracing: Use OTel AgentDetector instead of just system property - review comments from earlier PR [8374](https://github.com/helidon-io/helidon/pull/8374)
- WebServer: Adds support for sending websocket CLOSE frames without a payload [8408](https://github.com/helidon-io/helidon/pull/8408)
- WebServer: Fix buffering of content in our Jersey integration. [8461](https://github.com/helidon-io/helidon/pull/8461)
- WebServer: Include requested URI config in ListenerConfig; fix config keys [8371](https://github.com/helidon-io/helidon/pull/8371)
- WebServer: Releasing entity count down latch even if connection is set to close. [8376](https://github.com/helidon-io/helidon/pull/8376)
- WebServer: Return 500 when 204 with entity is sent from routing. [8357](https://github.com/helidon-io/helidon/pull/8357)
- WebServer: TLS Revocation config [8425](https://github.com/helidon-io/helidon/pull/8425)
- WebServer: TLS peer certs performance [8316](https://github.com/helidon-io/helidon/pull/8316)
- Build:  Upgrade GitHub actions [8442](https://github.com/helidon-io/helidon/pull/8442)
- Dependencies: Bump com.oracle.oci.sdk:oci-java-sdk-bom from 3.34.0 to 3.35.0 in /dependencies [8421](https://github.com/helidon-io/helidon/pull/8421)
- Dependencies: PostgreSQL JDBC driver updated to 42.7.2. [8413](https://github.com/helidon-io/helidon/pull/8413)
- Dependencies: Upgrades Oracle database libraries to version 21.9.0.0. Adds tests under datasource-ucp CDI integration. [8221](https://github.com/helidon-io/helidon/pull/8221)
- Docs: Externalize and compile documentation java snippets [8294](https://github.com/helidon-io/helidon/pull/8294)
- Docs: Replace deprecated session.readTransaction on session.executeRead [8420](https://github.com/helidon-io/helidon/pull/8420)
- Docs: Update `context-root` config documentation [8427](https://github.com/helidon-io/helidon/pull/8427)
- Docs: Update javadocs of Meter builders to cross reference MeterRegistry.getOrCreate() [8381](https://github.com/helidon-io/helidon/pull/8381)
- Docs: doc examples snippets cleanup [8439](https://github.com/helidon-io/helidon/pull/8439)
- Examples: Align README.md vs functionality [8380](https://github.com/helidon-io/helidon/pull/8380)
- Examples: Archetype - Fix sql script for oracle database [8426](https://github.com/helidon-io/helidon/pull/8426)
- Examples: Fix WebServer Mutual TLS example [8409](https://github.com/helidon-io/helidon/pull/8409)
- Examples: archetype cleanup  [8445](https://github.com/helidon-io/helidon/pull/8445)
- Examples: examples cleanup [8433](https://github.com/helidon-io/helidon/pull/8433)
- Examples: generate a .gitignore files from archetype [8401](https://github.com/helidon-io/helidon/pull/8401)
- Tests: Also catch UncheckedIOException in test to handle broken pipes [8462](https://github.com/helidon-io/helidon/pull/8462)
- Tests: Fix OTel agent detector test that was polluting the JVM for later tests [8449](https://github.com/helidon-io/helidon/pull/8449)
- Tests: Improve examples test coverage [8338](https://github.com/helidon-io/helidon/pull/8338)
- Tests: Make test execution conditional to IPv6 being configured for localhost [8388](https://github.com/helidon-io/helidon/pull/8388)
- Tests: Replace deprecated API for configuring ObjectMapper [8395](https://github.com/helidon-io/helidon/pull/8395)
- Tests: Replace deprecated Multi.from(Stream) on Multi.create(Stream) [8424](https://github.com/helidon-io/helidon/pull/8424)
- Tests: Replace deprecated OutputStreamMultiBuilder.timeout(long,TimeUnit) on OutputStreamMultiBuilder.timeout(Duration) [8404](https://github.com/helidon-io/helidon/pull/8404)
- Tests: Replace deprecated java.util.Locale constructors on factory methods [8355](https://github.com/helidon-io/helidon/pull/8355)
- Tests: Restfull tck 3.1.5 [8368](https://github.com/helidon-io/helidon/pull/8368)
- Tests: Updates test to handle IOException if connection was closed [8457](https://github.com/helidon-io/helidon/pull/8457)


## [4.0.5]

This release contains important bugfixes and ehancements and is recommended for all users of Helidon 4. 

Java 21 is required to use Helidon 4.0.5.

### CHANGES

- Builders: Use vararg instead of optional (as a name). [8301](https://github.com/helidon-io/helidon/pull/8301)
- HTTP: Fixes a few problems handling character encodings in URIs [8327](https://github.com/helidon-io/helidon/pull/8327)
- Metrics: Remove clear-out of registries from extension; do it in TCK-only shutdown observer; update doc [8292](https://github.com/helidon-io/helidon/pull/8292)
- Security: OIDC improvement [8323](https://github.com/helidon-io/helidon/pull/8323)
- Tracing: Add null check for resource path in filter [8312](https://github.com/helidon-io/helidon/pull/8312)
- Tracing: Fix unwrap methods to return cast delegates rather than cast 'this' [8298](https://github.com/helidon-io/helidon/pull/8298)
- Tracing: Set incoming baggage from parent on Span [8303](https://github.com/helidon-io/helidon/pull/8303)
- Dependencies: Upgrade OCI SDK to 3.31.1 [8304](https://github.com/helidon-io/helidon/pull/8304)
- Test: Fix KafkaSeTest on Windows [8322](https://github.com/helidon-io/helidon/pull/8322)
- Test: Fix CachedHandlerTest.testFsFromInMemory on Windows [8318](https://github.com/helidon-io/helidon/pull/8318)

## [4.0.4]

This release contains bugfixes and ehancements and is recommended for all users of Helidon 4. In particular
this release contains a fix for issue [8250](https://github.com/helidon-io/helidon/issues/8250) which can occur
when running previous releases of Helidon 4 on Java 21.0.2 or newer.

Java 21 is required to use Helidon 4.0.4.

### CHANGES

- JPA: Ensures that the enabled flag is honored in JpaExtension's observer methods [8235](https://github.com/helidon-io/helidon/pull/8235)
- Media: JSON unicode detection without UTF-32 workaround [8253](https://github.com/helidon-io/helidon/pull/8253)
- RestClient: TLS replace in HelidonConnector fix [8247](https://github.com/helidon-io/helidon/pull/8247)
- Security: Authorized set to false fix [8295](https://github.com/helidon-io/helidon/pull/8295)
- Security: Security propagation is now disabled with not configured [8239](https://github.com/helidon-io/helidon/pull/8239)
- Tracing: Guard against NPE during early invocation of Span.current() [8257](https://github.com/helidon-io/helidon/pull/8257)
- Tracing: Slight clean-up of recent baggage and span scope fix [8258](https://github.com/helidon-io/helidon/pull/8258)
- WebClient: Cleans up and simplifies logic to determine which type of IP addresses to consider [8280](https://github.com/helidon-io/helidon/pull/8280)
- WebClient: Http client protocol creation not honoring disabled service discovery [8284](https://github.com/helidon-io/helidon/pull/8284)
- WebClient: Set weight for `WebClientTracing` provider greater than that for `WebClientSecurity` provider [8274](https://github.com/helidon-io/helidon/pull/8274)
- WebServer: Fix for buffer data when created with an offset. [8251](https://github.com/helidon-io/helidon/pull/8251)
- WebServer: Some minor optimizations in WebServer [8242](https://github.com/helidon-io/helidon/pull/8242)
- Build: Change backport action to accept target versions for porting via checkboxes and select current issue's version from a list [8236](https://github.com/helidon-io/helidon/pull/8236)
- Dependencies: Upgrade Yasson to 3.0.3 [8272](https://github.com/helidon-io/helidon/pull/8272)
- Dependencies: Upgrading to latest Tyrus 2.1.5 [8278](https://github.com/helidon-io/helidon/pull/8278)
- Docs: broken link fix [8259](https://github.com/helidon-io/helidon/pull/8259)
- Docs: change WebServer.Builder to WebServerConfig.Builder. [8261](https://github.com/helidon-io/helidon/pull/8261)
- Docs: fix broken documentation links Part 2 [8300](https://github.com/helidon-io/helidon/pull/8300)
- Examples: Remove unnecessary GreetingProvider from example custom liveness health check [8184](https://github.com/helidon-io/helidon/pull/8184)
- Tests: Add back span name test containing an explicit Application bean [8238](https://github.com/helidon-io/helidon/pull/8238)
- Tests: Fix vault tests [8279](https://github.com/helidon-io/helidon/pull/8279)

## [4.0.3]

This release contains bugfixes and ehancements and is recommended for all users of Helidon 4.

Java 21 is required to use Helidon 4.0.3.

### CHANGES

- Builders: Support for Map<String, String> in configured builders. [8231](https://github.com/helidon-io/helidon/pull/8231)
- CORS: Add a little logic to CORS config processing and significantly update the CORS doc [8212](https://github.com/helidon-io/helidon/pull/8212)
- CORS: Include scheme and port of origin and host in deciding whether to classify a request as CORS or not [8166](https://github.com/helidon-io/helidon/pull/8166)
- Metrics: Allow programmatic look-up of MetricRegistry via CDI without NPE [8210](https://github.com/helidon-io/helidon/pull/8210)
- Metrics: Make `RegistryFactory` and its `getInstance` and `getRegistry` methods public [8175](https://github.com/helidon-io/helidon/pull/8175)
- OCI: Fix issue for checking if app is running in an OCI instance which causes Instance Principal auth to fail [8197](https://github.com/helidon-io/helidon/pull/8197)
- RestClient: Prevent Helidon connector from re-encoding URI  [8232](https://github.com/helidon-io/helidon/pull/8232)
- Security: Fixed IDCS role obtaining [8207](https://github.com/helidon-io/helidon/pull/8207)
- Security: OIDC id token validation and token refresh [8153](https://github.com/helidon-io/helidon/pull/8153)
- Security: TLS default config values [8206](https://github.com/helidon-io/helidon/pull/8206)
- Tracing: Add support for `@SpanAttribute` annotation, use entire path for REST resource span name [8216](https://github.com/helidon-io/helidon/pull/8216)
- Tracing: Manage scopes correctly with baggage; allow baggage to be mutable to honor the Helidon `Span#baggage` semantics [8225](https://github.com/helidon-io/helidon/pull/8225)
- WebClient: The Helidon WS client must include a Connection header [8198](https://github.com/helidon-io/helidon/pull/8198)
- WebClient: WebClientService duplication fix [8224](https://github.com/helidon-io/helidon/pull/8224)
- WebServer: Check result of Integer.parseUnsignedInt() to be non-negative [8215](https://github.com/helidon-io/helidon/pull/8215)
- Dependencies: Jersey 3.1.5 [8174](https://github.com/helidon-io/helidon/pull/8174)
- Dependencies: upgrade jsonp 2.1.3 [8202](https://github.com/helidon-io/helidon/pull/8202)
- Docs: Fix bad include in cors documentation [8220](https://github.com/helidon-io/helidon/pull/8220)
- Docs: Fix image sizing in SE tracing guide [8201](https://github.com/helidon-io/helidon/pull/8201)
- Docs: Realign SE tracing guide to code [8193](https://github.com/helidon-io/helidon/pull/8193)
- Docs: fix broken documentation links Part 1 [8219](https://github.com/helidon-io/helidon/pull/8219)
- Examples: Remove references to any JDK from examples [8213](https://github.com/helidon-io/helidon/pull/8213)

## [4.0.2]

This release contains bugfixes and ehancements and is recommended for all users of Helidon 4.

Java 21 is required to use Helidon 4.0.2.

### CHANGES

- Builders: required fixes to builders and other tooling [8076](https://github.com/helidon-io/helidon/pull/8076)
- Metrics: Fix premature access to RegistryFactory [8118](https://github.com/helidon-io/helidon/pull/8118)
- Metrics: jvm uptime units [8065](https://github.com/helidon-io/helidon/pull/8065)
- Native image: key-utils are missing native image prop [8146](https://github.com/helidon-io/helidon/pull/8146)
- Scheduling: 8059 Delayed fixed rate scheduling [8075](https://github.com/helidon-io/helidon/pull/8075)
- Tracing: Add proper handling of `content-read` and `content-write` logs within `HTTP Request` tracing span [8105](https://github.com/helidon-io/helidon/pull/8105)
- Tracing: MP OpenTelemetry and Helidon Tracing API [8073](https://github.com/helidon-io/helidon/pull/8073)
- Tracing: Several fixes to tracing config [8155](https://github.com/helidon-io/helidon/pull/8155)
- WebClient: #8077 Client keep alive fix [8101](https://github.com/helidon-io/helidon/pull/8101)
- WebServer: Update server's internal state if a listener fails to start [8111](https://github.com/helidon-io/helidon/pull/8111)
- WebSocket: Handle WebSocket frames of longer payload [8134](https://github.com/helidon-io/helidon/pull/8134)
- WebSocket: Make sure a WsListener supplier is called exactly once per connection [8116](https://github.com/helidon-io/helidon/pull/8116)
- Build: Aggregated javadoc and docs URLs updates [8171](https://github.com/helidon-io/helidon/pull/8171)
- Build: Incorporate release branch name in artifact bundle name [8081](https://github.com/helidon-io/helidon/pull/8081)
- Dependencies: 4.x: Upgrade grpc to 1.60.0 [8098](https://github.com/helidon-io/helidon/pull/8098)
- Dependencies: Upgrade slf4j to 2.0.9 and logback to 1.4.14 [8120](https://github.com/helidon-io/helidon/pull/8120)
- Docs: Change doc in the wake of OpenAPI UI service reorg in 4.0 to add the `services` level to the config structure [8100](https://github.com/helidon-io/helidon/pull/8100)
- Docs: Fix health and metrics URLs. Mention health returns 204 [8160](https://github.com/helidon-io/helidon/pull/8160)
- Docs: Remove doc for tags on `content-read` and `content-write` spans; the tags are no longer added by the code in 4.x [8112](https://github.com/helidon-io/helidon/pull/8112)
- Docs: Significant changes and corrections to the SE health doc [8125](https://github.com/helidon-io/helidon/pull/8125)
- Docs: tuning guide update [8140](https://github.com/helidon-io/helidon/pull/8140)
- Examples: Add missing metrics to Pokemons example [8129](https://github.com/helidon-io/helidon/pull/8129)
- Examples: Correct typo in MP health example [8131](https://github.com/helidon-io/helidon/pull/8131)
- Examples: Fix Mongo Dbclient + related example [8130](https://github.com/helidon-io/helidon/pull/8130)
- Examples: Fix and add test to database SE example [8097](https://github.com/helidon-io/helidon/pull/8097)
- Tests: Add @RequestScoped support for testing [7916](https://github.com/helidon-io/helidon/pull/7916)
- Tests: Add Hierarchy tests to MP Telemetry [8089](https://github.com/helidon-io/helidon/pull/8089)
- Tests: Avoid possible test ordering problem [8145](https://github.com/helidon-io/helidon/pull/8145)
- Tests: Disable failing `testNonTransactionalEntityManager` JPA test. [8124](https://github.com/helidon-io/helidon/pull/8124)
- Tests: Enable disabled TCKs [7781](https://github.com/helidon-io/helidon/pull/7781)

## [4.0.1]

This release contains bugfixes and ehancements and is recommended for all users of Helidon 4.

Java 21 is required to use Helidon 4.0.1.

### Notable Changes

- Added PROXY protocol support to Helidon WebServer
- WebServer performance improvements
- `CorsConfig.Builder.enabled()` now returns an `Optional<Boolean>` instead of `boolean`. This change was required to fix a CORS issue and we expect the user impact of this change to be minimal.

### CHANGES

- CORS: Change CorsConfig.enabled to optional and add logic to infer it [8038](https://github.com/helidon-io/helidon/pull/8038)
- Health: Add a new `addCheck` variant allowing the caller to set the health check name; add tests; revise doc [7994](https://github.com/helidon-io/helidon/pull/7994)
- Health: Fix nested config prefix for observer settings [8010](https://github.com/helidon-io/helidon/pull/8010)
- Http2: Graceful client connection close [8051](https://github.com/helidon-io/helidon/pull/8051)
- JPA: Adds a DialectFactory implementation to permit Hibernate to introspect database metadata properly when supporting container-mode JPA [7927](https://github.com/helidon-io/helidon/pull/7927)
- Metrics: Add meter type to JSON metrics metadata output [8057](https://github.com/helidon-io/helidon/pull/8057)
- Metrics: EOF at end of OpenMetrics output [7982](https://github.com/helidon-io/helidon/pull/7982)
- Metrics: Use correct config nodes for metrics settings; when using global meter registry set metrics config correctly [8008](https://github.com/helidon-io/helidon/pull/8008)
- MicroProfile: Update JaxRsService to reset status to 200 when nexting. [8056](https://github.com/helidon-io/helidon/pull/8056)
- OCI: Adds support for Provider-specializing injection points in OciExtension [8006](https://github.com/helidon-io/helidon/pull/8006)
- OpenAPI: Correct errors in how OpenAPI generator config settings are set [7970](https://github.com/helidon-io/helidon/pull/7970)
- WebServer: Fix for access-log feature. [8041](https://github.com/helidon-io/helidon/pull/8041)
- WebServer: GZIP encoder - properly trigger chunked enc [7977](https://github.com/helidon-io/helidon/pull/7977)
- WebServer: Headers on server cannot be set after entity was sent [8042](https://github.com/helidon-io/helidon/pull/8042)
- WebServer: Initial support for proxy protocol V1 and V2 [7829](https://github.com/helidon-io/helidon/pull/7829)
- WebServer: Optimize single provider case [8002](https://github.com/helidon-io/helidon/pull/8002)
- WebServer: Re-enable TCP auto-tuning [7989](https://github.com/helidon-io/helidon/pull/7989)
- WebServer: Updates header type hierarchy to make HeaderValueCached not writeable [8000](https://github.com/helidon-io/helidon/pull/8000)
- WebServer: WS disconnect [7890](https://github.com/helidon-io/helidon/pull/7890)
- WebSocket: Support for longer WebSocket frames [8025](https://github.com/helidon-io/helidon/pull/8025)
- gRPC: Fix repeated pseudo-header field `:status` [7995](https://github.com/helidon-io/helidon/pull/7995)
- native-image: JSON-P native image [8044](https://github.com/helidon-io/helidon/pull/8044)
- Dependencies: Align Websocket API versions with 3.x [7950](https://github.com/helidon-io/helidon/pull/7950)
- Dependencies: Upgrade log4j to 2.21.1 . Use log4j-bom [7900](https://github.com/helidon-io/helidon/pull/7900)
- Dependencies: Upgrade parsson to 1.1.5 [7958](https://github.com/helidon-io/helidon/pull/7958)
- Dependencies: Upgrade to mysql-connector-j 8.2.0 [7988](https://github.com/helidon-io/helidon/pull/7988)
- Dependencies: Upgrades to Tyrus 2.1.4. [7929](https://github.com/helidon-io/helidon/pull/7929)
- Docs: Adds Testing section back to the menu and fix maven coordinates [7992](https://github.com/helidon-io/helidon/pull/7992)
- Docs: Change "Java for cloud" to "Helidon" [8046](https://github.com/helidon-io/helidon/pull/8046)
- Docs: Fix broken link and explicitly state MP uses Helidon WebServer [8043](https://github.com/helidon-io/helidon/pull/8043)
- Docs: Fix telemetry docs [8014](https://github.com/helidon-io/helidon/pull/8014)
- Docs: Fix tracing documentation [7962](https://github.com/helidon-io/helidon/pull/7962)
- Docs: Minor fixes in SE Tracing documentation [7887](https://github.com/helidon-io/helidon/pull/7887)
- Docs: New section describing support for the Proxy Protocol [8007](https://github.com/helidon-io/helidon/pull/8007)
- Docs: SSE doc maven coordinates [7891](https://github.com/helidon-io/helidon/pull/7891)
- Docs: Update Neo4j module to generated config [7997](https://github.com/helidon-io/helidon/pull/7997)
- Docs: Upgrade gradle guide and examples [7910](https://github.com/helidon-io/helidon/pull/7910)
- Docs: WebServer fix req.next typo [8053](https://github.com/helidon-io/helidon/pull/8053)
- Docs: fix Security http basic authentication property [7987](https://github.com/helidon-io/helidon/pull/7987)
- Examples: Archetype - Fix the wrong metrics endpoint for Helidon SE [8020](https://github.com/helidon-io/helidon/pull/8020)
- Examples: Archetype - Generated projects does not contains empty application.yaml [7942](https://github.com/helidon-io/helidon/pull/7942)
- Examples: Corrects a case typo in the custom MP archetype that results in an invalid property being installed on UCP [7924](https://github.com/helidon-io/helidon/pull/7924)
- Examples: Use H2 in-memory in SE archetype [7877](https://github.com/helidon-io/helidon/pull/7877)
- Examples: Use MP OpenTelemetry instead of OpenTracing in archetypes [7993](https://github.com/helidon-io/helidon/pull/7993)
- Examples: archetype fix MP security  [7947](https://github.com/helidon-io/helidon/pull/7947)
- Examples: backport native-image fixes + support -Dnative.image.skip and -Dnative.image.buildStatic [7972](https://github.com/helidon-io/helidon/pull/7972)
- Examples: dockerFiles in examples [7909](https://github.com/helidon-io/helidon/pull/7909)
- Tests: Enable tests that were disabled during renaming to `jakarta` packages work [7949](https://github.com/helidon-io/helidon/pull/7949)

Thanks to @lilac for their contributions.


## [4.0.0]

We are pleased to announce the release of Helidon 4.0.0. The big news in Helidon 4.0.0 is the introduction of Helidon Níma -- a ground up webserver implementation based on JDK Project Loom virtual threads. With Helidon 4 you get the high throughput of a reactive server with the simplicity of thread-per-request style programming.

The Helidon SE API in 4.0.0 has changed significantly from Helidon 3. The use of virtual threads have enabled these APIs to change from asynchronous to blocking. This results in much simpler code that is easier to write, maintain, debug and understand. Existing Helidon SE code will require modification to run on these new APIs. For more information see the [Helidon SE Upgrade Guide](https://helidon.io/docs/v4/#/se/guides/upgrade_4x).

Helidon 4 supports MicroProfile 6. This means your existing Helidon MP 3.x applications will run on Helidon 4 with only minor modifications. And since Helidon’s MicroProfile server is based on the new Níma WebServer you get all the benefits of running on virtual threads. For more information see the [Helidon MP Upgrade Guide](https://helidon.io/docs/v4/#/mp/guides/upgrade_4x).

New to Helidon? Then jump in and [get started](https://helidon.io/docs/v4/#/about/prerequisites).

Java 21 is required to use Helidon 4.0.0.

### CHANGES

- DBClient: Fix DbClient JSON mapping [7844](https://github.com/helidon-io/helidon/pull/7844)
- DBClient: UriQueryEmpty + NoSuchElementException [7869](https://github.com/helidon-io/helidon/pull/7869)
- Http: Remove commented-out code [7868](https://github.com/helidon-io/helidon/pull/7868)
- Metrics: Restore percentile and bucket data to JSON metrics output [7849](https://github.com/helidon-io/helidon/pull/7849)
- Build: remove Jenkinsfile [7839](https://github.com/helidon-io/helidon/pull/7839)
- Dependencies: Update build-tools to 4.0.0 [7872](https://github.com/helidon-io/helidon/pull/7872)
- Dependencies: Upgrade kafka-clients to 3.6.0 [7833](https://github.com/helidon-io/helidon/pull/7833)
- Docs: AOT update, remove aot site, update guides for native image [7859](https://github.com/helidon-io/helidon/pull/7859)
- Docs: Config documentation update [7814](https://github.com/helidon-io/helidon/pull/7814)
- Docs: Final updates intros [7864](https://github.com/helidon-io/helidon/pull/7864)
- Docs: Health doc updates [7828](https://github.com/helidon-io/helidon/pull/7828)
- Docs: JWT Auth configuration properties updated [7816](https://github.com/helidon-io/helidon/pull/7816)
- Docs: Messaging doc update [7837](https://github.com/helidon-io/helidon/pull/7837)
- Docs: Metrics doc update [7851](https://github.com/helidon-io/helidon/pull/7851)
- Docs: Migration guides [7715](https://github.com/helidon-io/helidon/pull/7715)
- Docs: Observability endpoints documentation [7768](https://github.com/helidon-io/helidon/pull/7768)
- Docs: Refreshes persistence.adoc for Helidon 4.x [7834](https://github.com/helidon-io/helidon/pull/7834)
- Docs: Tracing documentation [7813](https://github.com/helidon-io/helidon/pull/7813)
- Docs: Update dbclient docs [7874](https://github.com/helidon-io/helidon/pull/7874)
- Docs: Update Helidon Webclient Guide documentation [7847](https://github.com/helidon-io/helidon/pull/7847)
- Docs: Update to WebServer documentation for Helidon 4 [7817](https://github.com/helidon-io/helidon/pull/7817)
- Docs: Update to gRPC documentation for Helidon 4 [7809](https://github.com/helidon-io/helidon/pull/7809)
- Docs: Update webclient documentation [7812](https://github.com/helidon-io/helidon/pull/7812)
- Docs: Updated OpenAPI and OpenAPI UI doc for 4.0 [7823](https://github.com/helidon-io/helidon/pull/7823)
- Docs: Updates for MP upgrade guide [7863](https://github.com/helidon-io/helidon/pull/7863)
- Docs: fix github links, microprofile version, SE upgrade guide [7871](https://github.com/helidon-io/helidon/pull/7871)
- Docs: json metadata fix [7858](https://github.com/helidon-io/helidon/pull/7858)
- Docs: webclient doc part2 4.x [7845](https://github.com/helidon-io/helidon/pull/7845)
- Examples: Archetype fix application yaml test and some unused import [7860](https://github.com/helidon-io/helidon/pull/7860)
- Examples: Fix Archetype native-image.properties path [7846](https://github.com/helidon-io/helidon/pull/7846)
- Examples: Fix MP quickstart native image build [7826](https://github.com/helidon-io/helidon/pull/7826)
- Examples: Fix java.lang.ClassNotFoundException: io.helidon.logging.jul.HelidonConsoleHandler[7866](https://github.com/helidon-io/helidon/pull/7866)
- Examples: Update archetypes and dbclient examples [7873](https://github.com/helidon-io/helidon/pull/7873)
- Tests: Removes two disabled tests in tests/integration/security/path-params per direction [7832](https://github.com/helidon-io/helidon/pull/7832)

## [4.0.0-RC2]

This is the second RC build of Helidon 4.0.0 and is intended as a preview release only. Do not use this release in production. It is suitable only for experimentation. APIs are subject to change. Documentation is incomplete. And some functionality is experimental.

The big news in Helidon 4.0.0 is the introduction of Helidon Níma -- a ground up webserver implementation based on JDK Project Loom virtual threads. With Helidon 4 you get the high throughput of a reactive server with the simplicity of thread-per-request style programming.

Java 21 is required to use Helidon 4.0.0-RC2.

### CHANGES

- Config: Replace usage of Priority with Weight in helidon-config [7776](https://github.com/helidon-io/helidon/pull/7776)
- JPA: Fixes a jar-only URL resolution bug [7748](https://github.com/helidon-io/helidon/pull/7748)
- JPA: Remove exclusion of jandex dependency now that hibernate has been upgraded [7778](https://github.com/helidon-io/helidon/pull/7778)
- JPA: Switches default JPA CDI portable extension to PersistenceExtension from JpaExtension [7719](https://github.com/helidon-io/helidon/pull/7719)
- Logging: Fix SLF4J binding for annotation processors [7706](https://github.com/helidon-io/helidon/pull/7706)
- Metrics: Reinstate exemplar support [7760](https://github.com/helidon-io/helidon/pull/7760)
- Native-image: Adjusts paths to native-image.properties files in two projects to conform to the native-maven-plugin requirements as reported by the plugin [7746](https://github.com/helidon-io/helidon/pull/7746)
- Native-image: Native image update [7711](https://github.com/helidon-io/helidon/pull/7711)
- OpenAPI: Add openapi-ui submodule directory to the integrations module list [7766](https://github.com/helidon-io/helidon/pull/7766)
- OpenAPI: Renamed openapi/openapi-ui back to integrations/openapi-ui [7761](https://github.com/helidon-io/helidon/pull/7761)
- Remove preview from features that are now considered production [7688](https://github.com/helidon-io/helidon/pull/7688)
- TLS: Partial fix for issue #7698 - TlsManager support for client-side [7699](https://github.com/helidon-io/helidon/pull/7699)
- Tracing: Decrease DEFAULT_SCHEDULE_DELAY time for JaegerTracerBuilder [7726](https://github.com/helidon-io/helidon/pull/7726)
- Tracing: Migrate opentracing to Helidon Tracing [7708](https://github.com/helidon-io/helidon/pull/7708)
- WebServer: Empty path [7770](https://github.com/helidon-io/helidon/pull/7770)
- WebServer: Fix small mistakes [7757](https://github.com/helidon-io/helidon/pull/7757)
- WebServer: HTTP2 concurrent streams check [7697](https://github.com/helidon-io/helidon/pull/7697)
- WebServer: Move SSL handshake logic from listener thread to connection thread [7764](https://github.com/helidon-io/helidon/pull/7764)
- WebServer: Update HttpRules API to not have varargs with generics [7687](https://github.com/helidon-io/helidon/pull/7687)
- WebServer: named routing [7705](https://github.com/helidon-io/helidon/pull/7705)
- WebServer: Server Features [7777](https://github.com/helidon-io/helidon/pull/7777)
- Build: Fix build noise [7740](https://github.com/helidon-io/helidon/pull/7740)
- Build: Java 21 follow up [7732](https://github.com/helidon-io/helidon/pull/7732)
- Build: release updates [7682](https://github.com/helidon-io/helidon/pull/7682)
- Dependencies: Upgrade ASM version used by  plugins to 9.5 [7677](https://github.com/helidon-io/helidon/pull/7677)
- Dependencies: Upgrade Weld and ClassFileWriter [7720](https://github.com/helidon-io/helidon/pull/7720)
- Dependencies: Upgrade okhttp3. Use OCI SDK BOM [7712](https://github.com/helidon-io/helidon/pull/7712)
- Dependencies: Upgrade parsson to 1.1.3 [7691](https://github.com/helidon-io/helidon/pull/7691)
- Dependencies: Upgrade to Microstream 08.01.01-MS-GA and other minor things [7752](https://github.com/helidon-io/helidon/pull/7752)
- Dependencies: Upgrades Hibernate to version 6.3.1.Final [7742](https://github.com/helidon-io/helidon/pull/7742)
- Dependencies: Upgrades ojdbc8 to 21.4.0.0 and fixes JAXP parser conflict [7762](https://github.com/helidon-io/helidon/pull/7762)
- Deprecations: Remove older deprecated methods and types. [7728](https://github.com/helidon-io/helidon/pull/7728)
- Docs: Add Helidon Connector to sitegen.yaml [7767](https://github.com/helidon-io/helidon/pull/7767)
- Docs: global config [7681](https://github.com/helidon-io/helidon/pull/7681)
- Docs: Fix occurences of reactive in the docs [7684](https://github.com/helidon-io/helidon/pull/7684)
- Docs: Fixes user-reported typo in persistence guide [7750](https://github.com/helidon-io/helidon/pull/7750)
- Docs: Initial documentation for the new Helidon connector. [7641](https://github.com/helidon-io/helidon/pull/7641)
- Docs: Makes it more clear in documentation that persistence.xml files are application-level concerns, not component-level concerns [7771](https://github.com/helidon-io/helidon/pull/7771)
- Docs: Reactive streams doc alignment #6458 [7723](https://github.com/helidon-io/helidon/pull/7723)
- Docs: Update maven, gradle, jlink, native-image guides [7704](https://github.com/helidon-io/helidon/pull/7704)
- Docs: updates to the general doc [7673](https://github.com/helidon-io/helidon/pull/7673)
- Examples: Fix several issue in archetype and add `native-image.properties` to generated projects [7731](https://github.com/helidon-io/helidon/pull/7731)
- Examples: Remove unnecessary metrics dependencies MP quickstart archetype  [7710](https://github.com/helidon-io/helidon/pull/7710)
- Examples: Update quickstart examples to use microprofile-core bundle [7772](https://github.com/helidon-io/helidon/pull/7772)
- Examples: Verify Starter with 4.x archetypes [7775](https://github.com/helidon-io/helidon/pull/7775)
- Tests: Move webserver/benchmark/jmh to tests/benchmark/jmh [7690](https://github.com/helidon-io/helidon/pull/7690)
- Tests: Temporarily disable unit test in example due to intermittent pipeline failures [7765](https://github.com/helidon-io/helidon/pull/7765)

## [4.0.0-RC1]

This is the first RC build of Helidon 4.0.0 and is intended as a preview release only. Do not use this release in production. It is suitable only for experimentation. APIs are subject to change. Documentation is incomplete. And some functionality is experimental.

The big news in Helidon 4.0.0 is the introduction of Helidon Nima -- a ground up webserver implementation based on JDK Project Loom virtual threads. With Helidon 4 you get the high throughput of a reactive server with the simplicity of thread-per-request style programming.

Java 21 is required to use Helidon 4.0.0-RC1.

Notable Changes

* Major refactoring of APIs is complete.
* HTTP/2 support complete and other WebServer and WebClient features complete.

### CHANGES

- CORS: Use requested URI info in CORS decision-making [7585](https://github.com/helidon-io/helidon/pull/7585)
- Config: Deprecate support for etcd v2 [7623](https://github.com/helidon-io/helidon/pull/7623)
- DBClient: Fixed DbClient H2 tests. [7639](https://github.com/helidon-io/helidon/pull/7639)
- Inject: Mark inject processor as preview (warning when executed). [7590](https://github.com/helidon-io/helidon/pull/7590)
- Integrations: Removes Jedis integration to reduce technical debt [7654](https://github.com/helidon-io/helidon/pull/7654)
- JPA: Adds zero-argument non-private constructors to NonTransactionalEntityManager and ExtendedEntityManager [7561](https://github.com/helidon-io/helidon/pull/7561)
- Metrics: Make SE metrics default scope application instead of nothing [7666](https://github.com/helidon-io/helidon/pull/7666)
- Metrics: Metrics followup [7547](https://github.com/helidon-io/helidon/pull/7547)
- OCI: Adjusts CDI OciExtension to use runtime OciExtension for certain authentication tasks [7373](https://github.com/helidon-io/helidon/pull/7373)
- OCI: Introduces OciSecretsMpMetaConfigProvider, which adapts OciSecretsConfigSourceProvider to the MpMetaConfigProvider contract [7520](https://github.com/helidon-io/helidon/pull/7520)
- OCI: OciExtension refinements [7563](https://github.com/helidon-io/helidon/pull/7563)
- Observability: metrics and openapi endpoints should be authorized by default [7572](https://github.com/helidon-io/helidon/pull/7572)
- Observability: update APIs [7625](https://github.com/helidon-io/helidon/pull/7625)
- OpenAPI: OpenAPI updates [7669](https://github.com/helidon-io/helidon/pull/7669)
- RestClient: Modifies Helidon Connector to use WebClient and also support HTTP/2 [7582](https://github.com/helidon-io/helidon/pull/7582)
- Security: TargetKeys Map changed to ConcurrentHashMap [7603](https://github.com/helidon-io/helidon/pull/7603)
- Tracing: Move Opentracing to Helidon Tracing API [7678](https://github.com/helidon-io/helidon/pull/7678)
- WebClient: HTTP/2 Client 100 continue [7604](https://github.com/helidon-io/helidon/pull/7604)
- WebClient: HTTP/2.0 Client trailers support #6544 [7516](https://github.com/helidon-io/helidon/pull/7516)
- WebClient: Remove prefetch #7663 [7676](https://github.com/helidon-io/helidon/pull/7676)
- WebClient: double-slash URI issue #7474 [7657](https://github.com/helidon-io/helidon/pull/7657)
- WebClient: Http2 OutputStream redirect support [7637](https://github.com/helidon-io/helidon/pull/7637)
- WebServer: HTTP/2 server 100-continue [7633](https://github.com/helidon-io/helidon/pull/7633)
- WebServer: Introducing request and response stream filters to server. [7608](https://github.com/helidon-io/helidon/pull/7608)
- WebServer: Refactor Http class [7570](https://github.com/helidon-io/helidon/pull/7570)
- WebServer: Server side trailers #7647 [7649](https://github.com/helidon-io/helidon/pull/7649)
- Build: Checkstyle suppression in code [7588](https://github.com/helidon-io/helidon/pull/7588)
- Build: Update workflows to Oracle JDK 21 LTS [7653](https://github.com/helidon-io/helidon/pull/7653)
- Build: release workflow [7569](https://github.com/helidon-io/helidon/pull/7569)
- Dependencies: Updates version of Micronaut libraries [7553](https://github.com/helidon-io/helidon/pull/7553)
- Dependencies: Upgrade Neo4j to v.5 [7636](https://github.com/helidon-io/helidon/pull/7636)
- Dependencies: Upgrade io.dropwizard.metrics:metrics-core to 4.1.36 [7624](https://github.com/helidon-io/helidon/pull/7624)
- Dependencies: Upgrade jboss logging to 3.5.3.Final [7595](https://github.com/helidon-io/helidon/pull/7595)
- Dependencies: Upgrade jgit to 6.7.0 [7586](https://github.com/helidon-io/helidon/pull/7586)
- Dependencies: Upgrade mongodb driver to 4.10.2 [7651](https://github.com/helidon-io/helidon/pull/7651)
- Dependencies: Upgrade zipkin-sender-urlconnection to 2.16.4 [7621](https://github.com/helidon-io/helidon/pull/7621)
- Dependencies: Upgrades Narayana to version 7.0.0.Final [7662](https://github.com/helidon-io/helidon/pull/7662)
- Docs: 4x overview and general intro doc updates [7571](https://github.com/helidon-io/helidon/pull/7571)
- Docs: Config metadata docs [7581](https://github.com/helidon-io/helidon/pull/7581)
- Docs: SE Diffs 3.x to Main .adoc  [7515](https://github.com/helidon-io/helidon/pull/7515)
- Examples: Move archetype metrics under `GreetService` [7612](https://github.com/helidon-io/helidon/pull/7612)
- Examples: Refactor archetype metrics [7556](https://github.com/helidon-io/helidon/pull/7556)
- Examples: Update examples to use Config.global(config) [7655](https://github.com/helidon-io/helidon/pull/7655)
- Examples: demonstrate proper use of GlobalConfig in archetypes [7664](https://github.com/helidon-io/helidon/pull/7664)
- Examples: fix archetypes code formatting [7670](https://github.com/helidon-io/helidon/pull/7670)
- Tests: Allowed cipher suite test added [7587](https://github.com/helidon-io/helidon/pull/7587)
- Tests: Fix MutualTlsTest and related issues [7622](https://github.com/helidon-io/helidon/pull/7622)
- Tests: Intermittent OciCertificatesTlsManagerTest fix [7607](https://github.com/helidon-io/helidon/pull/7607)
- Tests: MP testing refactoring [7548](https://github.com/helidon-io/helidon/pull/7548)
- Tests: Re-enables microprofile/server/ServerSseTest.java [7626](https://github.com/helidon-io/helidon/pull/7626)
- Tests: Restore test of model reader and filter [7579](https://github.com/helidon-io/helidon/pull/7579)


## [4.0.0-M2]


This is the second Milestone build of Helidon 4.0.0 and is intended as a preview release only. Do not use this release in production. It is suitable only for experimentation. APIs are subject to change. Documentation is incomplete. And some functionality is experimental.

The big news in Helidon 4.0.0 is the introduction of Helidon Nima -- a ground up webserver implementation based on JDK Project Loom virtual threads. With Helidon 4 you get the high throughput of a reactive server with the simplicity of thread-per-request style programming.

Java 21 is required to use Helidon 4.0.0-M2.

Any easy way to get started with Helidon 4.0.0-M2 is using the helidon CLI

helidon init --version 4.0.0-M2

Notable Changes

* Java 21 is required for 4.0.0-M2
* Finish integration of Níma WebServer into Helidon 4. This means that the `nima` Java package has been assimilated into the base Helidon packages. 
* Numerous enhancements to WebServer and WebClient to achieve feature parity with Helidon 3

The above is work-in-progress. There are still some gaps, and APIs are subject to change. 

### CHANGES

- WebSocket: Added support for WS endpoints in application scope [7340](https://github.com/helidon-io/helidon/pull/7340)
- WebServer: Support Http.Header.X_HELIDON_CN [7345](https://github.com/helidon-io/helidon/pull/7345)
- WebServer: Resource limits [7302](https://github.com/helidon-io/helidon/pull/7302)
- WebServer: Media Context and streaming [7396](https://github.com/helidon-io/helidon/pull/7396)
- WebServer: Http2Connection should ignore a max concurrent streams setting of zero from the client [7346](https://github.com/helidon-io/helidon/pull/7346)
- WebServer: Additional HTTP status codes. [7408](https://github.com/helidon-io/helidon/pull/7408)
- WebServer: Add configuration support for EXECUTOR_SHUTDOWN_MILLIS [6955](https://github.com/helidon-io/helidon/pull/6955)
- WebClient: include proxy setting for relative uris  4.x [7425](https://github.com/helidon-io/helidon/pull/7425)
- WebClient: Webclient redesign [7255](https://github.com/helidon-io/helidon/pull/7255)
- WebClient: WebClient Follow Up [7341](https://github.com/helidon-io/helidon/pull/7341)
- WebClient: Support for proxy config properties in client and request [7190](https://github.com/helidon-io/helidon/pull/7190)
- WebClient: Output stream redirect support [7366](https://github.com/helidon-io/helidon/pull/7366)
- WebClient: HTTP Proxy TODOs [7287](https://github.com/helidon-io/helidon/pull/7287)
- WebClient: Fixed ClientUri to extract query params when created from a URI [7297](https://github.com/helidon-io/helidon/pull/7297)
- WebClient: Create a different config setting for inbound and outbound HTTP1 header [7362](https://github.com/helidon-io/helidon/pull/7362)
- WebClient: ConcurrentModificationException in Http2ClientConnection.writeWindowsUpdate [7395](https://github.com/helidon-io/helidon/pull/7395)
- WebClient: Change default to SYSTEM proxy in WebClient [7292](https://github.com/helidon-io/helidon/pull/7292)
- WebClient: Cached connection close detection [7398](https://github.com/helidon-io/helidon/pull/7398)
- WebClient: 7301 WebClient - Local connection cache switch [7353](https://github.com/helidon-io/helidon/pull/7353)
- Uplevel Nima [7361](https://github.com/helidon-io/helidon/pull/7361)
- Tracing: Refactor to Tracing providers [7264](https://github.com/helidon-io/helidon/pull/7264)
- Serialization: Make sure JEP-290 is enforced in Níma [7334](https://github.com/helidon-io/helidon/pull/7334)
- Security: Unified constants for configuring outbound id and secret. [7415](https://github.com/helidon-io/helidon/pull/7415)
- Security: Tenant now uses WebClientSecurity module [7394](https://github.com/helidon-io/helidon/pull/7394)
- Security: Make check for audience claim in access token optional in OIDC provider [6959](https://github.com/helidon-io/helidon/pull/6959)
- OCI: Global Config Source [7352](https://github.com/helidon-io/helidon/pull/7352)
- OCI: Follow-up items for OCI Global Config [7387](https://github.com/helidon-io/helidon/pull/7387)
- OCI: Enables OIDC integration tests and fixes a couple of problems in WebClient [7390](https://github.com/helidon-io/helidon/pull/7390)
- OCI: Adds OciSecretsConfigSourceProvider.java [7391](https://github.com/helidon-io/helidon/pull/7391)
- Media: Header and Media type API consistency [7351](https://github.com/helidon-io/helidon/pull/7351)
- Inject: LoomServer parallel listener start [7200](https://github.com/helidon-io/helidon/pull/7200)
- Inject: Interceptor creator now uses TypeName (and related changes) [7420](https://github.com/helidon-io/helidon/pull/7420)
- gRPC: Support creating routes from standard gRPC  bindable services [7384](https://github.com/helidon-io/helidon/pull/7384)
- gRPC: Fix gRPC to calculate the correct class name for method request and response types [7228](https://github.com/helidon-io/helidon/pull/7228)
- DBClient: Issue #7187 - Blocking DBClient Part 2 [7231](https://github.com/helidon-io/helidon/pull/7231)
- DBClient: #7230 - JDBC type specific setters based on EclipseLink [7246](https://github.com/helidon-io/helidon/pull/7246)
- Config: Use common config [7336](https://github.com/helidon-io/helidon/pull/7336)
- Common: Using SE flavor instead of Nima [7338](https://github.com/helidon-io/helidon/pull/7338)
- Common: Remove public constructors from production types. [7335](https://github.com/helidon-io/helidon/pull/7335)
- Common: Remove non-virtual executor support [7324](https://github.com/helidon-io/helidon/pull/7324)
- Common: Remove VirtualExecutorUtil.java [7263](https://github.com/helidon-io/helidon/pull/7263)
- Common: Encode all characters that should be encoded in URI, including % itself. [7314](https://github.com/helidon-io/helidon/pull/7314)
- Builders: Support Supplier<X> in builder setters for types that have a builder. [7284](https://github.com/helidon-io/helidon/pull/7284)
- Builders: Supplier in builder [7402](https://github.com/helidon-io/helidon/pull/7402)
- Builders: Provider support in builders [7365](https://github.com/helidon-io/helidon/pull/7365)
- Builders: Prototype builder update [7281](https://github.com/helidon-io/helidon/pull/7281)
- Builders: Fix references to interceptor. Now using decorator for builders. [7405](https://github.com/helidon-io/helidon/pull/7405)
- Builders: Class model and builder generation reworked [7256](https://github.com/helidon-io/helidon/pull/7256)
- Builders: Allowed values are now checked when validating a builder. [7400](https://github.com/helidon-io/helidon/pull/7400)
- Tests: TCK Tracking: Jakarta EE 10 Core Profile #6799 [6885](https://github.com/helidon-io/helidon/pull/6885)
- Tests: Make OciMetricsDataTest.beforeEach public [7333](https://github.com/helidon-io/helidon/pull/7333)
- Tests: Fix tests disabled during WebClient redesign #7286 [7299](https://github.com/helidon-io/helidon/pull/7299)
- Tests: Fix intermittently failing test. [7379](https://github.com/helidon-io/helidon/pull/7379)
- Test: Helidon Arquillian module should only depend on MP core [7440](https://github.com/helidon-io/helidon/pull/7440)
- Examples: Enable disabled pokemon jpa/hibernate example test [7436](https://github.com/helidon-io/helidon/pull/7436)
- Examples: Archetype: remove unused file [7253](https://github.com/helidon-io/helidon/pull/7253)
- Examples: Archetype: Feature parity with 3.x + renaming from `nima` to `se` [7409](https://github.com/helidon-io/helidon/pull/7409)
- Examples: Archetype : generate module-info file [7232](https://github.com/helidon-io/helidon/pull/7232)
- Examples: Add app.yaml to quickstart [7217](https://github.com/helidon-io/helidon/pull/7217)
- Docs: javadocs: update external cross reference links and offline package-lists [7401](https://github.com/helidon-io/helidon/pull/7401)
- Docs: Update prereqs in Helidon Injection README [7326](https://github.com/helidon-io/helidon/pull/7326)
- Docs: Update development guidelines to include naming rules for builder types. [7148](https://github.com/helidon-io/helidon/pull/7148)
- Docs: PR replaces previous Nima removal PR [7347](https://github.com/helidon-io/helidon/pull/7347)
- Docs: Fix openapi links [7431](https://github.com/helidon-io/helidon/pull/7431)
- Docs: Add white paper to README.MD [7378](https://github.com/helidon-io/helidon/pull/7378)
- Dependencies: upgrade microprofile-openapi-api to 3.1.1 and add direct dependency [7453](https://github.com/helidon-io/helidon/pull/7453)
- Dependencies: Upgrade to OCI SDK 3.12.1 (#7163) [7211](https://github.com/helidon-io/helidon/pull/7211)
- Dependencies: Upgrade okio and oci-sdk [7262](https://github.com/helidon-io/helidon/pull/7262)
- Dependencies: Upgrade microprofile-health to 4.0.1 and microprofile-lra-api to 2.0 [7454](https://github.com/helidon-io/helidon/pull/7454)
- Dependencies: Upgrade microprofile config to 3.0.3 [7434](https://github.com/helidon-io/helidon/pull/7434)
- Dependencies: Upgrade grpc-java to 1.57.1 and remove repackaging of io.grpc [7304](https://github.com/helidon-io/helidon/pull/7304)
- Dependencies: Upgrade eclipselink to 4.0.2 [7435](https://github.com/helidon-io/helidon/pull/7435)
- Dependencies: Upgrade Jersey to 3.1.3 [7258](https://github.com/helidon-io/helidon/pull/7258)
- Dependencies: JMS bumpup 3.0 -> 3.1 [7380](https://github.com/helidon-io/helidon/pull/7380)
- Dependencies: Force upgrade of bytebuddy to 1.14.6 to support Java 21. [7438](https://github.com/helidon-io/helidon/pull/7438)
- Build: remove license headers in archetype generated files [7236](https://github.com/helidon-io/helidon/pull/7236)
- Build: Use Helidon copyright module for generated code [7192](https://github.com/helidon-io/helidon/pull/7192)
- Build: Java 21 [7222](https://github.com/helidon-io/helidon/pull/7222)
- Build: Integrate build tools 4.0.0-M1 [7330](https://github.com/helidon-io/helidon/pull/7330)
- Build: Address maven 3.9.2 plugin issues [7214](https://github.com/helidon-io/helidon/pull/7214)
- Build: Add -proc:full to javac to explicitly enable annotation processing [7452](https://github.com/helidon-io/helidon/pull/7452)


## [4.0.0-M1]

This is the first Milestone build of Helidon 4.0.0 and is intended as a preview release only. Do not use this release in production. It is suitable only for experimentation. APIs are subject to change. Documentation is incomplete. And some functionality is experimental and not fully tested.

The big news in Helidon 4.0.0 is the introduction of Helidon Nima -- a [ground up webserver implementation based on JDK Project Loom virtual threads](https://medium.com/helidon/helidon-n%C3%ADma-helidon-on-virtual-threads-130bb2ea2088). 

Java 20 is required to use Heldon 4.0.0-M1.

Any easy way to get started with Helidon 4.0.0-M1 is using the [helidon CLI](https://github.com/helidon-io/helidon#helidon-cli)

```
helidon init --version 4.0.0-M1
```

### Notable Changes

- Removed the Helidon Reactive WebServer and WebClient that were based on Netty as we fully commit to new implementations based on virtual threads that have a blocking style API (Project Níma).
- Converted other modules with reactive APIs to blocking style APIs. The `io.helidon.common.reactive` APIs will stay as general purpose reactive utilities and operators
- Introduced Helidon Injection, a deterministic, source-code-first, compile time injection framework
- Upgraded MicroProfile support to MicroProfile 6 and Jakarta 10 Core Profile running on the Níma WebServer
- Started adoption of Helidon Builders, a builder code generation framework

The above is work-in-progress. There are still missing features, and APIs are undergoing change. For example
the Grpc implementation is limited and MicroProfile Grpc support is temporarily absent.


### CHANGES

- Config: Config with injection [7080](https://github.com/helidon-io/helidon/pull/7080)
- Config: Generate config metadata fix. [7145](https://github.com/helidon-io/helidon/pull/7145)
- GraphQL: Update GraphQLServiceTest to use @ServerTest and Http1Client [6795](https://github.com/helidon-io/helidon/pull/6795)
- Grpc: Support for grpc proto packages. [7158](https://github.com/helidon-io/helidon/pull/7158)
- Inject: All Pico services should default to a lesser than DEFAULT_WEIGHT [6590](https://github.com/helidon-io/helidon/pull/6590)
- Inject: Allow empty list injection [7160](https://github.com/helidon-io/helidon/pull/7160)
- Inject: Annotation for Named that accepts a class [6779](https://github.com/helidon-io/helidon/pull/6779)
- Inject: Attempt to do type regeneration turned into an error condition instead of being logged [7155](https://github.com/helidon-io/helidon/pull/7155)
- Inject: Constructors are now intercepted with appropriate interceptors only. [6648](https://github.com/helidon-io/helidon/pull/6648)
- Inject: Custom Annotation Processor Refinements [6883](https://github.com/helidon-io/helidon/pull/6883)
- Inject: Fix pico lookup method returning <T> from generic criteria [6582](https://github.com/helidon-io/helidon/pull/6582)
- Inject: Fixes incremental compilation for Pico services and running unit tests from IDE [6863](https://github.com/helidon-io/helidon/pull/6863)
- Inject: Inheritance in DefaulQualifierAndValue [6639](https://github.com/helidon-io/helidon/pull/6639)
- Inject: Interceptor Redo [6658](https://github.com/helidon-io/helidon/pull/6658)
- Inject: Issue 6634 fix - interceptor args [6640](https://github.com/helidon-io/helidon/pull/6640)
- Inject: Pico ConfiguredBy services with abstract base [6589](https://github.com/helidon-io/helidon/pull/6589)
- Inject: Pico anno processor rewrite [6705](https://github.com/helidon-io/helidon/pull/6705)
- Inject: Pico interceptor arguments are now correctly handled. [6642](https://github.com/helidon-io/helidon/pull/6642)
- Inject: Preparation for Pico Extensibility Enhancements [6936](https://github.com/helidon-io/helidon/pull/6936)
- Inject: Rename Pico -> Inject / Injection [7174](https://github.com/helidon-io/helidon/pull/7174) [6682](https://github.com/helidon-io/helidon/pull/6682)
- Inject: TypeInfo and TypedElementName API name tuning [6841](https://github.com/helidon-io/helidon/pull/6841)
- Inject: module-info parsing [7156](https://github.com/helidon-io/helidon/pull/7156)
- LRA: Fix LRA Logging [6734](https://github.com/helidon-io/helidon/pull/6734)
- LRA: LRA coordinator docker fix [6727](https://github.com/helidon-io/helidon/pull/6727)
- LRA: expunge reactive webclient [7112](https://github.com/helidon-io/helidon/pull/7112)
- MP Config: convertToHelidon exception fix [6834](https://github.com/helidon-io/helidon/pull/6834)
- MP Metrics: MP Metrics 5.0 support for 4.x [7139](https://github.com/helidon-io/helidon/pull/7139)
- MP RestClient: Helidon connector configuration and redirects [7169](https://github.com/helidon-io/helidon/pull/7169)
- MP RestClient: Initial implementation of Helidon connector for Jakarta REST client [7039](https://github.com/helidon-io/helidon/pull/7039)
- MP Server: Support for injection of ServerRequest and ServerResponse also via CDI [6784](https://github.com/helidon-io/helidon/pull/6784)
- MP Server: Helidon main class [7136](https://github.com/helidon-io/helidon/pull/7136)
- MP Telemetry: MicroProfile Telemetry Support [6493](https://github.com/helidon-io/helidon/pull/6493)
- MP Telemetry: cleanup dependencies [6815](https://github.com/helidon-io/helidon/pull/6815)
- Media: Fix TODO to handle close call on entity [6501](https://github.com/helidon-io/helidon/pull/6501)
- Media: Move SnakeYAML log suppression to slightly earlier in the code path [6661](https://github.com/helidon-io/helidon/pull/6661)
- Messaging: WLS JMS connector doesn't support named factory bean [6923](https://github.com/helidon-io/helidon/pull/6923)
- Metrics: Convert PrometheusSupport from a reactive WebServer Service to a HelidonFeatureSupport [6837](https://github.com/helidon-io/helidon/pull/6837)
- Metrics: Fix path handling for metrics observe provider [7178](https://github.com/helidon-io/helidon/pull/7178)
- Metrics: MicroMeter contains Nima and SE integration [7090](https://github.com/helidon-io/helidon/pull/7090)
- Neo4j: Alternative implementation of Neo4jHealthCheck based on io.helidon.heath.HealthCheck [6850](https://github.com/helidon-io/helidon/pull/6850)
- Neo4j: Nima support [6909](https://github.com/helidon-io/helidon/pull/6909)
- OCI: Pico extensibility support for the OCI SDK [6982](https://github.com/helidon-io/helidon/pull/6982)
- OpenAPI 3.1 support for 4.x [6954](https://github.com/helidon-io/helidon/pull/6954)
- OpenAPI: OpenApiFeature instead of SeOpenApiFeature [7103](https://github.com/helidon-io/helidon/pull/7103)
- Reactive removal: Module helidon-reactive-webclient-jaxrs is no longer needed [6816](https://github.com/helidon-io/helidon/pull/6816)
- Reactive removal: Remove grpc and MP grpc based on Netty grpc from Helidon [7069](https://github.com/helidon-io/helidon/pull/7069)
- Reactive removal: common/rest and vault [7033](https://github.com/helidon-io/helidon/pull/7033)
- Reactive removal: Remove reactive module and netty and any additional cleanup [7165](https://github.com/helidon-io/helidon/issues/7165)
- Security: rework to synchronous [6230](https://github.com/helidon-io/helidon/pull/6230)
- Telemetry: Remove Microprofile dependencies from SE Telemetry [6998](https://github.com/helidon-io/helidon/pull/6998)
- Tracing: Add Baggage to Helidon Span [6581](https://github.com/helidon-io/helidon/pull/6581)
- Tracing: Support for different propagators for Jaeger OpenTelemetry integration. [6586](https://github.com/helidon-io/helidon/pull/6586)
- WebClient: Add header and config builder method to  webclient builder [7056](https://github.com/helidon-io/helidon/pull/7056)
- WebClient: Add relativeUris configuration to determine if relative or absolute URI will be used in the request [6952](https://github.com/helidon-io/helidon/pull/6952)
- WebClient: Add static factory methods to Http1Client [7119](https://github.com/helidon-io/helidon/pull/7119)
- WebClient: Additional methods for Http1ClientRequest  [6983](https://github.com/helidon-io/helidon/pull/6983)
- WebClient: Keep alive is now configurable per request [7122](https://github.com/helidon-io/helidon/pull/7122)
- WebClient: Shortcuts for media support [6951](https://github.com/helidon-io/helidon/pull/6951)
- WebClient: Skip uri encoding feature [6910](https://github.com/helidon-io/helidon/pull/6910)
- WebClient: Support for lastEndpointUri() in Webclient responses [7113](https://github.com/helidon-io/helidon/pull/7113)
- WebClient: Use current context instead of creating new one in ClientRequest [7014](https://github.com/helidon-io/helidon/pull/7014)
- WebClient: Validate http request and response headers when using the webclient [6515](https://github.com/helidon-io/helidon/pull/6515)
- WebClient: WebClient Proxy Support [6441](https://github.com/helidon-io/helidon/pull/6441)
- WebClient: configuration for timeouts and keepAlive [6971](https://github.com/helidon-io/helidon/pull/6971)
- WebClient: read timeout per request [7135](https://github.com/helidon-io/helidon/pull/7135)
- WebClient: redirect support [6929](https://github.com/helidon-io/helidon/pull/6929)
- WebClient: security propagation module [7109](https://github.com/helidon-io/helidon/pull/7109)
- WebClient: services for Níma client (HTTP/1 only for now). [6752](https://github.com/helidon-io/helidon/pull/6752)
- WebClient: should have a mode that is resilient to bad media/content types [6999](https://github.com/helidon-io/helidon/pull/6999)
- WebClient: support for properties [7028](https://github.com/helidon-io/helidon/pull/7028)
- WebServer: Avoid reflecting back user data in exception messages [6990](https://github.com/helidon-io/helidon/pull/6990)
- WebServer: Convenient method status(int) in ServerResponse.  [6833](https://github.com/helidon-io/helidon/pull/6833)
- WebServer: HTTP/2 header continuation [6907](https://github.com/helidon-io/helidon/pull/6907)
- WebServer: Rename `ServerConfig` to `WebServerConfig` [7108](https://github.com/helidon-io/helidon/pull/7108)
- WebServer: Set running flag to false in shutdownhook [6428](https://github.com/helidon-io/helidon/pull/6428)
- WebServer: TLS reloading fixed [7140](https://github.com/helidon-io/helidon/pull/7140)
- WebServer: Writing to closed socket with a silent error [6887](https://github.com/helidon-io/helidon/pull/6887)
- Builders: updates [6710](https://github.com/helidon-io/helidon/pull/6710)
- Builders: builder `toString` method update [7142](https://github.com/helidon-io/helidon/pull/7142)
- Builders: support lowercase for modifiers names [6844](https://github.com/helidon-io/helidon/pull/6844)
- Builders: ConfigBean not resolving config correctly [6652](https://github.com/helidon-io/helidon/pull/6652)
- Builders: Default prefixed builder types renamed to be suffixed [6796](https://github.com/helidon-io/helidon/pull/6796)
- Builders: Document assumptions about types of current processing round [7072](https://github.com/helidon-io/helidon/pull/7072)
- Builders: Fix error messages in validation task of builder processor. [7070](https://github.com/helidon-io/helidon/pull/7070)
- Builders: Follow up changes for builders PR [7074](https://github.com/helidon-io/helidon/pull/7074)
- Builders: GeneratedConfigBean should be easily available from Config-driven-services  [6680](https://github.com/helidon-io/helidon/pull/6680)
- Builders: New config builders [7008](https://github.com/helidon-io/helidon/pull/7008)
- Builders: Optional Builder methods should default to package private [6593](https://github.com/helidon-io/helidon/pull/6593)
- Builders: Pico cfg driven with additional ctor injection points fix [6612](https://github.com/helidon-io/helidon/pull/6612)
- Builders: TypeInfo and Builder Refinements [6729](https://github.com/helidon-io/helidon/pull/6729)
- Build: Change provided and optional to just optional - iteration 1 (#6495) [6503](https://github.com/helidon-io/helidon/pull/6503)
- Build: Change provided and optional to just optional - iteration 2 (#6495) [6650](https://github.com/helidon-io/helidon/pull/6650)
- Build: Fixed parallel build. [7111](https://github.com/helidon-io/helidon/pull/7111)
- Build: Switch metrics API jar scope to compile from provided [6677](https://github.com/helidon-io/helidon/pull/6677)
- Dependencies: Kafka bump up 2.8.1 > 3.4.0 [6708](https://github.com/helidon-io/helidon/pull/6708)
- Dependencies: Update grpc-java to version 1.54.1 [6693](https://github.com/helidon-io/helidon/pull/6693)
- Dependencies: Upgrade Jakarta Annotations to 2.1.1 [6595](https://github.com/helidon-io/helidon/pull/6595)
- Dependencies: Upgrade Jakarta EL to 5.0.1 [6603](https://github.com/helidon-io/helidon/pull/6603)
- Dependencies: Upgrade Jakarta jsonb to 3.0.0 and jsonp to 2.1.1 [6602](https://github.com/helidon-io/helidon/pull/6602)
- Dependencies: Upgrade graphql-java to 18.x [6965](https://github.com/helidon-io/helidon/pull/6965)
- Dependencies: Upgrade jersey to 3.1.2 [7147](https://github.com/helidon-io/helidon/pull/7147)
- Dependencies: Upgrade to WebSocket 2.1 API [6617](https://github.com/helidon-io/helidon/pull/6617)
- Dependencies: Upgrades JPA to 3.1 [6684](https://github.com/helidon-io/helidon/pull/6684)
- Dependencies: dependency upgrades: grpc, guava, jackson, netty [7162](https://github.com/helidon-io/helidon/pull/7162)
- Docs: MP Telemetry Documentation [6772](https://github.com/helidon-io/helidon/pull/6772)
- Docs: Some minor tweaks to the REST documentation [6686](https://github.com/helidon-io/helidon/pull/6686)
- Docs: Update documentation of composite provider flag. [6597](https://github.com/helidon-io/helidon/pull/6597)
- Docs: improve pico integration docs for oci sdk [7040](https://github.com/helidon-io/helidon/pull/7040)
- Docs: fix links for openapi in 4x [6688](https://github.com/helidon-io/helidon/pull/6688)
- Docs: New document describing the client and server WebSocket APIs in Nima [6578](https://github.com/helidon-io/helidon/pull/6578)
- Examples: Add metrics to quickstarts [7114](https://github.com/helidon-io/helidon/pull/7114)
- Examples: Creates a clean set of Pico examples [6800](https://github.com/helidon-io/helidon/pull/6800)
- Examples: Create file validations [6608](https://github.com/helidon-io/helidon/pull/6608)
- Examples: Fix Helidon Archetype generates broken projects [6722](https://github.com/helidon-io/helidon/pull/6722)
- Examples: Fix archetype build [7177](https://github.com/helidon-io/helidon/pull/7177)
- Examples: Fix params of @ExampleObject annotations in examples (#6791) [6792](https://github.com/helidon-io/helidon/pull/6792)
- Examples: Fix post merge compile error in examples.micrometer.se.MainTest [7115](https://github.com/helidon-io/helidon/pull/7115)
- Examples: MP Telemetry examples [6813](https://github.com/helidon-io/helidon/pull/6813)
- Examples: Nima custom archetype [6327](https://github.com/helidon-io/helidon/pull/6327)
- Examples: OCI examples remove reactive API [6969](https://github.com/helidon-io/helidon/pull/6969)
- Examples: Pico example pre req changes [6801](https://github.com/helidon-io/helidon/pull/6801)
- Examples: Port of static content example to Nima webserver [6778](https://github.com/helidon-io/helidon/pull/6778)
- Examples: Porting of example to Nima and removal of reactive dependencies [6839](https://github.com/helidon-io/helidon/pull/6839)
- Examples: Refactor examples/nima/media into examples/media/multipart [6812](https://github.com/helidon-io/helidon/pull/6812)
- Examples: Relocate Pico Examples [6783](https://github.com/helidon-io/helidon/pull/6783)
- Examples: Removed examples/webserver/jersey [6877](https://github.com/helidon-io/helidon/pull/6877)
- Examples: SE/Reactive archetype removal [6671](https://github.com/helidon-io/helidon/pull/6671)
- Examples: Temporarily remove examples generated by OpenAPITools  [6667](https://github.com/helidon-io/helidon/pull/6667)
- Examples: Update examples/graphql/basics to use Nima [6854](https://github.com/helidon-io/helidon/pull/6854)
- Examples: Update examples/logging to use Nima [6865](https://github.com/helidon-io/helidon/pull/6865) [6862](https://github.com/helidon-io/helidon/pull/6862) [6861](https://github.com/helidon-io/helidon/pull/6861)
- Examples: Update examples/logging/logback-aot to use Nima [6866](https://github.com/helidon-io/helidon/pull/6866)
- Examples: Updates to (standalone) Quickstart SE to use Nima and @ServerTest [6770](https://github.com/helidon-io/helidon/pull/6770)
- Examples: Updates to Quickstart SE to use Nima and @ServerTest [6766](https://github.com/helidon-io/helidon/pull/6766)
- Examples: archetypes generating poorly formatted code [6622](https://github.com/helidon-io/helidon/pull/6622)
- Test: Add @target(ElementType.METHOD) for annotation @mptest [6489](https://github.com/helidon-io/helidon/pull/6489)
- Test: Use Hamcrest assertions instead of JUnit in tests/integration/jms (#1749) [6643](https://github.com/helidon-io/helidon/pull/6643)
- Test: 7097 flaky HTTP/2 bookstore test [7137](https://github.com/helidon-io/helidon/pull/7137)
- Test: Add enum related config bean tests [6592](https://github.com/helidon-io/helidon/pull/6592)
- Test: Convenient flushing on response commit [6673](https://github.com/helidon-io/helidon/pull/6673)
- Test: Disable intermittently-failing async metrics MP test until we understand the failure [6980](https://github.com/helidon-io/helidon/pull/6980)
- Test: Port of tests/functional/config-profiles over to Nima [6826](https://github.com/helidon-io/helidon/pull/6826)
- Test: Re-enables ServerTest.java in nima/openapi [6585](https://github.com/helidon-io/helidon/pull/6585)
- Test: Reenables JerseyPropertiesTest.java in microprofile/server [6594](https://github.com/helidon-io/helidon/pull/6594)
- Test: Removed old bookstore-se app and renamed bookstore-nima to bookstore-se [6790](https://github.com/helidon-io/helidon/pull/6790)
- Test: Removed tests/integration/native-image/se-1 [6882](https://github.com/helidon-io/helidon/pull/6882)
- Test: Update MessagingHealthTest to use Nima WebClient instead of Reactive WebClient [6814](https://github.com/helidon-io/helidon/pull/6814)


## [4.0.0-ALPHA6]

This is the sixth Alpha build of Helidon 4.0.0 and is intended as a preview release only. Do not use this release in production. It is suitable only for experimentation. APIs are subject to change. Documentation is incomplete. And some functionality is experimental and not fully tested.

The big news in Helidon 4.0.0 is the introduction of Helidon Nima -- a [ground up webserver implementation based on JDK Project Loom virtual threads](https://medium.com/helidon/helidon-n%C3%ADma-helidon-on-virtual-threads-130bb2ea2088). Helidon 4.0.0 is a major release that includes significant new features and fixes. As a major release it also includes backward incompatible API changes.

Java 20 or newer is required to use Heldon 4.0.0-ALPHA6.

### CHANGES

- Common: Use helidon-common-config where possible/applicable [6448](https://github.com/helidon-io/helidon/pull/6448)
- Config: Escape the key when copying a config node [6296](https://github.com/helidon-io/helidon/pull/6296)
- Logging: Logging properties file clean up [6367](https://github.com/helidon-io/helidon/pull/6367)
- Logging: Remove FileHandler from logging.properties [6364](https://github.com/helidon-io/helidon/pull/6364)
- Messaging: 6303 JMS JNDI destination support [6305](https://github.com/helidon-io/helidon/pull/6305)
- Pico: Builder define default methods [6294](https://github.com/helidon-io/helidon/pull/6294)
- Pico: Pico and Builder Exception conventions [6525](https://github.com/helidon-io/helidon/pull/6525)
- Pico: no arg constructor support on pico interceptors [6552](https://github.com/helidon-io/helidon/pull/6552)
- Pico: pico runtime services [5750](https://github.com/helidon-io/helidon/pull/5750)
- Pico: renaming pico module names and api package names [6573](https://github.com/helidon-io/helidon/pull/6573)
- WebClient: Complete shortcut methods for all HTTP methods for the WebClient [6358](https://github.com/helidon-io/helidon/pull/6358)
- WebClient: Forward port of DNS resolver for Webclient [6551](https://github.com/helidon-io/helidon/pull/6551)
- WebClient: HTTP/2 Client with flow-control [6399](https://github.com/helidon-io/helidon/pull/6399)
- WebClient: Improve ClientRequestImpl [6208](https://github.com/helidon-io/helidon/pull/6208)
- WebServer: Add requested URI discovery support [6030](https://github.com/helidon-io/helidon/pull/6030)
- WebServer: Added shortcut methods for registering `MediaSupport` [6564](https://github.com/helidon-io/helidon/pull/6564)
- WebServer: Capture and propagate the CCL in ThreadPerTaskExecutor  [6322](https://github.com/helidon-io/helidon/pull/6322)
- WebServer: Complete Webserver HTTP routing shortcut methods [6404](https://github.com/helidon-io/helidon/pull/6404)
- WebServer: Do not split headers and payload into two different buffers [6491](https://github.com/helidon-io/helidon/pull/6491)
- WebServer: Error handling removed from the filter chain [6415](https://github.com/helidon-io/helidon/pull/6415)
- WebServer: Fix artifact ID, typo in name [6494](https://github.com/helidon-io/helidon/pull/6494)
- WebServer: Handle zero or more spaces after commas when parsing Accept-Encoding [6380](https://github.com/helidon-io/helidon/pull/6380)
- WebServer: Issue 5383: Added Content-Encoding header check when content encoding is disabled. [6267](https://github.com/helidon-io/helidon/pull/6267)
- WebServer: Issue 6278: Programmatically control media providers with Nima WebServer [6412](https://github.com/helidon-io/helidon/pull/6412)
- WebServer: Jackson media support for Níma [6432](https://github.com/helidon-io/helidon/pull/6432)
- WebServer: Make size of header buffer independent of payload size [6475](https://github.com/helidon-io/helidon/pull/6475)
- WebServer: Nima media support [6507](https://github.com/helidon-io/helidon/pull/6507)
- WebServer: SSE API and implementation in Nima [6096](https://github.com/helidon-io/helidon/pull/6096)
- WebServer: Wrap underlying output stream with a buffered one whose buffer size is configurable [6509](https://github.com/helidon-io/helidon/pull/6509)
- WebSocket: Renamed receive() method to onMessage() in WsListener [6571](https://github.com/helidon-io/helidon/pull/6571)
- Build: Upgrade Java to 20 GA [6474](https://github.com/helidon-io/helidon/pull/6474)
- Dependencies: Adopt SnakeYAML 2.0; add integration tests for reactive and Nima [6535](https://github.com/helidon-io/helidon/pull/6535)
- Dependencies: JWT-Auth upgrade to 2.1 version [6268](https://github.com/helidon-io/helidon/pull/6268)
- Dependencies: Upgrade graphql-java to 17.5 [6540](https://github.com/helidon-io/helidon/pull/6540)
- Docs: Add new dirs and docs for Nima 4 [6398](https://github.com/helidon-io/helidon/pull/6398)
- Docs: Created Nima dir for docs [6306](https://github.com/helidon-io/helidon/pull/6306)
- Docs: New documenation for FT in Nima [6565](https://github.com/helidon-io/helidon/pull/6565)
- Docs: New document that describes Nima's SSE APIs [6332](https://github.com/helidon-io/helidon/pull/6332)
- Docs: Remove claim that metrics are propagated from server to client [6361](https://github.com/helidon-io/helidon/pull/6361)
- Examples: Add OCI MP Archetype (4.x) [6147](https://github.com/helidon-io/helidon/pull/6147)
- Examples: Update mustache format in archetype files [6286](https://github.com/helidon-io/helidon/pull/6286)
- Tests: Fix RC in JMS error test [6375](https://github.com/helidon-io/helidon/pull/6375)
- Tests: JMS intermittent test fix [6392](https://github.com/helidon-io/helidon/pull/6392)
- Tests: Re-enable tests [6359](https://github.com/helidon-io/helidon/pull/6359) [6355](https://github.com/helidon-io/helidon/pull/6355) [6356](https://github.com/helidon-io/helidon/pull/6356) [6357](https://github.com/helidon-io/helidon/pull/6357)
- Tests: TestDisabledMetrics.java in microprofile/metrics [6436](https://github.com/helidon-io/helidon/pull/6436)
- Tests: TestExtendedKPIMetrics.java from microprofile/metrics as it i… [6437](https://github.com/helidon-io/helidon/pull/6437)
- Tests: Update bookstore test for Nima to add jsonb and jackson media [6577](https://github.com/helidon-io/helidon/pull/6577)
- Tests: Use Hamcrest assertions instead of JUnit in examples/todo-app (#1749) [6293](https://github.com/helidon-io/helidon/pull/6293) and others
- Tests: nima bookstore test [6349](https://github.com/helidon-io/helidon/pull/6349)

## [4.0.0-ALPHA5]

This is the fifth Alpha build of Helidon 4.0.0 and is intended as a preview release only. Do not use this release in production. It is suitable only for experimentation. APIs are subject to change. Documentation is incomplete. And some functionality is experimental and not fully tested.

The big news in Helidon 4.0.0 is the introduction of Helidon Nima -- a [ground up webserver implementation based on JDK Project Loom virtual threads](https://medium.com/helidon/helidon-n%C3%ADma-helidon-on-virtual-threads-130bb2ea2088). 

Helidon 4.0.0 is a major release that includes significant new features and fixes. As a major release it also includes some backward incompatible API changes.

Java 19 or newer is required to use Heldon 4.0.0-ALPHA5.

### CHANGES

- Config: Configuration fixes [6145](https://github.com/helidon-io/helidon/pull/6145)
- Config: Describe disabling config token replacement [6170](https://github.com/helidon-io/helidon/pull/6170)
- FaultTolerance: Reactive FT Module Info fix [6238](https://github.com/helidon-io/helidon/pull/6238)
- HTTP2: Issue 5425:  Added configurable validate-path and max-concurrent-streams to HTTP 2. [5981](https://github.com/helidon-io/helidon/pull/5981)
- Media: Reactive Media Common deprecated cleanup [6098](https://github.com/helidon-io/helidon/pull/6098)
- Messaging: AQ connector @ConnectorAttribute [6038](https://github.com/helidon-io/helidon/pull/6038)
- OCI: Register OciMetricsSupport service only when enable flag is set to true [6053](https://github.com/helidon-io/helidon/pull/6053)
- Security: Nima and Reactive implementation of OIDC provider separated [6055](https://github.com/helidon-io/helidon/pull/6055)
- Security: OIDC logout functionality fixed [6131](https://github.com/helidon-io/helidon/pull/6131)
- Security: Reloadable server TLS KeyStore [5964](https://github.com/helidon-io/helidon/pull/5964)
- Tracing: Fix order of initialization of tracing and security. (#5987) [6034](https://github.com/helidon-io/helidon/pull/6034)
- Tracing: Fix parent handling in OpenTelemetry [6092](https://github.com/helidon-io/helidon/pull/6092)
- WebClient: Need to use a ConcurrentHashMap in DefaultDnsResolver [6207](https://github.com/helidon-io/helidon/pull/6207)
- WebServer: 100 continue request reset fix [6251](https://github.com/helidon-io/helidon/pull/6251)
- WebServer: 100 continue triggered by content request [5965](https://github.com/helidon-io/helidon/pull/5965)
- WebServer: ContentEncodingContext Builder and passing ContentEncodingContext instance from WebServer to Http1Connection. [5921](https://github.com/helidon-io/helidon/pull/5921)
- WebServer: Port to Nima of enhancement to allow WebSocket applications on different ports [6004](https://github.com/helidon-io/helidon/pull/6004)
- WebServer: Proposal to implement a more efficient webserver shutdown strategy [5876](https://github.com/helidon-io/helidon/pull/5876)
- WebServer: Refactor Níma connection context [6109](https://github.com/helidon-io/helidon/pull/6109)
- WebServer: Static content update [6195](https://github.com/helidon-io/helidon/pull/6195)
- WebServer: Switch default back-pressure strategy to AUTO_FLUSH from LINEAR. [5983](https://github.com/helidon-io/helidon/pull/5983)
- WebServer: Update BodyPart to return Optional instead of a nullable String [6101](https://github.com/helidon-io/helidon/pull/6101)
- Webserver: Support for interruption of HTTP/2 connections for efficient shutdowns [6041](https://github.com/helidon-io/helidon/pull/6041)
- Build: Configure helidon-mave-plugin jlink-image to use --enable-preview [6048](https://github.com/helidon-io/helidon/pull/6048)
- Build: Correct arrangement of fields, methods and inner types. [6114](https://github.com/helidon-io/helidon/pull/6114)
- Build: Fix duplicate maven-failsafe-plugin declaration in dbclient integration test [6241](https://github.com/helidon-io/helidon/pull/6241)
- Build: Idea code style [6111](https://github.com/helidon-io/helidon/pull/6111)
- Build: Remove user specific package from the code style. [6144](https://github.com/helidon-io/helidon/pull/6144)
- Build: Use https in pom.xml schemaLocation - iteration 1 (#5657) [6043](https://github.com/helidon-io/helidon/pull/6043) and others
- Dependencies: Cleanup Helidon BOM by removing artifacts that are not deployed [6047](https://github.com/helidon-io/helidon/pull/6047)
- Dependencies: Upgrade Jersey 3.1.1 [6171](https://github.com/helidon-io/helidon/pull/6171)
- Dependencies: Upgrade Weld to 5.x #5815 [5830](https://github.com/helidon-io/helidon/pull/5830)
- Dependencies: jakarta.activation cleanup [6138](https://github.com/helidon-io/helidon/pull/6138)
- Docs: Restore navbar glyphs [6179](https://github.com/helidon-io/helidon/pull/6179)
- Examples: Add application parent pom for Nima applications. Use in nima examples. [6232](https://github.com/helidon-io/helidon/pull/6232)
- Examples: Nima Quickstart Archetype [6229](https://github.com/helidon-io/helidon/pull/6229)
- Tests: Use Hamcrest assertions instead of JUnit [6160](https://github.com/helidon-io/helidon/pull/6160) and others
- Tests: LRA TCK failing randomly #6106 [6107](https://github.com/helidon-io/helidon/pull/6107)
- Tests: intermittent issue on OciMetricsSupportTest [6151](https://github.com/helidon-io/helidon/pull/6151)


## [4.0.0-ALPHA4]

This is the fourth Alpha build of Helidon 4.0.0 and is intended as a preview release only. Do not use this release in production. It is suitable only for experimentation. APIs are subject to change. Documentation is incomplete. And some functionality is experimental and not fully tested.

The big news in Helidon 4.0.0 is the introduction of Helidon Nima -- a [ground up webserver implementation based on JDK Project Loom virtual threads](https://medium.com/helidon/helidon-n%C3%ADma-helidon-on-virtual-threads-130bb2ea2088). 

Helidon 4.0.0 is a major release that includes significant new features and fixes. As a major release it also includes some backward incompatible API changes.

Java 19 or newer is required to use Heldon 4.0.0-ALPHA4.

### MicroProfile Support

4.0.0-ALPHA4 now supports MicroProfile 5 running on Nima WebServer. Please give it a try! If you are upgrading an existing Helidon 3.x MicroProfile application and run into an error concerning `io.common.HelidonConsoleHandler` then change `logging.properties` to use `io.helidon.logging.jul.HelidonConsoleHandler` instead.

### CHANGES

- Logging: Change JUL to System.Logger in most modules  [5936](https://github.com/helidon-io/helidon/pull/5936)
- Messaging: WLS JMS Object-Based Security [5852](https://github.com/helidon-io/helidon/pull/5852)
- MicroProfile: Deprecate MicroProfile Tracing [5909](https://github.com/helidon-io/helidon/pull/5909)
- OCI: Replace OCI Java SDK shaded jar with v3 for OCI integration [5908](https://github.com/helidon-io/helidon/pull/5908)
- OCI: helidon metrics to oci integration [5945](https://github.com/helidon-io/helidon/pull/5945)
- Pico:  Builder updates, fixes and enhancements [5977](https://github.com/helidon-io/helidon/pull/5977)
- Security: Default tenant is not included for propagation [5900](https://github.com/helidon-io/helidon/pull/5900)
- Security: Oidc tenant name now properly escaped [5873](https://github.com/helidon-io/helidon/pull/5873)
- Tests: Dbclient Integration Tests Fixed [4860](https://github.com/helidon-io/helidon/pull/4860)
- WebServer: implement a more efficient webserver shutdown strategy [5876](https://github.com/helidon-io/helidon/pull/5876/)
- WebServer: Shutdown hook alignment Níma and MP. [5913](https://github.com/helidon-io/helidon/pull/5913)
- WebSocket: UriQuery should not support null parameters. [5950](https://github.com/helidon-io/helidon/pull/5950)
- WebSocket: client and testing update [5831](https://github.com/helidon-io/helidon/pull/5831)
- Dependencies: Upgrade OCI SDK to 3.2.1 [5956](https://github.com/helidon-io/helidon/pull/5956)
- Docs: Documentation updates to correct wrong instructions for HOCON config parsing [5975](https://github.com/helidon-io/helidon/pull/5975)
- Examples: examples missing helidon-config-yaml dependency [5919](https://github.com/helidon-io/helidon/pull/5919)
- HTTP/2: Configurable protocols [5883](https://github.com/helidon-io/helidon/pull/5883)

## [4.0.0-ALPHA3]

This is the third Alpha build of Helidon 4.0.0 and is intended as a preview release only. Do not use this release in production. It is suitable only for experimentation. APIs are subject to change. Documentation is incomplete. And some functionality is experimental and not fully tested.

The big news in Helidon 4.0.0 is the introduction of Helidon Nima -- a [ground up webserver implementation based on JDK Project Loom virtual threads](https://medium.com/helidon/helidon-n%C3%ADma-helidon-on-virtual-threads-130bb2ea2088). 

Helidon 4.0.0 is a major release that includes significant new features and fixes. As a major release it also includes some backward incompatible API changes.

Java 19 or newer is required to use Heldon 4.0.0-ALPHA3.

### MicroProfile Support

4.0.0-ALPHA3 now supports MicroProfile 5 running on Nima WebServer. Please give it a try! If you are upgrading an existing Helidon 3.x MicroProfile application and run into an error concerning `io.common.HelidonConsoleHandler` then change `logging.properties` to use `io.helidon.logging.jul.HelidonConsoleHandler` instead.


### CHANGES

- CORS: Fix CORS annotation handling error in certain cases [5105](https://github.com/helidon-io/helidon/pull/5105)
- Common: Add info to CharBuf exceptions  [5376](https://github.com/helidon-io/helidon/pull/5376)
- Common: Features formatting [5765](https://github.com/helidon-io/helidon/pull/5765)
- Common: Fix inconsistent status name. [5641](https://github.com/helidon-io/helidon/pull/5641)
- Common: Name all threads. [5397](https://github.com/helidon-io/helidon/pull/5397)
- Common: builder interceptor support [5591](https://github.com/helidon-io/helidon/pull/5591)
- Common: custom exception for CharBuffer [5505](https://github.com/helidon-io/helidon/pull/5505)
- Config: Add built-in enum config support [5795](https://github.com/helidon-io/helidon/pull/5795)
- Config: Cannot debug in Eclipse IDE Version: 2022-09 (4.25.0) #5706 [5708](https://github.com/helidon-io/helidon/pull/5708)
- Config: Provide MP config profile support for application.yaml (#5565) [5586](https://github.com/helidon-io/helidon/pull/5586)
- DBClient: Handle exception on inTransaction apply [5700](https://github.com/helidon-io/helidon/pull/5700)
- Fault Tolerance: Fixed race condition on bulkhead. [5747](https://github.com/helidon-io/helidon/pull/5747)
- FaultTolerance:Use lazy values to initialized HealthSupport FT handlers [5147](https://github.com/helidon-io/helidon/pull/5147)
- GraalVM: 4836 graalvm 22 4.x [5378](https://github.com/helidon-io/helidon/pull/5378)
- Grpc: Issue 4567 - Grpc component Does not handle package directive in proto files. [5284](https://github.com/helidon-io/helidon/pull/5284)
- Health: Builtin Helidon health features doubling fix [5615](https://github.com/helidon-io/helidon/pull/5615)
- Health: Pass correct config to health providers  [5572](https://github.com/helidon-io/helidon/pull/5572)
- JMS: JMS connector update [5592](https://github.com/helidon-io/helidon/pull/5592)
- LRA: 5405 LRA recovery cycle detection [5778](https://github.com/helidon-io/helidon/pull/5778)
- LRA: LRA false warning [5556](https://github.com/helidon-io/helidon/pull/5556)
- Logging: Observe log [5656](https://github.com/helidon-io/helidon/pull/5656)
- MP Client: Client tracing interceptor no longer clears exception [5620](https://github.com/helidon-io/helidon/pull/5620)
- MP FaultTolerance: Implementation of MP FT over Nima VTs [5271](https://github.com/helidon-io/helidon/pull/5271)
- MP Messaging: Bump-up reactive messaging/ops to 3.0 [5526](https://github.com/helidon-io/helidon/pull/5526)
- MP Metrics: Fix problems causing MP metrics TCK failures [5631](https://github.com/helidon-io/helidon/pull/5631)
- MP Metrics: Move fetch of metrics from endpoint so it too is retried [5143](https://github.com/helidon-io/helidon/pull/5143)
- MP: Add null check to MP Server.Builder.config() (#5363) [5374](https://github.com/helidon-io/helidon/pull/5374)
- Media: Added method hasEntity to readable entity [5602](https://github.com/helidon-io/helidon/pull/5602)
- Media: Support customize encoder and media context in Nima WebServer (#5256) [5257](https://github.com/helidon-io/helidon/pull/5257)
- Messaging: 5510 kafka prod nack 4x [5531](https://github.com/helidon-io/helidon/pull/5531)
- Metrics: Fix improper handling of metrics global tags [5814](https://github.com/helidon-io/helidon/pull/5814)
- Metrics: Fix incorrect tags comparison when trying to match metric IDs [5550](https://github.com/helidon-io/helidon/pull/5550)
- MicroProfile: MP on Níma [5176](https://github.com/helidon-io/helidon/pull/5176)
- OpenAPI: 5650 openapi examples issues [5651](https://github.com/helidon-io/helidon/pull/5651)
- Pico: Introduce Pico ConfigBean Builder Extensions [5482](https://github.com/helidon-io/helidon/pull/5482)
- Pico: Consolidates the pico spi with the api into the new replacement "pico" module [5400](https://github.com/helidon-io/helidon/pull/5400)
- Pico: Extend Builder to support abstract and concrete codegen, and add validation of required attributes during builder().build()  [5228](https://github.com/helidon-io/helidon/pull/5228)
- Pico: Introduce Helidon Pico [5141](https://github.com/helidon-io/helidon/pull/5141)
- Pico: Introduces Pico Builder [5195](https://github.com/helidon-io/helidon/pull/5195)
- Pico: Move bean utils from spi to processor [5448](https://github.com/helidon-io/helidon/pull/5448)
- Pico: Pico Tools - Part 1 of N [5598](https://github.com/helidon-io/helidon/pull/5598)
- Reactive: Multi.forEachCS [5532](https://github.com/helidon-io/helidon/pull/5532)
- Security: Accidentally removed updateRequest method returned [5843](https://github.com/helidon-io/helidon/pull/5843)
- Security: Add relativeUris flag in OidcConfig to allow Oidc webclient to use relative path on the request URI [5336](https://github.com/helidon-io/helidon/pull/5336)
- Security: CipherSuiteTest intermittent failure [5711](https://github.com/helidon-io/helidon/pull/5711)
- Security: Jwt scope handling extended over array support [5573](https://github.com/helidon-io/helidon/pull/5573)
- Security: OIDC multi-tenant and lazy loading implementation [5846](https://github.com/helidon-io/helidon/pull/5846)
- Security: Use only public APIs to read PKCS#1 keys [5240](https://github.com/helidon-io/helidon/pull/5240)
- WebClient: Custom DNS resolvers for Nima [4876](https://github.com/helidon-io/helidon/pull/4876)
- WebServer Nima: Add WebServer.Builder configuration option to support not registering JVM shutdown hook [5739](https://github.com/helidon-io/helidon/pull/5739)
- WebServer: 5409 duration timeout [5709](https://github.com/helidon-io/helidon/pull/5709)
- WebServer: Add Context to Loom Webserver. [5593](https://github.com/helidon-io/helidon/pull/5593)
- WebServer: Add Max Re-Route configurable [5587](https://github.com/helidon-io/helidon/pull/5587)
- WebServer: Add ServerResponse.reset() to support ErrorHandlers [5694](https://github.com/helidon-io/helidon/pull/5694)
- WebServer: AllowThreadLocals configurable [5292](https://github.com/helidon-io/helidon/pull/5292)
- WebServer: Error handling support in HTTP for Nima WebServer. [5436](https://github.com/helidon-io/helidon/pull/5436)
- WebServer: Fix handling of entity length in HTTP/2. [5610](https://github.com/helidon-io/helidon/pull/5610)
- WebServer: Fix handling of optional whitespace at the beginning of headers. [5441](https://github.com/helidon-io/helidon/pull/5441)
- WebServer: Fix user log entry of access log. [5715](https://github.com/helidon-io/helidon/pull/5715)
- WebServer: HTTP upgrade handler for Websockets [5569](https://github.com/helidon-io/helidon/pull/5569)
- WebServer: Níma: Static content handling rework [5543](https://github.com/helidon-io/helidon/pull/5543)
- WebServer: NullPointerException when there is an illegal character in the request (#5470) [5472](https://github.com/helidon-io/helidon/pull/5472)
- WebServer: Support for "raw protocol" string [5575](https://github.com/helidon-io/helidon/pull/5575)
- WebServer: Support for optional entity in Níma. [5200](https://github.com/helidon-io/helidon/pull/5200)
- WebServer: Throw an exception when route does not finish, reroute, or next [5834](https://github.com/helidon-io/helidon/pull/5834)
- WebServer: set transfer encoding as response header [5646](https://github.com/helidon-io/helidon/pull/5646)
- WebSocket: Tyrus integration into Nima [5464](https://github.com/helidon-io/helidon/pull/5464)
- Build: Remove dependency on yaml config from nima module. [5381](https://github.com/helidon-io/helidon/pull/5381)
- Build: Remove license-report from maven lifecycle [5246](https://github.com/helidon-io/helidon/pull/5246)
- Build: Upgrade dependency check plugin to 7.4.4 [5807](https://github.com/helidon-io/helidon/pull/5807)
- Build: cleanup pom files with duplicate declarations of maven-compiler-plugin [5810](https://github.com/helidon-io/helidon/pull/5810)
- Dependencies: Fix Guava version to match that required by the grpc-java libraries [5504](https://github.com/helidon-io/helidon/pull/5504)
- Dependencies: Manage protobuf version using BOM  [5178](https://github.com/helidon-io/helidon/pull/5178)
- Dependencies: Neo4j driver update [5751](https://github.com/helidon-io/helidon/pull/5751)
- Dependencies: Ugrade jersey to 3.0.9 [5789](https://github.com/helidon-io/helidon/pull/5789)
- Dependencies: Upgrade PostgreSQL JDBC driver dependency to 42.4.3 [5561](https://github.com/helidon-io/helidon/pull/5561)
- Dependencies: Upgrade build-tools to 3.0.3, fix hbs template file copyright [5735](https://github.com/helidon-io/helidon/pull/5735)
- Dependencies: Upgrade grpc-java to 1.49.2 [5361](https://github.com/helidon-io/helidon/pull/5361)
- Dependencies: Upgrade netty to 4.1.86.Final and use netty bom [5734](https://github.com/helidon-io/helidon/pull/5734)
- Dependencies: Upgrade protobuf-java [5134](https://github.com/helidon-io/helidon/pull/5134)
- Dependencies: Upgrade to jackson-databind-2.13.4.2 via bom 2.13.4.20221013 [5304](https://github.com/helidon-io/helidon/pull/5304)
- Docs: 5618 flatMapCompletionStage javadoc fix [5624](https://github.com/helidon-io/helidon/pull/5624)
- Docs: Replace deprecated ServerConfiguration.builder() on WebServer.builder() in docs (#5023) [5121](https://github.com/helidon-io/helidon/pull/5121)
- Docs: WLS connector doc typo [5802](https://github.com/helidon-io/helidon/pull/5802)
- Examples: 4269 openapi tools examples [5250](https://github.com/helidon-io/helidon/pull/5250)
- Examples: Make JSON-B a default option for Helidon MP projects [5207](https://github.com/helidon-io/helidon/pull/5207)
- Examples: Use property to skip execution of eclipselink weave [5312](https://github.com/helidon-io/helidon/pull/5312)
- Examples: [Archetypes] database choices should be before packaging [5293](https://github.com/helidon-io/helidon/pull/5293)
- Examples: remove -Ddocker.build=true [5485](https://github.com/helidon-io/helidon/pull/5485)
- Examples:Tracing config updates in archetype [5001](https://github.com/helidon-io/helidon/pull/5001)
- Tests: Intermittent failure (WebClient tracing test) [5755](https://github.com/helidon-io/helidon/pull/5755)
- Tests: Add some retries because post-request metrics updates occur after the response is sent [5122](https://github.com/helidon-io/helidon/pull/5122)
- Tests: Additional testing for Nima's websocket implementation [5595](https://github.com/helidon-io/helidon/pull/5595)
- Tests: Close client after each test to avoid intermittent failures on MacOS [5155](https://github.com/helidon-io/helidon/pull/5155)
- Tests: Fix Intermittent TestJBatchEndpoint.runJob [5542](https://github.com/helidon-io/helidon/pull/5542)
- Tests: Fix Intermittent TestJBatchEndpoint.runJob [5567](https://github.com/helidon-io/helidon/pull/5567)
- Tests: Fix intermittent jBatch test [5249](https://github.com/helidon-io/helidon/pull/5249)
- Tests: Fix problems in tracing e2e test [5280](https://github.com/helidon-io/helidon/pull/5280)
- Tests: Fixed OpentraceableClientE2ETest to be more deterministic [5536](https://github.com/helidon-io/helidon/pull/5536)
- Tests: Log4j integration test [5301](https://github.com/helidon-io/helidon/pull/5301)
- Tests: MP Opentracing TCK  fix [5599](https://github.com/helidon-io/helidon/pull/5599)
- Tests: Make Github pipeline more robust by increasing HTTP ttl and adding a retry [5164](https://github.com/helidon-io/helidon/pull/5164)
- Tests: Named port Nima ServerTest and RoutingTest [5551](https://github.com/helidon-io/helidon/pull/5551)
- Tests: New FT Nima executors and larger multiplier for TCK timeouts in pipeline [5317](https://github.com/helidon-io/helidon/pull/5317)
- Tests: Remove retries from executor metrics test fix; use the pre-existing countdown latch in `GreetService` instead [5109](https://github.com/helidon-io/helidon/pull/5109)
- Tests: Simplify named socket WebTarget injection in Tests [5314](https://github.com/helidon-io/helidon/pull/5314)
- Tests: TempDir support for tests [5508](https://github.com/helidon-io/helidon/pull/5508)
- Tests: Updated bulkhead test to properly wait until Task 1 is enqueued [5562](https://github.com/helidon-io/helidon/pull/5562)
- Tests: Updated test to use in-memory logging handler to avoid problems with flushing [5180](https://github.com/helidon-io/helidon/pull/5180)
- Tests: Various metrics test improvements to avoid intermittent failures [5611](https://github.com/helidon-io/helidon/pull/5611)


## [4.0.0-ALPHA2]

This is the second Alpha build of Helidon 4.0.0 and is intended as a preview release only. Do not use this release in production. It is suitable only for experimentation. APIs are subject to change. Documentation is incomplete. And some functionality is experimental and not fully tested.

The big news in Helidon 4.0.0 is the introduction of Helidon Nima -- a [ground up webserver implementation based on JDK Project Loom](https://medium.com/helidon/helidon-n%C3%ADma-helidon-on-virtual-threads-130bb2ea2088). 

Helidon 4.0.0 is a major release that includes significant new features and fixes. As a major release it also includes some backward incompatible API changes.

Java 19 is required to use Heldon 4.0.0.

### CHANGES

- Common: Refactor HTTP header class names to a more natural approach. [4858](https://github.com/helidon-io/helidon/pull/4858)
- Config: Refactor helidon config 4.0 [4776](https://github.com/helidon-io/helidon/pull/4776)
- Config: Use common Config in common configurable. [5015](https://github.com/helidon-io/helidon/pull/5015)
- Config: fix for issue in handling ConfigProperties using List [4959](https://github.com/helidon-io/helidon/pull/4959)
- DBClient: Helidon DBClient does not trigger an Exception when no sane DB connection can be obtained [4773](https://github.com/helidon-io/helidon/pull/4773)
- Dependencies: Upgrade EclipseLink to 3.0.3 and Hibernate to 6.1.4.Final [5101](https://github.com/helidon-io/helidon/pull/5101)
- Dependencies: Upgrade reactive streams to 1.0.4 [5046](https://github.com/helidon-io/helidon/pull/5046)
- Dependencies: Upgrade snakeyaml to 1.32 [4923](https://github.com/helidon-io/helidon/pull/4923)
- Dependencies: Update graphql-java to 17.4 [4983](https://github.com/helidon-io/helidon/pull/4983)
- FT: retry checkMetricsForExecutorService [5103](https://github.com/helidon-io/helidon/pull/5103)
- JAX-RS: Register a low-priority exception mapper to log internal errors [5082](https://github.com/helidon-io/helidon/pull/5082)
- Logging: Logging refactoring [4825](https://github.com/helidon-io/helidon/pull/4825)
- MicroProfile: Fix identification of parallel startup of CDI [4964](https://github.com/helidon-io/helidon/pull/4964)
- Native Image: remove redundant reflect-config.json [4844](https://github.com/helidon-io/helidon/pull/4844)
- Nima: Context support for Níma WebServer [4867](https://github.com/helidon-io/helidon/pull/4867)
- Nima: Perf improvements [4818](https://github.com/helidon-io/helidon/pull/4818)
- Nima: Port and cleanup of old reactive tests for Nima bulkheads [4823](https://github.com/helidon-io/helidon/pull/4823)
- Nima: Shutdown executors while stopping the server [4819](https://github.com/helidon-io/helidon/pull/4819)
- Reactive: MultiFromBlockingInputStream RC fix 4x [5055](https://github.com/helidon-io/helidon/pull/5055)
- Security: Access token refresh - 4.x backport [4822](https://github.com/helidon-io/helidon/pull/4822)
- WebServer: Default header size increased to 16K for Http1ConnectionProvider in NIMA [5017](https://github.com/helidon-io/helidon/pull/5017)
- WebServer: Use Header.create() for both header names and header values. [4864](https://github.com/helidon-io/helidon/pull/4864)
- WebServer: Watermarked response backpressure 4x [5063](https://github.com/helidon-io/helidon/pull/5063)
- Doc: Fix invalid example in se/config/advanced-configuration.adoc - backport 4.x (#4775) [4944](https://github.com/helidon-io/helidon/pull/4944)
- Doc: Fix misplaced attribute settings [4955](https://github.com/helidon-io/helidon/pull/4955)
- Doc: Formatting of generated Helidon SE quickstart [4967](https://github.com/helidon-io/helidon/pull/4967)
- Doc: Preamble fix [5051](https://github.com/helidon-io/helidon/pull/5051)
- Doc: Ported Access Log documentation to 4.x [5054](https://github.com/helidon-io/helidon/pull/5054)
- Examples: 4834 4835 fix archetype test issues [4841](https://github.com/helidon-io/helidon/pull/4841)
- Examples: Remove module-info files from examples [4895](https://github.com/helidon-io/helidon/pull/4895)
- Examples: WebClient dependency in generated Helidon SE Quickstart should be in test scope [5019](https://github.com/helidon-io/helidon/pull/5019)
- Examples: add serial config required for oracle driver [4961](https://github.com/helidon-io/helidon/pull/4961)
- Examples: fix db issues in Helidon archetype [4805](https://github.com/helidon-io/helidon/pull/4805)
- Examples: k8s and v8o support in archetype [4891](https://github.com/helidon-io/helidon/pull/4891)
- Test: 5068 mock connector beans xml 4x [5070](https://github.com/helidon-io/helidon/pull/5070)
- Test: Ported TestInstance.Lifecycle.PER_CLASS fix to 4.x [5052](https://github.com/helidon-io/helidon/pull/5052)
- Test: Updated intermittently failing CircuitBreakerTest  [5033](https://github.com/helidon-io/helidon/pull/5033)
- Test: Add robustness to some of the timing-susceptible metrics tests; add util matcher with retry [5032](https://github.com/helidon-io/helidon/pull/5032)
- Test: EchoServiceTest timeout [5007](https://github.com/helidon-io/helidon/pull/5007)
- Test: Ported combined FT test to Nima and enhancements to Async [4840](https://github.com/helidon-io/helidon/pull/4840)
- Test: Removed deprecated tests, disabled pipelining test. [5010](https://github.com/helidon-io/helidon/pull/5010)
- Test: Special Windows build Config TCK profile no longer needed - 4.x [4870](https://github.com/helidon-io/helidon/pull/4870)
- Test: Vault tests [5026](https://github.com/helidon-io/helidon/pull/5026)
- Test: integration tests should run on every build [5080](https://github.com/helidon-io/helidon/pull/5080)

## [4.0.0-ALPHA1]

We are pleased to announce Helidon 4.0.0-ALPHA1. This is the first Alpha build of Helidon 4.0.0 and is intended as a preview release only. Do not use this release in production. It is suitable only for experimentation. APIs are subject to change. Documentation is incomplete. And some functionality is experimental and not fully tested.

The big news in this build is the introduction of Helidon Nima -- a ground up webserver implementation based on JDK Project Loom. More information to come.

Helidon 4.0.0 is a major release that includes significant new features and fixes. As a major release it also includes some backward incompatible API changes.

### Notable Changes

- Java 19 early access (with Loom support) as the minimal Java version
- Using System.Logger instead of java util logging (incremental change)
- `HelidonServiceLoader` is now part of `helidon-common` module
- Introduction of `@Weight`, `Weighted` and `Weights` instead of `@Priority` and `Prioritized`, to base ordering on a double (allows to fit a component between any other two components), all modules using priority are refactored (except for MicroProfile where required by specifications).
  - higher weight means a component is more important 
  - moved priority related types to MP config (as that is the lowest level MP module)
  - replaces all instances in SE that use priority with weight (no dependency on Jakarta, predictible and easy to understand behavior)
- Introduction of `MediaType` as the abstraction of any media type, as used by Config, static content and HTTP in general. See `MediaType` and `MediaTypes`
- `MapperManager` now supports mapping qualifiers
- new `helidon-common-parameters` module contains an abstraction of a container that has named values (one or more); this is used in path parameters, query parameters, form parameters etc.
- new `helidon-common-uri` module contains URI abstraction (path with possible parameters, query, and fragment)
- Header processing now uses `HeaderName` and `HeaderValue` types. This allows you to prepare constants with custom names and values that
  are often reused. It also allows us to improve parsing speed of HTTP requests. 

### CHANGES

- Nima: [4748](https://github.com/oracle/helidon/pull/4748)
- Nima: a few optimizations [4809](https://github.com/oracle/helidon/pull/4809)
- Nima: Config and name [4802](https://github.com/oracle/helidon/pull/4802)
- Nima: Use a single SocketHttpClient. [4794](https://github.com/oracle/helidon/pull/4794)
- Nima: Http exception refactoring [4804](https://github.com/oracle/helidon/pull/4804)
- Nima: Cleaner stack when running HTTP requests. [4768](https://github.com/oracle/helidon/pull/4768)
- Nima: Fix query params being lost on upgrade requests. [4796](https://github.com/oracle/helidon/pull/4796)
- Common: Move DirectHandler to HTTP common and refactor reactive and Nima [4782](https://github.com/oracle/helidon/pull/4782)
- Common: Updates to Helidon Common, Part 1 [4693](https://github.com/oracle/helidon/pull/4693)
- Common: Updates to Helidon Common, Part 2 [4718](https://github.com/oracle/helidon/pull/4718)
- Config: Unescape the keys when config is returned as a map in Main branch (#4678) [4716](https://github.com/oracle/helidon/pull/4716)
- Dependencies: Upgrade Postgre driver to 42.4.1 [4779](https://github.com/oracle/helidon/pull/4779)
- Docs: Adopt review comments on doc updates (#4627) [4721](https://github.com/oracle/helidon/pull/4721)
- Docs: update old K8s deployment yaml (#4760) [4762](https://github.com/oracle/helidon/pull/4762)
- Examples: Fix intermittent failure - archetype build (FT timeout) [4690](https://github.com/oracle/helidon/pull/4690)
- MicroProfile: MP path based static content should use index.html (4.x) [4737](https://github.com/oracle/helidon/pull/4737)
- Build: 4.0 version and poms [4655](https://github.com/oracle/helidon/pull/4655)

[4.1.2]: https://github.com/oracle/helidon/compare/4.1.1...4.1.2
[4.1.1]: https://github.com/oracle/helidon/compare/4.1.0...4.1.1
[4.1.0]: https://github.com/oracle/helidon/compare/4.0.11...4.1.0
[4.0.11]: https://github.com/oracle/helidon/compare/4.0.10...4.0.11
[4.0.10]: https://github.com/oracle/helidon/compare/4.0.9...4.0.10
[4.0.9]: https://github.com/oracle/helidon/compare/4.0.8...4.0.9
[4.0.8]: https://github.com/oracle/helidon/compare/4.0.7...4.0.8
[4.0.7]: https://github.com/oracle/helidon/compare/4.0.6...4.0.7
[4.0.6]: https://github.com/oracle/helidon/compare/4.0.5...4.0.6
[4.0.5]: https://github.com/oracle/helidon/compare/4.0.4...4.0.5
[4.0.4]: https://github.com/oracle/helidon/compare/4.0.3...4.0.4
[4.0.3]: https://github.com/oracle/helidon/compare/4.0.2...4.0.3
[4.0.2]: https://github.com/oracle/helidon/compare/4.0.1...4.0.2
[4.0.1]: https://github.com/oracle/helidon/compare/4.0.0...4.0.1
[4.0.0]: https://github.com/oracle/helidon/compare/4.0.0-RC2...4.0.0
[4.0.0-RC2]: https://github.com/oracle/helidon/compare/4.0.0-RC1...4.0.0-RC2
[4.0.0-RC1]: https://github.com/oracle/helidon/compare/4.0.0-M2...4.0.0-RC1
[4.0.0-M2]: https://github.com/oracle/helidon/compare/4.0.0-M1...4.0.0-M2
[4.0.0-M1]: https://github.com/oracle/helidon/compare/4.0.0-ALPHA6...4.0.0-M1
[4.0.0-ALPHA6]: https://github.com/oracle/helidon/compare/4.0.0-ALPHA5...4.0.0-ALPHA6
[4.0.0-ALPHA5]: https://github.com/oracle/helidon/compare/4.0.0-ALPHA4...4.0.0-ALPHA5
[4.0.0-ALPHA4]: https://github.com/oracle/helidon/compare/4.0.0-ALPHA3...4.0.0-ALPHA4
[4.0.0-ALPHA3]: https://github.com/oracle/helidon/compare/4.0.0-ALPHA2...4.0.0-ALPHA3
[4.0.0-ALPHA2]: https://github.com/oracle/helidon/compare/4.0.0-ALPHA1...4.0.0-ALPHA2
[4.0.0-ALPHA1]: https://github.com/oracle/helidon/compare/main...4.0.0-ALPHA1
