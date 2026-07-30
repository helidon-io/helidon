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
[graphql-javadocs]: https://helidon.io/docs/v4/apidocs/io.helidon.graphql.server/module-summary.html
