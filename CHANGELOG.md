
# Changelog

All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.4.8-SNAPSHOT]

This is a bug fix release of Helidon.

### Fixes

## [1.4.7]

This is a bug fix release of Helidon.

### Fixes

- WebServer: Fix hangs when bad request was sent [2261](https://github.com/oracle/helidon/pull/2261)
- WebServer: Correctly handle invalid requests and custom configuration for HTTP [2244](https://github.com/oracle/helidon/pull/2244)
- Upgrade Netty to 4.1.51.Final [2236](https://github.com/oracle/helidon/pull/2236)
- Upgrade Yasson to 1.0.8 [2255](https://github.com/oracle/helidon/pull/2255)
- Update build.gradle files to correct dep mgmt and enable tests [2226](https://github.com/oracle/helidon/pull/2226)

## [1.4.6]

This is a bug fix release of Helidon.

### Fixes

- WebServer: Mutual TLS support backport [2166](https://github.com/oracle/helidon/pull/2166)
- Security: Security can now be disabled. [2156](https://github.com/oracle/helidon/pull/2156)
- Security: Boolean values of secure & httpOnly are ignored. [2049](https://github.com/oracle/helidon/pull/2049)

### Thanks

Thanks to community member BenjaminBuick for contributing to this release.

## [1.4.5]

This is a bug fix release of Helidon. It includes key bug and performance fixes.

### Fixes

- Decrypt AES method made visible [2005](https://github.com/oracle/helidon/pull/2005)
- Correctly validate mandatory JWT claims. [2012](https://github.com/oracle/helidon/pull/2012)
- Add tolerance to several metrics tests; change tolerance used in pipeline jobs [2001](https://github.com/oracle/helidon/pull/2001)
- Allows proxying of ServerRequest [1750](https://github.com/oracle/helidon/pull/1750)
- Minor gRPC fixes [1950](https://github.com/oracle/helidon/pull/1950)
- gRPC client API improvements [1850](https://github.com/oracle/helidon/pull/1850)
- Wait for thread completion only if interrupted flag set [1845](https://github.com/oracle/helidon/pull/1845)
- Add JSONB support to gRPC [1834](https://github.com/oracle/helidon/pull/1834)
- Fix #1711 StaticContentHandler fails with encoded URLs (#1811) [1817](https://github.com/oracle/helidon/pull/1817)
- Fix so we pick up 19.3.1 version of graalvm docker image. Pick up new… [1703](https://github.com/oracle/helidon/pull/1703)
- New implementation of PublisherInputStream that improves performance and fixes race conditions [1695](https://github.com/oracle/helidon/pull/1695)
- Upgrade yasson to 1.0.6 [1661](https://github.com/oracle/helidon/pull/1661)
- Executor service for Jersey managed async executor is now configurabl… [1645](https://github.com/oracle/helidon/pull/1645)
- Fix for #1618 - Unable to initialize gRPC service when using helidon-… [1638](https://github.com/oracle/helidon/pull/1638)
- Fixed static content handling bug [1641](https://github.com/oracle/helidon/pull/1641)
- Fix for prioritized custom MP config sources in Helidon config. [1620](https://github.com/oracle/helidon/pull/1620)
- Support for OpenTracing contrib's TracerResolver. [1610](https://github.com/oracle/helidon/pull/1610)
- Fix javadoc and injection in reactive service. [1609](https://github.com/oracle/helidon/pull/1609)
- Improve metrics interceptor performance; avoid MetricID creations - 1.x [1604](https://github.com/oracle/helidon/pull/1604)
- Fix keys and certs used in gRPC TLS/SSL tests [1611](https://github.com/oracle/helidon/pull/1611)
- Removed use of Java 9 API. [1580](https://github.com/oracle/helidon/pull/1580)
- Removed unnecessary synchronization in metrics registry [1581](https://github.com/oracle/helidon/pull/1581)
- Removes assertions that do not hold when alternatives and specialization are in play [1557](https://github.com/oracle/helidon/pull/1557)
- Openapi custom context root [1524](https://github.com/oracle/helidon/pull/1524)
- Update Dockerfiles to use mvn 3.6 and JDK 11 to build. [1724](https://github.com/oracle/helidon/pull/1724)
- 3rd party attribution updates: [2025](https://github.com/oracle/helidon/pull/2025) [2009](https://github.com/oracle/helidon/pull/2009)
- New pipeline infra [1958](https://github.com/oracle/helidon/pull/1958) [1994](https://github.com/oracle/helidon/pull/1994)

## [1.4.4]
### Notes

This is a bug fix release of Helidon. It includes another key fix for Helidon MP JPA/JTA support.
If you use JPA/JTA then it is strongly recommended that you upgrade to this release.

### Fixes

- JPA/JTA: Ensures that JtaDataSource instances that are acquired when a transaction is already active have their Synchronizations registered appropriately [1508](https://github.com/oracle/helidon/pull/1508)
- Config: Fix merging of value nodes [1490](https://github.com/oracle/helidon/pull/1490)
- Config: Config cache is not using SoftReference anymore. [1495](https://github.com/oracle/helidon/pull/1495)
- Security: Fail fast when policy validation fails because of setup/syntax  [1492](https://github.com/oracle/helidon/pull/1492)
- Reactive: BaseProcessor back-pressure fix [1343](https://github.com/oracle/helidon/pull/1343)

## [1.4.3]
### Notes

This is a bug fix release of Helidon. It includes a fix for a Helidon MP JPA/JTA regression
that occured in 1.4.2. If you use JPA/JTA then it is strongly recommended that you upgrade
to this release or newer.

### Fixes

- JPA/JTA: Resolve JTA/JPA transaction synchronization issues [1476](https://github.com/oracle/helidon/pull/1476)
- OpenAPI: Remove dependency on Jackson [1457](https://github.com/oracle/helidon/pull/1457)

## [1.4.2]
### Notes

This is a bug fix release of Helidon.

### Fixes

- JAX-RS: Application path had to contain leading "/" [1261](https://github.com/oracle/helidon/pull/1261)
- Security: OIDC and JWT: groups claim in a JWT is now honored and a Role is created in subject for each such group. [1325](https://github.com/oracle/helidon/pull/1325)
- Security: OIDC redirect with cookies now uses Same-Site policy of Lax by default to prevent infinite redirects. If the default value is use, a warning is printed explaining the change. [1314](https://github.com/oracle/helidon/pull/1314)
- Security: #1408 public fields for IdcsMtRoleMapperProvider.MtCacheKey for Helidon 1.x [1413](https://github.com/oracle/helidon/pull/1413)
- JPA/JTA: Transaction fixes [1376](https://github.com/oracle/helidon/pull/1376)
- Metrics: Fix Prometheus format problems [1427](https://github.com/oracle/helidon/pull/1427)
- OpenAPI: Support multiple jandex.idx files on the classpath [1318](https://github.com/oracle/helidon/pull/1318)
- gRPC: minor enhancements [1277](https://github.com/oracle/helidon/pull/1277)
- Fix application jar class-path for SNAPSHOT versioned dependencies [1298](https://github.com/oracle/helidon/pull/1298)
- Archetypes: Fix optional files in bare archetypes. [1250](https://github.com/oracle/helidon/pull/1250)
- Examples: Add OpenAPI annotations to MP quickstart [1393](https://github.com/oracle/helidon/pull/1393)
- Examples: Update gradle files to work with Gradle 5, 6. [1352](https://github.com/oracle/helidon/pull/1352)
- Upgrade jdk8-graalvm docker image to graalvm 19.3.1. Add libstdc++-6-dev [1428](https://github.com/oracle/helidon/pull/1428)
- Upgrade netty to 4.1.45 [1425](https://github.com/oracle/helidon/pull/1425)
- Upgrade Grpc java [1437](https://github.com/oracle/helidon/pull/1437)
- Upgrade SmallRye OpenAPI to 1.2.0 [1421](https://github.com/oracle/helidon/pull/1421)
- Upgrade reactor to 3.3.1-RELEASE [1237](https://github.com/oracle/helidon/pull/1237)
- Upgrade H2, HikariCP, Jedis Client, OCI Client [1270](https://github.com/oracle/helidon/pull/1270)
- Upgrade shrinkwrap used by arquillian to support https [1308](https://github.com/oracle/helidon/pull/1308)


## [1.4.1]

### Notes

This is a bug fix release of Helidon.

### Fixes

- WebServer: Better recovery for invalid URLs and content types [1233](https://github.com/oracle/helidon/pull/1233)
- Metrics: Fix Unable to find reusable metric with tags [1241](https://github.com/oracle/helidon/pull/1241)
- Metrics: Fix Metrics type of Fault Tolerance is displayed as invalid [1179](https://github.com/oracle/helidon/pull/1179)
- Security: Allow use of exception instead of abortWith in Jersey security. [1224](https://github.com/oracle/helidon/pull/1224)
- Security: Outbound with OIDC provider no longer causes an UnsupportedOperationException [1195](https://github.com/oracle/helidon/pull/1195)
- Add support for Lazy values [1225](https://github.com/oracle/helidon/pull/1225)
- Upgrade Google libraries for Google login provider. [1199](https://github.com/oracle/helidon/pull/1199)
- Upgrade config libraries [1234](https://github.com/oracle/helidon/pull/1234)
- Upgrade MP Fault Tolerance to 2.0.2 and Failsafe 2.2.3 [1197](https://github.com/oracle/helidon/pull/1197) [1186](https://github.com/oracle/helidon/pull/1186)
- Upgrade of libraries related to tracing [1169](https://github.com/oracle/helidon/pull/1169)
- JPA: Relax requirements on container-managed persistence units which can now be resource-local [1245](https://github.com/oracle/helidon/pull/1245)
- JPA: Addressing several rollback-related issues [1189](https://github.com/oracle/helidon/pull/1189)


## [1.4.0] 

### Notes

This is a feature release of Helidon and adds support for MicroProfile 3.2.
It also includes a number of additional bug fixes and enhancements.

### Improvements

- MicroProfile 3.2 full and core bundles. Deprecate old bundles. [1143](https://github.com/oracle/helidon/pull/1143)
- MicroProfile Metrics 2.2 [1117](https://github.com/oracle/helidon/pull/1117)
- MicroProfile Health 2.1 [1092](https://github.com/oracle/helidon/pull/1092)
- WebServer: add support for form parameters [1093](https://github.com/oracle/helidon/pull/1093)
- Tracing: allow customization of top level span name using message format [1090](https://github.com/oracle/helidon/pull/1090)
- Metrics: support for mutable vendor registry [1098](https://github.com/oracle/helidon/pull/1098)
- MicroProfile: support reactive services and custom routing/port config [1073](https://github.com/oracle/helidon/pull/1073)

### Fixes

- JAX-RS: UriInfo#getBaseUri() returns URL with 1 character short [1110](https://github.com/oracle/helidon/pull/1110)
- JAX-RS: Inform Jersey of broken pipe by throwing exception [1076](https://github.com/oracle/helidon/pull/1076)
- Tracing: Service name not needed when tracing is disabled. [1086](https://github.com/oracle/helidon/pull/1086)
- Tracing: Fix Tracing nesting and outbound security [1082](https://github.com/oracle/helidon/pull/1082)
- Tracing: Jaeger integration support for native-image [1084](https://github.com/oracle/helidon/pull/1084)
- Metrics: Restore helidon-metrics2 as default metrics library [1077](https://github.com/oracle/helidon/pull/1077)
- Metrics: Rationalize some metrics 1.1 and 2.0 support code; add sync to both impls [1071](https://github.com/oracle/helidon/pull/1071)
- Metrics: Brings our 1.1 and 2.0 impls in line with clarification of metric reuse [1080](https://github.com/oracle/helidon/pull/1080)
- Media: Throw JsonException when parser returns unexpected type [1085](https://github.com/oracle/helidon/pull/1085)
- Media: Detect media type [1091](https://github.com/oracle/helidon/pull/1091)
- Config: Fix Injecting @ConfigProperty fails [1074](https://github.com/oracle/helidon/pull/1074)
- Fault Tolerance: Fault Tolerance doesn't work for Rest Client [1124](https://github.com/oracle/helidon/pull/1124)
- MP Server: Refine start-up time calculation [1100](https://github.com/oracle/helidon/pull/1100)
- WebServer: Fixes NPE in RequestRouting#canonicalize() [1072](https://github.com/oracle/helidon/pull/1072)
- Upgrade to Jackson 2.10.0 [1088](https://github.com/oracle/helidon/pull/1088)
- Upgrade to Netty 4.1.42 [1096](https://github.com/oracle/helidon/pull/1096)
- Remove com.google.code.findbugs:jsr305 [1119](https://github.com/oracle/helidon/pull/1119)
- Helidon build fixed on Windows [1097](https://github.com/oracle/helidon/pull/1097)
- Changes default logging to write to System.out [1145](https://github.com/oracle/helidon/pull/1145)
- Documentation [1075](https://github.com/oracle/helidon/pull/1075) [1123](https://github.com/oracle/helidon/pull/1123) [1118](https://github.com/oracle/helidon/pull/1118) [1105](https://github.com/oracle/helidon/pull/1105) [1079](https://github.com/oracle/helidon/pull/1079) [1129](https://github.com/oracle/helidon/pull/1129) [1131](https://github.com/oracle/helidon/pull/1131)

### Deprecations

- The following MicroProfile bundles are deprecated: `helidon-microprofile-3.0`,
  `helidon-microprofile-2.2`, `helidon-microprofile-1.2`, `helidon-microprofile-1.1`,
  `helidon-microprofile-1.0`. Use `helidon-microprofile` or
  `helidon-microprofile-core` instead.


## Experimental

The following enhancements are experimental. They should be considered unstable and subject
to change.

- Support for gRPC in MicroProfile server [910](https://github.com/oracle/helidon/pull/910) [1125](https://github.com/oracle/helidon/pull/1125) [1128](https://github.com/oracle/helidon/pull/1128) [1137](https://github.com/oracle/helidon/pull/1137)

## Thanks!

Thanks to community members [Sobuno](https://github.com/Sobuno) and [pa314159](https://github.com/pa314159) for their contributions.

## [1.3.1] 

### Notes

This is a bugfix release. It fixes a Helidon MP 
configuration regression on Windows: [1038](https://github.com/oracle/helidon/issues/1038), as
well as other fixes listed below.

### Fixes

- Config: Fix UrlConfigSource to work on windows [1039](https://github.com/oracle/helidon/pull/1039)
- JAX-RS resources now have CDI dependent scope by default. [1050](https://github.com/oracle/helidon/pull/1050)
- Metrics: Allow config to disable all base metrics easily  [1052](https://github.com/oracle/helidon/pull/1052)
- Metrics: Fully enforce reusability in MP Metrics 1.1. support [1048](https://github.com/oracle/helidon/pull/1048)
- Security: Use RSA with padding [1036](https://github.com/oracle/helidon/pull/1036)
- Update POM names for consistency [1031](https://github.com/oracle/helidon/pull/1031) [1032](https://github.com/oracle/helidon/pull/1032)
- Add stand-alone pom examples [1031](https://github.com/oracle/helidon/pull/1031)
- Remove Oracle Maven Repository dependency [1040](https://github.com/oracle/helidon/pull/1040)
- Fixes a reference to a non-existent ResourceBundle [1058](https://github.com/oracle/helidon/pull/1058)
- Add exclusions for org.osgi:org.osgi.annotation.versioning [1034](https://github.com/oracle/helidon/pull/1034)
- Documentation fixes [1043](https://github.com/oracle/helidon/pull/1043) [1053](https://github.com/oracle/helidon/pull/1053) 
- Documentation: add tracing guides [1023](https://github.com/oracle/helidon/pull/1023) [1014](https://github.com/oracle/helidon/pull/1014)
- Examples fixes [1056](https://github.com/oracle/helidon/pull/1056)
- Build fixes [1027](https://github.com/oracle/helidon/pull/1027) [1033](https://github.com/oracle/helidon/pull/1033) [1035](https://github.com/oracle/helidon/pull/1035)

## [1.3.0] 

### Notes

This release is a feature release and adds support for MicroProfile 3.0 and
support for Hibernate as a JPA provider. It also includes a number of additional
bug fixes and enhancements.

**Note:** MicroProfile 3.0 breaks compatibility with MicroProfile 2.2 -- specifically
in the area of Metrics. Since Helidon 1.3 continues to support MicroProfile 2.2
your applications that depend on the MicroProfile 2.2 APIs should continue to work
when you upgrade to Helidon 1.3. But when you switch to MicroProfile 3.0 you will
likely need to change code that uses the MicroProfile Metrics API. For more
information see sections 6.5 and Chapter 7 of
[microprofile-metrics-spec-2.0.pdf](https://github.com/eclipse/microprofile-metrics/releases/download/2.0/microprofile-metrics-spec-2.0.pdf)

As part of this release we are also deprecating APIs for possible removal
in a future release. Please see the Deprecations section below.

### Improvements

- MicroProfile 3.0
- MicroProfile Metrics 2.0.1 [992](https://github.com/oracle/helidon/pull/992)
- MicroProfile REST Client 1.3 [1010](https://github.com/oracle/helidon/pull/1010)
- Security: New SecureUserStore [989](https://github.com/oracle/helidon/pull/989)
- Security: Allow injection of Security through CDI [986](https://github.com/oracle/helidon/pull/986)
- Security: Jersey client: propagate SecurityContext automatically [943](https://github.com/oracle/helidon/pull/943)
- JPA: Add Hibernate support [894](https://github.com/oracle/helidon/pull/894)
- OpenAPI: Support admin port [999](https://github.com/oracle/helidon/pull/999)
- Health check endpoint can now be disabled [990](https://github.com/oracle/helidon/pull/990)
- Introduce parent poms for applications to reduce Maven boilerplate [1022](https://github.com/oracle/helidon/pull/1022)
- Documentation: new guides [898](https://github.com/oracle/helidon/pull/898) [890](https://github.com/oracle/helidon/pull/890) [918](https://github.com/oracle/helidon/pull/918) [925](https://github.com/oracle/helidon/pull/925) [942](https://github.com/oracle/helidon/pull/942) [959](https://github.com/oracle/helidon/pull/959) [961](https://github.com/oracle/helidon/pull/961) [974](https://github.com/oracle/helidon/pull/974) [987](https://github.com/oracle/helidon/pull/987) [996](https://github.com/oracle/helidon/pull/996) [1011](https://github.com/oracle/helidon/pull/892) [1008](https://github.com/oracle/helidon/pull/1008)
- Helidon bare archetypes [950](https://github.com/oracle/helidon/pull/950) [995](https://github.com/oracle/helidon/pull/995)

### Fixes

- Upgrade Jersey to 2.29.1 [1010](https://github.com/oracle/helidon/pull/1010)
- Upgrade Jackson databind to 2.9.9.3 [969](https://github.com/oracle/helidon/pull/969)
- GraalVM: Support static content for Graal native-image [962](https://github.com/oracle/helidon/pull/962)
- GraalVM: Support for Jersey in Graal native-image [985](https://github.com/oracle/helidon/pull/985)
- Fix NullPointerException in ConfigCdiExtension; repairs erroneous private constructors in JPA and JTA integrations [1005](https://github.com/oracle/helidon/pull/1005)
- Config: Fix precedence for explicit sys prop and env var sources [1002](https://github.com/oracle/helidon/pull/1002)
- bom/pom.xml cleanup [980](https://github.com/oracle/helidon/pull/980)
- TracerBuilder now uses Helidon service loader [967](https://github.com/oracle/helidon/pull/967)
- Reactive utilities [1003](https://github.com/oracle/helidon/pull/1003)
- Tests: improve AccessLogSupportTest and fix test with locales other than en_US [998](https://github.com/oracle/helidon/pull/998)
- Documentation: javadoc cleanup [993](https://github.com/oracle/helidon/pull/993) [997](https://github.com/oracle/helidon/pull/997)

### Deprecations

The following APIs are deprecated and will be removed in a future release:

- MicroProfile 2.2 and earlier. Use MicroProfile 3.0.
- The `@SecureClient` annotation. Security propagation is now automatic across the MicroProfile and Jersey REST clients.
- `UserStore` in basic authentication. Replaced by `SecureUserStore`.
- All methods that use `Span` in Webserver APIs (and Security etc.). Use `SpanContext` instead.
- All methods that provide general health checks and not readiness or liveness checks. For example
  use `HealthSupportBuilder.addLiveness()` instead of `HealthSupportBuilder.add()`
- `io.helidon.common.reactive.SubmissionPublisher`
- The `io.helidon.service.configuration` package.
- The `io.helidon.common.reactive.valve` package.
- Any other classes and methods that have been annotated as deprecated.


## [1.2.1] - 2019-08-21

### Notes

This release contains bug and performance fixes as well as support for
the latest releases of GraalVM (19.1.1 and 19.2.0)

### Fixes

- Tracing: Zipkin integration no longer fails in MP due to class cast exception. [901](https://github.com/oracle/helidon/pull/901)
- Jersey client to automatically propagate context when using asynchronous operations. [905](https://github.com/oracle/helidon/pull/905)
- Smart resizing of Jersey thread pool [871](https://github.com/oracle/helidon/pull/871)
- WebServer: Upgrade Netty to 4.1.39 [899](https://github.com/oracle/helidon/pull/899)
- WebServer: Change Netty workers default to equal available processors. [874](https://github.com/oracle/helidon/pull/874)
- JPA: Add support for unitName-less PersistenceContext injection points [914](https://github.com/oracle/helidon/pull/914)
- JPA: Promoting JPA integration to supported status [908](https://github.com/oracle/helidon/pull/908)
- Health: Healthchecks moved to liveness checks. [903](https://github.com/oracle/helidon/pull/903)
- MP Server: Logging a warning when more than one MP server is started. [902](https://github.com/oracle/helidon/pull/902)
- GraalVM: Changes for Zipkin and Netty to run with latest Graal VM native-image [915](https://github.com/oracle/helidon/pull/915)
- Security: Properly log audit event [886](https://github.com/oracle/helidon/pull/886)
- OpenAPI: Allow different servers within an app to report different OpenAPI doc [883](Allow different servers within an app to report different OpenAPI doc)
- Correct erroneous assert statement in JedisExtension [917](https://github.com/oracle/helidon/pull/917)
- UCP Unit test [889](https://github.com/oracle/helidon/pull/889)
- Examples: Add H2-based example for the Hikari connection pool [898](https://github.com/oracle/helidon/pull/898)
- Documentation fixes [892](https://github.com/oracle/helidon/pull/892) [898](https://github.com/oracle/helidon/pull/898) [906](https://github.com/oracle/helidon/pull/906)


## [1.2.0] - 2019-07-29

### Notes

This release contains full support for MicroProfile 2.2. We also have a few new 
enhancements and bug and performances fixes.

### Improvements

- MicroProfile OpenTracing 1.3.1 [826](https://github.com/oracle/helidon/pull/826)
- MicroProfile REST Client 1.2.1 [407](https://github.com/oracle/helidon/issues/407)
- MicroProfile Health 2.0 [835](https://github.com/oracle/helidon/pull/835)
- WebServer: Access log support [800](https://github.com/oracle/helidon/pull/800) [837](https://github.com/oracle/helidon/pull/837)
- WebServer: Support for HTTP/2 negotiation with TLS/APLN [807](https://github.com/oracle/helidon/pull/807)
- Config: credentials support added to git config [810](https://github.com/oracle/helidon/pull/801)
- Early Access: support for the Oracle Universal Connection Pool [777](https://github.com/oracle/helidon/pull/777)
- Early Access: JPA/JTA: Support analog to extended persistence contexts [639](https://github.com/oracle/helidon/issues/639)

### Fixes

- WebServer: Return a 400 response for a request with an invalid URL [796](https://github.com/oracle/helidon/pull/796)
- WebServer: Changes to content length optimization to handle flushes [783](https://github.com/oracle/helidon/pull/783)
- WebServer: Refine content length support [795](https://github.com/oracle/helidon/pull/795)
- Upgrade Jersey to 2.29 [813](https://github.com/oracle/helidon/pull/813)
- Upgrade jackson databind to 2.9.9 [797](https://github.com/oracle/helidon/pull/797)
- Enable Netty autoread only after completing a response [823](https://github.com/oracle/helidon/pull/823)
- Tracing: add Tracing configuration module [809](https://github.com/oracle/helidon/pull/809)
- Tracing: Fix constant sampler name for Jaeger [828](https://github.com/oracle/helidon/pull/828)
- Tracing: IDCS Role mapper: calls to IDCS not associated with the call [794](https://github.com/oracle/helidon/issues/794)
- Config: Enable support of application.yaml use case in data source injection extensions [847](https://github.com/oracle/helidon/pull/847)
- Config: Correct traversal logic for MpConfig when acquiring configuration property names [845](https://github.com/oracle/helidon/pull/845)
- MicroProfile Config: MpConfig will never include Helidon Config property names in the return value of getPropertyNames() [844](https://github.com/oracle/helidon/issues/844)
- Update examples and tests to use microprofile-2.2 bundle [843](https://github.com/oracle/helidon/pull/843)

## [1.1.2] - 2019-06-14

### Notes

This is a bug fix release. It includes improved support for setting Content-Length on server responses, and
MicroProfile Fault Tolerance now passes all TCKs.

### Improvements

- MicroProfile: Create Helidon MP 2.2 bundle: [765](https://github.com/oracle/helidon/pull/765)
- Fault Tolerance: Completed support for async bulkheads [755](https://github.com/oracle/helidon/pull/755)

### Fixes

- WebServer: Content-length optimization [773](https://github.com/oracle/helidon/pull/773) [783](https://github.com/oracle/helidon/pull/783)
- WebServer: JAX-RS: Fixes support for application calls to flush() when using StreamingOutput. [758](https://github.com/oracle/helidon/pull/758)
- Security: Encryption changed to GCM method
- CDI Extension: JPA: disable DTDs and external entities [774](https://github.com/oracle/helidon/pull/774)
- OpenAPI: Expand openapi mediatypes [766](https://github.com/oracle/helidon/pull/766)
- Security: Remove unneeded dependency on JAXB [779](https://github.com/oracle/helidon/pull/779)
- Security: Make IdcsMtRoleMapperProvider more flexible [761](https://github.com/oracle/helidon/pull/761)
- gRPC Server: propagate context [769](https://github.com/oracle/helidon/pull/769)
- Examples: Add employee-app example [747](https://github.com/oracle/helidon/pull/747)
- Testing: Added Unit Tests to increase code coverage [660](https://github.com/oracle/helidon/pull/660)

## [1.1.1] - 2019-05-23

### Notes

This release adds support for MicroProfile OpenAPI 1.1.2. It also includes bug and performance fixes including substantial performance improvement with MP when under heavy load, and better heap utilization with Keep-Alive connections.

### Improvements

- MicroProfile OpenAPI 1.1.2 support [712](https://github.com/oracle/helidon/pull/712)
- MP: Support logging.properties from classpath or from a file. [693](https://github.com/oracle/helidon/pull/693)

### Fixes

- WebServer: Make default sizing of Jersey thread pool dynamic, based on # of cores [691](https://github.com/oracle/helidon/pull/691)
- WebServer: Remove the channel closed listener in BareResponseImpl when request completes. [695](https://github.com/oracle/helidon/pull/695)
- WebServer: Complete writing a response before checking unconsumed request payload [699](https://github.com/oracle/helidon/pull/699)
- WebServer: Ensure channel closed listener is removed on exception [702](https://github.com/oracle/helidon/pull/702)
- Metrics: Handle special case of NaN's to avoid NumberFormatException [723](https://github.com/oracle/helidon/pull/723)
- MicroProfile: Further `ConfigCdiExtension` fixes and improvements [724](https://github.com/oracle/helidon/pull/724)
- MicroProfile: Make a few FT params configurable via properties [725](https://github.com/oracle/helidon/pull/725)
- Upgrade Weld to 3.1.1.Final [659](https://github.com/oracle/helidon/pull/659)
- Upgrade Jandex to 2.1.1.Final [694](https://github.com/oracle/helidon/pull/694)
- Upgrade Oracle OCI SDK to 1.5.2 [715](https://github.com/oracle/helidon/pull/715)
- Fix incorrect `isAssignableFrom` [728](https://github.com/oracle/helidon/pull/728) [731](https://github.com/oracle/helidon/pull/731) [732](https://github.com/oracle/helidon/pull/732) [734](https://github.com/oracle/helidon/pull/734) [735](https://github.com/oracle/helidon/pull/735)
- Enable ThreadPool injection into Application. [713](https://github.com/oracle/helidon/pull/713)
- Use jandex index in MP quickstart [714](https://github.com/oracle/helidon/pull/714)
- Documentation fixes [697](https://github.com/oracle/helidon/pull/697) [704](https://github.com/oracle/helidon/pull/704) [719](https://github.com/oracle/helidon/pull/719)

## [1.1.0] - 2019-05-14

### Notes

This release contains bug fixes and a number of early implementations of 
significant new features.

### Improvements

These features represent work in progress and should be considered experimental.
These APIs are subject to change. 

- Initial implementation of gRPC server/framework [543](https://github.com/oracle/helidon/pull/543)
- Initial JTA/JPA support for MP [501](https://github.com/oracle/helidon/pull/501)
- Initial implementation of OpenApi support for SE [558](https://github.com/oracle/helidon/pull/558)
- Initial implementation of the common context [600](https://github.com/oracle/helidon/pull/600) [599](https://github.com/oracle/helidon/pull/599)

### Fixes

- Graal native image update to work with 19 GA [677](https://github.com/oracle/helidon/pull/677)
- Tracing: Activate and start instead of just starting the main span [598](https://github.com/oracle/helidon/pull/598)
- Config: refactor change support [579](https://github.com/oracle/helidon/pull/579)
- Config: Config object mapping was missing from bom pom. [529](https://github.com/oracle/helidon/pull/529)
- Config: MpcSourceEnvironmentVariablesTest::testPrecedence fails on Windows  [636](https://github.com/oracle/helidon/issues/636)
- Security: OIDC Provider : Incorrect error if scopes do not match security requirement [661](https://github.com/oracle/helidon/issues/661)
- Security: Scope annotation not working alone [646](https://github.com/oracle/helidon/issues/646)
- Security: Sub resource locator authorization does not work [552](https://github.com/oracle/helidon/issues/552)
- Security: OIDC fails with Okta identity provider [642](https://github.com/oracle/helidon/issues/642)
- Security: Multitenancy support for IDCS Role Mapping [571](https://github.com/oracle/helidon/pull/571)
- Security: JWT must be signed unless explicitly allowing unsigned JWTs. [523](https://github.com/oracle/helidon/pull/523)
- Security: Primitive types long and boolean now supported by MP-JWT-Auth [586](https://github.com/oracle/helidon/pull/586)
- WebServer: Request.Context copy ctor should not append default readers [653](https://github.com/oracle/helidon/issues/653)
- WebServer: JSON-Binding breaks prometheus metrics format [645](https://github.com/oracle/helidon/issues/645)
- WebServer: Removed unnecessary synchronization in content readers [576](https://github.com/oracle/helidon/pull/576)
- WebServer: Minimize writer creation in Response [603](https://github.com/oracle/helidon/pull/603)
- WebServer: Add enabled enabledSSLProtocols methods on ServerConfiguration.Builder [590](https://github.com/oracle/helidon/issues/590)
- WebServer: NettyWebServer uses a deprecated constructor for JdkSslContext  [519](https://github.com/oracle/helidon/issues/519)
- Documentation updates:  [671](https://github.com/oracle/helidon/pull/671) [634](https://github.com/oracle/helidon/pull/634) [620](https://github.com/oracle/helidon/pull/620) [584](https://github.com/oracle/helidon/pull/584) [608](https://github.com/oracle/helidon/pull/608) [589](https://github.com/oracle/helidon/pull/589)
- Add Implementation headers to jar MANIFEST [618](https://github.com/oracle/helidon/pull/618)
- Common Service Loader [621](https://github.com/oracle/helidon/issues/621)
- Update SnakeYAML to 1.24 [626](https://github.com/oracle/helidon/pull/626)
- Fix the TODO application example [604](https://github.com/oracle/helidon/pull/604)
- JSON handling improvements [609](https://github.com/oracle/helidon/pull/609)

## [1.0.3] - 2019-04-16

### Notes

This release contains bug and documentation fixes as well as progress towards
implementing MicroProfile 2.2. It also improves GraalVM native image support
in our SE quickstart example.

### Improvements

- Update MicroProfile Config to 1.3  [537](https://github.com/oracle/helidon/pull/537)
- Update MicroProfile Fault Tolerance to 2.0 [555](https://github.com/oracle/helidon/pull/555)
- Add GraalVM support to quickstart examples [574](https://github.com/oracle/helidon/pull/574) [547](https://github.com/oracle/helidon/pull/547)
- WebServer: Provide configuration for enabled SSL protocols [530](https://github.com/oracle/helidon/pull/530)
- Quickstart examples now use multi-stage Docker build [547](https://github.com/oracle/helidon/pull/547)

### Fixes

- Update Jackson to 2.9.8 [559](https://github.com/oracle/helidon/issues/559)
- Documentation: updates [544](https://github.com/oracle/helidon/pull/544) [531](https://github.com/oracle/helidon/pull/531) [584](https://github.com/oracle/helidon/pull/584) 
- Config: Fix race condition in SubscriberInputStream [540](https://github.com/oracle/helidon/pull/540)
- Config object mapping missing from bom pom [529](https://github.com/oracle/helidon/pull/529)
- Tracing: Finish write-content span before completing delegate [563](https://github.com/oracle/helidon/pull/563)
- Explicit authorization no longer hides other HTTP errors. [572](https://github.com/oracle/helidon/pull/572)


## [1.0.2] - 2019-03-21

### Fixes

- Config: SE config should map env vars the same way that MP does [446](https://github.com/oracle/helidon/issues/446)
- Config: Refactor usage of exceptions in optional sources [477](https://github.com/oracle/helidon/issues/477)
- WebServer: 404 Response without body [390](https://github.com/oracle/helidon/issues/390)

## [1.0.1] - 2019-03-13

### Notes

This release brings you GraalVM support in Helidon SE and a variety of
bug fixes and documentation improvements.

### Improvements

- Add support for GraalVM in Helidon SE
  [499](https://github.com/oracle/helidon/pull/499)
- Security: add JWT-Auth configuration to control secure by default
  [465](https://github.com/oracle/helidon/pull/465)
- Assorted documentation updates
  [418](https://github.com/oracle/helidon/pull/418) 
  [424](https://github.com/oracle/helidon/pull/424) 
  [435](https://github.com/oracle/helidon/pull/435) 
  [440](https://github.com/oracle/helidon/pull/440) 
  [448](https://github.com/oracle/helidon/pull/448) 

### Fixes

- WebServer: MetricSupport and RegistryFactory can be accessed in any order now
  [457](https://github.com/oracle/helidon/issues/457)
- WebServer: cleanup and optimizations when writing data from Jersey
  [463](https://github.com/oracle/helidon/pull/463)
- WebServer: NPE in ForwardingHandler
  [430](https://github.com/oracle/helidon/issues/430)
- Performance improvements
  [423](https://github.com/oracle/helidon/pull/423) 
- Security: NPE when IDCS returns no groups
  [454](https://github.com/oracle/helidon/pull/454)
- Json processing support now uses default encoding of JSON-P
  [421](https://github.com/oracle/helidon/pull/421)

## [1.0.0] - 2019-02-12

### Notes

This is our 1.0 release and we have finished the API changes that we've
been working on over the last few months. From this point on we will
have much greater API stability. Thanks for your patience.

If you are upgrading from 0.11.0 note that media support (for example JsonSupport)
has moved to a top level `media` component. This means you might have to change
your dependencies and Java imports. For more details see:
[API Changes](https://github.com/oracle/helidon/wiki/API-Changes-in-1.0). 

If you are upgrading from 0.10.5 or earlier you will need to change
your application due to a number of API changes we made in preperation
for 1.0. For details on API changes see:
[API Changes](https://github.com/oracle/helidon/wiki/API-Changes-in-1.0)

Helidon 1.0 supports [MicroProfile 1.2](https://github.com/eclipse/microprofile-bom/releases/download/1.2/microprofile-spec-1.2.pdf)
with updated versions of the components.

### Improvements

- WebServer: Add JSON-B (Yasson) support [388](https://github.com/oracle/helidon/pull/388)
- WebServer: Add Jackson support [351](https://github.com/oracle/helidon/pull/351)
- MicroProfile: update Hystrix to 1.5.18 [391](https://github.com/oracle/helidon/pull/391)
- Examples: Change quickstart examples so that PUT uses json and not path param [399](https://github.com/oracle/helidon/pull/399)

### Fixes

- WebServer: Fix for NullPointer when using Json support without configured charset [393](https://github.com/oracle/helidon/pull/393)

## [0.11.1] - 2019-02-07

### Notes

If you are upgrading from 0.10.5 or earlier you will need to change
your application (see Notes for 0.11.0 release). For details on API
changes see [API Changes](https://github.com/oracle/helidon/wiki/API-Changes-in-1.0)

If you are upgrading from 0.11.0 note that media support (for example JsonSupport)
has moved to a top level `media` component. Details
in [API Changes](https://github.com/oracle/helidon/wiki/API-Changes-in-1.0). 

Thanks for your patience with API changes. Once 1.0 is released this will
be behind us!

### Improvements

- Security: Support PermitAll and DenyAll annotations [334](https://github.com/oracle/helidon/pull/334)
- WebServer: Implement RequestPredicate [325](https://github.com/oracle/helidon/pull/325)
- WebServer: Move media processing separated to a top level module [326](https://github.com/oracle/helidon/pull/326)
- Documentation: Add CDI extensions documentation [324](https://github.com/oracle/helidon/pull/324)
- Documentation: Various documentation and examples updates

### Fixes

- Metrics: Prometheus default help value added [375](https://github.com/oracle/helidon/pull/375)
- Security: Jersey client fails in MP 1.2 with no security configured [332](https://github.com/oracle/helidon/issues/332)
- WebServer: URL encoding of characters for Webserver and Jersey [370](https://github.com/oracle/helidon/pull/370)
- Use JsonBuilderFactory instead of Json.create [330](https://github.com/oracle/helidon/pull/330)

## [0.11.0] - 2019-01-11

### Notes

We've made a number of API changes for this release in preparation for our
1.0 release. This means when you upgrade to 0.11.0 you will need to make
changes to your application. We apologies for the inconvenience, but we
wanted to get these changes in before 1.0. For details see
[API Changes](https://github.com/oracle/helidon/wiki/API-Changes-in-1.0)

### Improvements

- API refactoring. See [API Changes](https://github.com/oracle/helidon/wiki/API-Changes-in-1.0) 
- WebServer: Add health support in SE [287](https://github.com/oracle/helidon/pull/287)
- MicroProfile: JWT Auth [208](https://github.com/oracle/helidon/pull/208) 
- MicroProfile: update to Fault Tolerance 1.1.3 [253](https://github.com/oracle/helidon/pull/253)
- WebServer: update Netty to 4.1.30 [269](https://github.com/oracle/helidon/pull/269)
- CDI Extensions: Add MySQL CDI integration example [284](https://github.com/oracle/helidon/pull/284)
- Config: GenericType support for config mapping [238](https://github.com/oracle/helidon/pull/238)
- Config: Java Beans support [197](https://github.com/oracle/helidon/issues/197)
- Build: build on Windows [252](https://github.com/oracle/helidon/pull/252)
- Documentation: Add Creating Docker Images guide  [182](https://github.com/oracle/helidon/pull/182)
- Documentation: add development guidelines

### Fixes

- WebServer: detect and allow the default Accept header sent by HTTPURLConnection [309](https://github.com/oracle/helidon/pull/309)
- WebServer: Ensure proper path encoding with Jersey [317](https://github.com/oracle/helidon/pull/309)
- CDI Extensions: Add integrations modules to the bom pom [198](https://github.com/oracle/helidon/pull/198)
- Fault Tolerance: Memory improvement [180](https://github.com/oracle/helidon/pull/180) 
- Build: fails when compiling with Java 11 [225](https://github.com/oracle/helidon/issues/225)

## [0.10.6] - 2019-08-07

### Fixes

- WebServer (backport): Remove the channel closed listener in BareResponseImpl when request completes. [695](https://github.com/oracle/helidon/pull/695)

## [0.10.5] - 2018-11-06

### Fixes

- WebServer: Binary input stream truncated [159](https://github.com/oracle/helidon/issues/159)
- WebServer: Metrics endpoint provides JSON output by default [127](https://github.com/oracle/helidon/issues/127)
- WebServer: Static serving of a webpage does not work correctly [149](https://github.com/oracle/helidon/issues/149)
- Config direct values [134](https://github.com/oracle/helidon/pull/134)
- Documentation: Add health check and metrics to first guide [128](https://github.com/oracle/helidon/pull/128)

## [0.10.4] - 2018-10-19

### Fixes

- Update site to display and navigate to the guides [120](https://github.com/oracle/helidon/pull/120)
- Fixes minor error in OCI object storage integration [119](https://github.com/oracle/helidon/pull/119)

## [0.10.3] - 2018-10-18

### Improvements

- Helidon CDI Extensions [109](https://github.com/oracle/helidon/pull/109)
- Guide for building restful web services[117](https://github.com/oracle/helidon/pull/117)
- Experimental support for HTTP/2 [105](https://github.com/oracle/helidon/issues/105)

### Fixes

- WebServer: Prometheus fails to scrape Helidon metrics [111](https://github.com/oracle/helidon/issues/111)

## [0.10.2] - 2018-10-12

### Improvements

- MicroProfile Fault Tolerance 1.0 support [97](https://github.com/oracle/helidon/pull/97)
- WebServer: Support version 1 and 2 of Zipkin API and configuration based building [87](https://github.com/oracle/helidon/pull/87)

### Fixes

- WebServer: Eager cleanup of queues to reduce memory usage during heavy loads [90](https://github.com/oracle/helidon/pull/90)
- WebServer: Fix for jigsaw service loading problem [99](https://github.com/oracle/helidon/pull/99)

## [0.10.1] - 2018-09-28

### Improvements

- Security: Jersey integration to use pre-matching filter for securing requests

### Fixes

- WebServer: RouteListRoutingRules post method has copy/paste errors
- WebServer: Display friendly message if port is in use
- WebServer: Set the option with SO_TIMEOUT with correct value from config
- Build: Helidon 0.10.0 build failed in local system. 
- Documentation: various updates

## [0.10.0] - 2018-09-14
### Notes

In this release we have refactored some HTTP classes and moved
them from `io.helidon.webserver` to a new package `io.helidon.common.http`.
This is an incompatible change and you will need to update your `import`
statements. See
[javadocs](https://helidon.io/docs/0.10.0/apidocs/io/helidon/common/reactive/package-summary.html)
for details.

### Improvements
- Security: improve support for IDCS subject mapping

### Fixes
- Webserver: refactor common HTTP classes to common module
- Documentation: correct various links in documentation
- Security: updates to support chain of JWT and basic auth with OIDC roles
- Archetypes: fix formatting issue in generated pom.xml 

## [0.9.1] - 2018-09-07

### Notes
- Integrate helidon-sitegen 1.0.0, enable the docs

## 0.9.0 - 2018-09-07

### Notes
- Initial source drop on Github

[1.4.8-SNAPSHOT]: https://github.com/oracle/helidon/compare/1.4.7..helidon-1.x
[1.4.7]: https://github.com/oracle/helidon/compare/1.4.6..1.4.7
[1.4.6]: https://github.com/oracle/helidon/compare/1.4.5..1.4.6
[1.4.5]: https://github.com/oracle/helidon/compare/1.4.4..1.4.5
[1.4.4]: https://github.com/oracle/helidon/compare/1.4.3..1.4.4
[1.4.3]: https://github.com/oracle/helidon/compare/1.4.2..1.4.3
[1.4.2]: https://github.com/oracle/helidon/compare/1.4.1..1.4.2
[1.4.1]: https://github.com/oracle/helidon/compare/1.4.0..1.4.1
[1.4.0]: https://github.com/oracle/helidon/compare/1.3.1...1.4.0
[1.3.1]: https://github.com/oracle/helidon/compare/1.3.0...1.3.1
[1.3.0]: https://github.com/oracle/helidon/compare/1.2.1...1.3.0
[1.2.1]: https://github.com/oracle/helidon/compare/1.2.0...1.2.1
[1.2.0]: https://github.com/oracle/helidon/compare/1.1.2...1.2.0
[1.1.2]: https://github.com/oracle/helidon/compare/1.1.1...1.1.2
[1.1.1]: https://github.com/oracle/helidon/compare/1.1.0...1.1.1
[1.1.0]: https://github.com/oracle/helidon/compare/1.0.3...1.1.0
[1.0.3]: https://github.com/oracle/helidon/compare/1.0.2...1.0.3
[1.0.2]: https://github.com/oracle/helidon/compare/1.0.1...1.0.2
[1.0.1]: https://github.com/oracle/helidon/compare/1.0.0...1.0.1
[1.0.0]: https://github.com/oracle/helidon/compare/0.11.0...1.0.0
[0.11.1]: https://github.com/oracle/helidon/compare/0.11.0...0.11.1
[0.11.0]: https://github.com/oracle/helidon/compare/0.10.5...0.11.0
[0.10.6]: https://github.com/oracle/helidon/compare/0.10.5...0.10.6
[0.10.5]: https://github.com/oracle/helidon/compare/0.10.4...0.10.5
[0.10.4]: https://github.com/oracle/helidon/compare/0.10.3...0.10.4
[0.10.3]: https://github.com/oracle/helidon/compare/0.10.2...0.10.3
[0.10.2]: https://github.com/oracle/helidon/compare/0.10.1...0.10.2
[0.10.1]: https://github.com/oracle/helidon/compare/0.10.0...0.10.1
[0.10.0]: https://github.com/oracle/helidon/compare/0.9.1...0.10.0
[0.9.1]: https://github.com/oracle/helidon/compare/0.9.0...0.9.1
