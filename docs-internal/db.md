# Helidon Database Proposal

Provide an API that enables reactive database support to be used with Helidon SE.

## Proposal

The Helidon DB is an abstraction layer over
- database configuration
- statement configuration
- statement processing
- handling of results

The Helidon DB also supports
- queries using either indexed or named parameters
- mapping of POJOs to database parameters using provided mapper(s)
    - `.createNamedDmlStatement("insert-mon").namedParam(pokemon)` would work if `Pokemon` db mapper is registered
- mapping of database query results to POJOs using provided mapper(s)
    - `dbRow.as(Pokemon.class)` would work if `Pokemon` db mapper is registered
- mapping of database columns to arbitrary types using provided mappers(s)
    - `column.as(Long.class)` would work even for String column, as long as `String -> Long` mapper is registered
   

The Helidon DB is *NOT*
- Statement abstraction 
    - users write statements in the language understood by the database (such as `SQL`)
    - there is *NO* query language other than the database native query language
    
As part of this proposal there is also added support for [generic mapping](generic-mapping.md)

### Required features

1. The API must be reactive 
2. The API must support backpressure for cases where multiple results are returned (queries)
3. There must be support for configuring Tracing without dependency on Tracing API
4. There must be support for configuring Metrics without dependency on Metrics API
5. There must be support for configuring Healthchecks without dependency on Healthcheck API
6. The first implementation must work at least over JDBC
  
### API

The API main interfaces/classes:
- `HelidonDb` - the entry point to create an instance of a Helidon DB, uses the usual `Builder`/`Config` pattern
    the provider to be used is either explicitly configured, or defined by name (see `DbProvider` below) or
    the first available one is used (ordered by priority). The instance has two methods to execute statements -
    `execute` and `inTransaction`
- `HelidonDbExecute` - the entry point to execution of statements
- `DbStatement` - the abstraction of any type of statement (with more specific `DbStatementQuery` etc.)
- `DbRowResult` - the interface for query results (supports access via `Subscriber`, can collect rows in memory etc.)
- `DbRow` - represents a single row in the database (or single object), provides mapping methods (using mappers from 
        SPI `DbMapperProvider`)
- `DbColumn` - represent a single column in the database (or an object property), provides mapping methods (using
    generic `MapperProvider`)
- `DbResult` - used for statements of unknown type, invokes `Consumer` of either a DML statement (`Consumer<Long>`)
        or a query statement (`Consumer<DbRowResult>`)
- `DbMapper` - defines possible mapping operations required to map arbitrary types to types needed by the database 
- `DbMapperManager` - used by DB implementations to access `DbMapper`s configured by `DbMapperProvider`s
- `DbInterceptor` and `DbInterceptorContext` provide support for integration with the DB for Metrics, Tracing and similar
- `DbException` a runtime exception to use when something fails (for JDBC this would usually wrap a `java.sql.SqlException`)


### SPI

SPI is used to add support for additional drivers (such as JDBC, Mongo DB etc.).
The SPI Classes:
- `DbProvider` - a Java Service loader interface used to locate available implementations
- `DbProviderBuilder` - builder used to configure the underlying database driver and behavior of the 
    driver implementation (such as `DbMapperProvider`, statements etc.)
- `DbMapperProvider` - a Java Service loader interface used to locate available implementations of mappers specific
    to database handling

## Possible Implementations

Our plan is to provide Helidon Database API implementations as very thin layers over several existing database APIs which are not reactive or their reactive API is unnecessarily complex or does not match Helidon style of API:

- JDBC
- MongoDB reactive driver
- Oracle NoSQL
- R2DBC - currently milestone releases
- ADBA - currently in Alpha version

## Configuration

There is a single configuration option expected when loading the `HelidonDb` from configuration - 
`source`. If defined, it is used to locate the `DbProvider` with the same name. If undefined, the
first provider is used (ordered by priority).

Each database driver implementation may choose what is required in configuration, though the following
 is recommended:
- Connectivity details
    - `url` - the URL to the database (this may be a `jdbc` URL, or any database specific way of locating an instance)
    - `username` - the user to connect to the database. If a database supports username/password based authentication, use this 
            property
    - `password` - password of the user to connect. If a database supports username/password based authentication, use this 
        property
- Statements configuration
    - `statements` - a configuration node that contains name-statement pairs
    
*Note on named statements*
Helidon DB is using named statements as a preferred way of configuration, as we can simply reference a statement
 in logging, exception handling, tracing etc. If you use arbitrary statements, the name is generated as a SHA-256.
The statements can be configured either in configuration file, or using the `HelidonDb.Builder.statements(DbStatements)` method. 

### Example
The following Yaml file configures JDBC data source to MySQL with custom statements:
```yaml
helidon-db:
    source: jdbc
    url: jdbc:mysql://127.0.0.1:3306/pokemon?useSSL=false
    username: user
    password: password
    statements:
      # required ping statement (such as for Healthcheck support) 
      ping: "DO 0"
      # Insert new pokemon
      insert-mon: "INSERT INTO pokemons VALUES(:name, :type)"
      select-mon-by-type: "SELECT * FROM pokemons WHERE type = ?"
      select-mon: "SELECT * FROM pokemons WHERE name = ?"
      select-mon-all: "SELECT * FROM pokemons"
      update-mon-type: "UPDATE pokemons SET type = :type WHERE name = :name"
      delete-mon: "DELETE FROM pokemons WHERE name = ?"
      delete-mon-all: "DELETE FROM pokemons"
```

This configuration file defines both connectivity and statements.

It also shows an example of the two options for parameters:
1. Indexed parameters, such as in statement `delete-mon`
2. Named parameters, such as in statement `insert-mon`

## Statement Types

Two basic types of statements are recognized:

- DML (Data Manipulation Language) statements: INSERT, UPDATE, DELETE, etc.
- DQL (Data Query Language) statements: SELECT

Each type of statement execution returns different result.

### Statement Parameters

Statement may contain parameters. Supported parameter types are:

1. indexed parameters identical to JDBC: `?`
2. named parameters similar to JPQL: `:name`, `$name`

Both types of parameters can't be mixed in a single statement.

Statements can be defined in Helidon configuration file or passed directly as a String argument.

#### Indexed Parameters

Parameters values are supplied in order identical to order of the `?` symbols in the statement.
Setters do not contain index argument. Index is determined from the order of their calls or index of provided List or array of parameters.

#### Named Parameters

Parameters values are supplied with corresponding parameter names. This can be done using name and value pairs or a Map

### Parameters substitution

Parameter values can be read from various class instances if `DbMapper<T>` interface is defined for them.

*Parameter substitution shall always be done using prepared statement if target database supports it.*

## Statement Execution Result

Execution result depends on statement type:

- DML statement: returns information about number of modified rows in the database
- DQL (query) statement: returns database rows matching the query

Statement execution is blocking operation so `CompletionStage` or `Flow` API must be returned by statement execute methods.

### DML Statement Execution Result

DML statement execute method returns `CompletionStage<Long>` to allow asynchronous processing of the result or related exceptions.

### DQL (Query) Statement Execution Result

DQL statement execute method returns `DbRowResult<T>` interface which gives user several options how to process returned database rows asynchronously:

- `Flow.Publisher<T> publisher()`: allows registration of Flow.Subscriber to process database rows when they are available
- `CompletionStage<Void> consume(Consumer<DbRowResult<T>> consumer)`: allows to register consumer of database rows
- `CompletionStage<List<T>> collect()`: allows to retrieve and process result as List of database rows. This method is limited to small result sets.

### Database Row

Database rows returned by query statement execution are returned as `DbRow` interface. This interface allows direct column access. It's also possible to map this row to another class instance using `DbMapper<T>` interface.

## Transactions

Transactions are supported using similar code pattern like simple statement execution. The only difference is usage of `<T> T inTransaction(Function<HelidonDbExecute, T> executor)` method instead of `execute`. Transaction scope and lifecycle is bound to `executor` instance.
Transaction is committed at the end of `executor` function scope when no exception has been thrown. Any exception thrown from `executor` function will cause transaction rollback.

## Example

### Application Configuration File
A configuration file used when creating a `Config` instance:
```yaml
helidon-db:
    source: jdbc
    url: jdbc:mysql://127.0.0.1:3306/myDatabase
    username: user
    password: password
    statements:
        insert-indexed-params: "INSERT INTO my_table VALUES(?, ?)"
        insert-named-params:   "INSERT INTO my_table VALUES(:name, :type)"
        select-all: "SELECT * FROM my_table"
        select-indexed-params: "SELECT * FROM my_table WHERE name = ?"
        select-named-params:   "SELECT * FROM my_table WHERE name = :name"
```

### Database Initialization:
```java
HelidonDb db = config
                .get("helidon-db")
                .as(HelidonDb::create)
                .orElseThrow(() -> new IllegalStateException("Configuration is missing"));
```

### Statements Execution:

#### Insert with Indexed Parameters
```java
db.execute(exec -> exec
                .createNamedDmlStatement("insert-indexed-params")
                .addParam("Pikachu")
                .addParam("electric")
                .execute())
          .thenAccept(count -> <do something>)
          .exceptionally(throwable -> <fail somehow>);
```

#### Insert with Named Parameters
```java
db.execute(exec -> exec
                .createNamedDmlStatement("insert-named-params")
                .addParam("name", "Pikachu")
                .addParam("type", "electric")
                .execute()
          )
          .thenAccept(count -> <do something>)
          .exceptionally(throwable -> <fail somehow>);
```

#### Query without parameters

```java
db.execute(exec -> exec.namedQuery("select-all"))
                .consume(response::send)
                .exceptionally(throwable -> sendError(throwable, response));
```

#### Get with indexed parameters
The `get` methods (`get`, `namedGet`, `createGet` and `createNamedGet`) are a shortcut to a query method that expects zero to 1 results.
For Java 8, you need to use `OptionalHelper.from(maybeRow)` to be able to use `ifPresentOrElse`.
When using newer versions of Java, `ifPresentOrElse` is available on `Optional`.
```java
db.execute(exec -> exec.namedGet("select-indexed-params"), "Pikachu")  
          .thenAccept(maybeRow -> maybeRow.ifPresentOrElse(row -> <process row>,
                                                            () -> <process not found>))
          .exceptionally(throwable -> <fail somehow>);
```

#### Simple Transaction
```java
db.inTransaction(exec -> exec
        .get("SELECT type FROM my_table WHERE name = ?", "Pikachu")
        .thenAccept(maybeRow -> maybeRow
                .ifPresent(row -> exec
                        .insert("INSERT INTO my_table VALUES(?, ?)", "Raichu", row.column("type"))
                )
        )
).exceptionally(throwable -> <fail somehow>)
```

### Interceptor implementation

Example interceptor (for tracing):
```java
public class DbTracing implements DbInterceptor {
    @Override
    public void statement(DbInterceptorContext interceptorContext) {
        Context context = interceptorContext.context();
        Tracer tracer = context.get(Tracer.class).orElseGet(GlobalTracer::get);

        // now if span context is missing, we build a span without a parent
        Tracer.SpanBuilder spanBuilder = tracer.buildSpan(interceptorContext.dbType() + ":" + interceptorContext.statementName());

        context.get(SpanContext.class)
                .ifPresent(spanBuilder::asChildOf);

        Span span = spanBuilder.start();

        interceptorContext.statementFuture().thenAccept(nothing -> span.log(CollectionsHelper.mapOf("type", "statement")));

        interceptorContext.resultFuture().thenAccept(count -> span.log(CollectionsHelper.mapOf("type", "result",
                                                                "count", count)).finish())
                .exceptionally(throwable -> {
                    Tags.ERROR.set(span, Boolean.TRUE);
                    span.log(CollectionsHelper.mapOf("event", "error",
                                                     "error.kind", "Exception",
                                                     "error.object", throwable,
                                                     "message", throwable.getMessage()));
                    span.finish();
                    return null;
                });

    }

    public static DbTracing create() {
        return new DbTracing();
    }
}
```

### Mapper implementation

Example mapper implementation for Pokemon:
```java
public class PokemonMapper implements DbMapper<Pokemon> {

    @Override
    public Pokemon read(DbRow row) {
        DbColumn name = row.column("name");
        DbColumn type = row.column("type");
        return new Pokemon(name.as(String.class), type.as(String.class));
    }

    @Override
    public Map<String, Object> toNamedParameters(Pokemon value) {
        Map<String, Object> map = new HashMap<>(1);
        map.put("name", value.getName());
        map.put("type", value.getType());
        return map;
    }

    @Override
    public List<Object> toIndexedParameters(Pokemon value) {
        List<Object> list = new ArrayList<>(2);
        list.add(value.getName());
        list.add(value.getType());
        return list;
    }
    
}
```

## Open questions
The support for writing rows to WebServer currently transforms the row to JsonObject and then writes it to 
 the `DataChunk` to send it.
Integration with WebServer should be (IMHO) more straight-forward.
Example that does not work today:
```java
DbRowResult<DbRow> result = ....;
response.send(result.map(JsonObject.class).publisher());
```

Or even:
```java
DbRowResult<DbRow> result = ....;
response.send(result.map(Pokemon.class).publisher());
```