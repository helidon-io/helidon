# OpenAPI in Helidon

## Overview

The [OpenAPI specification](https://github.com/OAI/OpenAPI-Specification/blob/master/versions/3.0.0.md) defines a standard way to express the interface exposed by a REST service.

The [MicroProfile OpenAPI spec](https://download.eclipse.org/microprofile/microprofile-open-api-3.1.1/microprofile-openapi-spec-3.1.1.html) explains how MicroProfile embraces OpenAPI, adding annotations, configuration, and a service provider interface (SPI).

OpenAPI support in Helidon SE draws its inspiration from MicroProfile OpenAPI but does not implement the spec because Helidon SE does not support annotations.

The OpenAPI support in Helidon SE performs two main tasks:

- Build an in-memory model of the REST API your service implements.
- Expose the model in text format (YAML or JSON) via the `/openapi` endpoint.

To construct the model, Helidon gathers information about the service API from a static OpenAPI document file packaged as part of your service.

## Maven Coordinates

To enable OpenAPI, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

``` xml
<dependency>
    <groupId>io.helidon.openapi</groupId>
    <artifactId>helidon-openapi</artifactId>
</dependency>
```

## Usage

### Automatic Registration (default)

Simply by adding the dependency described above you add support for OpenAPI to your Helidon SE application. Because Helidon automatically discovers the OpenAPI feature, you do not have to make any changes to your application code.

### Explicit Registration

To control the behavior of the OpenAPI feature programmatically, you can add and configure the OpenAPI feature explicitly as explained below.

#### Create and Register `OpenApiFeature` in your application

Helidon SE provides the [`OpenApiFeature`](/apidocs/io.helidon.openapi/io/helidon/openapi/OpenApiFeature.html) class which your application uses to assemble the in-memory model and expose the `/openapi` endpoint to clients. You can create an instance either using a static `create` method or by instantiating its [`Builder`](/apidocs/io.helidon.openapi/io/helidon/openapi/OpenApiFeatureConfig.Builder.html). The [example below](#register-openapifeature-explicitly) illustrates one way to do this.

#### Furnish OpenAPI information about your endpoints

Your application supplies data for the OpenAPI model using a static OpenAPI file.

##### Provide a static OpenAPI file

Add a static file at `META-INF/openapi.yml`, `META-INF/openapi.yaml`, or `META-INF/openapi.json`. Tools such as Swagger let you describe your app’s API and they then generate an OpenAPI document file which you can include in your application so OpenAPI can use it.

### Accessing the REST Endpoint

Once you have added the SE OpenAPI dependency to your project, if you are using auto-discovery—​or if you are not using auto-discovery and you have added code to register the `OpenApiFeature` object with your routing—​then your application responds to the built-in endpoint — `/openapi` — and returns the OpenAPI document describing the endpoints in your application.

The default format of the OpenAPI document is YAML. There is not yet an adopted IANA YAML media type, but a proposed one specifically for OpenAPI documents that has some support is `application/vnd.oai.openapi`. That is what Helidon returns by default.

In addition, a client can specify the HTTP header `Accept` as either `application/vnd.oai.openapi+json` or `application/json` to request JSON. Alternatively, the client can pass the query parameter `format` as either `JSON` or `YAML` to receive `application/json` or `application/vnd.oai.openapi` (YAML) output, respectively.

## API

Helidon SE provides an API for creating and setting up the REST endpoint which serves OpenAPI documents to clients at the `/openapi` path. Use either static methods on [`OpenApiFeature`](/apidocs/io.helidon.openapi/io/helidon/openapi/OpenApiFeature.html) or use its [`Builder`](/apidocs/io.helidon.openapi/io/helidon/openapi/OpenApiFeatureConfig.Builder.html). Then add that instance or builder to your application’s routing. The [example](#register-openapifeature-explicitly) below shows how to do this.

## Configuration

Helidon SE OpenAPI configuration supports the settings described below in the `server.features.openapi` section.

### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a9052a-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Sets whether the feature should be enabled |
| <span id="a2bdbe-manager"></span> [`manager`](../../config/io_helidon_openapi_OpenApiManager.md) | `VALUE` | `i.h.o.OpenApiManager` |   | OpenAPI manager |
| <span id="a8b0d5-manager-discover-services"></span> `manager-discover-services` | `VALUE` | `Boolean` | `false` | Whether to enable automatic service discovery for `manager` |
| <span id="a99850-permit-all"></span> `permit-all` | `VALUE` | `Boolean` | `true` | Whether to allow anybody to access the endpoint |
| <span id="ad5281-roles"></span> `roles` | `LIST` | `String` | `openapi` | Hints for role names the user is expected to be in |
| <span id="a8653c-services"></span> [`services`](../../config/io_helidon_openapi_OpenApiService.md) | `LIST` | `i.h.o.OpenApiService` |   | OpenAPI services |
| <span id="ae938a-services-discover-services"></span> `services-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `services` |
| <span id="a47a74-sockets"></span> `sockets` | `LIST` | `String` |   | List of sockets to register this feature on |
| <span id="a0169c-static-file"></span> `static-file` | `VALUE` | `String` |   | Path of the static OpenAPI document file |
| <span id="ac378f-web-context"></span> `web-context` | `VALUE` | `String` | `/openapi` | Web context path for the OpenAPI endpoint |
| <span id="adb903-weight"></span> `weight` | `VALUE` | `Double` | `90.0` | Weight of the OpenAPI feature |

#### Deprecated Options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="ab0d30-cors"></span> [`cors`](../../config/io_helidon_cors_CrossOriginConfig.md) | `VALUE` | `i.h.c.CrossOriginConfig` | CORS config |

## Examples

Helidon SE provides a [complete OpenAPI example](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/openapi) based on the SE QuickStart sample app.

### Configure OpenAPI behavior

The following example shows how to use configuration to customize how OpenAPI works, in this case changing the endpoint where Helidon provides the OpenAPI document.

*Configure OpenAPI behavior*

``` yaml
server:
  port: 8080                  
  host: 0.0.0.0
  features:
    openapi:                  
      web-context: /myopenapi 
```

- The `port` and `host` settings are for the server as a whole, not specifically for OpenAPI.
- The `openapi` subsection within `features` contains OpenAPI settings.
- Changes the endpoint for returning the OpenAPI document from the default `/openapi` to `/myopenapi`.

Most Helidon SE applications need only add the dependency as explained above; Helidon discovers and registers OpenAPI automatically. The example below shows how to create and register `OpenApiFeature` explicitly instead.

### Register `OpenApiFeature` explicitly

*Java Code to Create and Register `OpenApiFeature`*

``` java
WebServer server = WebServer.builder()
        .config(config.get("server"))
        .addFeature(OpenApiFeature.create(config.get("openapi"))) 
        .routing(Main::routing)
        .build()
        .start();
```

- Adds the `OpenApiFeature` service to your server using the `openapi` section from configuration.

If you need programmatic control over the `OpenApiFeature` instance, invoke `OpenApiFeature.builder()` to get an `OpenApiFeature.Builder` object and work with it, then invoke the builder’s `build` method and pass the resulting `OpenApiFeature` instance to the `WebServer.Builder` `addFeature` method.
