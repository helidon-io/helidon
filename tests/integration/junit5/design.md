# jUnit 5 Test Suite Design

## Suite

Test suite represents set of jUnit test classes with defined life-cycle.

### Suite life-cycle

Suite life-cycle consists of three stages:
- suite setup
- tests execution
- suite cleanup

Setup phase is responsible for testing environment initialization. This means
especially
- test clients (e.g. JDBC, DbClient) initialization and configuration
  to be used with running contaienrs
- test containers (e.g. databases) startup

Cleanup phase si responsible for freeing all resources acquired by testing
environment. All testing clients must be closed and running containers must
be stopped and deleted.

Suite API is defined by `@Suite` annotation and `SuiteProvider` SPI interface.
Annotation defines `SuiteProvider` implementing class.

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith(SuiteJuit5Extension.class)
@Inherited
public @interface Suite {

  Class<? extends SuiteProvider> value();

}
```

`SuiteProvider` implementation class used as `@Suite` annotation should
be annotated with all required suite providers annotations. Those providers
will be applied on all test classes annotated with this `@Suite` annotation
with the same provider argument.
The only method of `SuiteProvider`
is `void suiteContext(SuiteContext suiteContext)` to retrieve shared suite
context. But it has default implementation, so it's not mandatory.

Other optional methods are providers configuration hooks. Those methods
are annotated with corresponding setup annotation, e.g. `@SetUpContainer`.

```java
@SetUpContainer
void setupContainer(ContainerConfig.Builder builder) {
    // any builder calls
}
```

### Suite test classes grouping

One suite is defined by the value of the `@Suite` annotation `provider`.
This value serves as suite unique ID. All test classes annotated
with the same `provider` form such a suite.
Each of the suites with unique `provider` has its own life-cycle and share
the same set of <i>suite providers</i>.

## Suite with resources

Testing environment may consist of several resources. Some of those resources
were already mentioned
- testing clients (HTTP client, DbClient)
- Docker containers, for example databases

Each resource requires specific handling of its life-cycle. Suite resource
providers represent tools to achieve this goal.

Suite resource is defined by <b>annotation</b> and <b>suite resource
provider</b>.

### Test configuration provider

Simple resource provider that reads Config from the file specified
in annotation. Annotation defines `ConfigProvider` implementing class
and source configuration file.

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface TestConfig {

  Class<? extends ConfigProvider> provider() default DefaultConfigProvider.class;

  String file() default "test.yaml";

}
```

`ConfigProvider` interface handles Config instance source, life-cycle and accessor. 

```java
/**
 * Helidon integration tests configuration provider.
 */
public interface ConfigProvider extends SuiteExtensionProvider {

  /**
   * Config file name from {@link io.helidon.tests.integration.junit5.TestConfig}
   * annotation.
   *
   * @param file config file name to read from classpath
   */
  void file(String file);

  /**
   * Build configuration builder with default initial values.
   */
  void setup();

  /**
   * Provide config builder to be used in setup hook.
   *
   * @return configuration builder with values from provided file.
   */
  Config.Builder builder();

  /**
   * Start the existence of Config.
   */
  void start();

  /**
   * Provide root {@link Config} instance for the tests.
   */
  Config config();

}
```

### Test container provider

Test container provider handles life-cycle of Docker container. This provider
is responsible for
- container startup
- providing configuration for related client (e.g. database URL for DbClient)
- container cleanup

Container provider API is defined by `@TestContainer` annotation
and `ContainerProvider` SPI interface. Annotation defines `ContainerProvider`
implementing class and optional Docker image name of the container.

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface ContainerTest {

  Class<? extends ContainerProvider> provider();

  String image() default "";

}
```

`ContainerProvider` interface handles container source image, life-cycle and builder accessor.

```java
/**
 * Helidon Database Client integration tests Docker container provider interface.
 */
public interface ContainerProvider extends SuiteExtensionProvider {

  /**
   * Docker image from {@link io.helidon.tests.integration.junit5.ContainerTest} annotation.
   * This method is called during {@link ContainerProvider} initialization phase.
   * Implementing class must store this value and handle it properly.
   *
   * @param image name of the Docker image including label or {@link Optional#empty()} when not defined
   */
  void image(Optional<String> image);

  /**
   * Build docker container configuration.
   * Default container configuration must be set in this method.
   *
   * @return docker container configuration builder with default configuration set
   */
  void setup();

  /**
   * Docker container configuration builder with default configuration set.
   * This is the {@link ContainerConfig.Builder} instance passed
   * to {@link io.helidon.tests.integration.junit5.SetUpContainer} annotated method
   * in related {@link SuiteProvider} implementing class.
   *
   * @return container configuration builder with default configuration
   */
  ContainerConfig.Builder builder();

  /**
   * Start Docker container.
   * Calling this method may change provided value of Docker container configuration.
   */
  void start();

  /**
   * Stop Docker container.
   */
  void stop();

}
```

### Test DbClient provider

This provider adds DbClient life-cycle handling. User code is responsible
for handling container port mapping manually. It must be done
in `@SetUpDbClient` annotated method.

DbClient provider API is defined by `@DbClientTest` annotation
and `DbClientProvider` SPI interface. Annotation defines `DbClientProvider`
implementing class.

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface DbClientTest {

  Class<? extends DbClientProvider> provider() default DefaultDbClientProvider.class;

}
```

`DbClientProvider` interface handles DbClient life-cycle, builder and instance accessor.

```java
/**
 * Helidon Database Client integration tests configuration provider interface.
 */
public interface DbClientProvider extends SuiteExtensionProvider {

  /**
   * Build configuration builder with default initial values.
   */
  void setup();

  /**
   * Provide config builder to be used in setup hook.
   *
   * @return configuration builder with values from provided file.
   */
  DbClient.Builder builder();

  /**
   * Start the existence of {@link DbClient}.
   */
  void start();

  /**
   * Provide root {@link DbClient} instance for the tests.
   */
  DbClient dbClient();

}
```

### Resources initialization order

Resources dependency:
 * Config has no dependency
 * Docker container depends on Config
 * DbClient depends on both Config and Docker container

This requires execution of all resources life-phases to be synchronized and in defined order: 
 
1. providers initialization phase
   - `ConfigProvider.suiteContext`, `ContainerProvider.suiteContext` and `DbClientProvider.suiteContext`
   - `ConfigProvider.file`
   - `ContainerProvider.image`

2. configuration initialization phase
   - `ConfigProvider.setup`
   - `ConfigProvider.builder` and suite `@SetUpConfig` annotated method
   - `ConfigProvider.start` creates and stores config

3. container configuration and startup
   - `ContainerProvider.setup`
   - `ContainerProvider.builder` and suite `@SetUpContainer` annotated method
   - `ContainerProvider.start` stores container info

4. `DbClient` configuration and startup
   - `DbClientProvider.setup`
   - `DbClientProvider.builder` and suite `@SetUpDbClient` annotated method
                                (responsible for config update)
   - `DbClientProvider.start`

5. tests execution

6. suite Cleanup
   - `DbClientProvider.stop` and `ContainerProvider.stop`

### Annotated test classes

Test class is annotated with `@Suite` annotation only. Provider `MySQLSuite` contains
both `@TestContainer` and `@DbClientTest` annotations.

```java
@Suite(MySQLSuite.class)
public class SimpleDmlIT extends io.helidon.tests.integration.dbclient.common.tests.SimpleDmlIT {

    public SimpleDmlIT(DbClient dbClient, Config config) {
        super(dbClient, config);
    }

}
```
