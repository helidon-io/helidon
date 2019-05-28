
# Changelog

All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Notes

### Improvements

### Fixes

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

[Unreleased]: https://github.com/oracle/helidon/compare/1.1.1...HEAD
[1.1.1]: https://github.com/oracle/helidon/compare/1.1.0...1.1.1
[1.1.0]: https://github.com/oracle/helidon/compare/1.0.3...1.1.0
[1.0.3]: https://github.com/oracle/helidon/compare/1.0.2...1.0.3
[1.0.2]: https://github.com/oracle/helidon/compare/1.0.1...1.0.2
[1.0.1]: https://github.com/oracle/helidon/compare/1.0.0...1.0.1
[1.0.0]: https://github.com/oracle/helidon/compare/0.11.0...1.0.0
[0.11.1]: https://github.com/oracle/helidon/compare/0.11.0...0.11.1
[0.11.0]: https://github.com/oracle/helidon/compare/0.10.5...0.11.0
[0.10.5]: https://github.com/oracle/helidon/compare/0.10.4...0.10.5
[0.10.4]: https://github.com/oracle/helidon/compare/0.10.3...0.10.4
[0.10.3]: https://github.com/oracle/helidon/compare/0.10.2...0.10.3
[0.10.2]: https://github.com/oracle/helidon/compare/0.10.1...0.10.2
[0.10.1]: https://github.com/oracle/helidon/compare/0.10.0...0.10.1
[0.10.0]: https://github.com/oracle/helidon/compare/0.9.1...0.10.0
[0.9.1]: https://github.com/oracle/helidon/compare/0.9.0...0.9.1
