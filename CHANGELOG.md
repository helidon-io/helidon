
# Changelog

All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

For Helidon 3.x releases please see [Helidon 3.x CHANGELOG.md](https://github.com/oracle/helidon/blob/helidon-3.x/CHANGELOG.md)

For Helidon 2.x releases please see [Helidon 2.x CHANGELOG.md](https://github.com/oracle/helidon/blob/helidon-2.x/CHANGELOG.md)

For Helidon 1.x releases please see [Helidon 1.x CHANGELOG.md](https://github.com/oracle/helidon/blob/helidon-1.x/CHANGELOG.md)

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

The big news in Helidon 4.0.0 is the introduction of Helidon Nima -- a [ground up webserver implementation based on JDK Project Loom](https://medium.com/helidon/helidon-n%C3%ADma-helidon-on-virtual-threads-130bb2ea2088). 

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


[4.0.0-ALPHA1]: https://github.com/oracle/helidon/compare/main...4.0.0-ALPHA1
