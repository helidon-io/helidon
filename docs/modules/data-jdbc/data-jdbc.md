<!--@frontmatter
description: "Use the Helidon Data JDBC provider"
-->
# Helidon Data JDBC Provider

## Overview

Helidon Data JDBC executes SQL that your application defines through Java Database
Connectivity (JDBC) APIs. It binds parameters, maps results, and manages the JDBC
resources used by each operation.

This document uses the shorter name Data JDBC for the provider.

For repositories backed by Jakarta Persistence and entity management, see
[Helidon Data Repository](../data.md). [DbClient](../dbclient.md) provides a
separate database client API with JDBC and MongoDB integrations.

Data JDBC does not provide entity management or derive queries from repository method
names. It supports two programming models:

- The [declarative programming model](declarative.md) defines operations on an
  annotated repository interface. Helidon validates the interface and generates
  the repository implementation during compilation.
- The [imperative programming model](imperative.md) uses `JdbcClient` to build
  and execute operations directly in application code.

You can combine declarative repository methods with imperative `JdbcClient` operations
in the same application. Both models provide consistent result mapping, transaction
participation, and resource management.

Each Data JDBC operation executes one SQL statement. Database schema creation remains
separate and should be complete before data operations begin. [Schema
Management](#schema-management) explains how schema changes fit into an application
using Data JDBC.

> [!NOTE]
> Helidon Data JDBC is an incubating feature. It is not production ready. Its
> APIs and behavior may change incompatibly or be removed in any release.

## Common Tasks

- [Run a first query](getting-started.md).
- [Configure a connection pool](configuration.md#registered-data-sources).
- [Bind parameters](parameters.md) or [map results](mapping.md).
- [Return an inserted identifier](queries.md#generated-keys).
- [Use a local transaction](transactions.md#local-transactions).
- [Check data source or client readiness](health-checks.md).

## Maven Coordinates

[Getting Started](getting-started.md#dependencies) lists runtime and driver
dependencies.

## Configuration

Configure named or default clients under `data.clients.jdbc`. See [Clients and Data
Sources](configuration.md#client-configuration).

### Direct JDBC Connections

Supply a JDBC URL and connection settings. See [Direct JDBC
Connections](configuration.md#direct-jdbc-connections).

### Registered Data Sources

Share a registered data source or pool by its name. See [Registered Data
Sources](configuration.md#registered-data-sources).

## SQL Guidelines

Each operation executes one statement. See [execution
contracts](queries.md#statement-execution) and [SQL safety and
markers](parameters.md#sql-safety-and-markers).

## Local Transactions

Managed clients and repositories can join local JDBC transactions. Standalone clients do
not join them. See [Local Transactions](transactions.md#local-transactions).

## Schema Management

Prepare the schema before data operations. DDL can commit implicitly and belongs outside
data transactions. See [Schema Management](transactions.md#schema-management).

## Database Portability

Data JDBC uses standard JDBC APIs, but SQL syntax, type conversions, generated keys, and
transaction behavior can vary by database and driver. Applications should verify each
database and driver combination that they support.

Connection pooling, timeouts, and properties defined by a database vendor are configured
through the data source, JDBC driver, connection URL, or database. Applications can
limit large results in SQL and provide deterministic ordering for paging.

## Errors and JDBC Warnings

Data JDBC converts an `SQLException` to a Helidon Data `DataException`. The exception
can retain the SQLSTATE and vendor error code reported by the driver. It excludes SQL
text, bound values, credentials, and driver messages that could reveal application data.

Outside a local transaction, a runtime exception raised by an application
`JdbcClient.RowMapper` passes through unchanged. Inside a managed local transaction, the
transaction provider can report a `TxException` and retain the mapper exception as its
cause.

Data JDBC does not promote JDBC warnings to exceptions. Applications that need to
monitor warnings can use facilities provided by the database or driver. These facilities
can also report data truncation that occurs while results are read.

For transaction and retry consequences, see [Failure
Handling](transactions.md#failure-handling).

## Parameter Count Cache

The client caches parameter counts, not prepared statements. See [Parameter Count
Cache](configuration.md#parameter-count-cache).

## Next Steps

From here, continue with the programming model that best fits the application:

- [Declarative Programming Model](declarative.md) explains repository
  definition, result mapping, generated keys, and transactions.
- [Imperative Programming Model](imperative.md) covers direct statement
  execution and custom row mapping.
