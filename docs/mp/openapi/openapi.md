# OpenAPI in Helidon

## Overview

The [OpenAPI specification][openapi-specific] defines a standard way to express
the interface exposed by a REST service.

The [MicroProfile OpenAPI spec][microprofile-ope] explains how MicroProfile
embraces OpenAPI, adding annotations, configuration, and a service provider
interface (SPI).

Helidon MP implements the MicroProfile OpenAPI specification.

The OpenAPI support in Helidon MP performs two main tasks:

- Build an in-memory model of the REST API your service implements.
- Expose the model in text format (YAML or JSON) via the `/openapi` endpoint.

To construct the model, Helidon gathers information about the service API from
whichever of these sources are present in the application:

- a static OpenAPI document file packaged as part of your service;
- a *model reader*

  The SPI defines an interface you can implement in your application for
  programmatically providing part or all the model;

- OpenAPI annotations;
- a *filter* class

  The SPI defines an interface you can implement in your application which can
  mask parts of the model.

## Maven Coordinates

To enable MicroProfile OpenAPI, either add a dependency on the
[helidon-microprofile bundle](../introduction.md) or add the following
dependency to your project’s `pom.xml` (see [Managing
Dependencies](../../managing-dependencies.md)).

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.microprofile.openapi</groupId>
  <artifactId>helidon-microprofile-openapi</artifactId>
  <scope>runtime</scope>
</dependency>
```

If you do not use the `helidon-microprofile-bundle` also add the following
dependency which defines the MicroProfile OpenAPI annotations so you can use
them in your code:

```xml [pom.xml]
<dependency>
  <groupId>org.eclipse.microprofile.openapi</groupId>
  <artifactId>microprofile-openapi-api</artifactId>
</dependency>
```

## Usage

### OpenAPI support in Helidon MP

You can very simply add support for OpenAPI to your Helidon MP application. This
document shows what changes you need to make to your application and how to
access the OpenAPI document for your application at runtime.

### Changing your application

To use OpenAPI from your Helidon MP app, in addition to adding dependencies as
described above:

1.  Furnish OpenAPI information about your application’s endpoints.
2.  Update your application’s configuration (optional).

#### Furnish OpenAPI information about your endpoints

Helidon MP OpenAPI combines information from all the following sources as it
builds its in-memory model of your application’s API. It constructs the OpenAPI
document from this internal model. Your application can use one or more of these
techniques.

##### Annotations on the endpoints in your app

You can add MicroProfile OpenAPI annotations to the endpoints in your source
code. These annotations allow the Helidon MP OpenAPI runtime to discover the
endpoints and information about them via CDI at app start-up.

Here is one of the endpoints, annotated for OpenAPI, from the example mentioned
earlier:

```java
@GET
@Operation(summary = "Returns a generic greeting", 
           description = "Greets the user generically")
@APIResponse(description = "Simple JSON containing the greeting", 
             content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = GreetingMessage.class)))
@Produces(MediaType.APPLICATION_JSON)
public JsonObject getDefaultMessage() {
    return Json.createObjectBuilder()
            .add("message", "Hello World!")
            .build();
}
```

- `@Operation` gives information about this endpoint.
- `@APIResponse` describes the HTTP response and declares its media type and
  contents.

You can also define any request parameters the endpoint expects, although this
endpoint uses none.

This excerpt shows only a few annotations for illustration. The [Helidon MP
OpenAPI basic example][helidon-mp-opena] illustrates more, and the [MicroProfile
OpenAPI spec][microprofile-ope] describes them all.

##### A static OpenAPI file

Add a static file at `META-INF/openapi.yml`, `META-INF/openapi.yaml`, or
`META-INF/openapi.json`. Tools such as Swagger let you describe your app’s API,
and they then generate an OpenAPI document file which you can include in your
application so OpenAPI can use it.

##### A model reader class your application provides

Write a Java class that implements the OpenAPI
[`org.eclipse.microprofile.openapi.OASModelReader`][org-eclipse-micr] interface.
Your model reader code programmatically adds elements to the internal model that
OpenAPI builds.

Then set the `mp.openapi.model.reader` configuration property to the
fully-qualified name of your model reader class.

##### A filter class your application provides

Write a Java class that implements the OpenAPI
[`org.eclipse.microprofile.openapi.OASFilter`][org-eclipse-micr-2] interface.
Helidon invokes your filter methods for each element of the in-memory model,
allowing your code to modify an element or completely remove it from the model.

Then set the `mp.openapi.filter` configuration property to the fully-qualified
name of your filter class.

### Update your application configuration

Beyond the two config properties that denote the model reader and filter,
Helidon MP OpenAPI supports a number of other mandated settings. These are
described in the [configuration section][configuration-se] of the MicroProfile
OpenAPI spec.

### Accessing the REST Endpoint

Once you have added the MP OpenAPI dependency to your project, then your
application responds to the built-in endpoint — `/openapi` — and returns the
OpenAPI document describing the endpoints in your application.

Per the MicroProfile OpenAPI spec, the default format of the OpenAPI document is
YAML. There is not yet an adopted IANA YAML media type, but a proposed one
specifically for OpenAPI documents that has some support is
`application/vnd.oai.openapi`. That is what Helidon returns by default.

In addition, a client can specify the HTTP header `Accept` as either
`application/vnd.oai.openapi+json` or `application/json` to request JSON.
Alternatively, the client can pass the query parameter `format` as either `JSON`
or `YAML` to receive `application/json` or `application/vnd.oai.openapi` (YAML)
output, respectively.

## API

The [MicroProfile OpenAPI specification][microprofile-ope] gives a listing and
brief examples of the annotations you can add to your code to convey OpenAPI
information.

The [MicroProfile OpenAPI Javadocs][microprofile-ope-2] give full details of the
annotations and the other classes and interfaces you can use in your code.

## Configuration

Helidon OpenAPI configuration supports the following settings:

### Configuration options

<!--@include ../../config/io.helidon.openapi.OpenApiFeature.md#configuration-options delim=--- offset=1 collapseTables=10 -->
See [Configuration options][io-helidon-opena].
<!--/include-->


Further, Helidon OpenAPI supports the MicroProfile OpenAPI settings described in
[the MicroProfile OpenAPI specification][the-microprofile].

### MicroProfile OpenAPI configuration options

| Key                                                 | Type      | Description                                                                                                                                                     |
|-----------------------------------------------------|-----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `mp.openapi.extensions.helidon.use-jaxrs-semantics` | `Boolean` | If `true` and the `jakarta.ws.rs.core.Application` class returns a non-empty set, endpoints defined by other resources are not included in the OpenAPI document |

## Examples

Helidon MP includes a [complete OpenAPI example][complete-openapi] based on the
MP quick-start sample app. The rest of this section shows, step-by-step, how one
might change the original QuickStart service to adopt OpenAPI.

### Helidon MP OpenAPI Example

This example shows a simple greeting application, similar to the one from the
Helidon MP QuickStart, enhanced with OpenAPI support.

```java
@Path("/greeting")
@PUT
@Operation(summary = "Set the greeting prefix",
           description = "Permits the client to set the prefix part of the greeting (\"Hello\")") 
@RequestBody( 
              name = "greeting",
              description = "Conveys the new greeting prefix to use in building greetings",
              content = @Content(
                      mediaType = "application/json",
                      schema = @Schema(implementation = GreetingUpdateMessage.class),
                      examples = @ExampleObject(
                              name = "greeting",
                              summary = "Example greeting message to update",
                              value = "{\"greeting\": \"New greeting message\"}")))
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public Response updateGreeting(JsonObject jsonObject) {
    return Response.ok().build();
}
```

- With `@Operation` annotation we document the current method.
- With `@RequestBody` annotation we document the content produced. Internal
  annotations `@Content`, `@Schema` and `@ExampleObjects` are used to give more
  details about the returned data.

If we want to hide a specific path an `OASFilter` is used.

The OASFilter interface allows application developers to receive callbacks for
various key OpenAPI elements. The interface has a default implementation for
every method, which allows application developers to only override the methods
they care about. To use it, simply create an implementation of this interface
and register it using the `mp.openapi.filter configuration` key, where the value
is the fully qualified name of the filter class.

The following example filter prevents information about a given path from
appearing in the OpenAPI document.

```java
public class SimpleAPIFilter implements OASFilter {

    @Override
    public PathItem filterPathItem(PathItem pathItem) {
        for (var methodOp : pathItem.getOperations().entrySet()) {
            if (SimpleAPIModelReader.DOOMED_OPERATION_ID
                    .equals(methodOp.getValue().getOperationId())) {
                return null;
            }
        }
        return OASFilter.super.filterPathItem(pathItem);
    }
}
```

You can implement a model reader to provide all or part of the in-memory
`OpenAPI` model programmatically. Helidon `OpenAPI` merges the model from the
model reader with models from the other sources (a static file and annotations).

The example model reader below creates an `OpenAPI` object describing two paths.
It turns out that the filter described earlier will suppress one of the paths,
but the model reader does not know or care.

<!--@mdc ::code-collapse -->
```java
/**
 * Defines two paths using the OpenAPI model reader mechanism, one that should
 * be suppressed by the filter class and one that should appear in the published
 * OpenAPI document.
 */
public class SimpleAPIModelReader implements OASModelReader {

    /**
     * Path for the example endpoint added by this model reader that should be visible.
     */
    public static final String MODEL_READER_PATH = "/test/newpath";

    /**
     * Path for an endpoint that the filter should hide.
     */
    public static final String DOOMED_PATH = "/test/doomed";

    /**
     * ID for an endpoint that the filter should hide.
     */
    public static final String DOOMED_OPERATION_ID = "doomedPath";

    /**
     * Summary text for the endpoint.
     */
    public static final String SUMMARY = "A sample test endpoint from ModelReader";

    @Override
    public OpenAPI buildModel() {
        /*
         * Add two path items, one of which we expect to be removed by
         * the filter and a very simple one that will appear in the
         * published OpenAPI document.
         */
        PathItem newPathItem = OASFactory.createPathItem()
                .GET(OASFactory.createOperation()
                             .operationId("newPath")
                             .summary(SUMMARY));
        PathItem doomedPathItem = OASFactory.createPathItem()
                .GET(OASFactory.createOperation()
                             .operationId(DOOMED_OPERATION_ID)
                             .summary("This should become invisible"));
        OpenAPI openAPI = OASFactory.createOpenAPI();
        Paths paths = OASFactory.createPaths()
                .addPathItem(MODEL_READER_PATH, newPathItem)
                .addPathItem(DOOMED_PATH, doomedPathItem);
        openAPI.paths(paths);

        return openAPI;
    }
}
```
<!--@mdc :: -->

Having written the filter and model reader classes, identify them by adding
configuration to `META-INF/microprofile-config.properties` as the following
example shows.

```properties [microprofile-config.properties]
mp.openapi.filter=io.helidon.microprofile.examples.openapi.internal.SimpleAPIFilter
mp.openapi.model.reader=io.helidon.microprofile.examples.openapi.internal.SimpleAPIModelReader
```

Now just build and run:

```shell [Terminal]
mvn package
java -jar target/helidon-examples-microprofile-openapi.jar
```

Try the endpoints:

```shell [Terminal]
curl -X GET http://localhost:8080/greet
{"message":"Hello World!"}

curl -X GET http://localhost:8080/openapi
[lengthy OpenAPI document]
```

The output describes not only then endpoints from `GreetResource` but also one
contributed by the `SimpleAPIModelReader`.

Full example is available [in our official repository][complete-openapi]

## Jandex

A Jandex index stores information about the classes and methods in your app and
what annotations they have. It allows CDI to process annotations faster during
your application’s start-up, and OpenAPI uses the Jandex index to discover
details about the types in your resource method signatures.

> [!NOTE]
> It is recommended to create the index at build-time to speed up the
> application start-up.

Add an invocation of the [Jandex maven plug-in][jandex-maven-plu] to the
`<build><plugins>` section of your `pom.xml` if it is not already there:

```xml [pom.xml]
<plugin>
  <groupId>io.smallrye</groupId>
  <artifactId>jandex-maven-plugin</artifactId>
  <executions>
    <execution>
      <id>make-index</id>
    </execution>
  </executions>
</plugin>
```

When you build your app the plug-in generates the Jandex index
`META-INF/jandex.idx` and `maven` adds it to the application JAR.

Invoking the Jandex plug-in as described above indexes only the types in your
project. If the signatures of your resource methods refer to types from
dependencies that do not have their own indexes then you should customize how
you use the plug-in.

The example below tailors the Jandex plug-in configuration to scan not only the
current project but another dependency and to index a specific type from it.

```xml [pom.xml]
<execution>
  <id>make-index</id>
  <configuration>
    <fileSets>
      <fileSet>
        <dependency>
          <groupId>jakarta.ws.rs</groupId>
          <artifactId>jakarta.ws.rs-api</artifactId>
        </dependency>
        <includes>
          <include>**/MediaType.class</include>
        </includes>
      </fileSet>
    </fileSets>
  </configuration>
</execution>
```

- Augments the default configuration.
- Adds a `fileSet` in the form of a `dependency` that is already declared in
  your project.
- Selects the type or types from the `fileSet` you want to include in the
  generated index.

You can add more than one dependency and scan for more than a single type. See
the [Helidon MP OpenAPI expanded Jandex example][helidon-mp-opena-2] for more
information and a complete project that indexes a dependency.

## Reference

- [MicroProfile OpenAPI GitHub Repository][microprofile-ope-3]
- [MicroProfile OpenAPI Specification][microprofile-ope]

[openapi-specific]: https://github.com/OAI/OpenAPI-Specification/blob/master/versions/3.0.0.md
[microprofile-ope]: https://download.eclipse.org/microprofile/microprofile-open-api-3.1.1/microprofile-openapi-spec-3.1.1.html
[helidon-mp-opena]: https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/microprofile/openapi/basic
[org-eclipse-micr]: https://download.eclipse.org/microprofile/microprofile-open-api-3.1.1/apidocs/org/eclipse/microprofile/openapi/OASModelReader.html
[org-eclipse-micr-2]: https://download.eclipse.org/microprofile/microprofile-open-api-3.1.1/apidocs/org/eclipse/microprofile/openapi/OASFilter.html
[configuration-se]: https://download.eclipse.org/microprofile/microprofile-open-api-3.1.1/microprofile-openapi-spec-3.1.1.html#configuration
[microprofile-ope-2]: https://download.eclipse.org/microprofile/microprofile-open-api-3.1.1/apidocs
[the-microprofile]: https://download.eclipse.org/microprofile/microprofile-open-api-3.1.1/microprofile-openapi-spec-3.1.1.html#_configuration
[complete-openapi]: https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/microprofile/openapi
[jandex-maven-plu]: https://github.com/smallrye/jandex/tree/main/maven-plugin
[helidon-mp-opena-2]: https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/microprofile/openapi/expanded-jandex
[microprofile-ope-3]: https://github.com/eclipse/microprofile-open-api
[io-helidon-opena]: ../../config/io.helidon.openapi.OpenApiFeature.md#configuration-options
