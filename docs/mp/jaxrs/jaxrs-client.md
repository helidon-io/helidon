# Jakarta REST Client

## Contents

- [Overview](#overview)
- [Maven Coordinates](#maven-coordinates)
- [API](#api)
- [Configuration](#configuration)
- [Examples](#examples)
- [Additional Information](#additional-information)
- [Reference](#reference)

## Overview

The Jakarta REST Client defines a programmatic API to access REST resources. This API sits at a higher level than traditional HTTP client APIs and provides full integration with server-side API concepts like providers. It differs from the [Rest Client API](../restclient/restclient.md) in that it does not support annotations or proxies, but instead uses builders and a fluent API to create and execute requests.

## Maven Coordinates

To enable Jakarta REST Client, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

``` xml
 <dependency>
     <groupId>io.helidon.jersey</groupId>
     <artifactId>helidon-jersey-client</artifactId>
 </dependency>
```

## API

Bootstrapping the API is done by obtaining an instance of `Client`. A single instance of this class can be used to create multiple service requests that share the same basic configuration, e.g., the same set of *providers*. More precisely, from a `Client` we can create multiple `WebTarget` s, and in turn, from each `WebTarget` we can create multiple `Invocation` s.

``` java
Client client = ClientBuilder.newClient();
Response res = client
        .target("http://localhost:8080/greet")
        .request("text/plain")
        .get();
```

In the snippet above, the call to `target` returns a `WebTarget`, and the call to `request` returns an `Invocation.Builder`; finally, the call to `get` returns the `Response` that results from accessing the remote resource.

Given that this API is fully integrated with message body readers and writers, it is possible to request the response body be provided after conversion to a Java type — such as a `String` in the example below.

``` java
Client client = ClientBuilder.newClient();
String res = client
        .target("http://localhost:8080/greet")
        .request("text/plain")
        .get(String.class);
```

Alternatively, there are also methods in `Response` that can trigger similar conversions.

Configuration can be specified at the `Client` or `WebTarget` level, as both types implement `Configurable<T>`. This enables common configuration to be inherited by a `WebTarget` created from a `Client` instance. In either case, several `register` methods can be used to configure providers such as filters and exception mappers.

``` java
Client client = ClientBuilder.newClient();
client.register(GreetFilter.class);
String res = client
        .target("http://localhost:8080/greet")
        .register(GreetExceptionMapper.class)
        .request("text/plain")
        .get(String.class);
```

The example above shows registration of `GreetFilter.class` for all targets and registration of `GreetExceptionMapper.class` for just one of them. The same logic applies to other types of configuration such as properties and features.

The Jakarta REST Client API has support for asynchronous invocations. Accessing a resource asynchronously prevents the calling thread from blocking for the duration of the call. By default, all invocations are *synchronous* but can be turned into either asynchronous or reactive calls by simply inserting the corresponding fluent method call during the creation phase.

Using `Future`:

``` java
Client client = ClientBuilder.newClient();
Future<String> res = client
        .target("http://localhost:8080/greet")
        .request("text/plain")
        .async()        // now asynchronous
        .get(String.class);
```

Or using a more modern, reactive style:

``` java
Client client = ClientBuilder.newClient();
CompletionStage<String> res = client
        .target("http://localhost:8080/greet")
        .request("text/plain")
        .rx()           // now reactive
        .get(String.class);
```

In either case, the implementation will ensure the calling thread is not blocked and that the result from the invocation is available upon request or via a callback mechanism.

## Configuration

Configuration for this API is all done programmatically as shown in the previous sections.

## Examples

See [API](#api) for same simple examples. For additional information, refer to the

[Jakarta REST Client Specification](https://jakarta.ee/specifications/restful-ws/3.1/jakarta-restful-ws-spec-3.1.html#client_api).

## Additional Information

For additional information, see the [Jakarta REST Javadocs](https://jakarta.ee/specifications/restful-ws/3.1/apidocs).

## Reference

- [Jakarta REST Client Specification](https://jakarta.ee/specifications/restful-ws/3.1/jakarta-restful-ws-spec-3.1.html#client_api)
