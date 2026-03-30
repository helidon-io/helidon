# Helidon MP 4.x Upgrade Guide

Helidon 4.x introduces significant changes to APIs and runtime behavior. Use this guide to help you understand the changes required to transition a Helidon MP 3.x application to Helidon 4.x.

## Significant Changes

The following sections describe the changes between Helidon 3.x and Helidon 4.x that can significantly impact your development process. Review them carefully.

You can also review the [Helidon repository CHANGELOG](https://github.com/helidon-io/helidon/blob/main/CHANGELOG.md) to see a detailed history of changes made to the project.

> [!NOTE]
> Helidon adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). As such, Helidon 4.x includes changes that are not backward compatible with Helidon 3.x.

### Java SE Support

Helidon 4.x removes support for Java SE 17. You must use Java SE 21 or later. If you are using Helidon 4.3.0 or later, using Java SE 25 or later is recommended.

### New Web Server Implementation

Helidon 4.x introduces Helidon WebServer, a virtual threads-based web server implementation based on the JDK Project Loom. Helidon WebServer replaces Netty, the server implementation used by previous versions of Helidon.

Helidon provides a MicroProfile server implementation (`io.helidon.microprofile.server.Server`) that encapsulates the Helidon WebServer.

### MicroProfile Support

Helidon 4.0.0 adds support for [MicroProfile 6.0](https://download.eclipse.org/microprofile/microprofile-6.0/microprofile-spec-6.0.html#microprofile6.0). Key changes include:

- Significant updates to the [MicroProfile Metrics specification](https://download.eclipse.org/microprofile/microprofile-metrics-5.0.0/microprofile-metrics-spec-5.0.0.html).
- Addition of the MicroProfile Telemetry specification which replaces the MicroProfile OpenTracing specification.
- Support for the [Jakarta EE 10 Core Profile](https://jakarta.ee/specifications/coreprofile/10/jakarta-coreprofile-spec-10.0#introduction) (instead of individual Jakarta EE specifications).
- Various minor updates to other MicroProfile specifications. Review the individual MicroProfile specifications for details.

Helidon 4.1.0 adds support for [MicroProfile 6.1](https://download.eclipse.org/microprofile/microprofile-6.1/microprofile-spec-6.1.html#microprofile6.1).

> [!NOTE]
> Read each specification carefully to check for incompatible changes.

## Other Changes

The following sections describe changes between Helidon 3.x and Helidon 4.x that may impact your development process.

### Jandex

The Jandex groupId changed from `org.jboss.jandex` to `io.smallrye`. You should update all references accordingly.

### Testing

Testing support moved to a new Maven artifact and Java package.

In Helidon 3.x, it was:

``` xml
<dependency>
    <groupId>io.helidon.microprofile.tests</groupId>
    <artifactId>helidon-microprofile-tests-junit5</artifactId>
    <scope>test</scope>
</dependency>
```

In Helidon 4.x, it is now:

``` xml
<dependency>
    <groupId>io.helidon.microprofile.testing</groupId>
    <artifactId>helidon-microprofile-testing-junit5</artifactId>
    <scope>test</scope>
</dependency>
```

Additionally, the Java package changed from `io.helidon.microprofile.tests.junit5` to `io.helidon.microprofile.testing.junit5`.

Update all references accordingly.

### Logging

The Helidon console handler changed from `io.helidon.common.HelidonConsoleHandler` to `io.helidon.logging.jul.HelidonConsoleHandler`.

If you use this handler in your `logging.properties` file, you must update it and add the following dependency:

``` xml
<dependency>
    <groupId>io.helidon.logging</groupId>
    <artifactId>helidon-logging-jul</artifactId>
    <scope>runtime</scope>
</dependency>
```
