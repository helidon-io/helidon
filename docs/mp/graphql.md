# MicroProfile GraphQL

## Contents

- [Overview](#overview)
- [Maven Coordinates](#maven-coordinates)
- [API](#api)
- [Configuration](#configuration)
- [Examples](#examples)
- [Additional Information](#additional-information)
- [Reference](#reference)

## Overview

Helidon MP implements the [MicroProfile GraphQL specification](https://download.eclipse.org/microprofile/microprofile-graphql-2.0/microprofile-graphql-spec-2.0.html). This specifcation describes how applications can be built to expose an endpoint for GraphQL. GraphQL is an open-source data query and manipulation language for APIs, and a runtime for fulfilling data queries. It provides an alternative to, though not necessarily a replacement for, REST.

## Maven Coordinates

To enable MicroProfile GraphQL, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../about/managing-dependencies.md)).

``` xml
<dependency>
    <groupId>io.helidon.microprofile.graphql</groupId>
    <artifactId>helidon-microprofile-graphql-server</artifactId>
</dependency>
```

## API

The MicroProfile GraphQL specification defines a number of key annotations to be used when writing a GraphQL endpoint:

- `@GraphQLApi` - identifies a CDI Bean as a GraphQL endpoint
- `@Query` - identifies a method as returning one or more entities
- `@Mutation` - identifies a method which creates, deletes or updates entities

For example, the following defines a GraphQL endpoint with a number of queries and mutations that work against a fictional `CustomerService` service and `Customer` class.

*Simple ContactGraphQLApi*

``` java
@ApplicationScoped
@GraphQLApi
public class ContactGraphQLApi {

    @Inject
    private CustomerService customerService;

    @Query
    public Collection<Customer> findAllCustomers() { 
        return customerService.getAllCustomers();
    }

    @Query
    public Customer findCustomer(@Name("customerId") int id) { 
        return customerService.getCustomer(id);
    }

    @Query
    public Collection<Customer> findCustomersByName(@Name("name") String name) { 
        return customerService.getAllCustomers(name);
    }

    @Mutation
    public Customer createCustomer(@Name("customerId") int id, 
                                  @Name("name") String name,
                                  @Name("balance") float balance) {
        return customerService.createCustomer(id, name, balance);
    }
}

public class customer {
    private int id;
    @NonNull
    private String name;
    private float balance;

    // getters and setters omitted for brevity
}
```

- a query with no-arguments that will return all `Customer` s
- a query that takes an argument to return a specific `Customer`
- a query that optionally takes a name and returns a collection of `Customer` s
- a mutation that creates a Customer and returns the newly created `Customer`

The example above would generate a GraphQL schema as shown below:

*Sample GraphQL schema*

``` graphql
type Query {
   findAllCustomers: [Customer]
   findCustomer(customerId: Int!): Customer
   findCustomersByName(name: String): [Customers]
}

type Mutation {
   createCustomer(customerId: Int!, name: String!, balance: Float!): Customer
}

type Customer {
   id: Int!
   name: String!
   balance: Float
}
```

After application startup, a GraphQL schema will be generated from your annotated API classes and POJO’s and you will be able to access these via the URLs described below.

### Building your application

As part of building your application, you must create a Jandex index using the `jandex-maven-plugin` for all API and POJO classes.

*Generate Jandex index*

``` xml
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

> [!NOTE]
> As per the instructions [here](introduction/microprofile.md) ensure you have added a `src/main/resources/META-INF/beans.xml` file, so the CDI implementation can pick up your classes.

### Accessing the GraphQL endpoints

After starting your application you should see a log message indicating that GraphQL is in the list of features. You can access the GraphQL endpoint at `http://host:port/graphql`, and the corresponding schema at `http://host:port/graphql/schema.graphql`. See [Configuration](#configuration) for additional information on how to change the location of these resources.
After starting your application you should see a log message indicating that GraphQL is in the list of features. You can access the GraphQL endpoint at `http://host:port/graphql`, and the corresponding schema at `http://host:port/graphql/schema.graphql`. See [Configuration](#configuration) for additional information on how to change the location of these resources.

If you wish to use the [GraphQL UI](https://github.com/graphql/graphiql) then please see the [GraphQL MP Example](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/microprofile/graphql).

## Configuration

The specification defines the following configuration options:

| key | default value | description |
|----|----|----|
| `mp.graphql.defaultErrorMessage` | `Server Error` | Error message to send to caller in case of error |
| `mp.graphql.exceptionsBlackList` |   | Array of checked exception classes that should return default error message |
| `mp.graphql.exceptionsWhiteList` |   | Array of unchecked exception classes that should return message to caller (instead of default error message) |

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

For a complete example, see [GraphQL MP Example](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/microprofile/graphql).

## Additional Information

- [GraphQL](http://graphql.org).

## Reference

- [MicroProfile GraphQL Javadocs](https://download.eclipse.org/microprofile/microprofile-graphql-2.0/apidocs).
