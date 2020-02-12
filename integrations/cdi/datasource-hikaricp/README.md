# Helidon Hikari Connection Pool `DataSource` CDI Integration

The Helidon Hikari Connection Pool `DataSource` CDI Integration
project supplies a [CDI portable extension](http://docs.jboss.org/cdi/spec/2.0/cdi-spec.html#spi)
 that lets the end user inject [`DataSource`](https://docs.oracle.com/javase/8/docs/api/javax/sql/DataSource.html)
objects into her CDI-based application.  The `DataSource` objects are
backed by the [Hikari connection pool](http://brettwooldridge.github.io/HikariCP/).

## Installation

Ensure that the Helidon Hikari Connection Pool `DataSource` CDI
Integration project and its runtime dependencies are present on your
application's runtime classpath.

For Maven users, your `<dependency>` stanza should look like this:

```xml
<dependency>
  <groupId>io.helidon.integrations.cdi</groupId>
  <artifactId>helidon-integrations-cdi-datasource-hikaricp</artifactId>
  <!-- See https://search.maven.org/classic/#search%7Cga%7C1%7Cg%3A%22io.helidon.integrations.cdi%22%20AND%20a%3A%22helidon-integrations-cdi-datasource-hikaricp%22 for available versions. -->
  <version>1.0.0</version>
  <scope>runtime</scope>
</dependency>
```

## Usage

If you want to use a `DataSource` named `orders` in your application
code, simply inject it in the [usual, idiomatic CDI way](http://docs.jboss.org/cdi/spec/2.0/cdi-spec.html#injection_and_resolution).
 Here is a field injection example:

```java
@Inject
@Named("orders")
private DataSource ordersDataSource;
```

And here is a constructor injection example:

```java
private final DataSource ds;

@Inject
public YourConstructor(@Named("orders") DataSource ds) {
  super();
  this.ds = ds;
}
```

The Helidon Hikari Connection Pool `DataSource` CDI Integration
project will satisfy this injection point with a [`HikariDataSource`](https://static.javadoc.io/com.zaxxer/HikariCP/2.7.8/com/zaxxer/hikari/HikariDataSource.html)
in [application scope](http://docs.jboss.org/cdi/api/2.0/javax/enterprise/context/ApplicationScoped.html).

To create the backing connection pool, the Helidon Hikari Connection
Pool `DataSource` CDI Integration project will use
[MicroProfile Config](https://static.javadoc.io/org.eclipse.microprofile.config/microprofile-config-api/1.3/index.html?overview-summary.html) to locate its configuration.
[Property names](https://static.javadoc.io/org.eclipse.microprofile.config/microprofile-config-api/1.3/org/eclipse/microprofile/config/Config.html#getPropertyNames--)
 that start with `javax.sql.DataSource.`_dataSourceName_`.` will
be parsed, and the remaining portion of each such name will be treated
as a
[Hikari connection pool property](https://github.com/brettwooldridge/HikariCP/blob/dev/README.md#configuration-knobs-baby).

So, for example, a System property with a name like this:

```
javax.sql.DataSource.orders.dataSourceClassName
```

...set to a value of:

```
org.h2.jdbcx.JdbcDataSource
```

...together with other similarly-named properties will result
ultimately in a Hikari connection pool named `orders` with a
`dataSourceClassName` property set to `org.h2.jdbcx.JdbcDataSource`,
injectable via:


```java
@Inject
@Named("orders")
private DataSource ordersDataSource;
```
