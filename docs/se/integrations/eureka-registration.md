<!--@frontmatter
description: "Eureka Server Service Instance Registration"
-->
# Eureka Registration

## Overview

Helidon’s Eureka Server Service Instance Registration Integration provides a
[`ServerFeature`][serverfeature] that offers support for automatically and
unobtrusively attempting to register a running Helidon microservice with an
available Netflix Eureka Server of at least version 2.0.5 in the microservice’s
runtime environment.

## Usage

### Installation

## Maven Coordinates

To enable Eureka Server Service Instance Registration Integration, add the
following dependency to your project’s `pom.xml` (see [Managing
Dependencies](../../dependency-management.md)).

<!--@mdc ::code-callout -->
```xml [pom.xml]
<dependency>
    <groupId>io.helidon.integrations.eureka</groupId>
    <artifactId>helidon-integrations-eureka</artifactId>
    <scope>runtime</scope><!-- (1) -->
</dependency>
```
1. Most users do not need to interact programmatically with this feature; the
   scope is therefore usually `runtime`, indicating that the feature will be
   available on the runtime classpath.
<!--@mdc :: -->

This feature is fundamentally a [`ServerFeature`][serverfeature], and is
automatically installed by its [associated
`ServerFeatureProvider`][associated-serve] when the provider is found in
configuration (see below).

### Configuration

You need to specify at an absolute minimum the URI to the available Netflix
Eureka Server of at least version 2.0.5 in the microservice’s runtime
environment:

<!--@mdc ::code-callout -->
```yaml [application.yaml]
server:
  features:
    eureka: # <1>
      client: # <2>
        base-uri: "http://localhost:8761/eureka" # <3> <4>
```
1. The feature’s configuration is a child of the `server.features.eureka` node,
   which lists available `ServerFeature` implementations. This feature is one
   such implementation.
2. Information about the HTTP client the feature uses to communicate with Eureka
   is a child of this node.
3. The `base-uri` needs to identify an available Netflix Eureka Server of at
   least version 2.0.5. Netflix Eureka Server is commonly made available on port
   `8761`.
4. Configuration under the `client` node is wholly defined by the
   [`HttpClientConfig`][httpclientconfig] interface.
<!--@mdc :: -->

All other configuration values can be (and ordinarily are) defaulted, but some
are best set explicitly:

<!--@mdc ::code-callout -->
```yaml [application.yaml]
server:
  features:
    eureka:
      instance: # <1>
        name: "My Application" # <2>
        hostName: example.com <3>
```
1. The feature’s configuration pertaining to the registration itself is a child
   of the `server.features.eureka.instance` node. Configuration is designed to be
   familiar to current users of other Netflix Eureka libraries. See the [Helidon
   Config Reference][helidon-config-r] for a full description of what
   configuration is allowed.
2. The `name` describes the microservice application, not any given instance of
   it. Its default value is `unknown`, following Netflix Eureka client
   convention, so it is best to set it explicitly here instead.
3. The `hostName` node identifies the host. It defaults to the current host,
   which may or may not be suitable in your environment. Most of the time you can
   simply omit this node and use the defaulted value.
<!--@mdc :: -->

Please consult the [Helidon Config Reference][helidon-config-r-2] for a full
description of the permitted configuration.

## Logging

This feature is deliberately designed to be *unobtrusive*. Unobtrusive means
that if everything is working properly Eureka Server service instance
registration will simply happen, quietly, behind the scenes, automatically. If
something goes wrong, service instance registration will not interrupt the
running microservice. Therefore, the information this feature logs can be
important.

Like all other Helidon components, this feature uses Java logging. Its loggers
begin with the `io.helidon.integrations.eureka` prefix, and log debug, warning
and error-level information.

Information about how this feature is communicating with the Eureka Server is
logged by loggers under the `io.helidon.webclient` prefix.

## Related Documentation

Users of this feature may also be interested in the (related) [Discovery
feature](../discovery.md), particularly its [Eureka
provider](../discovery.md#eureka).

[serverfeature]: https://helidon.io/docs/v4/apidocs/io.helidon.webserver/io/helidon/webserver/spi/ServerFeature.html
[associated-serve]: https://helidon.io/docs/v4/apidocs/io.helidon.integrations.eureka/io/helidon/integrations/eureka/EurekaRegistrationServerFeatureProvider.html
[httpclientconfig]: https://helidon.io/docs/v4/apidocs/io.helidon.webclient.api/io/helidon/webclient/api/HttpClientConfig.html
[helidon-config-r]: ../../config/io.helidon.integrations.eureka.InstanceInfoConfig.md
[helidon-config-r-2]: ../../config/io.helidon.integrations.eureka.EurekaRegistrationServerFeature.md
