
# Changelog

All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

For Helidon 2.x releases please see [Helidon 2.x CHANGELOG.md](https://github.com/oracle/helidon/blob/helidon-2.x/CHANGELOG.md)

For Helidon 1.x releases please see [Helidon 1.x CHANGELOG.md](https://github.com/oracle/helidon/blob/helidon-1.x/CHANGELOG.md)

## [3.0.0-SNAPSHOT]

This is the second milestone release of Helidon 3.0.

### Compatibility

### CHANGES

## [3.0.0-M1]

This is the first milestone release of Helidon 3.0. It contains significant new
features, enhancements and fixes. It also contains backward incompatible changes.
( see [Compatibility](#compatibility) below). This milestone release is provided for customers that
want early access to Helidon 3.0 for experimentation. It should be considered
unstable and is not intended for production use. APIs and features might not be
fully implemented and tested and are subject to change. Documentation is incomplete.
Those looking for a stable release should use Helidon 2.

Notable changes:

- MicroProfile 5.0 support
- javax to jakarta Java package namespace change for Jakarta 9 APIs
- Java 11 no longer supported. Java 17 or newer is required.

### Compatibility

3.0.0-M1 contains the following incompatibilities with 2.x:

- Java 17 or newer is required
- Java package namespace for Jakarta APIs has changed from `javax` to `jakarta`

### CHANGES

- Common: Builder API updates. [3641](https://github.com/oracle/helidon/pull/3641)
- Config: 3542 iterable configuration option [3600](https://github.com/oracle/helidon/pull/3600)
- Config: MicroProfile Config 3.0 [3644](https://github.com/oracle/helidon/pull/3644)
- Config: Support for mutable file based MP config sources. (#3666) [3730](https://github.com/oracle/helidon/pull/3730)
- DBClient: Fix dbclient threading issues when DML operations are executed multiple times in a tight loop [3849](https://github.com/oracle/helidon/pull/3849)
- Fault Tolerance: Implementation of Fault tolerance 4.0  [3664](https://github.com/oracle/helidon/pull/3664)
- Fault Tolerance: Improved support for cancellation of FT handlers [3680](https://github.com/oracle/helidon/pull/3680)
- Fault Tolerance: Only deactivate request context if it was inactive before migrating it (master) [3825](https://github.com/oracle/helidon/pull/3825)
- Grpc: Remove SecurityManager and AccessController dependencies in Grpc [3685](https://github.com/oracle/helidon/pull/3685)
- Health: Add support for Health 4.0 [3707](https://github.com/oracle/helidon/pull/3707)
- JAX-RS: Handle creation of InjectionManager when parent is a HelidonInjectionManager [3755](https://github.com/oracle/helidon/pull/3755)
- JAX-RS: Special treatment for ParamConverterProviders with multiple apps (master) [3857](https://github.com/oracle/helidon/pull/3857)
- Java 17: change sources to Java 17 [3633](https://github.com/oracle/helidon/pull/3633)
- LRA: Bump up to jakartified LRA 2.0 [3769](https://github.com/oracle/helidon/pull/3769)
- LRA: Missing wget in the coordinator's docker base image [3703](https://github.com/oracle/helidon/pull/3703)
- Logging: HelidonFormatter constructor made public [3609](https://github.com/oracle/helidon/pull/3609)
- Metrics: Add incorrectly-omitted tags to OpenMetrics (Prometheus) output for timers [3643](https://github.com/oracle/helidon/pull/3643)
- Metrics: Fix some remaining problems with disabling metrics, mostly deferring access to RegistryFactory (3.0) [3665](https://github.com/oracle/helidon/pull/3665)
- Metrics: Implement metrics for thread pool suppliers (3.0) [3652](https://github.com/oracle/helidon/pull/3652)
- Metrics: Metrics 4.0 implementation [3847](https://github.com/oracle/helidon/pull/3847)
- Metrics: Move scheduling of metrics periodic updater so it is run in MP as well as in SE  (3.x) [3733](https://github.com/oracle/helidon/pull/3733)
- Metrics: Prepare RegistryFactory lazily to use the most-recently-assigned MetricsSettings (3.0) [3661](https://github.com/oracle/helidon/pull/3661)
- Metrics: SamplerType now public. [3788](https://github.com/oracle/helidon/pull/3788)
- Metrics: Suppress warning when metrics PeriodicExecutor is stopped multiple times (e.g., during tests) [3617](https://github.com/oracle/helidon/pull/3617)
- Metrics: add missing tags for Prometheus output of timers [3647](https://github.com/oracle/helidon/pull/3647)
- MicroProfile: Update MP specs to latest versions [3670](https://github.com/oracle/helidon/pull/3670) [3743](https://github.com/oracle/helidon/pull/3743)
- MicroProfile: jakarta packages [3635](https://github.com/oracle/helidon/pull/3635)
- Native-image: Update native-image.properties for webserver and Netty [3772](https://github.com/oracle/helidon/pull/3772)
- OpenAPI: Catch all exceptions, not just IOException, when unable to read Jandex file [3626](https://github.com/oracle/helidon/pull/3626)
- OpenAPI: Redesign the per-application OpenAPI processing [3615](https://github.com/oracle/helidon/pull/3615)
- OpenAPI: Support MP OpenAPI 3.0 [3692](https://github.com/oracle/helidon/pull/3692)
- Reactive: Multi defaultIfEmpty [3592](https://github.com/oracle/helidon/pull/3592)
- Scheduling: SE Scheduling remove synchronized [3564](https://github.com/oracle/helidon/pull/3564)
- Security: Fix proxy configuration. (#3749) [3761](https://github.com/oracle/helidon/pull/3761)
- Security: JWK keys lazy load [3751](https://github.com/oracle/helidon/pull/3751)
- Security: JWT Auth 2.0 impl [3872](https://github.com/oracle/helidon/pull/3872)
- Security: SignedJwt's parseToken() expects characters from base64 instead of ba… [3722](https://github.com/oracle/helidon/pull/3722)
- Security: Upgrade MicroProfile JWT to 2.0 from 2.0-RC2 [3779](https://github.com/oracle/helidon/pull/3779)
- Test: TestNG support (#3263) [3672](https://github.com/oracle/helidon/pull/3672)
- Tracing: Close active span if an exception is thrown in client request processing [3725](https://github.com/oracle/helidon/pull/3725)
- WebClient: Do not create close listener handlers for every new request (master) [3859](https://github.com/oracle/helidon/pull/3859)
- WebClient: Netty order of writes #3674 [3681](https://github.com/oracle/helidon/pull/3681)
- WebClient: Propagate any existing server context into a Webclient reactive code (master) [3804](https://github.com/oracle/helidon/pull/3804)
- WebClient: event group initialization changed [3820](https://github.com/oracle/helidon/pull/3820)
- WebServer: 3640 Netty mixed writing (#3671) [3679](https://github.com/oracle/helidon/pull/3679)
- WebServer: Allow compression to be enabled together with HTTP/2 [3700](https://github.com/oracle/helidon/pull/3700)
- WebServer: Ensure all thread pools created by Helidon are named [3794](https://github.com/oracle/helidon/pull/3794)
- WebServer: Fix wrong connection close (#3830) [3866](https://github.com/oracle/helidon/pull/3866)
- WebServer: New default for io.netty.allocator.maxOrder (master) [3826](https://github.com/oracle/helidon/pull/3826) [3834](https://github.com/oracle/helidon/pull/3834)
- WebServer: Swallowed error fix [3791](https://github.com/oracle/helidon/pull/3791)
- Webclient: New flag to force the use of relative URIs in WebClient  [3614](https://github.com/oracle/helidon/pull/3614)
- Dependencies: Bump up cron utils [3677](https://github.com/oracle/helidon/pull/3677)
- Dependencies: Upgrade Neo4j to 4.4.3. for Helidon 3.x [3863](https://github.com/oracle/helidon/pull/3863)
- Dependencies: Upgrade grpc-java to 1.44.0 [3856](https://github.com/oracle/helidon/pull/3856)
- Dependencies: Upgrade logback to 1.2.10 [3889](https://github.com/oracle/helidon/pull/3889)
- Dependencies: Upgrades H2 to 2.0.206. [3744](https://github.com/oracle/helidon/pull/3744)
- Dependencies: Upgrades Netty to 4.1.73.Final [3797](https://github.com/oracle/helidon/pull/3797)
- Dependencies: Upgrades log4j to 2.17.1 [3777](https://github.com/oracle/helidon/pull/3777)
- Doc and JavaDoc fixes for #3747 and #3687. [3767](https://github.com/oracle/helidon/pull/3767)
- Docs: Documentation SE : Webclient guide [3189](https://github.com/oracle/helidon/pull/3189)
- Docs: LRA doc fix artefact and group ids [3688](https://github.com/oracle/helidon/pull/3688)
- Docs: New section about injection managers in docs (master) [3858](https://github.com/oracle/helidon/pull/3858)
- Docs: Update external Jakarta EE javadoc links [3709](https://github.com/oracle/helidon/pull/3709)
- Docs: javadoc: Add additional microprofile crossreferences [3715](https://github.com/oracle/helidon/pull/3715)
- Docs: macos quarantine and JDK version [3878](https://github.com/oracle/helidon/pull/3878)
- Examples: Archetype v2 [3874](https://github.com/oracle/helidon/pull/3874)
- Examples: Fix examples/grpc/metrics project with native-image #3759 [3803](https://github.com/oracle/helidon/pull/3803)
- Examples: Support Gradle application plugin in quickstart examples [3612](https://github.com/oracle/helidon/pull/3612)
- Examples: Update bare-mp archetype to use microprofile-core [3795](https://github.com/oracle/helidon/pull/3795)


[3.0.0-SHAPSHOT]: https://github.com/oracle/helidon/compare/3.0.0-M1...HEAD
[3.0.0-M1]: https://github.com/oracle/helidon/compare/2.4.0...3.0.0-M1
