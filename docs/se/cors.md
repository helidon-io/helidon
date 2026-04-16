# CORS in Helidon SE

## Overview

The [cross-origin resource sharing (CORS) protocol](https://www.w3.org/TR/cors) helps developers control if and how REST resources served by their applications can be shared across origins. Helidon SE includes an implementation of CORS that you can use to add CORS behavior to the services you develop. You can define your application’s CORS behavior programmatically using the Helidon CORS API alone or together with configuration.

## Before You Begin

### Planning Your Resource Sharing

Before you revise your application to add CORS support, you need to decide what type of cross-origin sharing you want to allow for each resource your application exposes. For example, suppose for a given resource you want to allow unrestricted sharing for GET, HEAD, and POST requests (what CORS refers to as "simple" requests), but permit other types of requests only from the two origins `foo.com` and `there.com`. Your application would implement two types of CORS sharing: more relaxed for the simple requests and stricter for others.

Once you know the type of sharing you want to allow for each of your resources—​including any from built-in services—​you can change your application accordingly.

#### Choosing How To Implement CORS

You can add CORS support to your application in either or both of the following ways, depending on your specific requirements:

- Use configuration and automatic feature detection: **recommended**.

  If you add the Helidon CORS Maven artifact to your project, at runtime Helidon automatically discovers it and activates it according to configuration. You do not need to change your Java code. Instead, you control your application’s CORS behavior entirely using configuration linked to the resource paths your application exposes.

  This is the simplest way to set up CORS for your service, and we recommend you use this approach.

- Use the Helidon CORS WebServer Feature to add CORS support programmatically

  Your code creates an instance of `io.helidon.webserver.cors.CorsFeature` with custom \`CorsPathConfig\`s, and adds it to the server builder.

The following sections briefly illustrate each approach.

## Maven Coordinates

To enable CORS, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../about/managing-dependencies.md)).

``` xml
<dependency>
    <groupId>io.helidon.webserver</groupId>
    <artifactId>helidon-webserver-cors</artifactId>
</dependency>
```

## API

### Using the Config-only Approach

If you add the `io.helidon.webserver:helidon-webserver-cors` Maven artifact to your project you do not have to add any CORS-specific code to your application to implement CORS. Express the CORS behavior you want in configuration, associating path patterns with the CORS settings you want to apply to the matching paths.

See the [configuration](#configuration) section below for more information.

### Adding Code to Include CORS in WebServer

The Helidon SE CORS API provides two key classes that you use in your application:

- [`CorsFeature`](/apidocs/io.helidon.webserver.cors/io/helidon/webserver/cors/CorsFeature.html) - `WebServer` feature that contains per-path CORS configurations (`CorsPathConfig`) to enforce at runtime; there will be exactly one instance of this class, and it can be configured using a builder
- [`CorsPathConfig`](/apidocs/io.helidon.webserver.cors/io/helidon/webserver/cors/CorsPathConfig.html) - Represents the details for a specific path and a particular type of sharing, such as which origins are allowed to have access using which HTTP methods, etc. Create one instance of `CorsPathConfig` for each path you want covered by CORS

The CORS feature works as follows:

- it registers a filter that handles both pre-flight `OPTIONS` requests and regular requests, the ordering is handled through its weight - by default it is always executed before routing and filters registered in routing
- it registers a low weight route for `OPTIONS` method, to ensure we return a non-`404` response for pre-flight requests
- if a pre-flight request comes to the webserver, the filter will check CORS against the configured path configurations, and configure the correct response headers
- if a request comes to the webserver that is not pre-flight, and it is a CORS request, the filter will validate the request can be executed, and adds appropriate headers to the response, OR it terminates the request as forbidden

### Sample Routing Setup Using the `CrossOriginConfig` API

The [Helidon SE Quickstart application](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/quickstarts/helidon-quickstart-se) lets you change the greeting by sending a `PUT` request to the `/greet/greeting` resource.

This example, based on the QuickStart greeting app, uses the CORS API to influence the [routing](../se/webserver/webserver.md#routing), thereby determining how that resource is shared. (If desired, you can use [configuration](#configuration) instead of the low-level API.)

The following code shows one way to prepare your application’s routing to support CORS.

``` java
CorsFeature corsFeature = CorsFeature.builder() // (1)
        .addPath(path -> path // (2)
                .pathPattern("/greet/*") // (3)
                .addAllowOrigin("http://foo.bar") // (4)
                .addAllowMethod(Method.PUT) // (5)
        )
        .build(); // (6)

WebServer.builder()
        .port(8080)
        .addFeature(corsFeature) // (7)
        .build();
```

1.  Create a builder for `CorsFeature`
2.  Add a `CorsPathConfig` using a builder (can be added multiple times with different configuration), this builder allows configuration of all available CORS options
3.  Configure the path pattern of this CORS config (uses the same pattern as WebServer routing)
4.  Add allow origin
5.  Add allow method
6.  Build the `CorsFeature` instance
7.  Register the new `CorsFeature` instance with WebServer builder

The ordering of `.addPath(…​)` methods when configuring the `CorsFeature` is significant, as they are checked in order, and the first `CorsPathConfig` that matches the requested path and method will be used.

If you configure

/greet/

first, and then `/greet/admin` with the same methods, but different allowed origins (or other options), the `/greet/` path will be used as it also matches `/greet/admin` and is first.

By adding the few additional lines described above you allow the greeting application to participate in CORS.

## Configuration

You can use configuration instead of or in combination with the Helidon CORS SE API to add CORS support to your resources by replacing some Java code with declarative configuration.

### Configuration for Automatic CORS Processing

Recall that simply by adding the `io.helidon.webserver:helidon-webserver-cors` artifact to your project you allow Helidon to automatically use configuration to set up CORS behavior throughout your application.

To use this automatic support, make sure your configuration contains a `cors` section which contains CORS path configuration as described below and as shown in the following example.

``` yaml
cors:
  paths:
    - "path-pattern": "/greeting"
      "allow-origins": ["https://foo.com", "https://there.com", "https://other.com"]
      "allow-methods": ["PUT", "DELETE"]
    - "path-pattern": "/"
      "allow-methods": ["GET", "HEAD", "OPTIONS", "POST"]
```

### Understanding the CORS Configuration Formats

CORS configuration is done through [`CorsFeature`](/apidocs/io.helidon.webserver.cors/io/helidon/webserver/cors/CorsFeature.html), a `WebServer` feature that configures CORS for the whole application. This configuration contains a list of protected `paths`, which use the Cross-Origin options and are mapped to the [`CorsPathConfig`](/apidocs/io.helidon.webserver.cors/io/helidon/webserver/cors/CorsPathConfig.html).

### Cross-Origin Server Feature Configuration

### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="ae53cb-add-defaults"></span> `add-defaults` | `VALUE` | `Boolean` | `true` | Whether to add a default path configuration, that matches all paths, `GET, HEAD, POST` methods, and allows all origins, methods, and headers |
| <span id="a6b476-enabled"></span> `enabled` | `VALUE` | `Boolean` |   | This feature can be disabled |
| <span id="a44bb0-paths"></span> [`paths`](../config/io_helidon_webserver_cors_CorsPathConfig.md) | `LIST` | `i.h.w.c.CorsPathConfig` |   | Per path configuration |
| <span id="a29c5b-paths-discover-services"></span> `paths-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `paths` |
| <span id="a93acb-sockets"></span> `sockets` | `LIST` | `String` |   | List of sockets to register this feature on |
| <span id="a96481-weight"></span> `weight` | `VALUE` | `Double` | `850.0` | Weight of the CORS feature |

## Examples

For a complete example, see [Helidon SE CORS Example](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/cors).

## Additional Information

### CORS and the Requested URI Feature

The decisions the Helidon CORS feature makes depend on accurate information about each incoming request, particularly the host to which the request is sent. Conveyed as headers in the request, this information can be changed or overwritten by intermediate nodes—​such as load balancers—​between the origin of the request and your service.

Well-behaved intermediate nodes preserve this important data in other headers, such as `Forwarded`. You can configure how the Helidon server handles these headers as described in the documentation for [requested URI discovery](../se/webserver/webserver.md#_requested_uri_discovery).

The CORS support in Helidon uses the requested URI feature to discover the correct information about each request, according to your configuration, so it can make accurate decisions about whether to permit cross-origin accesses.

### Configuring CORS for Built-in Services

Use configuration to control whether and how each of the built-in services works with CORS.

In the `cors` configuration section add a block for each built-in service using its path as described in the CORS configuration section. The following example restricts sharing of the `/observe/health` resource, provided by the health built-in service, to only the origin `https://there.com`.

``` yaml
cors:
  paths:
    - "path-pattern": "/observe/health"
      "allow-origins": ["https://there.com"]
    - "path-pattern": "/observe/metrics"
      "allow-origins": ["https://foo.com"]
```

### Accessing the Shared Resources

If you have edited the Helidon SE QuickStart application as described in the previous topics and saved your changes, you can build and run the application. Once you do so you can execute `curl` commands to demonstrate the behavior changes in the metric and health services with the addition of the CORS functionality. Note the addition of the `Origin` header value in the `curl` commands, and the `Access-Control-Allow-Origin` in the successful responses.

#### Build and Run the Application

Build and run the QuickStart application as usual.

``` bash
mvn package
java -jar target/helidon-quickstart-se.jar
```

``` text
WEB server is up! http://localhost:8080/greet
```

### Retrieve Metrics

The metrics service rejects attempts to access metrics on behalf of a disallowed origin.

``` bash
curl -i -H "Origin: https://other.com" http://localhost:8080/observe/metrics
```

Curl output

``` bash
HTTP/1.1 403 Forbidden
Date: Mon, 11 May 2020 11:08:09 -0500
transfer-encoding: chunked
connection: keep-alive
```

But accesses from `foo.com` succeed.

``` bash
curl -i -H "Origin: https://foo.com" http://localhost:8080/observe/metrics
```

Curl output

``` bash
HTTP/1.1 200 OK
Access-Control-Allow-Origin: https://foo.com
Content-Type: text/plain
Date: Mon, 11 May 2020 11:08:16 -0500
Vary: Origin
connection: keep-alive
content-length: 6065

# TYPE base_classloader_loadedClasses_count gauge
# HELP base_classloader_loadedClasses_count Displays the number of classes that are currently loaded in the Java virtual machine.
base_classloader_loadedClasses_count 3568
```

#### Retrieve Health

The health service rejects requests from origins not specifically approved.

``` bash
curl -i -H "Origin: https://foo.com" http://localhost:8080/observe/health
```

``` bash
HTTP/1.1 403 Forbidden
Date: Mon, 11 May 2020 12:06:55 -0500
transfer-encoding: chunked
connection: keep-alive
```

And responds successfully only to cross-origin requests from `https://there.com`.

``` bash
curl -i -H "Origin: https://there.com" http://localhost:8080/observe/health
```

``` bash
HTTP/1.1 200 OK
Access-Control-Allow-Origin: https://there.com
Content-Type: application/json
Date: Mon, 11 May 2020 12:07:32 -0500
Vary: Origin
connection: keep-alive
content-length: 461

{"outcome":"UP",...}
```
