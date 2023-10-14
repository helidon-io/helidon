
# Changelog

All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

For Helidon 3.x releases please see [Helidon 3.x CHANGELOG.md](https://github.com/oracle/helidon/blob/helidon-3.x/CHANGELOG.md)

For Helidon 2.x releases please see [Helidon 2.x CHANGELOG.md](https://github.com/oracle/helidon/blob/helidon-2.x/CHANGELOG.md)

For Helidon 1.x releases please see [Helidon 1.x CHANGELOG.md](https://github.com/oracle/helidon/blob/helidon-1.x/CHANGELOG.md)

## [4.0.0]

* WebServer no longer falls back to the default routing for additional sockets (in Helidon SE)
* Introduced `ServerFeature` concept, server feature can access routing builders for all sockets on WebServer
* SecurityFeature is now a WebServer feature
* ContextFeature is now a WebServer feature
* ObserveFeature is now a WebServer feature
* OpenApiFeature is now a WebServer feature
* CorsFeature is a new WebServer feature
* TracingFeature is now an observability feature
* Features use common config dependency - can still pass `io.helidon.Config` instance to them, only changes in SPI
* Metrics in SE now require user in `observe` role, or `metrics.permit-all` set to `true`, otherwise 403 is returned
* OpeanAPI in SE now requires user in `openapi` role, or `openapi.permit-all` set to `true`, otherwise 403 is returned

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

[4.0.0-RC1]: https://github.com/oracle/helidon/compare/4.0.0-M2...4.0.0-RC1
[4.0.0-M2]: https://github.com/oracle/helidon/compare/4.0.0-M1...4.0.0-M2
[4.0.0-M1]: https://github.com/oracle/helidon/compare/4.0.0-ALPHA6...4.0.0-M1
[4.0.0-ALPHA6]: https://github.com/oracle/helidon/compare/4.0.0-ALPHA5...4.0.0-ALPHA6
[4.0.0-ALPHA5]: https://github.com/oracle/helidon/compare/4.0.0-ALPHA4...4.0.0-ALPHA5
[4.0.0-ALPHA4]: https://github.com/oracle/helidon/compare/4.0.0-ALPHA3...4.0.0-ALPHA4
[4.0.0-ALPHA3]: https://github.com/oracle/helidon/compare/4.0.0-ALPHA2...4.0.0-ALPHA3
[4.0.0-ALPHA2]: https://github.com/oracle/helidon/compare/4.0.0-ALPHA1...4.0.0-ALPHA2
[4.0.0-ALPHA1]: https://github.com/oracle/helidon/compare/main...4.0.0-ALPHA1
