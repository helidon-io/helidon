# GraphQL Server Introduction

## Contents

- [Overview](#overview)
- [Maven Coordinates](#maven-coordinates)
- [API](#api)
- [Configuration](#configuration)
- [Examples](#examples)
- [Additional Information](#additional-information)

## Overview

The Helidon GraphQL Server provides a framework for creating [GraphQL](https://github.com/graphql-java/graphql-java) applications that integrate with the Helidon WebServer. GraphQL is a query language to access server data. The Helidon GraphQL integration enables HTTP clients to issue queries over the network and retrieve data; it is an alternative to other protocols such as REST or GRPC.

## Maven Coordinates

To enable GraphQL, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../about/managing-dependencies.md)).

``` xml
<dependency>
  <groupId>io.helidon.webserver</groupId>
  <artifactId>helidon-webserver-graphql</artifactId>
</dependency>
```

## API

An instance of `GraphQlSupport` must be registered in the Helidon WebServer routes to enable GraphQL support in your application. In addition, a GraphQL schema needs to be specified to verify and execute queries.

The following code fragment creates an instance of `GraphQlSupport` and registers it in the Helidon WebServer.

``` java
WebServer server = WebServer.builder()
        .routing(r -> r.register(GraphQlService.create(buildSchema())))
        .build();
```

By default, `GraphQlSupport` will reserve `/graphql` as the URI path to process queries. The `buildSchema` method creates the schema and defines 2 types of queries for this application:

``` java
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

The following is a description of each of these steps:

- Define the GraphQL schema.
- Create a `DataFetcher` to return a list of hellos in different languages.
- Wire up the `DataFetcher` s.
- Generate the GraphQL schema.

## Configuration

The following configuration keys can be used to set up integration with WebServer:

| key | default value | description |
|----|----|----|
| `graphql.web-context` | `/graphql` | Context that serves the GraphQL endpoint. |
| `graphql.schema-uri` | `/schema.graphql` | URI that serves the schema (under web context) |
| `graphql.executor-service` |   | Configuration of `ServerThreadPoolSupplier` used to set up executor service |

The following configuration keys can be used to set up GraphQL invocation:

| key | default value | description |
|----|----|----|
| `graphql.default-error-message` | `Server Error` | Error message to send to caller in case of error |
| `graphql.exception-white-list` |   | Array of checked exception classes that should return default error message |
| `graphql.exception-black-list` |   | Array of unchecked exception classes that should return message to caller (instead of default error message) |

## Examples

Using the schema defined in Section [API](#api), you can probe the following endpoints:

1.  Hello world endpoint

    ``` bash
    curl -X POST http://127.0.0.1:PORT/graphql -d '{"query":"query { hello }"}'

    "data":{"hello":"world"}}
    ```

2.  Hello in different languages

    ``` bash
    curl -X POST http://127.0.0.1:PORT/graphql -d '{"query":"query { helloInDifferentLanguages }"}'

    {"data":{"helloInDifferentLanguages":["Bonjour","Hola","Zdravstvuyte","Nǐn hǎo","Salve","Gudday","Konnichiwa","Guten Tag"]}}
    ```

## Additional Information

- [GraphQL Javadocs](/apidocs/io.helidon.graphql.server/module-summary.html)
