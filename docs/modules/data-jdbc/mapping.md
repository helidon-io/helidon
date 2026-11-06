<!--@frontmatter
description: "Map JDBC results to scalars, records, and application values"
-->
# Result Mapping

## Result Mapping

Data JDBC can read a scalar from the first selected column, construct a flat record from
column labels, or delegate the row to a mapper supplied by the application. Each
physical row is mapped independently.

Data JDBC does not assemble joined rows into an object graph. The application can
aggregate the materialized values after the repository method returns or obtain related
data through separate repository operations.

### Scalars and Records

Mapping to a required scalar fails when the first column contains SQL `NULL`. A nullable
record component uses `Optional<S>`, where `S` is a supported scalar.

Helidon matches column labels to record component names without regard to case or column
order. SQL aliases make the mapping explicit:

```java
record ContactSummary(long id, String name, Optional<String> email) {
}

@Jdbc.Statement("""
        SELECT EMAIL AS email, ID AS id, NAME AS name
        FROM CONTACT
        ORDER BY NAME
        """)
List<ContactSummary> findAll();
```

Mapping fails if a record component has no matching column label or if multiple column
labels match the component without regard to case. When a driver returns a blank label,
Data JDBC uses the physical column name. Mapping also fails if the driver provides
neither a usable label nor a usable column name.

Record component names must be unique without regard to case. Each component must be a
supported scalar or `Optional` of a supported scalar. A row mapper handles nested
records, mutable classes, and conversions specific to the application.

### Supported Scalar Types

| Family | Java types |
|--------|------------|
| Boolean and numeric | `boolean`/`Boolean`, `byte`/`Byte`, `short`/`Short`, `int`/`Integer`, `long`/`Long`, `float`/`Float`, `double`/`Double`, `BigDecimal` |
| Text and binary | `String`, `byte[]` |
| Java time | `LocalDate`, `LocalTime`, `LocalDateTime` |
| JDBC time | `java.sql.Date`, `java.sql.Time`, `java.sql.Timestamp` |

For a null reference argument, Data JDBC uses the following canonical JDBC type:

| Java type | JDBC type |
|-----------|-----------|
| `Boolean` | `BOOLEAN` |
| `Byte` | `TINYINT` |
| `Short` | `SMALLINT` |
| `Integer` | `INTEGER` |
| `Long` | `BIGINT` |
| `Float` | `REAL` |
| `Double` | `DOUBLE` |
| `BigDecimal` | `DECIMAL` |
| `String` | `VARCHAR` |
| `byte[]` | `VARBINARY` |
| `LocalDate`, `java.sql.Date` | `DATE` |
| `LocalTime`, `java.sql.Time` | `TIME` |
| `LocalDateTime`, `java.sql.Timestamp` | `TIMESTAMP` |

This mapping applies only to reference parameters because primitive parameters cannot be
null.

These types are available for repository parameters, automatic results, generated keys,
record components, imperative bind values, and typed row reads. For another Java type,
store a supported representation such as text, bytes, or a numeric value, then convert
it in application code or a row mapper.

Data JDBC does not map `OffsetTime` or `OffsetDateTime` implicitly because databases do
not preserve those values consistently. An application can store the local value and
offset separately, or store an ISO 8601 value in a character column and reconstruct the
value in a row mapper.

### Custom Row Mapping

A `JdbcClient.RowMapper<T>` can handle a result that does not fit automatic scalar or
record mapping. The mapper becomes a Service Registry service, and `@Jdbc.RowMapper`
connects it to a repository method.

A row mapper controls how a single row is converted. It does not change the supported
repository return types. Its result type must follow the restrictions in [Return
Types](queries.md#result-cardinality). A mapper can produce an application type that
holds detached collection or map data. The mapped result itself cannot be a
`Collection`, `Map`, or `BaseStream` implementation or subtype.

Because the mapper implements an incubating Data JDBC API, its class also acknowledges
that API status:

```java
@SuppressWarnings(Api.SUPPRESS_INCUBATING)
@Service.Singleton
final class ContactLabelMapper implements JdbcClient.RowMapper<ContactLabel> {

    @Override
    public ContactLabel map(JdbcClient.Row row) {
        return new ContactLabel(row.get("id", Long.class),
                                row.get("name", String.class));
    }
}

@Jdbc.Statement("SELECT ID AS id, NAME AS name FROM CONTACT WHERE ID = :id")
@Jdbc.RowMapper(ContactLabelMapper.class)
Optional<ContactLabel> findLabel(long id);
```

When the annotation names a mapper class, Helidon resolves that exact service type. When
the annotation does not name a class, Helidon obtains a `JdbcClient.RowMapper<T>`
service for the result type of the method. If several mappers are available, the Service
Registry selects the mapper with the highest weight. The selection order is undefined
when several mappers have the same weight. Repository activation fails when no suitable
mapper is available. See [Injection Points](../injection/injection.md#injection-points)
for more about service selection.

During the callback, `Row.get` reads a required column and `Row.optional` reads a
nullable column. Both methods accept a column label or an index whose numbering begins
at one. Column label lookup ignores case and fails when the requested label is missing
or ambiguous. Access by index does not resolve labels, so duplicate labels in other
columns do not affect it. The row remains valid only for the duration of the callback
and only on the callback thread, so the mapped result must contain all required values
and must not depend on the row or its JDBC resources.

A singleton repository can call its mapper concurrently, so the mapper needs to be
stateless or otherwise safe for concurrent calls. Outside a local transaction, a runtime
exception raised by the mapper passes through unchanged. Inside a managed local
transaction, the transaction provider can report a `TxException` with the mapper
exception as its cause.

## Imperative Mapping

The `map` method accepts the class of a supported scalar to read from the first selected
column:

```java
long count = jdbcClient.create("SELECT COUNT(*) FROM CONTACT")
        .map(Long.class)
        .one();
```

For scalar mapping, `one()` and `list()` fail when the selected column contains SQL
`NULL`. `optional()` returns `Optional.empty()` when no row exists or when the first
column of the single row is SQL `NULL`. A custom mapper that returns `Optional<T>` can
distinguish those cases because the outer optional then represents row presence.

## Imperative Row Mapping

A `JdbcClient.RowMapper<T>` handles a row that contains several columns or needs a
conversion specific to the application:

```java
List<ContactSummary> contacts = jdbcClient.create("""
        SELECT ID AS id, NAME AS name, EMAIL AS email
        FROM CONTACT
        ORDER BY ID
        """)
        .map(row -> new ContactSummary(row.get("id", Long.class),
                                       row.get("name", String.class),
                                       row.optional("email", String.class)))
        .list();
```

Within the mapper callback, `Row.get` reads a required value and `Row.optional` reads a
nullable value. Both methods accept a column label or an index whose numbering begins at
one. The row remains valid only on the callback thread and only until the mapper
returns, so the returned value must contain everything the application needs and must
not retain the `Row`.

Helidon invokes the mapper once for each physical row and does not combine rows into an
object graph. The mapper returns a value that is independent of the JDBC row and its
resources.

Outside a local transaction, a runtime exception raised by the mapper passes through the
`JdbcClient` terminal method unchanged. Inside a managed local transaction, the
transaction provider can report a `TxException` with the mapper exception as its cause.
