# Helidon jUnit 5 Test Suite

Helidon jUnit 5 Test Suite provides environment for tests execution with
external resources.

It supports following resources:
 * Config from file
 * Database container
   - MySQL
   - PosgreSQL
 * DbClient

Test suite represents set of jUnit testing classes with resources that are
available during the run of the whole suite. Suite provides its own
life-cycle handling and resource management.

## Suite Definition

Suite is Java implementation of `SuiteProvider` SPI interface. Suite has
no mandatory API, so it serves only to group jUnit testing classes by default.

Suite supports two method annotations to handle its life-cycle:
 * `@BeforeSuite` to initialize suite resources before tests will be executed
 * `@AfterSuite` to clean up suite resources after tests were executed

### Suite Attribute Resolver

Suite class may optionally implement `SuiteResolver` interface to grant testing
classes access to its internal attributes. This interface is similar to jUnit
`ParameterResolver`.

### Suite Resource Provider

Resource provider adds specific resource to the suite. Resources may be
of the following types:
 * Config
 * Docker container
 * DbClient

Each provider is defined by its specific annotation and corresponding
SPI provider interface. 

#### Helidon Config Provider

Adds Config support to the Suite class using `@TestConfig` annotation.
Provider has default SPI provider implementation so only config file has to be
specified in most of the cases.

`@TestConfig` annotation arguments:
 * `file` - configuration file, default value is `"test.yaml"`
 * `provider` - Config support provider, default value is existing
                `DefaultConfigProvider.class`

Config support adds `@SetUpConfig` method annotation to customize stored
Config node. This method has access to the `Config.Builder` of this config node
during it's initialization phase.

```java
    @SetUpConfig
    public void setupConfig(Config.Builder builder) {
        builder. ...
    }
```

#### Helidon Container Provider

Adds Docker container support to the Suite class using `@ContainerTest`
annotation. Provider is defined by `@ContainerTest` annotation and
`ContainerProvider` interface implementation.

`@ContainerTest` annotation arguments:
 * `image` - docker image to run, default value is `""`, default value shall
             be handled by corresponding provider implementation
 * `provider` - specific Docker container support provider, currently there
              are providers available for MySQL and PostgreSQL databases:
   - `MySqlContainer.class` - for MySQL database, default image
                            is `mysql:latest`
   - `PgSqlContainer.class` - for PostgreSQL database, default image
                            is `postgres:latest`

Docker container support adds `@SetUpContainer` method annotation to customize
container setup. This method has access to the `ContainerConfig.Builder`
of this container during it's initialization phase.
Database container setup also have access to `DatabaseContainerConfig.Builder`.
Content of this method depends on specific provider implementation.

```java
    @SetUpContainer
    public void setupContainer(Config config, DatabaseContainerConfig.Builder dbConfigBuilder, ContainerConfig.Builder builder) {
        dbConfigBuilder. ...
        builder. ...
    }
```

#### Helidon DbClient Provider

Adds DbClient support to the suite class using `@DbClientTest` annotation.
Provider has default SPI provider implementation so there is nothing to be
specified with the annotation.

`@DbClientTest` annotation arguments:
 * `provider` - DbClient support provider, default value is existing
              `DefaultDbClientProvider.class`

Docker container support adds `@SetUpDbClient` method annotation to customize
DbClient setup. This method has access to the `DbClient.Builder`
of this DbClient during it's initialization phase.
This method is executed after container startup so there is running container
information available, including container port mapping.

```java
    @SetUpDbClient
    public void setupDbClient(Config config, ContainerInfo info, DbClient.Builder builder) {
        builder. ...
    }
```

### Suite Context

Suite context interface `SuiteContext` may be passed to `@BeforeSuite`,
`@AfterSuite`, any `@SetUp...` annotated method and is also present in suite
resolver so any testing class constructor or test method may use it too. 

`SuiteContext` contains suite-wide storage space based on jUnit
`ExtensionContext.Store`.

## Suite Example

### Suite class

```java
@TestConfig
@DbClientTest
@ContainerTest(provider = MySqlContainer.class, image = "mysql:8")
public class MySqlSuite implements SuiteProvider {

    @SetUpConfig
    public void setupConfig(Config.Builder builder) {
        // Any config customization
    }

    @SetUpContainer
    public void setupContainer(Config config, DatabaseContainerConfig.Builder dbConfigBuilder) {
        // Example: setup database container using DbClient config node
        config.get(DB_NODE + ".connection").asNode().ifPresent(dbConfigBuilder::dbClient);
    }

    @SetUpDbClient
    public void setupDbClient(Config config, ConfigUpdate update, ContainerInfo info, DbClient.Builder builder) {
        // Merges ContainerInfo into DbClient config
        Map<String, String> updatedNodes = new HashMap<>(1);
        config.get(DB_NODE + ".connection.url").as(String.class).ifPresent(value -> updatedNodes.put(
                DB_NODE + ".connection.url",
                DatabaseContainer.replacePortInUrl(
                        value,
                        info.portMappings().get(info.config().exposedPorts()[0]))));
        Config containerConfig = Config.create(ConfigSources.create(updatedNodes), ConfigSources.create(config));
        // This replaces config stored by @TestConfig provider
        update.config(containerConfig);
        // Setup DbClient using updated config
        builder.config(containerConfig.get(DB_NODE));
    }

    @BeforeSuite
    public void beforeSuite(DbClient dbClient, Config config) {
        // Example: database initialization
        InitUtils.waitForStart(
                () -> DriverManager.getConnection(config.get("db.connection.url").asString().get(),
                                                  config.get("db.connection.username").asString().get(),
                                                  config.get("db.connection.password").asString().get()),
                60,
                1);
        InitUtils.initSchema(dbClient);
        InitUtils.initData(dbClient);
    }

    @AfterSuite
    public void afterSuite(DbClient dbClient) {
        // Example: database cleanup
        InitUtils.dropSchema(dbClient);
    }
}
```

### jUnit test class

```java
@Suite(MySqlSuite.class)
class MyTest {

    @Test
    void testSomething() {
        // testing code
    }

}
```

### Maven surefire-plugin config

Maven surefire or failsafe plugins must have extensions detection enabled

```xml
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <systemPropertyVariables>
                        <junit.jupiter.extensions.autodetection.enabled>true</junit.jupiter.extensions.autodetection.enabled>
                    </systemPropertyVariables>
                </configuration>
            </plugin>
```
