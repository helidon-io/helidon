# Rest Client

## Overview

MicroProfile Rest Client adds the capability to invoke remote services by defining a Java interface with Jakarta REST (JAX-RS) annotations that resembles a server-side resource class.

Helidon will automatically create a *proxy* class for the interface and map local proxy calls to remote REST calls.

For more information, see [Rest Client For MicroProfile Specification](https://download.eclipse.org/microprofile/microprofile-rest-client-3.0/microprofile-rest-client-spec-3.0.html).

You can also use metrics annotations on your Rest Client methods as described in [this related page.](restclientmetrics.md)

## Maven Coordinates

To enable MicroProfile Rest Client, either add a dependency on the [helidon-microprofile bundle](../../mp/introduction/microprofile.md) or add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

```xml
<dependency>
    <groupId>io.helidon.microprofile.rest-client</groupId>
    <artifactId>helidon-microprofile-rest-client</artifactId>
</dependency>
```

## API

| Class | Description |
|----|----|
| org.eclipse.microprofile.rest.client.RestClientBuilder | Base builder instance. Contains configuration options and a `build` method that creates the actual client instance. |

| Annotation | Description |
|----|----|
| @RegisterRestClient | A marker annotation to register a client at runtime. This marker must be applied to any CDI managed clients. |
| @RestClient | RestClient qualifier which should be used on an CDI injection points. |

### Creating a New Client Using a Builder

MicroProfile Rest Client can be created using a builder obtained from `RestClientBuilder.newBuilder()`.

The builder provides methods to specify the client interface to be proxied as well as to configure additional details such as server URI, SSL context, connection timeouts, etc. Any method call on the resulting proxy object will be automatically translated into a remote call to the service using the provided configuration.

*Example*

```java
GreetRestClient greetResource = RestClientBuilder.newBuilder()
        .baseUri(URI.create("http://localhost:8080/greet"))
        .build(GreetRestClient.class);
greetResource.getDefaultMessage();
```

The `RestClientBuilder` interface extends the `Configurable` interface from Jakarta REST (JAX-RS), enabling direct registration of *providers* such as filters, param converters, exception mappers, etc.

*Example*

```java
GreetRestClient greetResource = RestClientBuilder.newBuilder()
        .baseUri(URI.create("http://localhost:8080"))
        .register(GreetClientRequestFilter.class)
        .register(GreetClientExceptionMapper.class)
        .build(GreetRestClient.class);
greetResource.getDefaultMessage();
```

### Creating a New Client Using CDI

A client interface can be annotated with `@RegisterRestClient` to automatically register it with CDI. This annotation has a property called `baseUri` that can be used to define the base endpoint to be used by the client to access the service.

*Example*

```java
@Path("/greet")
@RegisterRestClient(baseUri = "http://localhost:8080")
public interface GreetRestClient {
    // ...
}
```

Any Jakarta REST (JAX-RS) providers for a client can be registered using the (repeatable) `@RegisterProvider` annotation on the interface as shown below.

*Example*

```java
@Path("/greet")
@RegisterRestClient(baseUri = "http://localhost:8080")
@RegisterProvider(GreetClientRequestFilter.class)
@RegisterProvider(GreetClientExceptionMapper.class)
public interface GreetRestClient {
    // ...
}
```

Once a client interface is annotated, it can be injected into any CDI bean.

All properties in annotation `RegisterRestClient` can be overridden via configuration as described in [Configuration options](#configuration-options)

*Example*

```java
public class MyBean {
    @Inject
    @RestClient
    GreetRestClient client;

    void myMethod() {
        client.getMessage("Helidon");
    }
}
```

## Configuration

Configuration is only available for CDI managed client instances, it is not supported for client created programmatically using `RestClientBuilder`.

Most of the configuration properties mentioned below have to be prepended with the fully qualified classname of the client interface to be configured.

It is possible to avoid fully qualified classname by using `@RegisterRestClient(configKey="clientAlias")`, the prefix `$restClient` is used below to indicate an alias or a class name.

### Configuration options

Required configuration options:

| key | type | default value | description |
|----|----|----|----|
| `$restClient/mp-rest/url` | string |   | Sets the base URL to use for this service. This option or `/mp-rest/uri` need to be set if the value is not present in `RegisterRestClient#baseUri`. |
| `$restClient/mp-rest/uri` | string |   | Sets the base URI to use for this service. This option or `/mp-rest/url` need to be set if the value is not present in `RegisterRestClient#baseUri`. |

Optional configuration options:

| key | type | default value | description |
|----|----|----|----|
| `$restClient/mp-rest/scope` | string | `jakarta.enterprise.context.Dependent` | The fully qualified classname to a CDI scope to use for injection. |
| `$restClient/mp-rest/connectTimeout` | long |   | Sets timeout in milliseconds to wait to connect to the remote endpoint. |
| `$restClient/mp-rest/readTimeout` | long |   | Sets timeout in milliseconds to wait for a response from the remote endpoint. |
| `$restClient/mp-rest/followRedirects` | boolean | `false` | Sets value used to determine whether the client should follow HTTP redirect responses. |
| `$restClient/mp-rest/proxyAddress` | string |   | Sets a string value in the form of \<proxyHost\>:\<proxyPort\> that specifies the HTTP proxy server hostname (or IP address) and port for requests of this client to use. |
| `$restClient/mp-rest/queryParamStyle` | string (MULTI_PAIRS, COMMA_SEPARATED, ARRAY_PAIRS) | `MULTI_PAIRS` | Sets enumerated type string value that specifies the format in which multiple values for the same query parameter is used. |
| `$restClient/mp-rest/trustStore` | string |   | Sets the trust store location. Can point to either a classpath resource (e.g. classpath:/client-truststore.jks) or a file (e.g. file:/home/user/client-truststore.jks). |
| `$restClient/mp-rest/trustStorePassword` | string |   | Sets the password for the trust store. |
| `$restClient/mp-rest/trustStoreType` | string | `JKS` | Sets the type of the trust store. |
| `$restClient/mp-rest/keyStore` | string |   | Sets the key store location. Can point to either a classpath resource (e.g. classpath:/client-keystore.jks) or a file (e.g. file:/home/user/client-keystore.jks). |
| `$restClient/mp-rest/keyStorePassword` | string |   | Sets the password for the keystore. |
| `$restClient/mp-rest/keyStoreType` | string | `JKS` | Sets the type of the keystore. |
| `$restClient/mp-rest/hostnameVerifier` | string |   | Sets the hostname verifier class. This class must have a public no-argument constructor. |

Configuration options affecting CDI and programmatically created clients:

| key | type | default value | description |
|----|----|----|----|
| `$restClient/mp-rest/providers` | string |   | A comma separated list of fully-qualified provider classnames to include in the client. |
| `$restClient/mp-rest/providers/<fully-qualified-provider-classname>/priority` | string |   | Sets the priority of the provider for this interface. |
| `org.eclipse.microprofile.rest.client.propagateHeaders` | string |   | To specify which headers to propagate from the inbound JAX-RS request to the outbound MP Rest Client request. Should not be prefixed with the rest client class or alias. |
| `microprofile.rest.client.disable.default.mapper` | boolean | `false` | Whether to disable default exception mapper. Should not be prefixed with the rest client class or alias. |

## Examples

To be able to run and test this example, use the [Helidon MP examples/quickstarts](../guides/quickstart.md). Add a dependency on the Helidon Rest Client implementation and create the following client interface:

*client interface*

```java
@Path("/greet")
interface GreetRestClient {

    @GET
    JsonObject getDefaultMessage();

    @Path("/{name}")
    @GET
    JsonObject getMessage(@PathParam("name") String name);

}
```

Then create a runnable method as described in [Creating new client](#creating-a-new-client-using-a-builder), but with baseUri `http://localhost:8080/greet` and the above interface.

By calling `GreetRestClient.getDefaultMessage()` you reach the endpoint of Helidon quickstart.

## Reference

- [Helidon MicroProfile RestClient JavaDoc](/apidocs/io.helidon.microprofile.restclient/module-summary.html)
- [MicroProfile RestClient Specification](https://download.eclipse.org/microprofile/microprofile-rest-client-3.0/microprofile-rest-client-spec-3.0.html)
- [MicroProfile RestClient on GitHub](https://github.com/eclipse/microprofile-rest-client)
