<!--@frontmatter
description: "Helidon OpenAPI Support"
-->
# OpenAPI

## Overview

The [OpenAPI specification][openapi-specific] defines a standard way to express
the interface exposed by a REST service.

The [MicroProfile OpenAPI spec][microprofile-ope] explains how MicroProfile
embraces OpenAPI, adding annotations, configuration, and a service provider
interface (SPI).

OpenAPI support in Helidon draws its inspiration from MicroProfile OpenAPI but
does not implement the specification. Helidon focuses on serving an OpenAPI
document and exposing it through the `/openapi` endpoint.

The OpenAPI support in Helidon performs two main tasks:

- Build an in-memory model of the REST API your service implements.
- Expose the model in text format (YAML or JSON) via the `/openapi` endpoint.

To construct the model, Helidon gathers information about the service API from
a static OpenAPI document file packaged as part of your service, generated
OpenAPI document sources, or both.

## Maven Coordinates

To enable OpenAPI, add the following dependency to your project’s `pom.xml` (see
[Managing Dependencies](../../dependency-management.md)).

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.openapi</groupId>
  <artifactId>helidon-openapi</artifactId>
</dependency>
```

## Usage

### Automatic Registration (default)

Simply by adding the dependency described above you add support for OpenAPI to
your Helidon application. Because Helidon automatically discovers the OpenAPI
feature, you do not have to make any changes to your application code.

### Explicit Registration

To control the behavior of the OpenAPI feature programmatically, you can add and
configure the OpenAPI feature explicitly as explained below.

#### Create and Register `OpenApiFeature` in your application

Helidon provides the [`OpenApiFeature`][openapifeature] class which your
application uses to assemble the in-memory model and expose the `/openapi`
endpoint to clients. You can create an instance either using a static `create`
method or by instantiating its [`Builder`][builder]. The [example
below][example-below] illustrates one way to do this.

#### Furnish OpenAPI information about your endpoints

Your application supplies data for the OpenAPI model using a static OpenAPI
file. When you use Helidon Declarative endpoints, Helidon can also generate
OpenAPI data from annotations at build time.

**Provide a static OpenAPI file**

Add a static file at `META-INF/openapi.yml`, `META-INF/openapi.yaml`, or
`META-INF/openapi.json`. Tools such as Swagger let you describe your app’s API ,
and they then generate an OpenAPI document file which you can include in your
application so OpenAPI can use it.

**Generate OpenAPI data from declarative endpoints**

> [!NOTE]
> Declarative OpenAPI generation and its model, annotation, version SPI, and
> generated-document configuration APIs are preview and may change.

For classes annotated with
[`@RestServer.Endpoint`][restserver-endpoint], Helidon generates OpenAPI data
when the endpoint type, one of its methods, or one of its method parameters uses
an endpoint-applicable `OpenApi` annotation. To opt in without adding
OpenAPI-specific metadata, annotate the endpoint with `@OpenApi.Endpoint`.
Helidon derives the generated data from the HTTP method, path, media type,
parameter, status, and response metadata and from the Java signatures.

Annotate non-built-in request and response model types with
[`@JsonSchema.Schema`][jsonschema-schema] so Helidon can generate their
component schemas. See the [JSON Schema documentation](../json/schema.md) for
details.

The following annotation placements opt an endpoint into OpenAPI processing:

- On the endpoint type: `Document`, `Hidden`,
  `SecuritySchemeRequirement`, `SecurityRequirement`, or
  `SecurityRequirements`.
- On an endpoint method: `Operation`, `Hidden`, `Server`, `Servers`,
  `ExternalDocs`, `Extension`, `Extensions`, `SecuritySchemeRequirement`,
  `SecurityRequirement`, `SecurityRequirements`, `Parameter`, `Parameters`,
  `RequestBody`, `Response`, or `Responses`.
- On a method parameter: `Parameter` or `Parameters`.

Document-only companion annotations such as `Info`, `Contact`, `License`,
`Tag`, and security scheme declarations do not opt an endpoint into generation
by themselves.

`OpenApi.Endpoint` and endpoint-level `Hidden` and security requirement
annotations declared on a declarative REST endpoint contract also apply to its
implementations. `OpenApi.Document` describes only its declaring document
metadata type and is not inherited by endpoint implementations. An
endpoint-level security requirement declared directly on an implementation
replaces the security requirements inherited from its endpoint contract.

Security requirements inherited from unrelated endpoint contracts are
combined. If one such contract clears endpoint security with an empty
`OpenApi.SecurityRequirements` container while another declares a requirement,
code generation fails because the contracts conflict. If matching methods
inherited from multiple endpoint contracts declare
`OpenApi.SecuritySchemeRequirement` annotations, each inherited method must
declare the same requirements, including repeated occurrences, although the
annotation order can differ. Helidon emits the inherited requirements once.
Different inherited declarations cause code generation to fail. A scheme
requirement declared directly on the endpoint implementation method replaces
all inherited method-level requirements.

The final composed OpenAPI document must contain Info metadata. Supply it using
`@OpenApi.Document` and `@OpenApi.Info` on an application type, from a custom
[`OpenApiDocumentSource`][openapi-document-source], or from a static OpenAPI
document when static and generated content are merged. In a multi-module
application, annotation processing must generate OpenAPI metadata while
compiling every module that declares opted-in declarative endpoints. Adding
`@OpenApi.Document` only in the final application module does not include
unannotated endpoints from previously compiled modules. Use
[`io.helidon.openapi.OpenApi`][openapi-annotations] annotations to add
OpenAPI-specific details such as document info, operation descriptions,
parameters, responses, schemas, security, tags, servers, external docs, and
extensions.

For OpenAPI 3.2, an application can set the document `$self` identity using
`OpenApi.Document.self` or `OpenApiDocument.Builder.self`. Helidon resolves a
relative `$self` against the configured OpenAPI web context for relative and
origin-relative references. Document composition does not have the request
scheme or authority, so an absolute reference cannot be recognized as a
same-document reference when `$self` is relative. This combination is not
supported. Use an absolute `$self` or relative references instead. The web
context is used only as the document retrieval-location base; Helidon does not
add the OpenAPI endpoint itself to the document's Paths Object.

Annotation string values that become OpenAPI text values, such as document
info, descriptions, server URLs, example values, and external documentation
URLs, can use Helidon config expressions only when the OpenAPI feature config
enables `generated.resolve-config-expressions`. When enabled, Helidon resolves
those expressions at runtime when serving the generated OpenAPI document. When
disabled, which is the default, Helidon uses annotation text values literally.

`@OpenApi.Extension` values are OpenAPI strings by default. Set `parseValue` to
`true` to parse the runtime-resolved annotation value as exactly one JSON value,
including a boolean, number, string, `null`, array, or object. Invalid JSON is
rejected when the generated OpenAPI document source runs.

For templated server URLs, declare each substitution using
`@OpenApi.ServerVariable`. Each variable requires a name and default value and
can also declare an enumeration of allowed values and a description.

Response metadata can declare links using `@OpenApi.Link`. A link identifies
its target using exactly one of `operationRef` or `operationId`.
`@OpenApi.LinkParameter` values and `requestBody` support literal strings and
OpenAPI runtime expressions; use the programmatic `OpenApiDocument.LinkBuilder`
for other OpenAPI value types.

Annotation string values that identify or select generated metadata, such as
extension names, tag names, security scheme names and types, media types,
parameter names and locations, link and link-parameter names, and security
requirement scheme names and scopes, are resolved when OpenAPI metadata is
generated. For those values Helidon uses the expression default value, if one
is present.

For generated security scheme components, prefer the type-specific annotations
`@OpenApi.ApiKeySecurityScheme`, `@OpenApi.HttpSecurityScheme`,
`@OpenApi.MutualTlsSecurityScheme`, `@OpenApi.OAuth2SecurityScheme`, and
`@OpenApi.OidcSecurityScheme`. Each exposes only the OpenAPI fields relevant to
that scheme type. Use the generic `@OpenApi.SecurityScheme` only when you need
the lower-level OpenAPI Security Scheme Object shape directly; Helidon rejects
fields that do not apply to the selected scheme type.

### Accessing the REST Endpoint

Once you have added the Helidon OpenAPI dependency to your project, if you are
using auto-discovery or if you are not using auto-discovery and you have added
code to register the `OpenApiFeature` object with your routing then your
application responds to the built-in endpoint — `/openapi` — and returns the
OpenAPI document describing the endpoints in your application.

The default format of the OpenAPI document is YAML. There is not yet an adopted
IANA YAML media type, but a proposed one specifically for OpenAPI documents that
has some support is `application/vnd.oai.openapi`. That is what Helidon returns
by default.

In addition, a client can specify the HTTP header `Accept` as either
`application/vnd.oai.openapi+json` or `application/json` to request JSON.
Alternatively, the client can pass the query parameter `format` as either `JSON`
or `YAML` to receive `application/json` or `application/vnd.oai.openapi` (YAML)
output, respectively.

### Static and Generated Documents

By default, `OpenApiFeature` uses `STATIC_FIRST` generated document mode: a
static file is used when present, otherwise Helidon serves generated OpenAPI
data. Configure `generated.mode` to change this behavior.

Generated OpenAPI document mode:

```yaml
server:
  features:
    openapi:
      static-file: "openapi.yaml"
      generated:
        mode: "MERGE"
```

Supported values are:

- `STATIC_FIRST` - Use a static document when present, otherwise use generated
  document sources.
- `STATIC_ONLY` - Use only a static document and ignore generated document
  sources.
- `MERGE` - Strictly merge generated document sources into a static document.
  Static and generated content must be non-conflicting; composition fails if
  both sides define incompatible document values, the same path operation, or
  duplicate `operationId` values. Helidon parses the static document and
  renders the merged document through the configured `document` provider, even
  for a listener where no generated source contributes.
- `GENERATED_ONLY` - Use only generated document sources, even when a static
  document is present.

When more than one generated document metadata source is visible, select the
sources to use with `generated.document-sources`. Sources generated from
`@OpenApi.Document` are named by the annotated type, using its dotted canonical
type name. Custom `OpenApiDocumentSource` services must be qualified with
`@Service.Named` to be selected by name. Unqualified sources always participate
when they support the document context and cannot be filtered with
`generated.document-sources`.

When static and generated content or multiple generated sources contribute
component schemas, Helidon renames conflicting component names in generated
documents and rewrites references in standard JSON Schema applicator locations.
Custom JSON Schema vocabularies can define other applicator keywords whose
values are schemas, but Helidon cannot identify those locations from the
document alone. Generated sources that refer to component schemas from custom
applicator keywords must therefore use component names that are unique across
the static and generated documents being combined.

Select generated document metadata sources:

```yaml
server:
  features:
    openapi:
      generated:
        document-sources:
          - "com.example.openapi.ApplicationOpenApi"
```

If a generated operation id needs to be stable or disambiguated, configure it
with `generated.operation-ids`. Each key is the fully qualified endpoint class
name, `#`, method name, and fully qualified parameter types separated by `,`.
The value is the operation id to use in the served document.

Configure generated operation ids:

```yaml
server:
  features:
    openapi:
      generated:
        operation-ids:
          "com.example.GreetingEndpoint#greet(java.lang.String)": "greet"
```

Generated annotation text values are treated literally by default. Enable
`generated.resolve-config-expressions` when your OpenAPI annotations
intentionally use Helidon config expressions in user-visible document text,
such as document descriptions or server URLs.

Resolve generated annotation config expressions:

```yaml
server:
  features:
    openapi:
      generated:
        resolve-config-expressions: true
```

## API

Helidon provides an API for creating and setting up the REST endpoint which
serves OpenAPI documents to clients at the `/openapi` path. Use either static
methods on [`OpenApiFeature`][openapifeature] or use its [`Builder`][builder].
Then add that instance or builder to your application’s routing. The
[example][example-below] below shows how to do this.

## Configuration

Helidon OpenAPI configuration supports the settings described below in the
`server.features.openapi` section.

### Configuration options

<!--@include ../../config/io.helidon.openapi.OpenApiFeature.md#configuration-options delim=--- offset=1 collapseTables=10 -->
See [Configuration options][io-helidon-opena].
<!--/include-->

## OpenAPI Document Versions

The base `helidon-openapi` module provides OpenAPI 3.0 support. To render
OpenAPI 3.1 or 3.2 documents, add the corresponding module.

When the `document` provider is not configured, Helidon selects the highest
available OpenAPI document version provider discovered at runtime. With only
`helidon-openapi`, generated output uses OpenAPI 3.0.3. If
`helidon-openapi-31` is available, generated output uses OpenAPI 3.1.1. If
`helidon-openapi-32` is available, generated output uses OpenAPI 3.2.0.
Configure the `document` provider when your application must pin the generated
OpenAPI document version independently of which OpenAPI version modules are
present.

Declarative `@OpenApi.MutualTlsSecurityScheme` metadata and
`@OpenApi.SecurityScheme` metadata with type `mutualTLS` require OpenAPI 3.1 or
3.2 output. Use `helidon-openapi-31`, `helidon-openapi-32`, or another
configured document provider which renders one of those versions. Generation
fails if the selected provider renders OpenAPI 3.0.

Declarative `@OpenApi.OAuthFlows` metadata which configures
`deviceAuthorization` requires OpenAPI 3.2 output. Use `helidon-openapi-32` or
another configured document provider which renders OpenAPI 3.2. Generation
fails if the selected provider renders OpenAPI 3.0 or 3.1.

Declarative `@OpenApi.Content` metadata which configures `itemSchema` requires
OpenAPI 3.2 output. When `itemSchema` is set without an explicit `schema`, it
replaces the inferred request or response entity schema. Earlier output
versions omit `itemSchema`.

The configured `document` provider is used when Helidon renders generated or
merged OpenAPI documents. In `MERGE` mode, Helidon parses the static document
and renders the result with the configured provider, so the served document
version can differ from the static file's declared `openapi` version. Helidon
does not provide complete conversion between OpenAPI versions. A static
document which uses version-specific features must use a `document` provider
which renders a compatible OpenAPI version. When no custom `OpenApiManager` is
configured, Helidon serves a static document as-is in `STATIC_ONLY` mode and
when `STATIC_FIRST` finds one; it does not rewrite the document to the
configured `document` version. A configured custom `OpenApiManager` still loads
and formats the static content in these modes.

To add OpenAPI 3.1 support:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.openapi</groupId>
  <artifactId>helidon-openapi-31</artifactId>
</dependency>
```

To add OpenAPI 3.2 support:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.openapi</groupId>
  <artifactId>helidon-openapi-32</artifactId>
</dependency>
```

Configure OpenAPI 3.0 output:

```yaml
server:
  features:
    openapi:
      document:
        "3.0":
          version: "3.0.3"
```

Configure OpenAPI 3.1 output:

```yaml
server:
  features:
    openapi:
      document:
        "3.1":
          version: "3.1.1"
```

Configure OpenAPI 3.2 output:

```yaml
server:
  features:
    openapi:
      document:
        "3.2":
          version: "3.2.0"
```

## Examples

Helidon provides a [complete OpenAPI example][complete-openapi] based on the
Helidon QuickStart sample app.

### Configure OpenAPI behavior

The following example shows how to use configuration to customize how OpenAPI
works, in this case changing the endpoint where Helidon provides the OpenAPI
document.

Configure OpenAPI behavior:

<!--@mdc ::code-callout -->
```yaml
server:
  port: 8080                  <1>
  host: 0.0.0.0
  features:
    openapi:                  <2>
      web-context: /myopenapi <3>
```
1. The `port` and `host` settings are for the server as a whole, not
   specifically for OpenAPI.
2. The `openapi` subsection within `features` contains OpenAPI settings.
3. Changes the endpoint for returning the OpenAPI document from the default
   `/openapi` to `/myopenapi`.
<!--@mdc :: -->

Most Helidon applications need only add the dependency as explained above;
Helidon discovers and registers OpenAPI automatically. The example below shows
how to create and register `OpenApiFeature` explicitly instead.

### Register `OpenApiFeature` explicitly

Java Code to Create and Register OpenApiFeature:

<!--@mdc ::code-callout -->
```java
WebServer server = WebServer.builder()
        .config(config.get("server"))
        .addFeature(OpenApiFeature.create(config.get("openapi"))) // <1>
        .routing(Main::routing)
        .build()
        .start();
```
1. Adds the `OpenApiFeature` service to your server using the `openapi` section
   from configuration.
<!--@mdc :: -->

If you need programmatic control over the `OpenApiFeature` instance, invoke
`OpenApiFeature.builder()` to get an `OpenApiFeature.Builder` object and work
with it, then invoke the builder’s `build` method and pass the resulting
`OpenApiFeature` instance to the `WebServer.Builder` `addFeature` method.

[openapi-specific]: https://spec.openapis.org/oas/latest.html
[microprofile-ope]: https://download.eclipse.org/microprofile/microprofile-open-api-3.1.1/microprofile-openapi-spec-3.1.1.html
[openapifeature]: https://helidon.io/docs/v27/apidocs/io.helidon.openapi/io/helidon/openapi/OpenApiFeature.html
[builder]: https://helidon.io/docs/v27/apidocs/io.helidon.openapi/io/helidon/openapi/OpenApiFeatureConfig.Builder.html
[restserver-endpoint]: https://helidon.io/docs/v27/apidocs/io.helidon.webserver/io/helidon/webserver/http/RestServer.Endpoint.html
[jsonschema-schema]: https://helidon.io/docs/v27/apidocs/io.helidon.json.schema/io/helidon/json/schema/JsonSchema.Schema.html
[openapi-document-source]: https://helidon.io/docs/v27/apidocs/io.helidon.openapi/io/helidon/openapi/spi/OpenApiDocumentSource.html
[openapi-annotations]: https://helidon.io/docs/v27/apidocs/io.helidon.openapi/io/helidon/openapi/OpenApi.html
[example-below]: #register-openapifeature-explicitly
[complete-openapi]: https://github.com/helidon-io/helidon-examples/tree/helidon-27.x/examples/openapi
[io-helidon-opena]: ../../config/io.helidon.openapi.OpenApiFeature.md#configuration-options
