
# Changelog

All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

For Helidon 3.x releases please see [Helidon 3.x CHANGELOG.md](https://github.com/oracle/helidon/blob/helidon-3.x/CHANGELOG.md)

For Helidon 2.x releases please see [Helidon 2.x CHANGELOG.md](https://github.com/oracle/helidon/blob/helidon-2.x/CHANGELOG.md)

For Helidon 1.x releases please see [Helidon 1.x CHANGELOG.md](https://github.com/oracle/helidon/blob/helidon-1.x/CHANGELOG.md)

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

[4.0.0-ALPHA6]: https://github.com/oracle/helidon/compare/4.0.0-ALPHA5...4.0.0-ALPHA6
[4.0.0-ALPHA5]: https://github.com/oracle/helidon/compare/4.0.0-ALPHA4...4.0.0-ALPHA5
[4.0.0-ALPHA4]: https://github.com/oracle/helidon/compare/4.0.0-ALPHA3...4.0.0-ALPHA4
[4.0.0-ALPHA3]: https://github.com/oracle/helidon/compare/4.0.0-ALPHA2...4.0.0-ALPHA3
[4.0.0-ALPHA2]: https://github.com/oracle/helidon/compare/4.0.0-ALPHA1...4.0.0-ALPHA2
[4.0.0-ALPHA1]: https://github.com/oracle/helidon/compare/main...4.0.0-ALPHA1

