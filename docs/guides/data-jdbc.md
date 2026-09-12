<!--@frontmatter
description: "Build an application with Helidon Data JDBC"
navigation:
  icon: i-lucide-database
-->
# Helidon Data JDBC

> [!NOTE]
> Helidon Data JDBC is an incubating feature. It is not production ready. Its
> APIs and behavior may change incompatibly or be removed in any release.

This guide shows you how to build a small application with Helidon Data JDBC.
The application reads contacts from an H2 database through a repository that
Helidon generates at compile time. You will define the SQL statement, configure
the JDBC client, build the application, and verify the result.

Helidon Data JDBC executes SQL statements defined by your application using the
JDBC API. It manages connections, statements, result sets, mapping, and cleanup
for each operation.

## What You Need

For this 15 minute tutorial, you need the following tools:

_Prerequisite product versions for Helidon 27.0.0-SNAPSHOT_:

| Requirement | Description |
|-------------|-------------|
| [Java 26][java-26] ([OpenJDK 26][open-jdk-26]) | Helidon requires Java 26 or newer. |
| [Maven 3.8+][maven-3-8] | Helidon requires Maven 3.8 or newer. |

Verify the installed versions:

```shell [Terminal]
java -version
mvn --version
```

## What You Will Build

The application contains:

- An H2 database with two contact records
- A JDBC client configured through `application.yaml`
- A Java record that represents one result row
- A repository interface with one SQL query
- A main class that obtains the generated repository and prints the contacts

The tutorial uses an H2 database that runs in the application process. You do
not need to install a database server.

## Create a Helidon Project

Generate a Helidon SE project with the Maven archetype:

```shell [Terminal]
mvn -U archetype:generate -DinteractiveMode=false \
    -DarchetypeGroupId=io.helidon.archetypes \
    -DarchetypeArtifactId=helidon-quickstart-se \
    -DarchetypeVersion=27.0.0-SNAPSHOT \
    -DgroupId=io.helidon.examples \
    -DartifactId=helidon-data-jdbc-quickstart \
    -Dpackage=io.helidon.examples.data.jdbc
```

Change to the generated project directory:

```shell [Terminal]
cd helidon-data-jdbc-quickstart
```

The generated project contains an HTTP example that this tutorial replaces.
Delete `src/main/java/io/helidon/examples/data/jdbc/GreetService.java` and the
generated files under `src/test`. Those tests exercise the original HTTP
endpoint and do not apply to this application.

## Add the Dependencies

The quickstart already contains the `helidon-config-yaml` dependency. Keep that
dependency so Helidon can read `application.yaml`. Add the following
dependencies to the `dependencies` section of `pom.xml`:

<!--@mdc ::code-callout -->
```xml [pom.xml]
<dependencies>
    <!-- Other application dependencies -->
    <dependency>
        <groupId>io.helidon.data</groupId>
        <artifactId>helidon-data</artifactId> <!-- (1) -->
    </dependency>
    <dependency>
        <groupId>io.helidon.data.jdbc</groupId>
        <artifactId>helidon-data-jdbc</artifactId> <!-- (2) -->
    </dependency>
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId> <!-- (3) -->
        <scope>runtime</scope>
    </dependency>
</dependencies>
```
1. Provides the Helidon Data annotations and repository API.
2. Provides the JDBC annotations, client API, and runtime implementation.
3. Provides the JDBC driver and database used by this tutorial.
<!--@mdc :: -->

## Configure Annotation Processing

Helidon generates the repository implementation while Maven compiles the
application. Add the following plugin to the `plugins` section of `pom.xml`.
Its annotation processor path contains the general Helidon processor and the
JDBC repository processor:

```xml [pom.xml]
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <compilerArgs combine.children="append">
            <arg>-Ahelidon.api.incubating=warn</arg>
        </compilerArgs>
        <annotationProcessorPaths>
            <path>
                <groupId>io.helidon.bundles</groupId>
                <artifactId>helidon-bundles-apt</artifactId>
                <version>${helidon.version}</version>
            </path>
            <path>
                <groupId>io.helidon.data.jdbc</groupId>
                <artifactId>helidon-data-jdbc-codegen</artifactId>
                <version>${helidon.version}</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

The Helidon processor discovers repository interfaces. The JDBC processor
generates their implementations.

The API stability processor rejects Incubating API usage by default. The
`warn` policy explicitly opts this application into Data JDBC while keeping a
visible compilation diagnostic. A project that wants to limit this choice to
the code that uses Data JDBC can keep the default compiler policy and apply
`@SuppressWarnings(Api.SUPPRESS_INCUBATING)` only to the package, type, or
method that uses Data JDBC. The constant is declared by
`io.helidon.common.Api`.

## Configure the JDBC Client

Replace `src/main/resources/application.yaml` with the following
configuration:

<!--@mdc ::code-callout -->
```yaml [application.yaml]
data:
  clients:
    jdbc: # <1>
      - name: "@default" # <2>
        connection:
          url: "jdbc:h2:mem:contacts;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM 'classpath:schema.sql'" # <3>
          jdbc-driver-class-name: "org.h2.Driver"
```
1. `data.clients.jdbc` contains a list of JDBC client configurations.
2. `@default` is the client used when a repository does not select another
   client.
3. H2 creates an in memory database and loads the tutorial data from
   `schema.sql`.
<!--@mdc :: -->

Keep the `-` before `name`, even when the application has only one JDBC client.
The value of `data.clients.jdbc` must always be a list.

The `INIT` option belongs to the H2 JDBC driver. It is convenient for this
tutorial, but it is not a schema management feature of Helidon Data JDBC. Use
your normal database deployment or migration process in an application.

## Create the Database Schema

Create `src/main/resources/schema.sql` with the following content:

```sql [schema.sql]
CREATE TABLE CONTACT (
    ID BIGINT PRIMARY KEY,
    NAME VARCHAR(100) NOT NULL
);

INSERT INTO CONTACT (ID, NAME) VALUES (1, 'Ada Lovelace');
INSERT INTO CONTACT (ID, NAME) VALUES (2, 'Grace Hopper');
```

This file contains several SQL statements because it is a database setup
script run by H2. Do not pass the file or its complete contents to
`@Jdbc.Statement` or `JdbcClient.create`. Each Data JDBC operation supports one
SQL statement.

## Define the Result Type

Create
`src/main/java/io/helidon/examples/data/jdbc/Contact.java`:

```java [Contact.java]
package io.helidon.examples.data.jdbc;

public record Contact(long id, String name) {
}
```

Helidon maps the `id` and `name` column labels to record components without
regard to letter case or column order.

## Define the Repository

Create
`src/main/java/io/helidon/examples/data/jdbc/ContactRepository.java`:

```java [ContactRepository.java]
package io.helidon.examples.data.jdbc;

import java.util.List;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;

@Data.Repository
@Data.Provider("jdbc")
public interface ContactRepository
        extends Data.GenericRepository<Contact, Long> {

    @Jdbc.Statement("SELECT ID AS id, NAME AS name FROM CONTACT ORDER BY ID")
    List<Contact> findAll();
}
```

`@Data.Repository` asks Helidon to generate an implementation.
`@Data.Provider("jdbc")` selects JDBC when the build contains more than one
Helidon Data generator. `Data.GenericRepository<Contact, Long>` identifies the
entity and identifier types. It does not add repository operations or generate
SQL.

Every abstract repository method must have one `@Jdbc.Statement`. Its value
must contain exactly one SQL statement. Batch execution, stored procedures,
callable statements, and SQL scripts are not supported.

## Run the Repository

Replace the generated `Main.java` with the following class:

```java [Main.java]
package io.helidon.examples.data.jdbc;

import io.helidon.service.registry.Services;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        ContactRepository repository = Services.get(ContactRepository.class);

        repository.findAll()
                .forEach(contact -> System.out.println(contact.id() + ": " + contact.name()));
    }
}
```

`Services.get` obtains the repository implementation generated during
compilation. The repository receives the configured `@default` JDBC client from
the Service Registry.

## Build and Run the Application

Build the project:

```shell [Terminal]
mvn package
```

Run the application:

```shell [Terminal]
java -jar target/helidon-data-jdbc-quickstart.jar
```

The application prints:

```text
1: Ada Lovelace
2: Grace Hopper
```

You have now configured a JDBC client, generated a repository implementation,
executed SQL using the JDBC API, mapped the rows to Java records, and received
fully materialized results.

## Where to Go Next

The following documentation describes the complete API and behavior:

- [Helidon Data with JDBC](../modules/data-jdbc/README.md)
- [Declarative JDBC Repositories](../modules/data-jdbc/declarative.md)
- [JDBC Client](../modules/data-jdbc/imperative.md)

The Helidon examples repository contains complete applications for several
databases:

- [Declarative Data JDBC examples][declarative-examples]
- [Imperative Data JDBC examples][imperative-examples]

The database directories include their JDBC drivers, configuration, schema,
application code, tests, and instructions for running each application.

## Summary

In this guide, you:

- Added the Data JDBC runtime and code generator to a Helidon application
- Configured a JDBC client as a list entry
- Defined a record and a repository with one SQL statement
- Built and ran the generated repository
- Read fully materialized rows from an H2 database

[java-26]: https://www.oracle.com/java/technologies/downloads/
[open-jdk-26]: https://jdk.java.net/26/
[maven-3-8]: https://maven.apache.org/download.cgi
[declarative-examples]: https://github.com/helidon-io/helidon-examples/tree/helidon-27.x/examples/declarative/data-jdbc
[imperative-examples]: https://github.com/helidon-io/helidon-examples/tree/helidon-27.x/examples/imperative/data-jdbc
