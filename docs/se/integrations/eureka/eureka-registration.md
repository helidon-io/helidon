# Eureka Server Service Instance Registration

## Contents

- [Overview](#overview)
- [Usage](#usage)
- [Installation](#installation)
- [Configuration](#configuration)
- [Logging](#logging)
- [Related Documentation](#related-documentation)

## Overview

Helidon’s Eureka Server Service Instance Registration Integration provides a [`ServerFeature`](/apidocs/io.helidon.webserver/io/helidon/webserver/spi/ServerFeature.html) that offers support for automatically and unobtrusively attempting to register a running Helidon microservice with an available Netflix Eureka Server of at least version 2.0.5 in the microservice’s runtime environment.

## Usage

### Installation

## Maven Coordinates

To enable Eureka Server Service Instance Registration Integration, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../../about/managing-dependencies.md)).

``` xml
<dependency>
    <groupId>io.helidon.integrations.eureka</groupId>
    <artifactId>helidon-integrations-eureka</artifactId>
    <scope>runtime</scope>
</dependency>
```

- Most users do not need to interact programmatically with this feature; the scope is therefore usually `runtime`, indicating that the feature will be available on the runtime classpath.

This feature is fundamentally a [`ServerFeature`](/apidocs/io.helidon.webserver/io/helidon/webserver/spi/ServerFeature.html), and is automatically installed by its [associated `ServerFeatureProvider`](/apidocs/io.helidon.integrations.eureka/io/helidon/integrations/eureka/EurekaRegistrationServerFeatureProvider) when the provider is found in configuration (see below).

### Configuration

You need to specify at an absolute minimum the URI to the available Netflix Eureka Server of at least version 2.0.5 in the microservice’s runtime environment:

*`application.yaml`*

``` yaml
server:
  features:
    eureka: 
      client: 
        base-uri: "http://localhost:8761/eureka"  
```

- The feature’s configuration is a child of the `server.features.eureka` node, which lists available `ServerFeature` implementations. This feature is one such implementation.
- Information about the HTTP client the feature uses to communicate with Eureka is a child of this node.
- The `base-uri` needs to identify an available Netflix Eureka Server of at least version 2.0.5. Netflix Eureka Server is commonly made available on port `8761`.
- Configuration under the `client` node is wholly defined by the [`HttpClientConfig`](/apidocs/io.helidon.webclient.api/io/helidon/webclient/api/HttpClientConfig.html) interface.

All other configuration values can be (and ordinarily are) defaulted, but some are best set explicitly:

*`application.yaml`*

``` yaml
server:
  features:
    eureka:
      instance: 
        name: "My Application" 
        hostName: example.com 
```

- The feature’s configuration pertaining to the registration itself is a child of the `server.features.eureka.instance` node. Configuration is designed to be familiar to current users of other Netflix Eureka libraries. See the [Helidon Config Reference](../../../config/io_helidon_integrations_eureka_InstanceInfoConfig.md) for a full description of what configuration is allowed.
- The `name` describes the microservice application, not any given instance of it. Its default value is `unknown`, following Netflix Eureka client convention, so it is best to set it explicitly here instead.
- The `hostName` node identifies the host. It defaults to the current host, which may or may not be suitable in your environment. Most of the time you can simply omit this node and use the defaulted value.

Please consult the [Helidon Config Reference](../../../config/io_helidon_integrations_eureka_EurekaRegistrationServerFeature.md) for a full description of the permitted configuration.

## Logging

This feature is deliberately designed to be *unobtrusive*. Unobtrusive means that if everything is working properly Eureka Server service instance registration will simply happen, quietly, behind the scenes, automatically. If something goes wrong, service instance registration will not interrupt the running microservice. Therefore, the information this feature logs can be important.

Like all other Helidon components, this feature uses Java logging. Its loggers begin with the `io.helidon.integrations.eureka` prefix, and log debug, warning and error-level information.

Information about how this feature is communicating with the Eureka Server is logged by loggers under the `io.helidon.webclient` prefix.

## Related Documentation

Users of this feature may also be interested in the (related) [Discovery feature](../../../se/discovery.md), particularly its [Eureka provider](../../../se/discovery.md#_eureka).
