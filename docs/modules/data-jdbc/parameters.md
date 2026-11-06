<!--@frontmatter
description: "Bind JDBC parameters and handle SQL null values safely"
-->
# Parameter Binding

## SQL Safety and Markers

Values supplied by the application belong in bound parameters, while the application
controls the SQL structure. Parameter markers can represent values, but they cannot
represent table names, column names, operators, or sort directions. When these
structural elements vary, select them from a closed set of values defined by the
application. Do not construct them directly from application input.

Declarative statements support either named markers or positional `?` markers in a given
statement. Imperative statements use positional markers. [Statement
Parameters](parameters.md#declarative-binding) and [Parameter
Binding](parameters.md#imperative-binding) explain the two forms.

Data JDBC does not treat text inside standard string literals, quoted identifiers,
comments, PostgreSQL escape strings, PostgreSQL strings that use dollar quoting, or
valid Oracle strings that use alternative quoting as parameters. It also preserves
doubled `??` characters for drivers that use them as escape syntax.

Portable parsing relies on a few SQL conventions. An ordinary string literal represents
an apostrophe with two apostrophes instead of a MySQL backslash escape. A line comment
begins when `--` is followed by whitespace, an ISO control character, or the end of the
SQL text. Block comments are not nested.

Data JDBC recognizes an Oracle `q` or `nq` literal only when its prefix does not
immediately follow a letter, digit, underscore, or dollar sign. SQL Server bracketed
identifiers require particular care because Data JDBC treats square brackets as ordinary
punctuation. A colon or question mark inside a bracketed identifier can therefore be
interpreted as a parameter marker and cause parameter validation or binding to fail.

## Declarative Binding

Repository statements accept named or positional parameters. A named marker matches an
explicit or inherited `@Data.Param` alias, or the Java parameter name when there is no
alias. A named marker can appear more than once:

```java
@Jdbc.Statement("""
        SELECT ID AS id, NAME AS name
        FROM CONTACT
        WHERE OWNER_ID = :ownerId OR DELEGATE_ID = :ownerId
        """)
List<Contact> findOwnedOrDelegated(long ownerId);
```

Positional `?` markers bind parameters in Java declaration order:

```java
@Jdbc.Statement("UPDATE CONTACT SET STATUS = ? WHERE ID = ?")
@Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
long updateStatus(String status, long id);
```

A statement uses either named markers or positional markers rather than mixing the two
forms. For ordinary query and update methods, every method parameter must be referenced
by at least one named marker or represented by one positional marker. A named parameter
can appear more than once. With positional markers, the number of Java parameters must
equal the number of markers.
For batch methods, each source contributes values to a logical item; see [Sources and
Parameter Binding](batch.md#sources-and-parameter-binding) for component expansion and
physical ordering.

An alias supplies a case-sensitive marker name without the colon. It must be nonblank,
follow Java identifier character rules, and contain no whitespace. Duplicate effective
names are rejected, including an alias that collides with an unaliased Java name.
Aliases require named SQL and do not change the declared Java type or SQL NULL mapping.

Repository parameters cannot represent a variable length SQL `IN` list. Each value
requires a separate scalar parameter and marker. The imperative model can construct SQL
when the number of values is determined at runtime.

Ordinary query and update parameters use the [supported scalar
types](mapping.md#supported-scalar-types). Reference arguments can be `null`, in which
case the generated implementation binds SQL `NULL` with the corresponding JDBC type.
Primitive parameters cannot be null, and scalar inputs do not use `Optional`. Batch
methods also accept the source containers described in [Batch Processing](batch.md).

SQL continues to determine what a null argument means. In the following example, a null
`email` removes the filter and allows every row to match:

```java
@Jdbc.Statement("""
        SELECT ID AS id, NAME AS name
        FROM CONTACT
        WHERE (:email IS NULL OR EMAIL = :email)
        """)
List<Contact> findByOptionalEmail(String email);
```

If a null argument should match only rows where `EMAIL` is SQL `NULL`, the predicate can
express that distinction explicitly:

```java
@Jdbc.Statement("""
        SELECT ID AS id, NAME AS name
        FROM CONTACT
        WHERE ((:email IS NULL AND EMAIL IS NULL) OR EMAIL = :email)
        """)
List<Contact> findByNullableEmail(String email);
```

Data JDBC supplies the typed SQL `NULL` without changing the predicate's meaning.

Data JDBC passes wildcard characters in a value for an SQL `LIKE` predicate without
escaping them. When the input represents literal text, the application escapes the
configured escape character, `%`, and `_`, while the SQL declares the same escape
character:

```java
@Jdbc.Statement("""
        SELECT ID AS id, NAME AS name
        FROM CONTACT
        WHERE NAME LIKE :pattern ESCAPE '!'
        """)
List<Contact> findByNamePattern(String pattern);
```

In this example, `pattern` uses `!` to escape `!`, `%`, and `_`. The database determines
case sensitivity and collation.

> [!NOTE]
> `@Data.Param` aliases are retained in compiled parent interfaces without
> `-parameters`. An override without an alias preserves inherited aliases; an explicit
> alias replaces them. Conflicting aliases from unrelated parents require an explicit
> child resolution. Without an alias, named markers require reliable Java parameter
> names, so a published parent must retain those names with `-parameters` or use
> positional SQL.

## Imperative Binding

Imperative statements use positional `?` markers. Positions begin at one and follow the
order of the markers in the SQL. Execution can begin after every position has been bound
exactly once:

```java
List<String> names = jdbcClient.create("""
        SELECT NAME
        FROM CONTACT
        WHERE OWNER_ID = ? OR DELEGATE_ID = ?
        ORDER BY NAME
        """)
        .bind(1, ownerId)
        .bind(2, ownerId)
        .map(String.class)
        .list();
```

Each ordinary `Statement.bind` value must be a nonnull [supported
scalar](mapping.md#supported-scalar-types). That ordinary statement API has no typed
null binding operation, so an ordinary operation needing SQL `NULL` includes it in the
SQL and uses a separate statement for that case. Batch callbacks instead use
`BatchBindings.bindNull(index, JDBCType)` to supply explicitly typed SQL NULL. See
[Imperative Batch Usage](batch.md#imperative-usage).

Parameter markers represent values rather than SQL structure. Identifiers, operators,
and sort directions remain values selected and controlled by the application.
