<!--@frontmatter
description: "Helidon GraphQL Server Support"
navigation:
  icon: i-simple-icons-graphql
-->
# GraphQL

## Overview

The Helidon GraphQL Server provides a framework for creating [GraphQL][graphql]
applications that integrate with the Helidon WebServer. GraphQL is a query
language to access server data. The Helidon GraphQL integration enables HTTP
clients to issue queries over the network and retrieve data; it is an
alternative to other protocols such as REST or GRPC.

## Maven Coordinates

To enable GraphQL, add the following dependency to your project’s `pom.xml` (see
[Managing Dependencies](../dependency-management.md)).

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webserver</groupId>
  <artifactId>helidon-webserver-graphql</artifactId>
</dependency>
```

## API

An instance of `GraphQlService` must be registered in the Helidon WebServer
routes to enable GraphQL support in your application. In addition, a GraphQL
schema needs to be specified to verify and execute queries.

The following code fragment creates an instance of `GraphQlService`, disables
authentication for the public example endpoint, and registers it in the Helidon
WebServer.

```java
Config graphQlConfig = Services.get(Config.class).get("graphql");

InvocationHandler invocationHandler = InvocationHandler.builder()
        .config(graphQlConfig)
        .schema(buildSchema())
        .build();

WebServer server = WebServer.builder()
        .routing(r -> r.register(GraphQlService.builder()
                                 .config(graphQlConfig)
                                 .invocationHandler(invocationHandler)
                                 .permitAll(true)
                                 .build()))
        .build();
```

By default, `GraphQlService` will reserve `/graphql` as the URI path to process
queries and require requests to be authenticated. Set `permitAll` to `true` only
for endpoints intended to be public. The `buildSchema` method creates the schema
and defines 2 types of queries for this application:

<!--@mdc ::code-collapse -->
```java
static GraphQLSchema buildSchema() {
    String schema =
            """
            type Query {
                hello: String\s
                helloInDifferentLanguages: [String]\s
            }
            """;

    SchemaParser schemaParser = new SchemaParser();
    TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);

    DataFetcher<List<String>> dataFetcher = env -> List.of(
            "Bonjour",
            "Hola",
            "Zdravstvuyte",
            "Nǐn hǎo",
            "Salve",
            "Gudday",
            "Konnichiwa",
            "Guten Tag");

    RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
            .type("Query", builder -> builder
                    .dataFetcher("hello", new StaticDataFetcher("world")))
            .type("Query", builder -> builder
                    .dataFetcher("helloInDifferentLanguages", dataFetcher))
            .build();

    SchemaGenerator generator = new SchemaGenerator();
    return generator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);
}
```
<!--@mdc :: -->

The following is a description of each of these steps:

- Define the GraphQL schema.
- Create a `DataFetcher` to return a list of hellos in different languages.
- Wire up the `DataFetcher` s.
- Generate the GraphQL schema.

GraphQL responses are serialized with Helidon JSON binding. JSON-native values,
maps, and iterable values are written directly. Other Java objects in the
response, such as custom values stored in `GraphQLError.extensions`, must have a
Helidon JSON serializer. Annotate the type with `@Json.Entity` to generate one,
use `@Json.Serializer` to select a custom serializer, or register a
`JsonSerializer` service. Helidon annotation processing must be enabled when
using the JSON annotations. A response containing an unsupported value cannot
be serialized.

## Declarative API

Helidon Declarative can generate the GraphQL schema, GraphQL Java runtime
wiring, and WebServer registration from annotated Service Registry classes. Use
declarative GraphQL when the schema should be derived from Java resolver methods
and Java schema types at build time.

Declarative GraphQL applications use the normal declarative application startup
described in [Helidon Declarative](injection/declarative.md#overview). Add the
GraphQL modules, WebServer, and Service Registry to your application:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.graphql</groupId>
  <artifactId>helidon-graphql</artifactId>
</dependency>
<dependency>
  <groupId>io.helidon.graphql</groupId>
  <artifactId>helidon-graphql-server</artifactId>
</dependency>
<dependency>
  <groupId>io.helidon.webserver</groupId>
  <artifactId>helidon-webserver</artifactId>
</dependency>
<dependency>
  <groupId>io.helidon.webserver</groupId>
  <artifactId>helidon-webserver-graphql</artifactId>
</dependency>
<dependency>
  <groupId>io.helidon.service</groupId>
  <artifactId>helidon-service-registry</artifactId>
</dependency>
```

Configure the Helidon annotation processors as described in [Declarative
Usage](injection/declarative.md#usage), then start the generated application
binding:

```java
@Service.GenerateBinding
static class DeclarativeMain {
    public static void main(String[] args) {
        LogConfig.configureRuntime();
        ServiceRegistryManager.start(ApplicationBinding.create());
    }
}
```

Annotate a Service Registry singleton with `@GraphQlServer.Endpoint`. Resolver
methods annotated with `@GraphQl.Query` and `@GraphQl.Mutation` become top-level
GraphQL fields. Methods annotated with `@GraphQlServer.Field` become child field
resolvers on generated object types. `@GraphQl.Description` on resolver methods
and parameters becomes the description of the generated SDL field or argument.

```java
@GraphQlServer.Endpoint
static class CatalogEndpoint {
    @GraphQl.Query
    @GraphQl.Description("Looks up a book by ISBN")
    Book book(@GraphQl.Argument("isbn") @GraphQl.Description("ISBN of the book") String isbn) {
        return new Book("Dune", BookStatus.AVAILABLE, List.of("classic"), isbn);
    }

    @GraphQl.Query
    String search(@GraphQl.Argument("criteria") @GraphQl.NonNull BookSearch criteria) {
        return criteria.phrase() + ":" + criteria.status();
    }

    @GraphQl.Mutation
    boolean update(@GraphQl.Argument("enabled") boolean enabled) {
        return enabled;
    }

    @GraphQlServer.Field
    String summary(@GraphQlServer.Source Book book,
                   @GraphQl.Argument("prefix") String prefix) {
        return prefix + ": " + book.title();
    }
}

@GraphQl.Entity
@GraphQl.Description("Book result")
record Book(@GraphQl.NonNull String title,
            @GraphQl.Name("state") BookStatus status,
            List<String> tags,
            @GraphQl.Ignore String internal) {
}

@GraphQl.Entity
record BookSearch(@GraphQl.NonNull String phrase,
                  @GraphQl.NonNull BookStatus status) {
}

@GraphQl.Entity
enum BookStatus {
    AVAILABLE,
    @GraphQl.Name("OUT_OF_PRINT")
    OUT
}
```

Java classes, records, interfaces, and enums must be annotated with
`@GraphQl.Entity` before they contribute SDL object or enum definitions.
GraphQL input objects are generated from records annotated with
`@GraphQl.Entity`. Built-in GraphQL scalar types do not need this marker. The
initial declarative generator supports `String`, `int`/`Integer`,
`double`/`Double`, and `boolean`/`Boolean` as built-in scalar Java types.

A `List<T>` field or argument must declare exactly one concrete element type;
wildcards and generic element variables are rejected. Other domain scalar
values require an application-owned Java type annotated with `@GraphQl.Scalar`
and a matching `GraphQlScalar` service. JDK types such as `Long`, `Float`, and
`BigDecimal`, container types such as maps, and optional values are not
supported directly by the initial generator. Automatic entity fields support
only GraphQL schema annotations; use an explicit `@GraphQlServer.Field`
resolver when a field needs annotation-driven runtime behavior. Generated
GraphQL names must match the GraphQL `Name` grammar,
`[_A-Za-z][_0-9A-Za-z]*`, and must not start with `__`, which GraphQL reserves
for introspection.

Declarative resolver parameters can bind:

- GraphQL arguments, using `@GraphQl.Argument` on resolver parameters when the
  Java parameter name should not be used.
- Child resolver source objects, using `@GraphQlServer.Source` when the source
  parameter cannot be inferred.
- `graphql.schema.DataFetchingEnvironment`.
- Helidon `Context`, GraphQL `ExecutionContext`, and `SecurityContext`
  propagated from the current request.
- Custom resolver-only parameters through the GraphQL parameter codegen SPI.

By default, generated GraphQL endpoints use `/graphql`, and the generated
schema is available under `/graphql/schema.graphql`. You can override the
listener, web context, and schema URI with `@GraphQlServer.Listener`,
`@GraphQlServer.Context`, and `@GraphQlServer.SchemaUri`. Endpoints that share
the same listener and web context are generated into one GraphQL route, so they
must be in the same Java package, must declare the same endpoint-level request
metadata annotations, and must not use conflicting schema URIs.

Endpoint-level security annotations also protect the generated schema and
GraphQL introspection. When an endpoint uses `@Authorized(explicit=true)`,
application resolvers must still call `SecurityContext.authorize`. The
framework-owned schema, `__schema`, `__type`, and `__typename` operations cannot
make that application call, so Helidon evaluates authorization implicitly for
those metadata operations while preserving the configured authorization
provider and endpoint security constraints.

## Configuration

The following configuration keys can be used to set up integration with
WebServer:

<table>
<thead>
<th>Key</th>
<th>Default Value</th>
<th>Description</th>
</thead>
<tr>
<td><code>graphql.<wbr>web-context</code></td>
<td><code>/graphql</code></td>
<td>Context that serves the GraphQL endpoint</td>
</tr>
<tr>
<td><code>graphql.<wbr>schema-uri</code></td>
<td><code>/schema.<wbr>graphql</code></td>
<td>URI that serves the schema (under web context)</td>
</tr>
<tr>
<td><code>graphql.<wbr>permit-all</code></td>
<td><code>false</code></td>
<td>Whether GraphQL requests are permitted without authentication</td>
</tr>
<tr>
<td><code>graphql.<wbr>executor-service</code></td>
<td></td>
<td>Configuration of `Server<wbr>ThreadPool<wbr>Supplier` used to set up executor service</td>
</tr>
</table>

The following configuration keys can be used to set up GraphQL invocation:

<table>
<thead>
<th>Key</th>
<th>Default Value</th>
<th>Description</th>
</thead>
<tr>
<td><code>graphql.<wbr>default-error-message</code></td>
<td><code>Server Error</code></td>
<td>Error message to send to caller in case of error</td>
</tr>
<tr>
<td><code>graphql.<wbr>max-query-depth</code></td>
<td><code>100</code></td>
<td>
Maximum GraphQL query depth. Must not be negative. Set to <code>0</code> to
disable the limit.
</td>
</tr>
<tr>
<td><code>graphql.<wbr>max-query-complexity</code></td>
<td><code>1000</code></td>
<td>
Maximum GraphQL query complexity. Must not be negative. Set to <code>0</code>
to disable the limit.
</td>
</tr>
<tr>
<td><code>graphql.<wbr>exception-white-list</code></td>
<td></td>
<td>
Array of checked exception classes that should return default error message
</td>
</tr>
<tr>
<td><code>graphql.<wbr>exception-black-list</code></td>
<td></td>
<td>
Array of unchecked exception classes that should return message to caller
(instead of default error message)</td>
</tr>
</table>

By default, GraphQL invocation rejects queries deeper than
`graphql.max-query-depth` or more complex than `graphql.max-query-complexity`.
Applications that intentionally serve deeper or more complex queries should tune
these limits for their schemas. Setting a limit to `0` restores the previous
unlimited behavior for that limit.

## Examples

Using the schema defined in Section [API](#api), you can probe the following
endpoints:

1.  Hello world endpoint

    ```shell [Terminal]
    curl -X POST http://127.0.0.1:PORT/graphql \
      -d '{"query":"query { hello }"}'
    ```

    ```json [Response]
    "data":{"hello":"world"}}
    ```

2.  Hello in different languages

    ```shell [Terminal]
    curl -X POST http://127.0.0.1:PORT/graphql \
      -d '{"query":"query { helloInDifferentLanguages }"}'
    ```

    ```json [Response]
    {"data":{"helloInDifferentLanguages":["Bonjour","Hola","Zdravstvuyte","Nǐn hǎo","Salve","Gudday","Konnichiwa","Guten Tag"]}}
    ```

## Additional Information

- [GraphQL Javadocs][graphql-javadocs]

[graphql]: https://github.com/graphql-java/graphql-java
[graphql-javadocs]: https://helidon.io/docs/v27/apidocs/io.helidon.graphql.server/module-summary.html
