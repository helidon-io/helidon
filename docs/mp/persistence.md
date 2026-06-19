# Persistence

## Overview

Helidon MP comes with deep integration for three specification-defined, broadly
persistence-related technologies that can be used together or separately:

- [Named data sources](#named-data-source-integration)
- [Jakarta Transactions (JTA)][jakarta-transact]
- [Jakarta Persistence (JPA)](#jakarta-persistence-jpa)

Each integration’s setup, configuration, and usage are described below.

## Named Data Source Integration

### Overview

Helidon MP’s named data source integration allows you to safely inject managed
[`javax.sql.DataSource`][javax-sql-dataso] instances that are annotated with
[`jakarta.inject.Named` annotations][jakarta-inject-n] into your Helidon MP
application. [`java.sql.Connection` objects][java-sql-connect]
[acquired][acquired] from these data sources will be pooled by your choice of
one of two possible connection pool implementations.

The connections managed by the connection pool will be supplied by your
relational database vendor’s JDBC driver.

How you set up Helidon MP’s named data source integration differs depending on
which of these two connection pools, which JDBC driver, and which relational
database product you use.

Representative setups are described below. This list of setups is not
exhaustive.

### Project Setup

#### Setting Up a Connection Pool

##### Overview

Helidon MP’s named data source integration requires a connection pool
implementation.

Helidon MP comes with support for two connection pools:

1.  [HikariCP][hikaricp]
2.  [Oracle Universal Connection Pool][oracle-universal]

You can choose to use either, but not both.

Details concerning each connection pool’s setup are described below.

##### Setting Up the HikariCP Connection Pool

###### Maven Coordinates (HikariCP)

To include the [HikariCP connection pool][hikaricp] in your Helidon MP
application:

- [Ensure your dependencies are managed](../managing-dependencies.md)
- Ensure the following `<dependency>` element is present as a child element of
  your project’s `pom.xml` file’s `<dependencies>` element:

  <!--@mdc ::code-callout -->
  ```xml [pom.xml]
  <dependency>
    <groupId>io.helidon.integrations.cdi</groupId>
    <artifactId>helidon-integrations-cdi-datasource-hikaricp</artifactId>
    <scope>runtime</scope> <!-- (1) -->
  </dependency>
  ```
  1. The `scope` is `runtime`, indicating that the HikariCP integration will be
    available on the runtime classpath.
  <!--@mdc :: -->

##### Setting up the Oracle Universal Connection Pool

###### Maven Coordinates (Oracle Universal Connection Pool)

To include the [Oracle Universal Connection Pool][oracle-universal] in your
Helidon MP application:

- [Ensure your dependencies are managed](../managing-dependencies.md)
- Ensure the following `<dependency>` element is present as a child element of
  your project’s `pom.xml` file’s `<dependencies>` element:

  <!--@mdc ::code-callout -->
  ```xml [pom.xml]
  <dependency>
    <groupId>io.helidon.integrations.cdi</groupId>
    <artifactId>helidon-integrations-cdi-datasource-ucp</artifactId>
    <scope>runtime</scope> <!-- (1) -->
  </dependency>
  ```
  1. The `scope` is `runtime`, indicating that the Oracle Universal Connection
    Pool integration will be available on the runtime classpath.
  <!--@mdc :: -->

#### Setting Up a Database Driver

##### Overview

Regardless of which connection pool you use, at the lowest level, JDBC database
driver classes are what is ultimately responsible for making any connections to
a relational database. JDBC database driver classes are
database-product-specific.

Once you have decided upon a relational database product to use, and JDBC driver
classes to use to connect to it, [ensure your dependencies are
managed](../managing-dependencies.md), and then ensure that a `runtime`-scoped
`<dependency>` element describing your JDBC driver classes is present as a child
element of your project’s `pom.xml` file’s `<dependencies>` element.

See the [JDBC 4.3 Specification][jdbc-4-3-specifi] for more information about
JDBC.

Representative setups are described below. This list of setups is not
exhaustive.

##### Setting Up H2

###### Maven Coordinates (H2)

To include the [H2 JDBC driver][h2-jdbc-driver] classes in your Helidon MP
application so your application can [connect to an H2
database][connect-to-an-h2] (whether in-memory or persistent):

- [Ensure your dependencies are managed](../managing-dependencies.md)
- Ensure the following `<dependency>` element is present as a child element of
  your project’s `pom.xml` file’s `<dependencies>` element:

  <!--@mdc ::code-callout -->
  ```xml [pom.xml]
  <dependency>
    <groupId>io.helidon.integrations.db</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope> <!-- (1) -->
  </dependency>
  ```
  1. The `scope` is `runtime`, indicating that the H2 JDBC driver classes will be
    available on the runtime classpath.
  <!--@mdc :: -->

##### Setting Up Oracle JDBC

###### Maven Coordinates (Oracle JDBC)

To include the [Oracle JDBC driver classes][oracle-jdbc-driv] in your Helidon MP
application so your application can [connect to an Oracle
database][connect-to-an-or]:

- [Ensure your dependencies are managed](../managing-dependencies.md)
- Read and understand [Developer’s Guide For Oracle JDBC 21c on Maven
  Central][developer-s-guid]
- For a basic setup, ensure the following `<dependency>` element is present as a
  child element of your project’s `pom.xml` file’s `<dependencies>` element:

  <!--@mdc ::code-callout -->
  ```xml [pom.xml]
  <dependency>
    <groupId>io.helidon.integrations.db</groupId>
    <artifactId>ojdbc</artifactId>
    <scope>runtime</scope> <!-- (1) -->
  </dependency>
  ```
  1. The `scope` is `runtime`, indicating that the Oracle JDBC driver classes
    will be available on the runtime classpath.
  <!--@mdc :: -->

### Configuration

#### Overview

Each connection pool supported by Helidon’s named data source integration
support is, itself, a `DataSource` that wraps a *vendor-supplied* `DataSource`
present in the JDBC driver classes you added to your project. You must configure
both the pool and the vendor-supplied `DataSource`.

To configure Helidon MP’s named data source integration:

1.  Decide where each property of the configuration will reside, as permitted by
    [Helidon MP’s MicroProfile Config implementation][helidon-mp-s-mic]
2.  Create configuration suitable for the combination of your selected
    connection pool and your selected vendor-supplied `DataSource`
    implementation in those locations

Helidon MP’s named data source integration relies on [Helidon MP’s usage of
MicroProfile Config](config/introduction.md), so you have many choices for each
configuration property when deciding on your configuration’s location in (1)
above.

The configuration property values themselves are necessarily specific to the
connection pool you selected, and to the vendor-supplied `DataSource`
responsible for actually connecting to your relational database. In general, at
a minimum, in your configuration you typically supply:

- Information so the connection pool knows which vendor-supplied `DataSource`
  implementation to manage
- A JDBC URL specific to the vendor-supplied `DataSource` describing where the
  database is located, so the managed vendor-supplied `DataSource` knows how to
  connect to it
- Information required for the vendor-supplied `DataSource` to authenticate to
  the database and otherwise tailor itself to it

Some examples for representative configurations follow. This list of
configurations is not exhaustive.

#### Configuration Prefixes

All MicroProfile Config-compatible property names for Helidon MP’s named data
source integration follow a common pattern:

***objecttype***.***datasourcename***.***propertyname***

- The name of a given configuration property always begins with the
  ***objecttype*** portion: a fully-qualified Java class name of the object
  being configured. Configuration for Helidon MP’s named data source integration
  concerns the behavior of `javax.sql.DataSource` objects, so Helidon MP’s named
  data source integration configuration property names begin with
  `javax.sql.DataSource`.
  - A period (`.`) separates the *objecttype* portion from the rest of the
    property name.
- The ***datasourcename*** portion, the name of the data source being
  configured, comes next. It cannot contain a period (`.`).
  - A period (`.`) separates the *datasourcename* portion from the rest of the
    property name.
- The ***propertyname*** portion, identifying the connection-pool- or
  vendor-supplied-`DataSource`-specific configuration property name, comes last.
  It may contain periods (`.`).

As an example, configuration to set an imaginary `foo.bar` property on the
`test` data source’s associated connection pool or vendor-specific `DataSource`
to `baz` looks like this in Java `.properties` format:

<!--@mdc ::code-callout -->
```properties [microprofile-config.properties]
javax.sql.DataSource.test.foo.bar=baz# <1><2><3>
```
1. The ***objecttype*** portion of the configuration property name is
   `javax.sql.DataSource`.
2. The ***datasourcename*** portion of the configuration property name is `test`.
3. The ***propertyname*** portion of the configuration property name is
   `foo.bar`.
<!--@mdc :: -->

#### Examples

Here are some examples illustrating general named data source configuration
patterns in various [common MicroProfile Config-compatible
locations][common-microprof].

##### Example: `META-INF/microprofile-config.properties` Classpath Resource

Here is an example of some named data source configuration as might be found in
a `src/main/resources/META-INF/microprofile-config.properties` configuration
source:

```properties [microprofile-config.properties]
javax.sql.DataSource.yourDataSourceName.somePropertyOfYourConnectionPoolAndDataSource = itsValue
javax.sql.DataSource.yourDataSourceName.someOtherPropertyOfYourConnectionPoolAndDataSource = anotherValue
```

##### Example: System Properties Set on the Command Line

Here is an example of some named data source configuration using system
properties on the command line instead:

```shell [Terminal]
java \
  -Djavax.sql.DataSource.yourDataSourceName.somePropertyOfYourConnectionPoolAndDataSource=itsValue \
  -Djavax.sql.DataSource.yourDataSourceName.someOtherPropertyOfYourConnectionPoolAndDataSource=anotherValue \
  # ...
```

##### Example: Environment Variables Set on the Command Line

Here is an example of some named data source configuration using environment
variables as typed directly into a command line shell, relying on [MicroProfile
Config’s mapping rules][microprofile-con], since many shells will not understand
environment variable names with periods (.) in them:

```shell [Terminal]
JAVAX_SQL_DATASOURCE_YOURDATASOURCENAME_SOMEPROPERTYOFYOURCONNECTIONPOOLANDDATASOURCE=itsValue \
JAVAX_SQL_DATASOURCE_YOURDATASOURCENAME_SOMEOTHERPROPERTYOFYOURCONNECTIONPOOLANDDATASOURCE=anotherValue \
java # ...
```

##### Example: Environment Variables Set By the `env` Command

Here is an example of some named data source configuration using environment
variables as supplied via the [`env` shell command][env-shell-comman], thus
removing the need for [MicroProfile Config’s mapping rules][microprofile-con]:

```shell [Terminal]
env 'javax.sql.DataSource.yourDataSourceName.somePropertyOfYourConnectionPoolAndDataSource=itsValue' \
  'javax.sql.DataSource.yourDataSourceName.someOtherPropertyOfYourConnectionPoolAndDataSource=anotherValue' \
  java # ...
```

##### Example: `application.yaml` Classpath Resource

Here is an example of some named data source configuration as might be found in
a `src/main/resources/application.yaml` classpath resource:

```yaml
javax:
  sql:
    DataSource:
      yourDataSourceName:
        somePropertyOfYourConnectionPoolAndDataSource: itsValue
        someOtherPropertyOfYourConnectionPoolAndDataSource: anotherValue
```

##### Example: Configuring the Oracle Universal Connection Pool and Oracle JDBC

This example presumes you have:

- [set up the Oracle Universal Connection Pool][set-up-the-oracl]
- [set up Oracle JDBC](#setting-up-oracle-jdbc)

This example, in Java properties file format, configures an Oracle Universal
Connection Pool-managed data source named `main` to [connect to an Oracle
Database][connect-to-an-or-2] on `localhost` port `1521`, using the
`oracle.jdbc.poolOracleDataSource` vendor-supplied `DataSource`, with a service
name of `XE`, a `user` of `scott`, and a `password` of `tiger`:

<!--@mdc ::code-callout -->
```properties [microprofile-config.properties]
javax.sql.DataSource.main.connectionFactoryClassName = oracle.jdbc.pool.OracleDataSource# <1>
javax.sql.DataSource.main.URL = jdbc:oracle:thin:@//localhost:1521/XE# <2>
javax.sql.DataSource.main.user = scott
javax.sql.DataSource.main.password = tiger
```
1. Why `connectionFactoryClassName`? See
   [`PoolDataSourceImpl#setConnectionFactoryClassName(String)`][pooldatasourceim]).
2. See [Thin-style Service Name Syntax][connect-to-an-or].
<!--@mdc :: -->

In general, the properties that can be set on the Oracle Universal Connection
Pool can be inferred from the "setter" methods found in [the Javadoc for the
`PoolDataSourceImpl` class][the-javadoc-for].

In general, the properties that can be set on the
[`oracle.jdbc.pool.OracleDataSource`][oracle-jdbc-pool] `DataSource`
implementation can be inferred from the "setter" methods found in [its
Javadoc][oracle-jdbc-pool].

> [!NOTE]
> Unlike [HikariCP][unlike-hikaricp], the Oracle Universal Connection Pool does
> not distinguish cleanly between configuration properties that affect *its*
> behavior and those that affect the behavior of the vendor-supplied
> `DataSource` implementation whose connections it pools.

For example, in the example above it is not possible to tell that
[`connectionFactoryClassName`][pooldatasourceim] is a property of the Oracle
Universal Connection Pool, and `user` is a property of the
[`oracle.jdbc.pool.OracleDataSource`][user-is-a-proper] `DataSource`
implementation. In some cases, the Oracle Universal Connection Pool will set the
given [property][set-the-given-pr] on *both* the connection pool itself *and* on
the vendor-supplied `DataSource` it manages.

##### Example: Configuring the HikariCP Connection Pool and H2

This example presumes you have:

- [set up the HikariCP connection pool][set-up-the-hikar]
- [set up H2](#setting-up-h2)

This example, in Java properties file format, configures a HikariCP-managed data
source named `test` to connect to an in-memory H2 database named `unit-testing`
with a `user` of `sa` and an empty password:

<!--@mdc ::code-callout -->
```properties [microprofile-config.properties]
javax.sql.DataSource.test.dataSourceClassName = org.h2.jdbcx.JdbcDataSource# <1>
javax.sql.DataSource.test.dataSource.url = jdbc:h2:mem:unit-testing;DB_CLOSE_DELAY=-1# <2><3>
javax.sql.DataSource.test.dataSource.user = sa
javax.sql.DataSource.test.dataSource.password =
```
1. Why `dataSourceClassName`? See [HikariCP’s configuration
   documentation][hikaricp-s-confi] for information about how HikariCP separates
   configuration of the connection pool itself from configuration of the
   vendor-supplied `DataSource`.
2. Why `dataSource.`? See [`PropertyElf.java`, lines 47–49][propertyelf-java].
3. See [the H2 database’s documentation about its URL format][connect-to-an-h2].
<!--@mdc :: -->

HikariCP’s configuration properties are described [on its GitHub
repository][on-its-github-re]. Properties that should be forwarded on to the
vendor-supplied `DataSource` [are prefixed with `dataSource.`][unlike-hikaricp]
as seen in the example above.

In general, the properties that can be set on the
[`org.h2.jdbcx.JdbcDataSource`][org-h2-jdbcx-jdb] vendor-supplied `DataSource`
can be inferred from the "setter" methods found in [its
Javadoc][org-h2-jdbcx-jdb].

#### Usage

You use Helidon MP’s named data source integration in the same way, regardless
of your choices of vendor-supplied `DataSource` and connection pool.

To use Helidon MP’s named data source integration in your application, once it
has been [set up](#project-setup) and [configured](#configuration), create an
ordinary [`DataSource`][javax-sql-dataso]-typed injection point in a [Java class
representing a CDI bean][java-class-repre] somewhere in your application,
[annotated with the name][jakarta-inject-n] of the data source you wish to use.

Here is how to define such a field-backed injection point:

<!--@mdc ::code-callout -->
```java
@Inject // <1>
@Named("test") // <2>
private DataSource ds; // <3>
```
1. [`@Inject`][inject] marks the field as an injection point. Its behavior is
   defined by the [Jakarta Dependency Injection specification][jakarta-dependen].
2. [`@Named("test")`][jakarta-inject-n] says to use the data source named `test`
   (as declared by the [*datasourcename* portion](#configuration-prefixes) of a
   named data source configuration property).
3. The field injection point has a type of
   [`javax.sql.DataSource`][javax-sql-dataso], and the field itselfmay be named
   anything you like.
<!--@mdc :: -->

<!--@mdc ::code-callout -->
```java
private final DataSource ds; // <1>

@Inject // <2>
public SomeObject(@Named("test") DataSource ds) { // <3>
    this.ds = ds; // <4>
}
```
1. This is the field whose value will be set in the constructor.
2. [`@Inject`][inject] marks the constructor as one containing parameter
   injection points. Its behavior is defined by the [Jakarta Dependency Injection
   specification][jakarta-dependen].
3. [`@Named("test")`][jakarta-inject-n] says to use the data source named `test`
   (as declared by the [*datasourcename* portion](#configuration-prefixes) of a
   named data source configuration property). The parameter injection point has a
   type of [`javax.sql.DataSource`][javax-sql-dataso], and the parameter itself
   may be named anything you like.
4. The injected argument will never be `null`.
<!--@mdc :: -->

## Jakarta Transactions (JTA) Integration

### Overview

Helidon MP’s Jakarta Transactions integration integrates the [Naryana
transaction engine](https://www.narayana.io/), an implementation of the [Jakarta
Transactions Specification][jakarta-transact-2], into Helidon MP. It lets you
use [`@jakarta.transaction.Transactional`][jakarta-transact-3] to declare JTA
transactions in your Java code.

### Maven Coordinates (JTA)

To include Helidon’s JTA integration in your application:

- [Ensure your dependencies are managed](../managing-dependencies.md)
- Ensure the following `<dependency>` elements are present as child elements of
  your project’s `pom.xml` file’s `<dependencies>` element:

  <!--@mdc ::code-callout -->
  ```xml [pom.xml]
  <dependencies>
    <dependency>
      <groupId>jakarta.transaction</groupId>
      <artifactId>jakarta.transaction-api</artifactId>
      <scope>provided</scope> <!-- (1) -->
    </dependency>
    <dependency>
      <groupId>io.helidon.integrations.cdi</groupId>
      <artifactId>helidon-integrations-cdi-jta-weld</artifactId>
      <scope>runtime</scope> <!-- (2) -->
    </dependency>
  </dependencies>
  ```
  1. The `scope` is `provided`, which ensures that the [JTA classes required for
    compilation][jakarta-transact-3] are available at compile time.
  2. The implementation of these API classes (provided by
    [Narayana](https://narayana.io/)) will be available at runtime.
  <!--@mdc :: -->

### Configuration

#### Overview

Helidon MP’s Jakarta Transactions integration does not require configuration,
but configuration is possible. Because configuration is of the [underlying
Narayana transaction engine](https://narayana.io/), any restrictions are those
of the engine, not of Helidon itself.

Narayana, unlike Helidon MP, does not use MicroProfile Config, so its
configuration options are less flexible.

Some common examples of Narayana configuration follow.

#### Configuring the Object Store Directory

Narayana features an object store directory which it uses to store information
about transaction outcomes. To set its location, you may set the
[`ObjectStoreEnvironmentBean.objectStoreDir`][objectstoreenvir] system property
to the full path of a writeable directory:

```shell [Terminal]
java -DObjectStoreEnvironmentBean.objectStoreDir=/var/tmp # ...
```

See [Specifying the object store location][specifying-the-o] for more
information.

#### Configuring the Default Transaction Manager Timeout

To configure Narayana’s [default transaction manager timeout][default-transact],
set the `com.arjuna.ats.arjuna.coordinator.defaultTimeout` system property to an
integral value in seconds:

```shell [Terminal]
java -Dcom.arjuna.ats.arjuna.coordinator.defaultTimeout=60 # ...
```

For more on configuring Narayana, see [Setting Properties][setting-properti] in
the Naryana documentation.

### Usage

To use Helidon MP’s Jakarta Transactions integration, annotate a method with the
[`jakarta.transaction.Transactional`][jakarta-transact-3] annotation:

<!--@mdc ::code-callout -->
```java
@Transactional // <1>
public void setGreeting(Integer id) {
    // Do something transactional.
    greetingProvider.setMessage("Hello[" + id + "]"); // <2>
}
```
1. The [`@Transactional` annotation][jakarta-transact-3] indicates that this
   method should be invoked in the scope of a JTA transaction. **The object on
   which the method is invoked must be one that Helidon MP’s CDI container has
   created**, i.e. it must be managed. ([CDI beans are
   managed][cdi-beans-are-ma], as are [Jakarta RESTful Web Services resource
   classes][jakarta-restful].)
2. For [`@Transactional`][jakarta-transact-3] to have any effect, whatever is
   used inside the method must be JTA-aware (such as a [Jakarta
   Persistence][jakarta-persiste] object like a managed
   [`EntityManager`][entitymanager]).
<!--@mdc :: -->

## Jakarta Persistence (JPA)

### Overview

Helidon MP’s [Jakarta Persistence][jakarta-persiste] integration allows you to
interact with Jakarta Persistence (JPA) objects as if your code were running in
an application server, handling automatic creation and management of objects
such as `EntityManager` and `EntityManagerFactory` instances.

More pragmatically, it allows you to inject managed
[`EntityManager`][entitymanager] instances using the
[`@PersistenceContext`][persistenceconte] annotation.

Jakarta Persistence is a Jakarta EE specification that describes, among other
things, how its implementations:

1.  Map Java objects to relational database tables
2.  Manage such persistent Java objects
3.  Interact with [Jakarta Transactions][jakarta-transact]
4.  Interact with [named data sources](#named-data-source-integration)

Jakarta Persistence may be used in an entirely application-managed manner, which
requires no integration at all. This application-managed mode places the burden
of error handling, thread safety, transaction management, and other concerns on
the user. **This documentation does *not* cover application-managed mode JPA.**

Jakarta Persistence may also (preferably) be used in a fully container-managed
manner, which requires that a container, like Helidon MP, handle error
management, thread safety and transaction management on behalf of the user.
**This documentation covers this container-managed mode of JPA exclusively.**

Helidon MP’s Jakarta Persistence integration comes with support for two JPA
implementations, known as *JPA providers*:

1.  [Hibernate ORM][hibernate-orm]
2.  [EclipseLink][eclipselink]

In any given project, you use one or the other, but not both.

How you set up Helidon MP’s Jakarta Persistence integration differs depending on
which of these JPA providers you choose to use.

Jakarta Persistence requires [Jakarta Transactions][jakarta-transact] and makes
use of [named data sources](#named-data-source-integration), so as you set up
your project you will need to understand:

- [Helidon MP’s named data source integration][helidon-mp-s-nam]
- [Helidon MP’s Jakarta Transactions integration][jakarta-transact]

### Project Setup

#### Setting Up a JPA Provider

##### Overview

While the Jakarta Persistence specification standardizes many aspects around
programming and usage, it deliberately leaves many required setup and
configuration aspects up to the JPA provider. You will need to set up your
project differently depending on which JPA provider you choose.

To set up Helidon MP’s Jakarta Persistence integration in your application to
work with your chosen JPA provider, you must:

1.  [Set up and configure named data sources as appropriate][helidon-mp-s-nam]
2.  [Set up and configure Helidon MP’s Jakarta Transactions
    support][jakarta-transact]
3.  Include the proper Jakarta Persistence-related dependencies
4.  Set up your project to generate and compile the [static
    metamodel][static-metamodel]
5.  Set up your project for *static weaving*

Details and examples for each supported JPA provider are below.

##### Maven Coordinates (Common)

To include the Jakarta Persistence APIs that you will need and to include the
core of Helidon’s Jakarta Persistence integration:

- [Ensure your dependencies are managed](../managing-dependencies.md)
- [Ensure you have set up and configured named data sources as
  appropriate][helidon-mp-s-nam]
- [Ensure you have set up and configured Helidon MP’s Jakarta Transactions
  support][jakarta-transact]
- Ensure the following `<dependency>` elements are present as child elements of
  your project’s `pom.xml` file’s `<dependencies>` element:

  <!--@mdc ::code-callout -->
  ```xml [pom.xml]
  <dependencies>
    <dependency>
      <groupId>jakarta.persistence</groupId>
      <artifactId>jakarta.persistence-api</artifactId>
      <scope>provided</scope> <!-- (1) -->
    </dependency>
    <dependency>
      <groupId>io.helidon.integrations.cdi</groupId>
      <artifactId>helidon-integrations-cdi-jpa</artifactId>
      <scope>runtime</scope> <!-- (2) -->
    </dependency>
  </dependencies>
  ```
  1. The `scope` is `provided`, which ensures that the [JPA classes required for
    compilation][jpa-classes-requ] are available at compile time.
  2. The `scope` is `runtime`, which ensures that Helidon’s core,
    provider-independent Jakarta Persistence integration is available at
    runtime.
  <!--@mdc :: -->

These `<dependency>` elements do not set up a JPA provider. See details below
for the JPA provider you have chosen to use.

##### Setting Up Static Metamodel Generation

To generate and compile the Jakarta Persistence static metamodel for your
application, regardless of whether you are using Hibernate ORM or EclipseLink,
[ensure your dependencies are managed](../managing-dependencies.md), and then
make sure the `<plugin>` element in the following code snippet is present as a
child element of the `<pluginManagement><plugins>` element sequence as shown
below:

<!--@mdc ::code-callout -->
```xml [pom.xml]
<pluginManagement>
    <plugins>

        <!-- ... -->

        <plugin>
            <artifactId>maven-compiler-plugin</artifactId>
            <executions>
                <execution>
                    <id>default-compile</id>
                    <configuration>
                        <annotationProcessorPaths>
                            <annotationProcessorPath>
                                <groupId>org.hibernate.orm</groupId>
                                <artifactId>hibernate-jpamodelgen</artifactId> <!-- (1) -->
                                <version>${version.lib.hibernate}</version> <!-- (2) -->
                            </annotationProcessorPath>
                        </annotationProcessorPaths>
                    </configuration>
                </execution>
            </executions>
        </plugin>

        <!-- ... -->

    </plugins>
</pluginManagement>
```
1. This adds the `hibernate-jpamodelgen` jar, which contains a [Java annotation
   processor that generates the static metamodel source code][java-annotation],
   to the Java compiler’s annotation processor path so that it is active at
   compile time.
2. Because your [dependencies are managed](../managing-dependencies.md), this
   will resolve to the currently supported version of Hibernate ORM.
<!--@mdc :: -->

For more on the Hibernate ORM `hibernate-jpamodelgen` annotation processor, see
[Hibernate Metamodel Generator][hibernate-metamo] in Hibernate ORM’s
documentation.

> [!NOTE]
> Many parts of Hibernate ORM’s documentation of this feature are outdated.

##### Maven Coordinates (Hibernate ORM)

To include Helidon’s Jakarta Persistence-related integration for Hibernate ORM:

- [Ensure your dependencies are managed](../managing-dependencies.md)
- [Ensure the basics of your JPA project are set up properly][ensure-the-basic]
- Ensure the following `<dependency>` elements are present as child elements of
  your project’s `pom.xml` file’s `<dependencies>` element:

  ```xml [pom.xml]
  <dependency>
    <groupId>io.helidon.integrations.cdi</groupId>
    <artifactId>helidon-integrations-cdi-hibernate</artifactId>
    <scope>runtime</scope>
  </dependency>
  ```

  - The `scope` is `runtime`, which ensures that Helidon MP’s Hibernate ORM
    integration is available at runtime.

##### Setting Up Static Weaving (Hibernate ORM)

Hibernate ORM can alter your classes' bytecode at build time to keep track of
changes made to objects participating in Jakarta Persistence workflows.

To set up this required static weaving for Hibernate ORM, ensure that the
following `<plugin>` element is present as a child element of your project’s
`pom.xml` file’s `<plugins>` element:

```xml [pom.xml]
<plugin>
  <groupId>org.hibernate.orm.tooling</groupId>
  <artifactId>hibernate-enhance-maven-plugin</artifactId>
  <!--
    Ideally, your plugin versions are managed via a
    <pluginManagement> element, which is why the <version> element
    is commented out below.  If, nevertheless, you opt for the
    explicit version, check
    https://search.maven.org/artifact/org.hibernate.orm/hibernate-enhance-maven-plugin
    for up-to-date versions, and make sure the version is the same
    as that of Hibernate ORM itself.
  -->
  <!-- <version>6.3.1.Final</version> -->
  <executions>
    <execution>
      <id>Statically enhance JPA entities for Hibernate</id>
      <phase>compile</phase>
      <goals>
        <goal>enhance</goal>
      </goals>
      <configuration>
        <failOnError>true</failOnError>
        <enableDirtyTracking>true</enableDirtyTracking>
        <enableLazyInitialization>true</enableLazyInitialization>
      </configuration>
    </execution>
  </executions>
</plugin>
```

For more on the `hibernate-enhance-maven-plugin` in particular, see [its
documentation][its-documentatio].

For more on Hibernate ORM’s bytecode enhancement (weaving) in general, see
[Bytecode Enhancement][bytecode-enhance] in Hibernate ORM’s documentation.

For more on bytecode enhancement properties, see [Bytecode Enhancement
Properties][bytecode-enhance-2] in Hibernate ORM’s documentation.

##### Maven Coordinates (EclipseLink)

To include Helidon’s Jakarta Persistence-related integration for EclipseLink:

- [Ensure your dependencies are managed](../managing-dependencies.md)
- [Ensure the basics of your JPA project are set up properly][ensure-the-basic]
- Ensure the following `<dependency>` elements are present as child elements of
  your project’s `pom.xml` file’s `<dependencies>` element:

  <!--@mdc ::code-callout -->
  ```xml [pom.xml]
  <dependency>
    <groupId>io.helidon.integrations.cdi</groupId>
    <artifactId>helidon-integrations-cdi-eclipselink</artifactId>
    <scope>runtime</scope> <!-- (1) -->
  </dependency>
  ```
  1. The `scope` is `runtime`, which ensures that Helidon MP’s EclipseLink
    integration is available at runtime.
  <!--@mdc :: -->

##### Setting Up Static Weaving (EclipseLink)

EclipseLink can alter your classes' bytecode at build time to keep track of
changes made to objects participating in Jakarta Persistence workflows.

To set up this required static weaving for EclipseLink, ensure that the
following `<plugin>` element is present as a child element of your project’s
`pom.xml` file’s `<plugins>` element:

<!--@mdc ::code-callout -->
```xml [pom.xml]
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.1.0</version> <!-- (1) -->
    <executions>
        <execution>
            <id>weave</id>
            <phase>process-classes</phase>
            <goals>
                <goal>java</goal>
            </goals>
            <configuration combine.self="override">
                <classpathScope>compile</classpathScope>
                <mainClass>org.eclipse.persistence.tools.weaving.jpa.StaticWeave</mainClass>
                <arguments>
                    <argument>-loglevel</argument>
                    <argument>INFO</argument>
                    <argument>-persistenceinfo</argument>
                    <argument>${project.build.outputDirectory}</argument>
                    <argument>${project.build.outputDirectory}</argument>
                    <argument>${project.build.outputDirectory}</argument>
                </arguments>
            </configuration>
        </execution>
    </executions>
</plugin>
```
1. Always check [Maven Central][maven-central] for up-to-date versions.
<!--@mdc :: -->

For more on the EclipseLink static weaving command-line utility, see [Static
Weaving][static-weaving] in the EclipseLink documentation.

### Configuration

To configure Helidon MP’s Jakarta Persistence integration, you author a
[`META-INF/persistence.xml` file][meta-inf-persist]. It contains a mix of
standardized elements and JPA provider-specific properties.

If you are writing a component or a library, then you place this in your Maven
project’s `src/test/resources` directory (because a library or component is not
itself an application, and by definition can be included in many applications,
so it is inappropriate to put application-level configuration in your
component). If you are working on a project that contains the `main` method (or
similar) that starts your application, then and only then do you place a
`META-INF/persistence.xml` in your persistence-oriented Maven project’s
`src/main/resources` directory.

For details about the structure and syntax of the `META-INF/persistence.xml`
file, see [persistence.xml file][meta-inf-persist] in the Jakarta Persistence
specification.

#### Use Only One `META-INF/persistence.xml` Per Application

Like any configuration, a `META-INF/persistence.xml` file is normally an
*application-level* concern, not a *component-level* concern. In other words,
your Java application, made up of various components, or libraries, some of
which you may have written, and many of which you have not, should normally have
exactly one `META-INF/persistence.xml` on its classpath, describing the
persistence-related aspects of the application in its particular environment.
There are very few use cases where multiple `META-INF/persistence.xml` classpath
resources are called for.

A common mistake is to write a component or library—by definition intended for
use in possibly more than one application—and include a
`src/main/resources/META-INF/persistence.xml` in its Maven project. If two
components or libraries containing `META-INF/persistence.xml` classpath
resources like this are deployed as part of an application, it can make for a
confusing state of affairs at application runtime, and may lead to exceptions
indicating more *persistence units* are present than are expected.

Most library projects that work with JPA artifacts should probably have a
`src/test/resources/META-INF/persistence.xml` in their Maven projects instead.
This allows you to test your JPA-centric work against a test configuration,
rather than a "main" or production one, which is almost certainly what you want
in nearly all cases.

#### Persistence Units

Fundamentally, a `META-INF/persistence.xml` file contains a collection of
*persistence units*. A persistence unit represents a collection of entities in a
relational database loosely coupled to a [named data
source](#named-data-source-integration) that knows how to connect to it.

Your `META-INF/persistence.xml` file must begin (and end) with the following
XML:

<!--@mdc ::code-callout -->
```xml [META-INF/persistence.xml]
<persistence xmlns="https://jakarta.ee/xml/ns/persistence"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="https://jakarta.ee/xml/ns/persistence
                                 https://jakarta.ee/xml/ns/persistence/persistence_3_1.xsd"
             version="3.1"> <!-- (1) -->

    <!-- (2) -->
</persistence>
```
1. Helidon MP’s Jakarta Persistence integration supports [Jakarta Persistence
   version 3.1][jakarta-persiste].
2. `<persistence-unit>` elements are listed here.
<!--@mdc :: -->

#### Persistence Unit

You list your application’s persistence units as `<persistence-unit>` child
elements of the enclosing `<persistence>` element. Each `<persistence-unit>`
element identifies a named persistence unit that will correspond to an
`EntityManager` in your code, and represents a collection of entities in a
relational database.

##### Example: Persistence Unit Skeleton

Here is a partial example of a persistence unit named `test` with a helpful
description:

<!--@mdc ::code-callout -->
```xml [META-INF/persistence.xml]
<!-- ... -->

<persistence-unit name="test" transaction-type="JTA"> <!-- (1) -->
    <description>A testing database</description>

    <!-- (2) -->

</persistence-unit>

<!-- ... -->
```
1. Because Helidon MP’s JPA integration is for container-managed JPA, the
   [`transaction-type` attribute][transaction-type] must in practice always be
   set to `JTA`.
2. The order of subsequent child elements is significant and governed by the [XML
   schema][xml-schema].
<!--@mdc :: -->

> [!NOTE]
> In most microservices, there will be only one persistence unit.

> [!TIP]
> A `<persistence-unit>` is represented in Jakarta Persistence as an instance of
> the [`PersistenceUnitInfo`][persistenceuniti] class.

##### JTA Data Source

A persistence unit is always associated with exactly one [named data
source](#named-data-source-integration).

Because Helidon MP’s Jakarta Persistence integration provides support for
container-managed JPA, and because container-managed JPA requires Jakarta
Transactions (JTA), the kind of named data source a persistence unit is
associated with is always a [JTA][jakarta-transact] data source. The
`<jta-data-source>` element, a child of the `<persistence-unit>` element, is how
you link a persistence unit to a [named data
source](#named-data-source-integration) you previously
[configured](#configuration).

###### Example: Persistence Unit with JTA Data Source

Here is a partial example of a persistence unit named `test`, with a helpful
description, linked with a JTA data source named `main`:

<!--@mdc ::code-callout -->
```xml [META-INF/persistence.xml]
<!-- ... -->

<persistence-unit name="test" transaction-type="JTA">
    <description>A testing database</description>
    <jta-data-source>main</jta-data-source> <!-- (1) -->

    <!-- (2) -->

</persistence-unit>

<!-- ... -->
```
1. This links this persistence unit to a [data
   source](#named-data-source-integration) named `main`, whose [connectivity
   information](#configuration) can be found in a MicroProfile-Config-compatible
   location, as detailed in the [data source configuration](#configuration)
   section above.
2. Other persistence unit characteristics go here.
<!--@mdc :: -->

##### Classes

A persistence unit lists the classes that should be managed and that will take
part in Jakarta Persistence workflows. You must list:

1.  [Entity classes][entity-classes]
2.  [Embeddable classes][embeddable-class]
3.  [Mapped superclasses][mapped-superclas]
4.  [Converter classes][converter-classe]

You use a [sequence of `<class>` elements][sequence-of-clas] to do this. Each
`<class>` element contains the fully-qualified class name of one of the types of
managed classes listed above.

> [!NOTE]
> There are [other mechanisms that can be used in a `META-INF/persistence.xml`
> file to describe managed classes][other-mechanisms], but they may or may not
> be honored by a given JPA provider.

###### Example: Persistence Unit with Class Elements

Here is a partial example of a persistence unit named `test`, with a helpful
description, linked with a JTA data source named `main`, containing two entity
classes:

<!--@mdc ::code-callout -->
```xml [META-INF/persistence.xml]
<!-- ... -->

<persistence-unit name="test" transaction-type="JTA">
    <description>A testing database</description>
    <jta-data-source>main</jta-data-source>
    <class>com.example.ExampleEntity0</class> <!-- (1) -->
    <class>com.example.ExampleEntity1</class>

    <!-- (2) -->

</persistence-unit>

<!-- ... -->
```
1. Each entity class is listed with a separate `<class>` element, and there is no
   containing `<classes>` element or similar.
2. Other persistence unit characteristics go here.
<!--@mdc :: -->

##### Properties

Persistence units can have simple properties attached to them to further
configure the backing JPA provider. You use the [`<properties>`
element][properties-eleme] to specify them.

> [!NOTE]
> Helidon MP’s Jakarta Persistence integration is for container-managed JPA, so
> the vendor-independent properties [described in the
> specification][properties-eleme] directly concerned with database connectivity
> information, such as `jakarta.persistence.jdbc.url`, **do not apply** and will
> be ignored if present. See [the JTA Data Source](#jta-data-source) section
> above for how a persistence unit is linked to a [named data
> source](#named-data-source-integration).

###### Example: Persistence Unit with Properties

Here is a partial exmaple of a persistence unit named `test`, with a helpful
description, linked with a JTA data source named `sample`, containing two entity
classes, configuring a Hibernate ORM-specific property:

<!--@mdc ::code-callout -->
```xml [META-INF/persistence.xml]
<!-- ... -->

<persistence-unit name="test" transaction-type="JTA">
    <description>A testing database</description>
    <jta-data-source>sample</jta-data-source> <!-- (1) -->
    <class>com.example.ExampleEntity0</class>
    <class>com.example.ExampleEntity1</class>
    <properties>
        <property name="hibernate.show_sql" value="true"/> <!-- (2) -->
        <property name="eclipselink.weaving" value="false"/> <!-- (3) -->
    </properties>
</persistence-unit>

<!-- ... -->
```
1. The name identifies a name present in the [*datasourcename* portion of a named
   datasource configuration][datasourcename-p]. There is no need for any kind of
   reserved prefix (like `java:comp/env`).
2. This is a Hibernate ORM-specific property and will be properly ignored if the
   JPA provider you have [set up](#project-setup-1) is EclipseLink. See
   [Statement logging and statistics][statement-loggin] in the Hibernate ORM
   documentation for more details about the `hibernate.show_sql` property.
3. This is an EclipseLink-specific property (and (a) is required and (b) must be
   set to `false` if you are using EclipseLink), and will be properly ignored if
   the JPA provider you have [set up](#project-setup-1) is Hibernate ORM. See
   [weaving][weaving] in the EclipseLink documentation for more details about the
   `eclipselink.weaving` property.
<!--@mdc :: -->

> [!TIP]
> For an exhaustive list of Hibernate ORM-specific properties, see
> [Configurations][configurations] in the Hibernate ORM documentation.

> [!TIP]
> For an exhaustive list of EclipseLink-specific properties, see [Persistence
> Property Extensions Reference][persistence-prop] in the EclipseLink
> documentation.

### Usage

To use Helidon MP’s Jakarta Persistence integration, once you have [set
up](#project-setup-1) and [configured](#configuration-2) your project, you use
the Jakarta Persistence APIs in almost the same manner as if your project were
deployed to a Jakarta EE application server.

Specifically, you:

1.  Annotate your managed classes (entities, mapped superclasses, etc.)
    appropriately (using [`@Entity`][entity] and similar annotations)
2.  Inject [`EntityManager`][entitymanager] instances appropriately with the
    [`@PersistenceContext` annotation][persistenceconte]
3.  Use an injected [`EntityManager`][entitymanager] to work with your managed
    objects

In addition, you [use Helidon MP’s JTA integration](#usage-1) to declare
transactional boundaries where appropriate.

A full tutorial of Jakarta Persistence is *well* beyond the scope of this
documentation. Consult [the specification][the-specificatio] for details on how
to map your entity classes to relational database tables, and how to perform
other related tasks.

### Examples

- [JPA Pokemons Example][jpa-pokemons-exa]

## References

- [Managing Dependencies in Helidon MP](../managing-dependencies.md)
- [MicroProfile Config in Helidon MP](config/introduction.md)
- [JDBC 4.3 Specification][jdbc-4-3-specifi]
- [HikariCP 5.0.1 documentation][hikaricp-5-0-1-d]
- [Developers Guide For Oracle JDBC 21c on Maven Central][developer-s-guid]
- [Oracle® Universal Connection Pool Developer’s Guide, Release
  21c][oracle-universal]
- [Oracle® Universal Connection Pool Java API Reference, Release
  21c][oracle-universal-2]
- [Oracle® Database JDBC Developer’s Guide and Reference, Release
  21c][oracle-database]
- [Oracle® Database JDBC Java API Reference, Release 21c][oracle-jdbc-driv]
- [H2 Database Engine documentation][h2-jdbc-driver]
- [Jakarta Transactions 2.0 Specification][jakarta-transact-4]
- [Jakarta Transactions 2.0 API Reference][jakarta-transact-5]
- [Narayana Project Documentation][narayana-project]
- [Narayana API Reference][narayana-api-ref]
- [Jakarta Persistence 3.1 Specification][jakarta-persiste-2]
- [Jakarta Persistence 3.1 API Reference][jakarta-persiste-3]
- [Hibernate ORM User Guide][hibernate-orm-us]
- [EclipseLink documentation][eclipselink]

[jakarta-transact]: #jakarta-transactions-jta-integration
[javax-sql-dataso]: https://docs.oracle.com/en/java/javase/21/docs/api/java.sql/javax/sql/DataSource.html
[jakarta-inject-n]: https://jakarta.ee/specifications/dependency-injection/2.0/apidocs/jakarta/inject/named
[java-sql-connect]: https://docs.oracle.com/en/java/javase/21/docs/api/java.sql/java/sql/Connection.html
[acquired]: <https://docs.oracle.com/en/java/javase/21/docs/api/java.sql/javax/sql/DataSource.html#getConnection()>
[hikaricp]: https://github.com/brettwooldridge/HikariCP
[oracle-universal]: https://docs.oracle.com/en/database/oracle/oracle-database/21/jjucp/index.html
[jdbc-4-3-specifi]: https://docs.oracle.com/en/java/javase/21/docs/api/java.sql/java/sql/package-summary.html
[h2-jdbc-driver]: https://www.h2database.com/html/main.html
[connect-to-an-h2]: https://www.h2database.com/html/features.html#database_url
[oracle-jdbc-driv]: https://docs.oracle.com/en/database/oracle/oracle-database/21/jajdb/index.html
[connect-to-an-or]: https://docs.oracle.com/en/database/oracle/oracle-database/21/jjdbc/data-sources-and-URLs.html#GUID-EF07727C-50AB-4DCE-8EDC-57F0927FF61A
[developer-s-guid]: https://www.oracle.com/database/technologies/maven-central-guide.html
[microprofile-con]: https://download.eclipse.org/microprofile/microprofile-config-3.1/apidocs/org/eclipse/microprofile/config/spi/ConfigSource.html#default_config_sources
[env-shell-comman]: https://www.gnu.org/software/coreutils/manual/html_node/env-invocation.html
[set-up-the-oracl]: #setting-up-the-oracle-universal-connection-pool
[connect-to-an-or-2]: https://docs.oracle.com/en/database/oracle/oracle-database/21/jjdbc/data-sources-and-URLs.html#GUID-C4F2CA86-0F68-400C-95DA-30171C9FB8F0
[pooldatasourceim]: https://docs.oracle.com/en/database/oracle/oracle-database/21/jjuar/oracle/ucp/jdbc/PoolDataSourceImpl.html#setConnectionFactoryClassName(java.lang.String)
[the-javadoc-for]: https://docs.oracle.com/en/database/oracle/oracle-database/21/jjuar/oracle/ucp/jdbc/PoolDataSourceImpl.html
[oracle-jdbc-pool]: https://docs.oracle.com/en/database/oracle/oracle-database/21/jajdb/oracle/jdbc/pool/OracleDataSource.html
[unlike-hikaricp]: https://github.com/brettwooldridge/HikariCP/blob/HikariCP-5.0.1/src/main/java/com/zaxxer/hikari/util/PropertyElf.java#L46-L53
[user-is-a-proper]: https://docs.oracle.com/en/database/oracle/oracle-database/21/jajdb/oracle/jdbc/datasource/impl/OracleDataSource.html#setUser(java.lang.String)
[set-the-given-pr]: https://docs.oracle.com/en/database/oracle/oracle-database/21/jjuar/oracle/ucp/jdbc/PoolDataSource.html#setUser(java.lang.String)
[set-up-the-hikar]: #setting-up-the-hikaricp-connection-pool
[hikaricp-s-confi]: https://github.com/brettwooldridge/HikariCP#essentials
[propertyelf-java]: https://github.com/brettwooldridge/HikariCP/blob/HikariCP-5.0.1/src/main/java/com/zaxxer/hikari/util/PropertyElf.java#L47
[on-its-github-re]: https://github.com/brettwooldridge/HikariCP#gear-configuration-knobs-baby
[org-h2-jdbcx-jdb]: https://www.h2database.com/javadoc/org/h2/jdbcx/JdbcDataSource.html
[java-class-repre]: https://github.com/helidon-io/helidon/wiki/FAQ#how-do-i-make-a-class-a-cdi-bean
[inject]: https://jakarta.ee/specifications/dependency-injection/2.0/apidocs/jakarta/inject/inject
[jakarta-dependen]: https://jakarta.ee/specifications/dependency-injection/2.0/jakarta-injection-spec-2.0.html
[jakarta-transact-2]: https://jakarta.ee/specifications/transactions/2.0/
[jakarta-transact-3]: https://jakarta.ee/specifications/transactions/2.0/apidocs/jakarta/transaction/transactional
[objectstoreenvir]: https://www.narayana.io/docs/api/com/arjuna/ats/arjuna/common/ObjectStoreEnvironmentBean.html#setObjectStoreType-java.lang.String-
[specifying-the-o]: https://www.narayana.io/docs/project/index.html#d0e4013
[default-transact]: https://www.narayana.io/docs/api/com/arjuna/ats/arjuna/common/CoordinatorEnvironmentBean.html#setDefaultTimeout-int-
[setting-properti]: https://www.narayana.io/docs/project/index.html#chap-JBossJTA_Installation_Guide-Test_Chapter
[cdi-beans-are-ma]: https://jakarta.ee/specifications/cdi/4.0/jakarta-cdi-spec-4.0.html#implementation
[jakarta-restful]: https://jakarta.ee/specifications/restful-ws/3.1/jakarta-restful-ws-spec-3.1.html#resource-classes
[jakarta-persiste]: https://jakarta.ee/specifications/persistence/3.1/
[entitymanager]: https://jakarta.ee/specifications/persistence/3.1/apidocs/jakarta.persistence/jakarta/persistence/entitymanager
[persistenceconte]: https://jakarta.ee/specifications/persistence/3.1/apidocs/jakarta.persistence/jakarta/persistence/persistencecontext
[hibernate-orm]: https://hibernate.org/orm/documentation/6.1
[eclipselink]: https://www.eclipse.org/eclipselink/documentation/
[static-metamodel]: https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html#a6933
[jpa-classes-requ]: https://jakarta.ee/specifications/persistence/3.1/apidocs/jakarta.persistence/jakarta/persistence/package-summary.html
[java-annotation]: https://docs.jboss.org/hibernate/orm/6.1/javadocs/org/hibernate/jpamodelgen/JPAMetaModelEntityProcessor.html
[hibernate-metamo]: https://hibernate.org/orm/processor/
[its-documentatio]: https://docs.jboss.org/hibernate/orm/6.1/userguide/html_single/Hibernate_User_Guide.html#tooling-maven
[bytecode-enhance]: https://docs.jboss.org/hibernate/orm/6.1/userguide/html_single/Hibernate_User_Guide.html#BytecodeEnhancement
[bytecode-enhance-2]: https://docs.jboss.org/hibernate/orm/6.1/userguide/html_single/Hibernate_User_Guide.html#configurations-bytecode-enhancement
[maven-central]: https://search.maven.org/artifact/org.codehaus.mojo/exec-maven-plugin
[static-weaving]: https://wiki.eclipse.org/EclipseLink/UserGuide/JPA/Advanced_JPA_Development/Performance/Weaving/Static_Weaving#Use_the_Command_Line
[meta-inf-persist]: https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html#persistence-xml-file
[transaction-type]: https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html#a12296
[xml-schema]: https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html#persistence-xml-schema
[persistenceuniti]: https://jakarta.ee/specifications/persistence/3.1/apidocs/jakarta.persistence/jakarta/persistence/spi/persistenceunitinfo
[entity-classes]: https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html#a18
[embeddable-class]: https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html#a487
[mapped-superclas]: https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html#mapped-superclasses
[converter-classe]: https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html#a2999
[sequence-of-clas]: https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html#list-of-managed-classes
[other-mechanisms]: https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html#a12305
[properties-eleme]: https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html#a12384
[statement-loggin]: https://docs.jboss.org/hibernate/orm/6.1/userguide/html_single/Hibernate_User_Guide.html#configurations-logging
[weaving]: https://www.eclipse.org/eclipselink/documentation/4.0.2/jpa/extensions/persistenceproperties_ref.htm#weaving
[configurations]: https://docs.jboss.org/hibernate/orm/6.1/userguide/html_single/Hibernate_User_Guide.html#configurations
[persistence-prop]: https://www.eclipse.org/eclipselink/documentation/4.0.2/jpa/extensions/persistenceproperties_ref.htm#sthref733
[entity]: https://jakarta.ee/specifications/persistence/3.1/apidocs/jakarta.persistence/jakarta/persistence/entity
[the-specificatio]: https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html#entities
[jpa-pokemons-exa]: https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/integrations/cdi/jpa/oracle
[hikaricp-5-0-1-d]: https://github.com/brettwooldridge/HikariCP/blob/HikariCP-5.0.1/README.md
[oracle-universal-2]: https://docs.oracle.com/en/database/oracle/oracle-database/21/jjuar/index.html#
[oracle-database]: https://docs.oracle.com/en/database/oracle/oracle-database/21/jjdbc/index.html
[jakarta-transact-4]: https://jakarta.ee/specifications/transactions/2.0/jakarta-transactions-spec-2.0.html
[jakarta-transact-5]: https://jakarta.ee/specifications/transactions/2.0/apidocs/
[narayana-project]: https://www.narayana.io/docs/project/index.html
[narayana-api-ref]: https://www.narayana.io/docs/api/index.html
[jakarta-persiste-2]: https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html
[jakarta-persiste-3]: https://jakarta.ee/specifications/persistence/3.1/apidocs/
[hibernate-orm-us]: https://docs.jboss.org/hibernate/orm/6.1/userguide/html_single/Hibernate_User_Guide.html
[helidon-mp-s-mic]: config/introduction.md
[common-microprof]: config/advanced-configuration.md
[helidon-mp-s-nam]: #named-data-source-integration
[ensure-the-basic]: #maven-coordinates-common
[datasourcename-p]: #configuration-prefixes
