# Changelog
All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Improvements

- MicroProfile Fault Tolerance 1.0 support [97](https://github.com/oracle/helidon/pull/97)
- WebServer: Support version 1 and 2 of Zipkin API and configuration based building [87](https://github.com/oracle/helidon/pull/87)

### Fixes

- WebServer: Eager cleanup of queues to reduce memory usage during heavy loads [90](https://github.com/oracle/helidon/pull/90)
- WebServer: Fix for jigsaw service loading problem [99](https://github.com/oracle/helidon/pull/99)

## [0.10.1] - 2019-09-28

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

[Unreleased]: https://github.com/oracle/helidon/compare/0.10.0...HEAD
[0.10.0]: https://github.com/oracle/helidon/compare/0.9.1...0.10.0
[0.9.1]: https://github.com/oracle/helidon/compare/0.9.0...0.9.1
