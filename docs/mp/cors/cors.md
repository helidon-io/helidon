# CORS in Helidon MP

## Overview

The [cross-origin resource sharing (CORS) protocol](https://www.w3.org/TR/cors) helps developers control if and how REST resources served by their applications can be shared across origins. Helidon MP includes an implementation of CORS that you can use to add CORS behavior to the services you develop. You can define your application’s CORS behavior programmatically using the Helidon CORS API alone or together with configuration.

## Before You Begin

Before you revise your application to add CORS support, you need to decide what type of cross-origin sharing you want to allow for each resource your application exposes. For example, suppose for a given resource you want to allow unrestricted sharing for GET, HEAD, and POST requests (what CORS refers to as "simple" requests), but permit other types of requests only from the two origins `foo.com` and `there.com`. Your application would implement two types of CORS sharing: more relaxed for the simple requests and stricter for others.

Once you know the type of sharing you want to allow for each of your resources—​including any from built-in services—​you can change your application accordingly.

## Maven Coordinates

To enable CORS, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

```xml
<dependency>
    <groupId>io.helidon.microprofile</groupId>
    <artifactId>helidon-microprofile-cors</artifactId>
</dependency>
```

## Usage

Once you have planned how each of your resources should support CORS, you specify the CORS behavior in one of two ways:

- add `@Cors.*` annotations to the Java code for the resources, or
- add configuration.

You can do both. CORS configuration for a resource overrides any CORS settings declared using `@Cors.*` in the Java class for the resource.

## API

### The `@Cors.*` Annotations

Adding CORS behavior to your Helidon MP application involves just a few simple steps.

For each resource class in your application:

1.  Identify the resources and sub-resources—​in other words, the paths—​declared in the resource class which you want to support CORS.
2.  For each of those resources and sub-resources which should support CORS:
    1.  Find or create a Java method annotated with `@OPTIONS` and with the correct `@Path`.
    2.  To that `@OPTIONS` Java method add a Helidon [`@Cors.*`](/apidocs/io.helidon.webserver.cors/io/helidon/webserver/cors/Cors.html) annotation(s) that describes the cross-origin sharing you want for that resource.

> [!NOTE]
> Use the `@Cors.*` annotations *only* on methods which also have the `@OPTIONS` annotation. Remember that the CORS settings apply to a given path and therefore to all Java resource methods which share that path.
>
> Helidon MP aborts the server start-up if you use the `@Cors.*` annotations on a resource method other than an `@OPTIONS` method.
>
> For an informal look at the reasons for applying the `@Cors.*` annotations to the `@OPTIONS` method, instead of another method, see [Why `@OPTIONS`?](../../mp/cors/why-options.md).

The following annotations are available:

- [\`@Cors.Defaults](/apidocs/io.helidon.webserver.cors/io/helidon/webserver/cors/Cors.Defaults.html) - has no values, applies all defaults (do not combine with annotations below)
- [\`@Cors.AllowOrigins](/apidocs/io.helidon.webserver.cors/io/helidon/webserver/cors/Cors.AllowOrigins.html) - value is the allowed origins, defaults to all origins
- [\`@Cors.AllowHeaders](/apidocs/io.helidon.webserver.cors/io/helidon/webserver/cors/Cors.AllowHeaders.html) - value is the allowed HTTP header names, defaults to all headers
- [\`@Cors.AllowMethods](/apidocs/io.helidon.webserver.cors/io/helidon/webserver/cors/Cors.AllowMethods.html) - value is the allowed HTTP method names, defaults to all methods
- [\`@Cors.ExposeHeaders](/apidocs/io.helidon.webserver.cors/io/helidon/webserver/cors/Cors.ExposeHeaders.html) - value is the exposed HTTP header names, defaults to none
- [\`@Cors.AllowCredentials](/apidocs/io.helidon.webserver.cors/io/helidon/webserver/cors/Cors.AllowCredentials.html) - value is a boolean, defaults to false
- [\`@Cors.MaxAgeSeconds](/apidocs/io.helidon.webserver.cors/io/helidon/webserver/cors/Cors.MaxAgeSeconds.html) - value is the max age as a number of seconds

## Configuration

You can define CORS behavior—​and you or your users can override behavior declared in your code—​using configuration.

For each resource you want to configure, add a section to `META-INF/microprofile-config.properties` file:

*General form of CORS configuration*

```properties
cors.enabled= 

cors.paths.i.path-pattern= 
cors.paths.i.allow-headers=
cors.paths.i.max-age= 
cors.paths.i.allow-credentials=
cors.paths.i.allow-origins=
cors.paths.i.expose-headers=
cors.paths.i.allow-methods=
cors.paths.i.enabled= 
```

- You can disable CORS processing for all resources by setting `cors.enabled` to `false`. Defaults to `true`.
- Add a block for each resource you want to configure. The index `i` is an integer (0, 1, 2, etc).
- Specify the settings as needed to define the CORS behavior you want for that resource.
- The `max-age` option is a `Duration` string, such as `PT1H` for 1 hour
- The `enabled` setting lets you control whether the system uses that set of CORS configuration. Defaults to `true`.

The system uses the index `i`, not the position in the config file, to identify the settings for a particular resource.

Path patterns can be any expression accepted by the [`PathMatcher`](/apidocs/io.helidon.http/io/helidon/http/PathMatcher.html) class.

> [!NOTE]
> Helidon scans the cross-origin entries in index order (0, 1, 2, etc.) until it finds an entry that matches an incoming request’s path and HTTP method, so be sure to assign index values to the entries so Helidon will check them in the order you want. In particular, use lower index values for entries with more specific path patterns.

Each annotation in `Cors` class (except for `Defaults`) is mapped to one of the configuration options, see details below:

### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a63978-allow-credentials"></span> `allow-credentials` | `VALUE` | `Boolean` | `false` | Whether to allow credentials |
| <span id="abc506-allow-headers"></span> `allow-headers` | `LIST` | `String` | `*` | Set of allowed headers, defaults to all |
| <span id="a7f636-allow-methods"></span> `allow-methods` | `LIST` | `String` | `*` | Set of allowed methods, defaults to all |
| <span id="a10bcf-allow-origins"></span> `allow-origins` | `LIST` | `String` | `*` | Set of allowed origins, defaults to all |
| <span id="aeefbd-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether this CORS configuration should be enabled or not |
| <span id="abb307-expose-headers"></span> `expose-headers` | `LIST` | `String` |   | Set of exposed headers, defaults to none |
| <span id="a1f548-max-age"></span> `max-age` | `VALUE` | `i.h.w.c.C.PathCustomMethods` | `PT1H` | Max age as a duration |
| <span id="afe1ca-path-pattern"></span> `path-pattern` | `VALUE` | `String` |   | Path pattern to apply this configuration for |

## Examples

The [Helidon MP Quickstart application](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/quickstarts/helidon-quickstart-mp) allows users to:

- obtain greetings by sending `GET` requests to the `/greet` resource, and
- change the greeting message by sending a `PUT` request to the `/greet/greeting` resource.

The [Helidon MP CORS Example](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/microprofile/cors) shows the basic quickstart example enhanced for CORS.

The discussion below describes the changes in the application which:

- permit unrestricted sharing of the resource `/greet`, and
- restrict sharing of the resource `/greet/greeting` so that only the origins `http://foo.com` and `http://there.com` can change the greeting.

### Adding Annotations

*Using annotations to declare CORS behavior*

```java
@Path("/greet")
public class GreetResource { 

    @GET
    public JsonObject getDefaultMessage() { 
        return Json.createObjectBuilder()
                .add("message", "Hello")
                .build();
    }

    @Path("/greeting")
    @PUT
    public Response updateGreeting(JsonObject jsonObject) { 
        return Response.ok().build();
    }

    @OPTIONS
    @Cors.Defaults
    public void optionsForRetrievingUnnamedGreeting() { 
    }

    @OPTIONS
    @Path("/greeting")
    @Cors.AllowOrigins({"http://foo.com", "http://there.com"})
    @Cors.AllowMethods(HttpMethod.PUT)
    public void optionsForUpdatingGreeting() { 
    }
}
```

- Existing `GreetResource` resource class with path `/greet`.
- Existing `@GET` method for resource `/greet`.
- Existing `@PUT` method for resource `/greet/greeting`.
- New `@OPTIONS` method for `/greet`. (Just like the `@GET` method `getDefaultMessage`, this `@OPTIONS` method does not have a `@Path` annotation; both "inherit" the class-level `@Path` setting `/greet`.) The `@Cors.Defaults` annotation declares default cross-origin sharing which permits sharing via all HTTP methods to all origins.
- New `@OPTIONS` method for `/greet/greeting`. The `@Cors.AllowMethods` annotations specifies sharing only via the `PUT` HTTP method, and the `@Cors.AllowOrigins` specifies sharing only to the two listed origins.

### Adding Configuration

You could use the following configuration in place of using annotations to set up the same CORS behavior.

*Using configuration to set up the same CORS behavior*

```properties
cors.paths.0.path-pattern=/greet 

cors.paths.1.path-pattern=/greet/greeting 
cors.paths.1.allow-origins=https://foo.com,https://there.com
cors.paths.1.allow-methods=PUT
```

- Enables default CORS settings for the `/greet` resource.
- Sets up sharing for the `/greet/greeting` resource only via `PUT` requests and only from the specified origins.

Or, alternatively, the following configuration example augments the settings from the `@Cors.*` annotations in the code.

*Using configuration to augment or override declared CORS behavior*

```properties
cors.paths.0.path-pattern=/greet 
cors.paths.0.allow-methods=GET
cors.paths.0.allow-origins=https://here.com,https://foo.com,https://there.com

cors.paths.1.path-pattern=/greet/greeting 
cors.paths.1.allow-methods=PUT
cors.paths.1.allow-origins=https://foo.com
```

- Changes the declared settings to restrict cross-origin use of `/greet` to only `GET` and only from `foo.com` and `there.com`.
- Changes the settings for `/greet/greeting` from what they were declared; with this configuration, only the origin `foo.com` is permitted. (The declared setting also allowed `there.com`).

## Additional Information

## CORS and the Requested URI Feature

The decisions the Helidon CORS feature makes depend on accurate information about each incoming request, particularly the host to which the request is sent. Conveyed as headers in the request, this information can be changed or overwritten by intermediate nodes—​such as load balancers—​between the origin of the request and your service.

Well-behaved intermediate nodes preserve this important data in other headers, such as `Forwarded`. You can configure how the Helidon server handles these headers as described in the documentation for [requested URI discovery](../../mp/server.md#_using_requested_uri_discovery).

The CORS support in Helidon uses the requested URI feature to discover the correct information about each request, according to your configuration, so it can make accurate decisions about whether to permit cross-origin accesses.

## Configuring CORS for Built-in Services

Use configuration to control whether and how each of the built-in services works with CORS.

In the `cors` configuration section add a block for each built-in service using its path as described in the CORS configuration section.

The following example restricts sharing of

- the `/health` resource, provided by the health built-in service, to only the origin `https://there.com`, and
- the `/metrics` resource, provided by the metrics built-in service, to only the origin `https://foo.com`.

*Configuration which restricts sharing of the health and metrics resources*

```properties
cors.paths.0.path-pattern=/health
cors.paths.0.allow-origins=https://there.com
cors.paths.1.path-pattern=/metrics
cors.paths.1.allow-origins=https://foo.com
```

### Accessing the Shared Resources

If you have edited the Helidon MP QuickStart application as described in the previous topics and saved your changes, you can build and run the application. Once you do so you can execute `curl` commands to demonstrate the behavior changes in the metric and health services with the addition of the CORS functionality. Note the addition of the `Origin` header value in the `curl` commands, and the `Access-Control-Allow-Origin` in the successful responses.

#### Build and Run the Application

Build and run the QuickStart application as usual.

```bash
mvn package
java -jar target/helidon-quickstart-mp.jar
```

*Console output*

...
     2020.05.12 05:44:08 INFO io.helidon.microprofile.server.ServerCdiExtension Thread[main,5,main]: Server started on http://localhost:8080 (and all other host addresses) in 5280 milliseconds (since JVM startup).
     ...

### Retrieve Metrics

The metrics service rejects attempts to access metrics on behalf of a disallowed origin.

```bash
curl -i -H "Origin: https://other.com" http://localhost:8080/metrics
```

*Curl output*

```text
HTTP/1.1 403 Forbidden
Date: Mon, 11 May 2020 11:08:09 -0500
transfer-encoding: chunked
connection: keep-alive
```

But accesses from `foo.com` succeed.

```bash
curl -i -H "Origin: https://foo.com" http://localhost:8080/metrics
```

*Curl output*

```text
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

```bash
curl -i -H "Origin: https://foo.com" http://localhost:8080/health
```

```text
HTTP/1.1 403 Forbidden
Date: Mon, 11 May 2020 12:06:55 -0500
transfer-encoding: chunked
connection: keep-alive
```

And responds successfully only to cross-origin requests from `https://there.com`.

```bash
curl -i -H "Origin: https://there.com" http://localhost:8080/health
```

```text
HTTP/1.1 200 OK
Access-Control-Allow-Origin: https://there.com
Content-Type: application/json
Date: Mon, 11 May 2020 12:07:32 -0500
Vary: Origin
connection: keep-alive
content-length: 461

{"outcome":"UP",...}
```
