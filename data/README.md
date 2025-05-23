Helidon Data
----

Repository based database access.

# Glossary

| Name             | Description                                                                                                          |
|------------------|----------------------------------------------------------------------------------------------------------------------|
| Repository       | Annotated interface with methods named according to a specification; implementation is code generated                |
| Provider         | A persistence provider that provides a codegen to generate repository implementations, and runtime to handle methods |
| Data Source      | A named service implementing a data source contract (such as `javax.sql.DataSource`)                                 |
| Persistence unit | A named configurable component used by a Provider, such as JPA Persistence Unit                                      |

# Implementation notes

Summary for SQL based:
- (1 - n) Databases (not part of Helidon code or configuration)
- (1 - n) Data Sources - each represents a connection (or connection pool) to a Database (m : n)
- (1 - n) Persistence Units - each represents an approach of invoking statements on the connection, and uses a Data Source (m : n)
- (1 - n) Entities - each represents a table in a Database (0 - 1 : 1) / there may be a table that does not have an entity
- (1 - n) Repository interfaces - each represents operations on an Entity (0 - 1 : 1) / there may be an entity that does not have a repository
- (1 - n) Repository implementations - for each persistence unit provider that is on codegen path

## Data Source

Each configured data source is available in `ServiceRegistry` under its configured name. There can be one data source that is
unnamed of each data source type (such as `javax.sql.DataSource`).

Data sources are expected to be used by `Persistence units`.

## Provider and Repository

A `Provider` codegen module generates implementations of `Repository` interfaces.
For each repository, there is a named instance with the provider name (i.e. `jakarta-persistence`), and an unnamed instance
with the weight of provider.
`Repository` interface may have a `Data.Provider` annotation with the `Provider` name, to limit code generation only to that 
provider.

User chooses at injection time which instance they desire (if there is more than one option). If an unnamed instance is injected,
the one with the highest weight will be used. They can also use `@Service.Named("...")` with the provider name to inject
the specific provider based instance.

## Persistence Unit and Repository

Each provider may require additional configuration. This is achieved through a persistence unit, which is expected to 
reference a `Data Source` to be used, and possible additional configuration (such as JPA properties).

`Repository` interface may have a `Data.Pu` annotation with the persistence unit name, to require a named PU at runtime.
Note that the PU must be of the same type as the Provider

## Configuration

There are the following new nodes of configuration:

- `data-sources` - a section for data sources
- `data-sources.sql` - a list of SQL data sources (implement `javax.sql.DataSource`)
- `persistence-units` - a section for persistence units
- `persistence-units.jakarta` - a list of JPA Persistence unit configurations


## Services in Service Registry

Always
- Repository instance annotated with `@Data.Repository`, named with provider name, or unnamed

When `helidon-data-sql-datasource` is on classpath (and at least one provider of it, such as `hikari` or `ucp`)
- `javax.sql.DataSource` for each `data-sources.sql` configuration (named or unnamed) `io.helidon.data.sql.datasource.DataSourceConfigFactory.SQL_DATA_SOURCES_CONFIG_KEY`

When `helidon-data-jakarta-persistence` is on classpath
- `jakarta.persistence.EntityManagerFactory` for each `persistence-untis.jakarta` configuration (named or unnamed) `io.helidon.data.jakarta.persistence.PersistenceUnitFactory.JPA_PU_CONFIG_KEY`
- `jakarta.persistence.EntityManager` dtto., this should be injected as a `Supplier<EntityManager>` for cases that require it being closed; note that `@PersistenceContext` annotation is NOT supported in Helidon Data, only `@Service.Inject`

# Testing

We need to support test containers running on random ports.

This will be supported by defining a single SQL data source, such as:

```yaml
# this section is required for our testcontainer support
test.database:
    username: "test"  # will be honored
    password: "changeit"  # will be honored
    url: "jdbc:mysql://localhost:3306/testdb" # everything except port is honored

# this section is the usual Helidon Data setup of data sources and persistence units
data-sources:
  sql:
    - name: "test" # arbitrary name
      provider.hikari: # any provider that extends `ConnectionConfig`
        username: "${test.database.username}"
        password: "${test.database.password}"
        url: "${test.database.url}"
```

This information will be read by `io.helidon.data.sql.testing.SqlTestContainerConfig.configureContainer(io.helidon.common.config.Config, org.testcontainers.containers.JdbcDatabaseContainer<?>)` and a container will be initialized with it.
The method returns a `TestContainerHandler` that can be used to start and stop the container, and to get the new mapped port. Its method `setConfig()` can be called to register the config instance with updated port numbers in ServiceRegistry



