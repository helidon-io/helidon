# Discovery

## Contents

- [Overview](#overview)
- [Maven Coordinates](#maven-coordinates)
- [API Usage](#api-usage)
- [Providers](#providers)
  - [Eureka](#eureka)
- [Integrations](#integrations)
  - [Web Client](#web-client-discovery-integration)
- [References](#references)

## Overview

In Helidon, *discovery* is the general process of finding named sets of advertised resources at a moment of an application’s runtime. The advertised resources are often URIs representing microservice endpoints. In some environments, those endpoints might frequently come and go at unpredictable intervals, as microservices are started, stopped, and redeployed. The named applications they represent, however, are relatively static. Discovery helps link such a named application to its transient resources, so that clients can more easily contact it, knowing only its name.

Helidon Discovery is a feature with a vendor- and implementation-independent API backed by vendor-specific implementations of that API known as *providers*. A developer programs against the Discovery API, and packages a (normally Helidon-supplied) conformant Discovery implementation (a provider) with her application at deployment time. See [Providers](#providers) below.

## Maven Coordinates

To enable Helidon Discovery, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../about/managing-dependencies.md)).

`pom.xml`

``` xml
<dependencies>
    <dependency>
        <groupId>io.helidon.discovery</groupId>
        <artifactId>helidon-discovery</artifactId> <!--(1)-->
    </dependency>
</dependencies>
```

1.  Helidon Discovery API dependency.

Discovery is implemented by one or more

[discovery providers](#providers). Generally you will choose a single provider and include its relevant dependencies on your runtime classpath as well. See the [Providers](#providers) section for more details.

## API Usage

To use Helidon Discovery, you acquire an [`io.helidon.discovery.Discovery` object](/apidocs/io.helidon.discovery/io/helidon/discovery/Discovery.html) and call its [`uris(String, URI)` method](/apidocs/io.helidon.discovery/io/helidon/discovery/Discovery.html#uris(java.lang.String,java.net.URI)) to find resources represented as [`io.helidon.discovery.DiscoveredUri` instances](/apidocs/io.helidon.discovery/io/helidon/discovery/DiscoveredUri.html). You supply a *discovery name*, which is the name under which you expect to find advertised resources, and a *default value*, which is a [`URI`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URI.html) to use in case the provider does not supply any resources. In general, [`DiscoveredUri`](/apidocs/io.helidon.discovery/io/helidon/discovery/DiscoveredUri.html)s you receive are ordered from more suitable to less suitable, where the definition of *suitable* is up to the provider. Some providers consider aspects like the health or uptime of an advertised resource when returning results. Others may not. Finally, a [`DiscoveredUri`](/apidocs/io.helidon.discovery/io/helidon/discovery/DiscoveredUri.html) representing the default value you supply will always be present as the last element in the set of resources you receive.

### `Discovery` Acquisition

#### `Discovery` Acquisition Using [Helidon Inject](injection/injection.md#_injection_points)

You can acquire a [`io.helidon.discovery.Discovery` object](/apidocs/io.helidon.discovery/io/helidon/discovery/Discovery.html) by [injecting](injection/injection.md#_injection_points) it into your Helidon SE application:

Acquiring a `Discovery` object using Helidon Inject

``` java
import java.util.Objects;
import io.helidon.discovery.Discovery;
import io.helidon.service.registry.Service;

public class MyClass {

    private final Discovery discovery;

    @Service.Inject // (1)
    public MyClass(Discovery discovery) { // (2)
        this.discovery = Objects.requireNonNull(discovery, "discovery"); // (3)
    }

}
```

1.  Use the [`io.helidon.service.registry.Service.Inject` annotation](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.Inject.html) to indicate that this constructor has an [injection point](injection/injection.md#_injection_points).
2.  Here, the `discovery` constructor parameter is the injection point and will receive a non-`null` [instance of `io.helidon.discovery.Discovery`](/apidocs/io.helidon.discovery/io/helidon/discovery/Discovery.html).
3.  The constructor explicitly assigns the injected reference to the `discovery` instance field.

#### `Discovery` Acquisition Using the Helidon [Service Registry](injection/injection.md#_programmatic_lookup)

You can acquire a [`io.helidon.discovery.Discovery` object](/apidocs/io.helidon.discovery/io/helidon/discovery/Discovery.html) by [using the Helidon Service Registry](injection/injection.md#_programmatic_lookup) via the [`io.helidon.service.registry.Services` façade](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Services.html):

Acquiring a `Discovery` object using the Helidon Service Registry

``` java
import io.helidon.discovery.Discovery;
import io.helidon.service.registry.Services;

public class MyOtherClass {

    private final Discovery discovery;

    public MyOtherClass() {
        this.discovery = Services.get(Discovery.class); // (1)
    }

}
```

1.  Use the [`io.helidon.service.registry.Services#get(Class)` method](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Services.html#get(java.lang.Class)) to acquire an instance of the [`io.helidon.discovery.Discovery` class](/apidocs/io.helidon.discovery/io/helidon/discovery/Discovery.html), and assign it to an instance field.

### Discovering URIs

Discovery uses a *discovery name* to identify and discover URIs notionally belonging to an application. An application may have several URIs. The discovery name is the name that identifies the application for discovery purposes.

To discover a named application’s URIs, call the [`Discovery#uris(String, URI)` method](/apidocs/io.helidon.discovery/io/helidon/discovery/Discovery.html#uris(java.lang.String,java.net.URI)), passing it the discovery name and a `URI` to use as a default value. Both values must be non-`null`. You will receive an immutable [`SequencedSet`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/SequencedSet.html) of [`io.helidon.discovery.DiscoveredUri` instances](/apidocs/io.helidon.discovery/io/helidon/discovery/DiscoveredUri.html) representing the URIs, ordered from the most to the least *suitable*, according to the provider. A [`DiscoveredUri`](/apidocs/io.helidon.discovery/io/helidon/discovery/DiscoveredUri.html) representing the default value will appear last in the set:

Discovering URIs

``` java
import java.net.URI;
import java.util.SequencedSet;
import io.helidon.discovery.DiscoveredUri;
import io.helidon.discovery.Discovery;

SequencedSet<DiscoveredUri> uris = // (1)
    discovery.uris("EXAMPLE", // (2)
                   URI.create("http://example.com/")); // (3)
URI uri = uris.getFirst().uri(); // (4)
```

1.  URIs that are discovered are represented as a [`SequencedSet`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/SequencedSet.html) of [`io.helidon.discovery.DiscoveredUri` instances](/apidocs/io.helidon.discovery/io/helidon/discovery/DiscoveredUri.html). This is the *discovered set*. In general, the first element in the set is the [discovered URI](/apidocs/io.helidon.discovery/io/helidon/discovery/DiscoveredUri.html) that is the most *suitable*, as determined by the Discovery provider. (The last element is a [`DiscoveredUri`](/apidocs/io.helidon.discovery/io/helidon/discovery/DiscoveredUri.html) whose [`uri()` method](/apidocs/io.helidon.discovery/io/helidon/discovery/DiscoveredUri.html#uri()) yields a [`URI`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URI.html) that is identical or equal to the [`URI`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URI.html) that was supplied as the default value.)
2.  `EXAMPLE` is the discovery name for which URIs are being sought.
3.  This [`URI`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URI.html) is a default value in case the Discovery provider finds no URIs, or encounters an error. A [`DiscoveredUri`](/apidocs/io.helidon.discovery/io/helidon/discovery/DiscoveredUri.html) representing it will appear last in the discovered set.
4.  This [`URI`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URI.html) is the most suitable one for use, and may or may not be equal to the supplied default value.

## Providers

The Discovery API is implemented at runtime by a *Discovery provider*. Helidon currently ships with a [Eureka Discovery provider](#eureka). Others may follow in the future.

To use a Discovery provider, include it on your runtime classpath. See the provider’s documentation for details about installing, configuring, and using the provider.

### Eureka

The Helidon Eureka Discovery provider implements the Discovery API at runtime by communicating with a [Netflix Eureka server](https://github.com/Netflix/eureka/tree/v2.0.5) (version 2.0.5 or later).

#### Maven Coordinates

To use the Helidon Eureka Discovery provider, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../about/managing-dependencies.md)).

`pom.xml`

``` xml
<dependencies>
    <dependency>
        <groupId>io.helidon.discovery.providers</groupId>
        <artifactId>helidon-discovery-providers-eureka</artifactId> <!--(1)-->
        <scope>runtime</scope> <!--(2)-->
    </dependency>
</dependencies>
```

1.  Helidon Eureka Discovery provider dependency.
2.  The scope for the provider. Use `runtime` if you have no interest in provider-specific classes and methods (the most common case). Use `compile` if you plan to call provider-specific methods.

#### Configuration

The Helidon Eureka Discovery provider can be configured using [Helidon Config](config/introduction.md). Examples shown below are in YAML, but are expressible in any format and any location that Helidon Config supports.

Configuration for the Helidon Eureka Discovery provider is found under a top-level `discovery.eureka` key path.

Generated documentation normatively describing the provider’s configuration in full can be found in Helidon’s [Configuration Reference](../config/io_helidon_discovery_providers_eureka_EurekaDiscovery.md). Some common usages and examples are detailed below.

##### Configuring the Location of the Eureka Server

In order for the Helidon Eureka Discovery provider to do any meaningful work, you must tell it where the Eureka server is. (Discovery cannot bootstrap itself!) This is the only configuration that is effectively required. (If it is omitted, no error will occur, but the provider will log a message and effectively do nothing.)

To do this, you specify attributes about the internal [HTTP client](webclient.md) it uses, specifically its [`base-uri` property](../config/io_helidon_webclient_api_HttpClientConfig.md):

`application.yaml`

``` yaml
discovery: #(1)
  eureka: #(2)
    client: #(3)
      base-uri: "http://example.com:8761/eureka" #(4)
```

1.  `discovery` is the topmost key of the provider’s logical configuration tree.
2.  `eureka` is the configuration name of the Helidon Eureka Discovery provider.
3.  `client` identifies [HTTP client configuration](../config/io_helidon_webclient_api_HttpClientConfig.md).
4.  `base-uri` is a [property of the HTTP client](../config/io_helidon_webclient_api_HttpClientConfig.md) identifying the location of a Netflix Eureka server (version 2.0.5 or later). Eureka servers are normally hosted on port `8761`.

##### Configuring Caching

The Helidon Eureka Discovery provider uses a local cache of discovered URIs by default. You can configure, among [other things](../config/io_helidon_discovery_providers_eureka_CacheConfig.md):

- whether the cache is enabled
- how often the cache refreshes
- whether the cache is computed or fully replaced

`application.yaml`

``` yaml
discovery: #(1)
  eureka: #(2)
    cache: #(3)
      compute-changes: true # (4)
      defer-sync: false # (5)
      enabled: true # (6)
      fetch-thread-name: "Eureka registry fetch thread" # (7)
      sync-interval: "PT30S" # (8)
```

1.  `discovery` is the topmost key of the provider’s logical configuration tree.
2.  `eureka` is the configuration name of the Helidon Eureka Discovery provider.
3.  `cache` identifies configuration related to the local cache of Eureka-supplied information.
4.  `compute-changes` controls how the cache’s content is determined: if `true`, by applying a series of changes against an initial state; if `false`, by replacing the contents of the cache with a new copy. `true` by default.
5.  `defer-sync` controls whether the cache should be synchronized as late as possible (`true`), or as early as possible (`false`). `false` by default.
6.  `enabled` controls whether the cache is enabled. If `false`, then none of the other configuration items in the `cache` tree are relevant, and every invocation of the [`Discovery#uris(String, URI)` method](/apidocs/io.helidon.discovery/io/helidon/discovery/Discovery.html#uris(java.lang.String,java.net.URI)) will result in a network call.
7.  `fetch-thread-name` contains the name of the thread that synchronizes the cache. `Eureka registry fetch thread` by default.
8.  `sync-interval` controls the time between synchronizations of the cache. `PT30S` (30 seconds) by default.

##### Configuring IP Address vs. Hostname

The Helidon Eureka Discovery provider can be configured to prefer IP addresses in URIs when possible (instead of hostnames).

`application.yaml`

``` yaml
discovery: # (1)
  eureka: # (2)
    preferIpAddress: false # (3)
```

1.  `discovery` is the topmost key of the provider’s logical configuration tree.
2.  `eureka` is the configuration name of the Helidon Eureka Discovery provider.
3.  `preferIpAddress` controls whether the host component of a URI should use an IP address, when possible (`true`), or a hostname (`false`). `false` by default.

##### Disabling the Provider

In some testing scenarios, it may be useful to disable the Helidon Eureka Discovery provider entirely. (When any Discovery provider is disabled, only default values supplied to the [`Discovery#uris(String, URI)` method](/apidocs/io.helidon.discovery/io/helidon/discovery/Discovery.html#uris(java.lang.String,java.net.URI)) will be returned.)

`application.yaml`

``` yaml
discovery: # (1)
  eureka: # (2)
    enabled: false # (3)
```

1.  `discovery` is the topmost key of the provider’s logical configuration tree.
2.  `eureka` is the configuration name of the Helidon Eureka Discovery provider.
3.  `enabled` controls whether the provider is enabled at all (`true`) or completely disabled (`false`), in which case all other configuration pertaining to it is irrelevant. `true` by default.

#### Related Documentation

Users of the Helidon Eureka Discovery provider may also be interested in the (related) [Eureka Server Service Instance Registration](../se/integrations/eureka/eureka-registration.md) feature.

## Integrations

Helidon integrates a [Discovery provider](#providers) with other Helidon modules as described below.

### Web Client Discovery Integration

Helidon integrates a [Discovery provider](#providers) with [Web Client](webclient.md).

#### Maven Coordinates

To include the Helidon Web Client Discovery integration in your project, you add the Web Client Discovery integration dependency as well as a [Discovery provider](#providers) dependency (see [Managing Dependencies](../about/managing-dependencies.md)):

`pom.xml`

``` xml
<dependencies>
    <dependency>
        <groupId>io.helidon.webclient</groupId>
        <artifactId>helidon-webclient-discovery</artifactId> <!--(1)-->
        <scope>runtime</scope> <!--(2)-->
    </dependency>
    <dependency>
        <groupId>io.helidon.discovery.providers</groupId>
        <artifactId>helidon-discovery-providers-eureka</artifactId> <!--(3)-->
        <scope>runtime</scope> <!--(4)-->
    </dependency>
</dependencies>
```

1.  Helidon Web Client Discovery integration dependency.
2.  The scope for the integration. `runtime` since the integration is never required at compile time.
3.  Helidon [Eureka Discovery provider](#eureka) dependency (for example).
4.  The scope for the provider. Use `runtime` if you have no interest in provider-specific classes and methods (the most common case). Use `compile` if you plan to call provider-specific methods.

The behavior of the Web Client Discovery integration is [fully specified and documented](/apidocs/io.helidon.webclient.discovery/io/helidon/webclient/discovery/WebClientDiscovery.html#handle(io.helidon.webclient.spi.WebClientService.Chain,io.helidon.webclient.api.WebClientServiceRequest)).

#### Configuration

The Helidon Web Client Discovery integration can be configured using [Helidon Config](config/introduction.md). Examples shown below are in YAML, but are expressible in any format and any location that Helidon Config supports.

Because the Helidon Web Client Discovery integration is fundamentally a [Web Client Service](webclient.md#_adding_service_to_webclient), you configure it under a Web Client’s `services` configuration node:

`application.yaml`

``` yaml
webclient:
  services:
    discovery: # (1)
```

1.  Indicates that the Web Client Discovery integration should apply to this Web Client configuration. More configuration is required; see below.

You also configure the Discovery provider in use following its documentation. See, for example, [Eureka configuration](#configuration).

#### Configuring URIs

To mark URIs requested by a Web Client as subject to discovery, and to use discovery names appropriate for them, you need to configure *prefix URIs*. URIs that match no prefix will not be subject to discovery:

`application.yaml`

``` yaml
webclient:
  services:
    discovery:
      prefix-uris:
        EXAMPLE: "https://example.com:443/" # (1)
        TEST: "https://test.example.com:443/" # (2) (3)
```

1.  Indicates that URIs starting with <a href="https://example.com:443/" class="bare"><code>https://example.com:443/</code></a> will be subject to discovery, using the discovery name of `EXAMPLE`
2.  Indicates that URIs starting with <a href="https://test.example.com:443/" class="bare"><code>https://test.example.com:443/</code></a> will be subject to discovery, using the discovery name of `TEST`
3.  URIs that begin with text other than <a href="https://example.com:443/" class="bare"><code>https://example.com:443/</code></a> or <a href="https://test.example.com:443/" class="bare"><code>https://test.example.com:443/</code></a> will not be subject to discovery

## References

- [Discovery Javadoc](/apidocs/io.helidon.discovery/module-summary.html)
- [Eureka Discovery Provider Javadoc](/apidocs/io.helidon.discovery.providers.eureka/module-summary.html)
- [Web Client Discovery Integration Javadoc](/apidocs/io.helidon.webclient.discovery/module-summary.html)
- [Helidon Web Client](webclient.md)
