# Changelog
All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/oracle/helidon/compare/0.10.5...HEAD
[0.10.5]: https://github.com/oracle/helidon/compare/0.10.4...0.10.5
[0.10.4]: https://github.com/oracle/helidon/compare/0.10.3...0.10.4
[0.10.3]: https://github.com/oracle/helidon/compare/0.10.2...0.10.3
[0.10.2]: https://github.com/oracle/helidon/compare/0.10.1...0.10.2
[0.10.1]: https://github.com/oracle/helidon/compare/0.10.0...0.10.1
[0.10.0]: https://github.com/oracle/helidon/compare/0.9.1...0.10.0
[0.9.1]: https://github.com/oracle/helidon/compare/0.9.0...0.9.1
