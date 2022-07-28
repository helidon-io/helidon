
# Changelog

All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

For Helidon 2.x releases please see [Helidon 2.x CHANGELOG.md](https://github.com/oracle/helidon/blob/helidon-2.x/CHANGELOG.md)

For Helidon 1.x releases please see [Helidon 1.x CHANGELOG.md](https://github.com/oracle/helidon/blob/helidon-1.x/CHANGELOG.md)

## [3.0.1-SNAPSHOT]

This is a bugfix release of Helidon and is recommended for all users of Helidon 3.

### CHANGES

## [3.0.0]

We are pleased to announce Helidon 3.0.0 a major release that includes significant new features and fixes. As a major release it also includes some backward incompatible API changes.

### Notable Changes

- MicroProfile 5.0
- Jakarta EE 9.1 with `javax` to `jakarta` Java package namespace change
- Java 17 minimum JDK. Java 11 no longer supported.
- JEP-290 security hardening
- Updated Helidon SE routing API
- Numerous other enhancements and fixes

### Upgrading from Helidon 2

For information concerning upgrading your Helidon 2 application to Helidon 3 please see:

* [Helidon MP 3.x Upgrade Guide](https://helidon.io/docs/v3/#/mp/guides/migration_3x)
* [Helidon SE 3.x Upgrade Guide](https://helidon.io/docs/v3/#/se/guides/migration_3x)

### CHANGES from RC2

- JAX-RS: Set executor of CompletableFuture for response with no content. [4540](https://github.com/oracle/helidon/pull/4540) (Thank you @MadsBrun).
- Dependencies: Integrate Build Tools 3.0.0 [TBD](https://github.com/oracle/helidon/pull/TBD) [4580](https://github.com/oracle/helidon/pull/4580)
- Dependencies: Upgrade Tyrus. [4597](https://github.com/oracle/helidon/pull/4597)
- Archetype: Enable Database MP tests  [4570](https://github.com/oracle/helidon/pull/4570)
- Archetypes: Generate docker files for quickstart and database [4581](https://github.com/oracle/helidon/pull/4581)
- Archetypes: Generated GreetingResource contains incorrect javadoc [4599](https://github.com/oracle/helidon/pull/4599)
- Examples: Fix READMEs required jdk version [4572](https://github.com/oracle/helidon/pull/4572)
- Examples: Fix gRPC examples that are failing. [4585](https://github.com/oracle/helidon/pull/4585)
- Build: Remove -x option in CI scripts [4593](https://github.com/oracle/helidon/pull/4593)
- Doc updates: [4598](https://github.com/oracle/helidon/pull/4598) [4609](https://github.com/oracle/helidon/pull/4609) [4594](https://github.com/oracle/helidon/pull/4594) [4507](https://github.com/oracle/helidon/pull/4507) [4606](https://github.com/oracle/helidon/pull/4606) [4590](https://github.com/oracle/helidon/pull/4590) [4608](https://github.com/oracle/helidon/pull/4608) [4542](https://github.com/oracle/helidon/pull/4542) [4565](https://github.com/oracle/helidon/pull/4565) [4566](https://github.com/oracle/helidon/pull/4566) [4564](https://github.com/oracle/helidon/pull/4564) [4584](https://github.com/oracle/helidon/pull/4584) [4545](https://github.com/oracle/helidon/pull/4545) [4561](https://github.com/oracle/helidon/pull/4561) [4595](https://github.com/oracle/helidon/pull/4595) [4605](https://github.com/oracle/helidon/pull/4605) [4574](https://github.com/oracle/helidon/pull/4574) [4569](https://github.com/oracle/helidon/pull/4569) [4517](https://github.com/oracle/helidon/pull/4517) [4579](https://github.com/oracle/helidon/pull/4579)

## [3.0.0-RC2]

This is the second RC release of Helidon 3.0. It contains significant new
features, enhancements and fixes. It also contains backward incompatible changes.
(see Compatibility below). This milestone release is provided for customers that
want early access to Helidon 3.0 for experimentation. It should be considered
unstable and is not intended for production use. APIs and features might not be
fully implemented and tested and are subject to change. Documentation is incomplete.
Those looking for a stable release should use Helidon 2.

Notable changes from 2.x:

- MicroProfile 5.0 support
- javax to jakarta Java package namespace change for Jakarta 9 APIs
- Java 11 no longer supported. Java 17 or newer is required.

### Compatibility

3.0.0-RC2 contains the following incompatibilities with 2.x:

- Java 17 or newer is required
- Java package namespace for Jakarta APIs has changed from `javax` to `jakarta`
- Deprecated APIs have been removed

### CHANGES from RC1

Changes from RC1:

- Archetypes: 3.0 archetypes minor issues : Multiple fix to templates [4556](https://github.com/oracle/helidon/pull/4556)
- Archetypes: Fix Ambiguous dependencies for type PersistenceUnitInfo [4568](https://github.com/oracle/helidon/pull/4568)
- Dependencies: Bump Reactive Messaging API to 3.0-RC3 to allow CCR [4535](https://github.com/oracle/helidon/pull/4535)
- Dependencies: Integrate build tools 3.0.0-RC2 [4562](https://github.com/oracle/helidon/pull/4562)
- Deserialization configuration update. [4533](https://github.com/oracle/helidon/pull/4533)
- Native Image: Add module info to native image extensions. [4529](https://github.com/oracle/helidon/pull/4529)
- Documentation updates: [4546](https://github.com/oracle/helidon/pull/4546) [4541](https://github.com/oracle/helidon/pull/4541) [4478](https://github.com/oracle/helidon/pull/4478) [4558](https://github.com/oracle/helidon/pull/4558) [4520](https://github.com/oracle/helidon/pull/4520) [4560](https://github.com/oracle/helidon/pull/4560) [4506](https://github.com/oracle/helidon/pull/4506) [4543](https://github.com/oracle/helidon/pull/4543) [4557](https://github.com/oracle/helidon/pull/4557) [4563](https://github.com/oracle/helidon/pull/4563) [4521](https://github.com/oracle/helidon/pull/4521) [4536](https://github.com/oracle/helidon/pull/4536) [4515](https://github.com/oracle/helidon/pull/4515) [4513](https://github.com/oracle/helidon/pull/4513) [4525](https://github.com/oracle/helidon/pull/4525) [4527](https://github.com/oracle/helidon/pull/4527) [4505](https://github.com/oracle/helidon/pull/4505) [4555](https://github.com/oracle/helidon/pull/4555) [4469](https://github.com/oracle/helidon/pull/4469) [4477](https://github.com/oracle/helidon/pull/4477) [4528](https://github.com/oracle/helidon/pull/4528)

## [3.0.0-RC1]

This is the first RC release of Helidon 3.0. It contains significant new
features, enhancements and fixes. It also contains backward incompatible changes.
(see Compatibility below). This milestone release is provided for customers that
want early access to Helidon 3.0 for experimentation. It should be considered
unstable and is not intended for production use. APIs and features might not be
fully implemented and tested and are subject to change. Documentation is incomplete.
Those looking for a stable release should use Helidon 2.

Notable changes from 2.x:

- MicroProfile 5.0 support
- javax to jakarta Java package namespace change for Jakarta 9 APIs
- Java 11 no longer supported. Java 17 or newer is required.

### Compatibility

3.0.0-RC1 contains the following incompatibilities with 2.x:

- Java 17 or newer is required
- Java package namespace for Jakarta APIs has changed from `javax` to `jakarta`
- Deprecated APIs have been removed

### CHANGES from M2

- WebServer: Routing 3 [4236](https://github.com/oracle/helidon/pull/4236)
- WebServer: Removed deprecated classes from WebServer, mostly static content. [4376](https://github.com/oracle/helidon/pull/4376)
- WebServer: Remove deprecated method from `HelidonRestServiceSupport` [4393](https://github.com/oracle/helidon/pull/4393)
- WebServer: Nocache [4258](https://github.com/oracle/helidon/pull/4258)
- Tracing: Tracing abstraction for Helidon [4458](https://github.com/oracle/helidon/pull/4458)
- Tracing: Add OpenTelemetry support [4518](https://github.com/oracle/helidon/pull/4518)
- Test: ThreadPoolTest rc fix [4460](https://github.com/oracle/helidon/pull/4460)
- Test: Removed final from proxied types, these could no longer be proxied. [4489](https://github.com/oracle/helidon/pull/4489)
- Test: Re-enable Packaging Tests [4412](https://github.com/oracle/helidon/pull/4412)
- Test: Mock connector [4457](https://github.com/oracle/helidon/pull/4457)
- Test: Load both bean definitions of the same type instead of choosing just one [4494](https://github.com/oracle/helidon/pull/4494)
- Test: Intermittent SubscriberPublMsgToPaylRetComplVoidBean test fix [4474](https://github.com/oracle/helidon/pull/4474)
- Test: Intermittent SubscriberPublMsgToMsgRetComplBean test fix [4491](https://github.com/oracle/helidon/pull/4491)
- Test: Use 127.0.0.1 for client connections in test (instead of 0.0.0.0) [4279](https://github.com/oracle/helidon/pull/4279)
- Test: Add test-nightly.sh [4278](https://github.com/oracle/helidon/pull/4278)
- Security: RoleContainer support added [4271](https://github.com/oracle/helidon/pull/4271)
- Security: Remove deprecated method from Jwt. [4400](https://github.com/oracle/helidon/pull/4400)
- Security: OIDC update to support HTTPS identity provider on plain socket Helidon [4222](https://github.com/oracle/helidon/pull/4222)
- Security: HTTP Signatures: Fixed deprecation and modified defaults. [4399](https://github.com/oracle/helidon/pull/4399)
- Security: Change defaults for Open ID Connect [4335](https://github.com/oracle/helidon/pull/4335)
- Security: Added X509 certificate context key when client certificate is present and pem trust store configuration [4185](https://github.com/oracle/helidon/pull/4185)
- Security: Add IDCS related info to MP Security example [4343](https://github.com/oracle/helidon/pull/4343)
- Reactive: Multi support in Reactive Messaging 3.0 [4475](https://github.com/oracle/helidon/pull/4475)
- OCI: Makes OCI CDI extension work with OCI's shaded full jar [4498](https://github.com/oracle/helidon/pull/4498) [4531](https://github.com/oracle/helidon/pull/4531) [4483](https://github.com/oracle/helidon/pull/4483) [4488](https://github.com/oracle/helidon/pull/4488) [4522](https://github.com/oracle/helidon/pull/4522)
- OCI: Integration Examples Update and Deprecation [4516](https://github.com/oracle/helidon/pull/4516)
- Misc: Remove experimental flags from relevant modules. [4361](https://github.com/oracle/helidon/pull/4361)
- Metrics: Properly count completed and failed thread pool tasks (master) [4247](https://github.com/oracle/helidon/pull/4247)
- Metrics: Deal with deprecations in metrics [4397](https://github.com/oracle/helidon/pull/4397)
- Metrics: Correct Prometheus output for timer and JSON output for SimpleTimer [4243](https://github.com/oracle/helidon/pull/4243)
- Metrics: Convert  `synchronized` to use semaphores [4284](https://github.com/oracle/helidon/pull/4284)
- Metrics: Address deprecations in MP metrics [4500](https://github.com/oracle/helidon/pull/4500)
- Messaging: Messaging Deprecations cleanup [4454](https://github.com/oracle/helidon/pull/4454)
- Messaging: Kafka nack support [4443](https://github.com/oracle/helidon/pull/4443)
- Messaging: JMS configurable producer properties #3980 [4272](https://github.com/oracle/helidon/pull/4272)
- LRA: LRA Deprecations removal [4449](https://github.com/oracle/helidon/pull/4449)
- JPA: Updates persistence.xmls to 3.0. Re-enables JPA tests. Removes eclips… [4446](https://github.com/oracle/helidon/pull/4446)
- gRPC: Removed support for JavaMarshaller as default marshaller [4423](https://github.com/oracle/helidon/pull/4423)
- gRPC: Fixing module-info files so that gRPC works when using Java modules [4235](https://github.com/oracle/helidon/pull/4235)
- GraphQL: Update to mp-graphql 2.0 [4190](https://github.com/oracle/helidon/pull/4190)
- FT: Update feature catalog with FT for SE (master) [4351](https://github.com/oracle/helidon/pull/4351)
- FT: Port of a few Fault Tolerance related PRs to master [4255](https://github.com/oracle/helidon/pull/4255)
- FT: Fixed problem in DelayRetryPolicyTest that would cause all delays to be zero (master) [4221](https://github.com/oracle/helidon/pull/4221)
- FT: Avoid calling getPackage() on an annotation type (master) [4350](https://github.com/oracle/helidon/pull/4350)
- FT: Added config support for bulkheads, breakers, timeouts and retries (master) [4357](https://github.com/oracle/helidon/pull/4357)
- Examples: Uses lowercase for database column names in se database archetype to fix issue #4187 [4273](https://github.com/oracle/helidon/pull/4273)
- Examples: Gradle: Add helidon test dependency. Add task dependency for jandex [4231](https://github.com/oracle/helidon/pull/4231)
- Examples: Use standard graalvm base image in Dockerfile.native [4501](https://github.com/oracle/helidon/pull/4501)
- Docs: [New Docs PR] - Helidon MP WebSocket 4213 [4442](https://github.com/oracle/helidon/pull/4442)
- Docs: [New Docs PR] - Helidon MP Scheduling #4206 [4456](https://github.com/oracle/helidon/pull/4456)
- Docs: [New Docs PR] - Helidon MP OpenTracing #4199 [4428](https://github.com/oracle/helidon/pull/4428)
- Docs: [New Docs PR] - Helidon MP OpenAPI #4209 [4421](https://github.com/oracle/helidon/pull/4421)
- Docs: [New Docs PR] - Helidon MP Bean Validation #4202 [4426](https://github.com/oracle/helidon/pull/4426)
- Docs: WebServer documentation [4461](https://github.com/oracle/helidon/pull/4461)
- Docs: Updated doc for FT MP using new template [4438](https://github.com/oracle/helidon/pull/4438)
- Docs: Updated FT SE documentation [4465](https://github.com/oracle/helidon/pull/4465)
- Docs: New formatting for Helidon Health Checks SE document [4495](https://github.com/oracle/helidon/pull/4495)
- Docs: New formatting for GraphQL MP document [4514](https://github.com/oracle/helidon/pull/4514)
- Docs: New formatting for CORS SE documentation [4470](https://github.com/oracle/helidon/pull/4470)
- Docs: New doc file that briefly describes the JAX-RS Client API [4451](https://github.com/oracle/helidon/pull/4451)
- Docs: MP CORS doc update [4511](https://github.com/oracle/helidon/pull/4511)
- Docs: Latest sitegen and functional docs improvements [4463](https://github.com/oracle/helidon/pull/4463)
- Docs: Java doc typo fixing [4473](https://github.com/oracle/helidon/pull/4473)
- Docs: Helidon MP RestClient documentation [4448](https://github.com/oracle/helidon/pull/4448)
- Docs: Helidon MP Health new style documentation  [4387](https://github.com/oracle/helidon/pull/4387)
- Docs: Fix config doc annotations for OpenAPI builder [4419](https://github.com/oracle/helidon/pull/4419)
- Docs: Fix Javadoc links [4479](https://github.com/oracle/helidon/pull/4479)
- Docs: Documentation : Helidon DB Client Guide [2673](https://github.com/oracle/helidon/pull/2673)
- Docs: Change last use of '{...}' for a placeholder to `*` [4455](https://github.com/oracle/helidon/pull/4455)
- Docs: Add default value constants and use in config doc [4408](https://github.com/oracle/helidon/pull/4408)
- Docs: 4367 doc update for java marshaller deprecation [4441](https://github.com/oracle/helidon/pull/4441)
- Docs: Update OCI integration documentation to reflect use of new OCI SDK CDI Extension [4409](https://github.com/oracle/helidon/pull/4409)
- Docs: Add tuning guides [4156](https://github.com/oracle/helidon/pull/4156)
- Dependencies: Yasson version increased to 2.0.4 [4260](https://github.com/oracle/helidon/pull/4260)
- Dependencies: Upgrades Jandex to 2.4.3.Final [4487](https://github.com/oracle/helidon/pull/4487)
- Dependencies: Upgrades HikariCP to 5.0.1 [4519](https://github.com/oracle/helidon/pull/4519)
- Dependencies: Upgrades Hibernate to version 6.1.1.Final [4484](https://github.com/oracle/helidon/pull/4484)
- Dependencies: Upgrades H2 to 2.1.212 [4298](https://github.com/oracle/helidon/pull/4298)
- Dependencies: Revert back to using javax.validation as jakarta.validation is not supported by micronaut [4476](https://github.com/oracle/helidon/pull/4476)
- Dependencies: Restoring dependency with Tyrus 2.0.1 [4429](https://github.com/oracle/helidon/pull/4429)
- Dependencies: Bump derby from 10.13.1.1 to 10.14.2.0 in /examples/jbatch [4453](https://github.com/oracle/helidon/pull/4453)
- Dependencies: Upgrade Netty to 4.1.77.Final [4252](https://github.com/oracle/helidon/pull/4252)
- Dependencies: Upgrade Jackson to 2.13.2.2 [4178](https://github.com/oracle/helidon/pull/4178)
- Dependencies: Move mockito dependency management to root pom [4285](https://github.com/oracle/helidon/pull/4285)
- Dependencies: upgrade google api client [4297](https://github.com/oracle/helidon/pull/4297)
- DataSource: Adds XA support to Helidon's UCP integration [4291](https://github.com/oracle/helidon/pull/4291)
- Config: Support Hocon/Json Configuration Source for MP [4347](https://github.com/oracle/helidon/pull/4347)
- Config: Metadata argument number changed [4386](https://github.com/oracle/helidon/pull/4386)
- Config: Deserialization disabled by default. [4334](https://github.com/oracle/helidon/pull/4334)
- Config: Config metadata update [4403](https://github.com/oracle/helidon/pull/4403)
- Config: Add helidon-config-yaml-mp as a dependency in helidon-microprofile-config [4379](https://github.com/oracle/helidon/pull/4379)
- Common: Removal of deprecated code in common context [4388](https://github.com/oracle/helidon/pull/4388)
- Common: Removal of deprecated code in common configurable [4405](https://github.com/oracle/helidon/pull/4405)
- Common: Enable thread pool growth at threshold instead of above it (master) [4248](https://github.com/oracle/helidon/pull/4248)
- Common: Remove dependency on helidon-common-reactive from config (#4225) [4229](https://github.com/oracle/helidon/pull/4229)
- Cleanup: Fix code with 'TODO 3.0.0-JAKARTA' comment [4447](https://github.com/oracle/helidon/pull/4447)
- CORS: Preserve order of mapped cross-origin config path entries from config; add test [4431](https://github.com/oracle/helidon/pull/4431)
- CORS: Correct return of path from MP CORS request adapter; add test [4435](https://github.com/oracle/helidon/pull/4435)
- CDI: Updates all beans.xml files to version 3.0 [4404](https://github.com/oracle/helidon/pull/4404)

## [3.0.0-M2]

This is the second milestone release of Helidon 3.0. It contains significant new
features, enhancements and fixes. It also contains backward incompatible changes.
(see [Compatibility](#compatibility) below). This milestone release is provided for customers that
want early access to Helidon 3.0 for experimentation. It should be considered
unstable and is not intended for production use. APIs and features might not be
fully implemented and tested and are subject to change. Documentation is incomplete.
Those looking for a stable release should use Helidon 2.

Notable changes:

- MicroProfile 5.0 support
- javax to jakarta Java package namespace change for Jakarta 9 APIs
- Java 11 no longer supported. Java 17 or newer is required.

### Compatibility

3.0.0-M2 contains the following incompatibilities with 2.x:

- Java 17 or newer is required
- Java package namespace for Jakarta APIs has changed from `javax` to `jakarta`

### CHANGES from M1

- BV: Support of Bean Validation in Helidon MP #721 [3780](https://github.com/oracle/helidon/pull/3780)
- CDI: Abstract decorator class now gets correct proxy name in Weld [4148](https://github.com/oracle/helidon/pull/4148)
- CORS support to OidcSupport (#3844) [3871](https://github.com/oracle/helidon/pull/3871)
- CORS: CORS `@CrossOrigin` checking and request-time performance improvements [3932](https://github.com/oracle/helidon/pull/3932)
- CORS: Compare origin URLs based on protocol, host and port [3934](https://github.com/oracle/helidon/pull/3934)
- Common: Improve threadNamePrefix defaulting to be more informative (#4165) [4166](https://github.com/oracle/helidon/pull/4166)
- Config: Fix retention of @Configured [4115](https://github.com/oracle/helidon/pull/4115)
- Config: Support for Hocon inclusion of files without an extensions [4168](https://github.com/oracle/helidon/pull/4168)
- Config: Turn off reference resolution/substitution at parser level [4175](https://github.com/oracle/helidon/pull/4175)
- Config: Fixed problem supporting config profiles with JSON and HOCON [3958](https://github.com/oracle/helidon/pull/3958)
- Config: fixed manifest and documentation for Helidon Config Encryption [4013](https://github.com/oracle/helidon/pull/4013)
- Config: hocon include [3998](https://github.com/oracle/helidon/pull/3998)
- Fault Tolerance: Fixed a few problems with Fallback and Multi's [4176](https://github.com/oracle/helidon/pull/4176)
- GraphQL: Enable mp-graphql tck with 2.0-RC3 of Microprofile GraphQL Spec [4147](https://github.com/oracle/helidon/pull/4147)
- Health: HEAD support to health endpoints [3936](https://github.com/oracle/helidon/pull/3936)
- JAX-RS Client: Jersey JAX-RS client context propagation #3762 [3865](https://github.com/oracle/helidon/pull/3865)
- JAX-RS: Follow up to PR 3921 that adds support for base interfaces as well [3982](https://github.com/oracle/helidon/pull/3982)
- JAX-RS: Search for @Path annotations in base classes [3921](https://github.com/oracle/helidon/pull/3921)
- LRA Custom headers propagation #3702 (#3768) [3877](https://github.com/oracle/helidon/pull/3877)
- Media: Fix body part header encoding [3969](https://github.com/oracle/helidon/pull/3969)
- Media: MimeParser parses closing boundary as normal boundary [3968](https://github.com/oracle/helidon/pull/3968)
- Media: Remove buffered multipart [4103](https://github.com/oracle/helidon/pull/4103)
- Media: Update MediaType parser to handle parameter without value [3999](https://github.com/oracle/helidon/pull/3999)
- Messaging - Fix badly subscribed connector to processor signature [3957](https://github.com/oracle/helidon/pull/3957)
- Messaging - signature detection fix #3883 3x [3973](https://github.com/oracle/helidon/pull/3973)
- MicroProfile: Messaging 3.0 [4091](https://github.com/oracle/helidon/pull/4091)
- Native image: Native image configuration reflection update for Jaeger [4117](https://github.com/oracle/helidon/pull/4117)
- OpenAPI: Replace dep on SmallRye pom with deps on 2 artifacts (3.0) [4010](https://github.com/oracle/helidon/pull/4010)
- Security: Correctly resolve OIDC metadata. (#3985) [4006](https://github.com/oracle/helidon/pull/4006)
- Security: Do not fail when expected audience is null [4160](https://github.com/oracle/helidon/pull/4160)
- Security: Fix JwtProvider wrong error message [4136](https://github.com/oracle/helidon/pull/4136)
- Security: Fixed builder from configuration in OutboundTargetDefinition [3919](https://github.com/oracle/helidon/pull/3919)
- Security: Injection of empty SecurityContext [4161](https://github.com/oracle/helidon/pull/4161)
- Security: New security response mapper mechanism [4093](https://github.com/oracle/helidon/pull/4093)
- Tests: Fixed dbclient integration tests build issues. [4100](https://github.com/oracle/helidon/pull/4100)
- Tests: possible fix for HttpPipelineTest hang  [4143](https://github.com/oracle/helidon/pull/4143)
- Tracing: Disable paths such as /metrics and /health from tracing [3977](https://github.com/oracle/helidon/pull/3977)
- Tracing: Enable OpenTracing 2.0 TCKs [3962](https://github.com/oracle/helidon/pull/3962)
- Tracing: Fix #3966 set collectorUri() with URL with no port number adds port  [3993](https://github.com/oracle/helidon/pull/3993)
- WebClient: Case insensitive client request headers fix [4108](https://github.com/oracle/helidon/pull/4108)
- WebClient: MDC propagation [4109](https://github.com/oracle/helidon/pull/4109)
- WebClient: hang fix [3997](https://github.com/oracle/helidon/pull/3997)
- WebServer: Allow a list of path patterns to be specified for exclusion in the access log[3959](https://github.com/oracle/helidon/pull/3959)
- WebServer: Do not log full stack traces in SEVERE when a connection reset is received [3918](https://github.com/oracle/helidon/pull/3918)
- WebServer: Explicit 404 in Jersey no longer calls next() [3976](https://github.com/oracle/helidon/pull/3976)
- WebServer: Upgrade WebSocket from Java HttpClient [3994](https://github.com/oracle/helidon/pull/3994)
- Dependencies: Jersey version bump to 3.0.4 [3884](https://github.com/oracle/helidon/pull/3884)
- Dependencies: MySQL JDBC driver updated to 8.0.28 and PostgreSQL JDBC driver updated to 42.3.3. [4098](https://github.com/oracle/helidon/pull/4098)
- Dependencies: Upgrade Jackson Databind to 2.13.2.1 (BOM 2.13.2.20220324) [4028](https://github.com/oracle/helidon/pull/4028)
- Dependencies: Upgrade Netty to 4.1.76.Final [4121](https://github.com/oracle/helidon/pull/4121)
- Dependencies: Upgrade grpc-java to 1.45.1 [4150](https://github.com/oracle/helidon/pull/4150)
- Dependencies: Upgrade snakeyaml and typesafe-config [3941](https://github.com/oracle/helidon/pull/3941)
- Docs: Add discussion of Helidon-specific config settings to MP OpenAPI doc [3955](https://github.com/oracle/helidon/pull/3955)
- Docs: Describe more Scheduled properties [4043](https://github.com/oracle/helidon/pull/4043)
- Docs: Fix guide broken links and typos 4119 [4133](https://github.com/oracle/helidon/pull/4133)
- Docs: Fix guide. Change JPA Scope. [4170](https://github.com/oracle/helidon/pull/4170)
- Docs: JBatch guide for issue 2785 [3895](https://github.com/oracle/helidon/pull/3895)
- Docs: README: Update JDK and mvn versions for build [3946](https://github.com/oracle/helidon/pull/3946)
- Docs: SecMP JWT Auth and Rest Client spec links updated [4005](https://github.com/oracle/helidon/pull/4005)
- Docs: fixed links in the cards for extensions [3930](https://github.com/oracle/helidon/pull/3930)
- Docs: updated link to the new spec for issue 3899 [3909](https://github.com/oracle/helidon/pull/3909)
- Examples: Fix deps in jpa examples and do other cleanup [4132](https://github.com/oracle/helidon/pull/4132)
- Examples: JBatch example for [3927](https://github.com/oracle/helidon/pull/3927)
- Examples: Quickstart cleanup, using @HelidonTest in MP. (#4011) [4014](https://github.com/oracle/helidon/pull/4014)
- Examples: Removed incorrect call to indexOf [3920](https://github.com/oracle/helidon/pull/3920)
- Examples: Update quickstart Dockerfiles  [4088](https://github.com/oracle/helidon/pull/4088)
- Examples: Use OBJECT schema type with requiredProperties in Quickstart MP [4151](https://github.com/oracle/helidon/pull/4151)

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
- WebServer: Provide access to client x509 certificate when under mTLS [4185](https://github.com/oracle/helidon/pull/4185)
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


[3.0.1-SNAPSHOT]: https://github.com/oracle/helidon/compare/3.0.0...3.0.1
[3.0.0]: https://github.com/oracle/helidon/compare/3.0.0-RC2...3.0.0
[3.0.0-RC2]: https://github.com/oracle/helidon/compare/3.0.0-RC1...3.0.0-RC2
[3.0.0-RC1]: https://github.com/oracle/helidon/compare/3.0.0-M2...3.0.0-RC1
[3.0.0-M2]: https://github.com/oracle/helidon/compare/3.0.0-M1...3.0.0-M2
[3.0.0-M1]: https://github.com/oracle/helidon/compare/2.4.0...3.0.0-M1
