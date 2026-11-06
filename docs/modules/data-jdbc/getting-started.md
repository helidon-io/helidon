<!--@frontmatter
description: "Run a first query with declarative repositories or an imperative JDBC client"
-->
# Getting Started

Run either example against a MySQL database containing one contact. Both examples print
`Helidon`. The declarative path generates a repository during compilation; the imperative
path builds a standalone client in application code.

> [!NOTE]
> Helidon Data JDBC is incubating. Its APIs and behavior may change incompatibly
> or be removed in any release.

After setup, choose [Declarative First Query](#declarative-first-query) or
[Imperative First Query](#imperative-first-query).

## Prerequisites

Use JDK 27 and Maven. These examples use Helidon `28.0.0-SNAPSHOT`; make the matching
Helidon artifacts available from your source build or snapshot repository before
building the example. Use the same Helidon version for runtime dependencies and
annotation processors.

You also need a MySQL server and its command-line client. Use an existing local server
or start a disposable server with Docker:

```shell [Terminal]
docker run --name jdbc-docs-mysql -e MYSQL_ROOT_PASSWORD=example-root -e MYSQL_DATABASE=contacts -e MYSQL_USER=contacts -e MYSQL_PASSWORD=contacts-pass -p 3306:3306 -d mysql:8.4
```

Wait until the server accepts connections before preparing the schema. If port 3306 is
already in use, use your existing server or choose another host port and adjust the
commands and JDBC URLs below.

## Prepare the Schema

Save this file as `schema.sql`:

```sql [schema.sql]
CREATE TABLE CONTACT (
    ID BIGINT AUTO_INCREMENT PRIMARY KEY,
    NAME VARCHAR(100) NOT NULL,
    STATUS VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    EMAIL VARCHAR(255),
    OWNER_ID BIGINT,
    DELEGATE_ID BIGINT
);
INSERT INTO CONTACT (ID, NAME, STATUS) VALUES (100, 'Helidon', 'ACTIVE');
```

Run it once against the `contacts` database:

```shell [Terminal]
mysql -h 127.0.0.1 -P 3306 -u contacts -p contacts < schema.sql
```

Enter `contacts-pass` for the disposable server. Schema setup is separate from Data JDBC
operations. Use migration tooling for application schema changes.

## Dependencies

Create a directory for the example and save this `pom.xml` there. This POM supports both
paths below. The imperative path does not require annotation processing; remove
`annotationProcessorPaths` when using that path alone.

```xml [pom.xml]
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>example</groupId>
  <artifactId>jdbc-first-query</artifactId>
  <version>1.0-SNAPSHOT</version>
  <properties>
    <helidon.version>28.0.0-SNAPSHOT</helidon.version>
    <maven.compiler.release>27</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>io.helidon</groupId>
        <artifactId>helidon-dependencies</artifactId>
        <version>${helidon.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.helidon.data.jdbc</groupId>
      <artifactId>helidon-data-jdbc</artifactId>
    </dependency>
    <dependency>
      <groupId>io.helidon.config</groupId>
      <artifactId>helidon-config-yaml</artifactId>
    </dependency>
    <dependency>
      <groupId>com.mysql</groupId>
      <artifactId>mysql-connector-j</artifactId>
      <version>26.7.0</version>
      <scope>runtime</scope>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.16.0</version>
        <configuration>
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
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>3.6.3</version>
      </plugin>
    </plugins>
  </build>
</project>
```

The Helidon BOM manages Helidon dependency versions. The driver and build plugins have
explicit versions because this project does not inherit a Helidon application parent.
For other options, see [Dependency Management](../../dependency-management.md).

## Declarative First Query

Save this configuration as `src/main/resources/application.yaml`:

```yaml [application.yaml]
data:
  clients:
    jdbc:
      - name: "contacts"
        connection:
          url: "jdbc:mysql://127.0.0.1:3306/contacts"
          username: "contacts"
          password: "contacts-pass"
```

Save the repository as `src/main/java/example/ContactRepository.java`:

```java [ContactRepository.java]
package example;

import java.util.Optional;

import io.helidon.common.Api;
import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;

@SuppressWarnings(Api.SUPPRESS_INCUBATING)
@Data.Repository
@Data.Provider("jdbc")
@Jdbc.Client("contacts")
public interface ContactRepository {
    @Jdbc.Statement("SELECT NAME FROM CONTACT WHERE ID = :id")
    Optional<String> findName(long id);
}
```

The focused suppression acknowledges the incubating API. Compilation generates the
repository implementation and Service Registry metadata. Save the caller as
`src/main/java/example/DeclarativeMain.java`:

```java [DeclarativeMain.java]
package example;

import io.helidon.service.registry.ServiceRegistryManager;

public final class DeclarativeMain {
    public static void main(String[] args) {
        ServiceRegistryManager manager = ServiceRegistryManager.start();
        try {
            ContactRepository repository = manager.registry().get(ContactRepository.class);
            System.out.println(repository.findName(100).orElseThrow());
        } finally {
            manager.shutdown();
        }
    }
}
```

Build and run from the directory containing `pom.xml`:

```shell [Terminal]
mvn compile exec:exec -Dexec.executable=java -Dexec.args="-classpath %classpath example.DeclarativeMain"
```

The application prints `Helidon`, in addition to any build and runtime logging. Continue
with [Declarative Repositories](declarative.md) for queries and updates.

## Imperative First Query

Use the same schema and POM. This example supplies its connection settings in code and
does not need `application.yaml` or a Service Registry. Save it as
`src/main/java/example/ImperativeMain.java`:

```java [ImperativeMain.java]
package example;

import io.helidon.common.Api;
import io.helidon.data.jdbc.JdbcClient;

@SuppressWarnings(Api.SUPPRESS_INCUBATING)
public final class ImperativeMain {
    public static void main(String[] args) {
        JdbcClient client = JdbcClient.builder()
                .connection(connection -> connection
                        .url("jdbc:mysql://127.0.0.1:3306/contacts")
                        .username("contacts")
                        .password("contacts-pass"))
                .build();
        String name = client.create("SELECT NAME FROM CONTACT WHERE ID = ?")
                .bind(1, 100L)
                .map(String.class)
                .one();
        System.out.println(name);
    }
}
```

Build and run:

```shell [Terminal]
mvn compile exec:exec -Dexec.executable=java -Dexec.args="-classpath %classpath example.ImperativeMain"
```

The application prints `Helidon`, in addition to any build and runtime logging. Helidon
closes the operation's connection, statement, and result set before `one()` returns.
This standalone client does not join local JDBC transactions. Continue with [Imperative
JDBC Client](imperative.md).

When finished with the disposable database, remove it with `docker rm -f
jdbc-docs-mysql`.
