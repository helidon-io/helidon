# Testing with JUnit5

## Overview

Helidon provides a JUnit5 extension that integrates CDI to support testing with
Helidon MP.

The test class is added as a CDI bean to support injection and the CDI container
is started lazily during test execution.

## Maven Coordinates

To enable Helidon MicroProfile Testing JUnit5, add the following dependency to
your project’s `pom.xml` (see [Managing
Dependencies](../../dependency-management.md)).

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.microprofile.testing</groupId>
  <artifactId>helidon-microprofile-testing-junit5</artifactId>
  <scope>test</scope>
</dependency>
```

## Usage

Basic usage:

<!--@mdc ::code-callout -->
```java
@HelidonTest // <1>
class MyTest {
}
```
1. Enable the test class
<!--@mdc :: -->

> [!NOTE]
> By default, a MicroProfile Config profile named "test" is defined.
>
> It can be changed via:
>
> - `@AddConfig(key = "mp.config.profile", value = "otherProfile")`
>
> - `@Configuration(profile = "otherProfile")`
>
> - Using `mp.config.profile` property and `@Config(useExisting = true)`

### CDI Container Setup

By default, CDI discovery is enabled:

- CDI beans and extensions in the classpath are added automatically
- If disabled, the CDI beans and extensions must be added manually

> [!NOTE]
> Customization of the CDI container on a test method changes the CDI container
> affinity.
>
> I.e. The test method will use a dedicated CDI container.

> [!NOTE]
> It is not recommended to provide a `beans.xml` along the test classes, as it
> would combine beans from all tests.
>
> Instead, you should use [`@AddBean`][addbean] to specify the beans per test or
> method.

CDI discovery can be disabled using [`@DisableDiscovery`][disablediscovery].

Disable discovery:

<!--@mdc ::code-callout -->
```java
@DisableDiscovery // <1>
@AddBean(MyBean.class) // <2>
@HelidonTest
class MyTest {
}
```
1. Disable CDI discovery
2. Add a bean class
<!--@mdc :: -->

When disabling discovery, it can be difficult to identify the CDI extensions
needed to activate the desired features.

JAXRS (Jersey) support can be added easily using [`@AddJaxRs`][addjaxrs].

Add JAX-RS (Jersey):

<!--@mdc ::code-callout -->
```java
@DisableDiscovery
@AddJaxRs // <1>
@AddBean(MyResource.class) // <2>
@HelidonTest
class MyTest {
}
```
1. Add JAX-RS (Jersey) support
2. Add a resource class to the CDI container
<!--@mdc :: -->

Note the following Helidon CDI extensions:

| Extension                                | Note                                      |
|------------------------------------------|-------------------------------------------|
| [`ConfigCdiExtension`][configcdiextensi] | Add MicroProfile Config injection support |
| [`ServerCdiExtension`][servercdiextensi] | Optional if using [`@AddJaxRs`][addjaxrs] |
| [`JaxRsCdiExtension`][jaxrscdiextensio]  | Optional if using [`@AddJaxRs`][addjaxrs] |

### CDI Container Afinity

By default, one CDI container is created per test class and is shared by all
test methods.

However, test methods can also require a dedicated CDI container:

- By forcing a reset of the CDI container between methods
- By customizing the CDI container per test method

Reset the CDI container between methods:

<!--@mdc ::code-callout -->
```java
@HelidonTest(resetPerTest = true)
class MyTest {

    @Test
    void testOne() { // <1>
    }

    @Test
    void testTwo() { // <2>
    }
}
```
1. `testOne` executes in a dedicated CDI container
2. `testTwo` also executes in a dedicated CDI container
<!--@mdc :: -->

<!--@mdc ::code-callout -->
```java
@HelidonTest
class MyTest {

    @Test
    void testOne() { // <1>
    }

    @Test
    @DisableDiscovery
    @AddBean(MyBean.class)
    void testTwo() { // <2>
    }
}
```
1. `testOne` executes in the shared CDI container
2. `testTwo` executes in a dedicated CDI container
<!--@mdc :: -->

### Configuration

The test configuration can be set up in two exclusive ways:

- Using the "synthetic" configuration expressed with annotations (default)
- Using the "existing" configuration of the current environment

Use [`@Configuration`][configuration] to switch to the "existing" configuration.

Switch to the existing configuration:

```java
@Configuration(useExisting = true)
@HelidonTest
class MyTest {
}
```

> [!NOTE]
> Customization of the test configuration on a test method changes the CDI
> container affinity.
>
> I.e. The test method will use a dedicated CDI container.

#### Synthetic Configuration

The "synthetic" configuration can be expressed using the following annotations:

| Type                                  | Usage                      |
|---------------------------------------|----------------------------|
| [`@AddConfig`][addconfig]             | Key value pair             |
| [`@AddConfigBlock`][addconfigblock]   | Formatted text block       |
| [`@AddConfigSource`][addconfigsource] | Programmatic config source |
| [`@Configuration`][configuration]     | Classpath resources using  |

Add a key value pair:

```java
@AddConfig(key = "foo", value = "bar")
@HelidonTest
class MyTest {
}
```

Add a properties text block:

```java
@AddConfigBlock("""
    foo=bar
    bob=alice
    """)
@HelidonTest
class MyTest {
}
```

Add a YAML text block:

```java
@AddConfigBlock(
    type = "yaml",
    value = """
        my-test:
          foo: bar
          bob: alice
        """)
@HelidonTest
class MyTest {
}
```

Add config programmatically:

```java
@HelidonTest
class MyTest {

    @AddConfigSource
    static ConfigSource config() {
        return MpConfigSources.create(Map.of(
                "foo", "bar",
                "bob", "alice"));
    }
}
```

Add classpath resources:

```java
@Configuration(
    configSources = {
        "my-test1.yaml",
        "my-test2.yaml"
})
@HelidonTest
class MyTest {
}
```

#### Configuration Ordering

The ordering of the test configuration can be controlled using the mechanism
defined by the [MicroProfile Config specification][microprofile-con].

Add a properties text block with ordinal:

```java
@AddConfigBlock(
    value = """
        config_ordinal=120
        foo=bar
        """)
@HelidonTest
class MyTest {
}
```

The default ordering is the following

| Annotation                            | Ordinal |
|---------------------------------------|---------|
| [`@AddConfig`][addconfig]             | 1000    |
| [`@AddConfigBlock`][addconfigblock]   | 900     |
| [`@AddConfigSource`][addconfigsource] | 800     |
| [`@Configuration`][configuration]     | 700     |

### Injectable Types

Helidon provides injection support for types that reflect the current server.
E.g. JAXRS client.

Here are all the built-in types that can be injected:

| Type                     | Usage                                              |
|--------------------------|----------------------------------------------------|
| [`WebTarget`][webtarget] | A JAX-RS client configured for the current server. |
| `URI`                    | A URI representing the current server              |
| `String`                 | A raw URI representing the current server          |

> [!NOTE]
> Types that reflect the current server require
> [`ServerCdiExtension`][servercdiextensi]

Inject a JAX-RS client for the default socket:

```java
@HelidonTest
class MyTest {

    @Inject
    WebTarget target;
}
```

Use [`@Socket`][socket] to specify the socket for the clients and URIs.

Inject a JAX-RS client for the admin socket:

```java
@HelidonTest
class MyTest {

    @Inject
    @Socket("admin")
    WebTarget target;
}
```

> [!NOTE]
> Except [`WebTarget`][webtarget], all types require the [`@Socket`][socket]
> annotation

Inject a URI for the default socket:

```java
@HelidonTest
class MyTest {

    @Inject
    @Socket("@default")
    URI uri;
}
```

> [!NOTE]
> All the injectable types are also available as method parameters.

Get a JAX-RS client for the default socket:

```java
@HelidonTest
class MyTest {

    @Test
    void testOne(WebTarget target) {
    }
}
```

Get a URI for the default socket:

```java
@HelidonTest
class MyTest {

    @Test
    void testOne(@Socket("@default") URI uri) {
    }
}
```

The current CDI [`container`][container] is also available as a method
parameter.

Get the current CDI container:

```java
@HelidonTest
class MyTest {

    @Test
    void testOne(SeContainer container) {
    }
}
```

> [!NOTE]
> You can also use CDI qualifier annotations to resolve a method parameter using
> CDI.

Resolve a CDI bean:

```java
@HelidonTest
class MyTest {

    @Test
    void testOne(@Default MyBean myBean) {
    }
}
```

### Test Instance Lifecyle

The CDI scope used by the test instance follows the lifecyle defined by JUnit5.
The default is `PER_CLASS` and is enforced by [`@HelidonTest`][helidontest].

I.e. By default, the test instance is re-used between test methods.

> [!NOTE]
> The test instance is not re-used between CDI container, using a dedicated CDI
> container implies a new test instance

Using per method lifecycle:

```java
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@HelidonTest
class MyTest {
}
```

### Using meta-annotations

Meta-annotations are supported on both test classes and test methods and can be
used as a composition mechanism.

Class-level meta-annotation example:

```java
@HelidonTest
@AddBean(FirstBean.class)
@AddBean(SecondBean.class)
@DisableDiscovery
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CustomMetaAnnotation {
}

@CustomMetaAnnotation
class AnnotationOnClass {
}
```

Method-level meta-annotation example:

```java
@Test
@AddBean(FirstBean.class)
@AddBean(SecondBean.class)
@DisableDiscovery
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MyTestMethod {
}

@HelidonTest
class AnnotationOnMethod {

    @MyTestMethod
    void testOne() {
    }

    @MyTestMethod
    void testTwo() {
    }
}
```

## API

Here is a brief overview of the MicroProfile testing annotations:

<!--@mdc ::table-collapse -->
| Annotation                              | Usage                                                                                                |
|-----------------------------------------|------------------------------------------------------------------------------------------------------|
| [`@AddBean`][addbean]                   | Add a CDI bean class to the CDI container                                                            |
| [`@AddExtension`][addextension]         | Add a CDI extension to the CDI container                                                             |
| [`@DisableDiscovery`][disablediscovery] | Disable automated discovery of beans and extensions                                                  |
| [`@AddJaxRs`][addjaxrs]                 | Shorthand to add JAX-RS (Jersey) support                                                             |
| [`@AddConfig`][addconfig]               | Define a key value pair in the "synthetic" configuration                                             |
| [`@AddConfigBlock`][addconfigblock]     | Define a formatted text block in the "synthetic" configuration                                       |
| [`@AddConfigSource`][addconfigsource]   | Add a programmatic config source to the "synthetic" configuration                                    |
| [`@Configuration`][configuration]       | Switch between "synthetic" and "existing" ; Add classpath resources to the "synthetic" configuration |
| [`@Socket`][socket]                     | CDI qualifier to inject a JAX-RS client or URI for a named socket                                    |
| [`@AfterStop`][afterstop]               | Mark a static method to be executed after the container is stopped                                   |
<!--@mdc :: -->

## Examples

### Config Injection

The following example demonstrates how to enable the use of
[`@ConfigProperty`][configproperty] without CDI discovery.

Config Injection Example:

<!--@mdc ::code-callout{collapsed} -->
```java
@HelidonTest
@DisableDiscovery // <1>
@AddBean(MyBean.class) // <2>
@AddExtension(ConfigCdiExtension.class) // <3>
@AddConfig(key = "app.greeting", value = "TestHello") // <4>
class MyTest {
    @Inject
    MyBean myBean;

    @Test
    void testGreeting() {
        assertThat(myBean, notNullValue());
        assertThat(myBean.greeting(), is("TestHello"));
    }
}

@ApplicationScoped
class MyBean {

    @ConfigProperty(name = "app.greeting") // <5>
    String greeting;

    String greeting() {
        return greeting;
    }
}
```
1. CDI discovery is disabled
2. Add `MyBean` to the CDI container
3. Add [`ConfigCdiExtension`][configcdiextensi] to the CDI container
4. Define test configuration
5. Inject the configuration
<!--@mdc :: -->

### Request Scope

The following example demonstrates how to use [`@RequestScoped`][requestscoped]
with JAXRS without CDI discovery.

Request Scope Example:

<!--@mdc ::code-callout -->
```java
@HelidonTest
@DisableDiscovery // <1>
@AddJaxRs // <2>
@AddBean(MyResource.class) // <3>
class MyTest {

    @Inject
    WebTarget target;

    @Test
    void testGet() {
        String greeting = target.path("/greeting")
                .request().get(String.class);
        assertThat(greeting, is("Hallo!"));
    }
}

@Path("/greeting")
@RequestScoped
class MyResource {
    @GET
    Response get() {
        return Response.ok("Hallo!").build();
    }
}
```
1. CDI discovery is disabled
2. Add JAXRS (Jersey) support
3. Add `MyResource` to the CDI container
<!--@mdc :: -->

## Mock Support

Mocking in Helidon MP is all about replacing CDI beans with instrumented mock
classes.

This can be done using CDI alternatives, however Helidon provides an annotation
to make it easy.

### Maven Coordinates

To enable mock mupport add the following dependency to your project’s pom.xml.

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.microprofile.testing</groupId>
  <artifactId>helidon-microprofile-testing-mocking</artifactId>
  <scope>test</scope>
</dependency>
```

### Usage

Use the [`@MockBean`][mockbean] annotation to inject an instrumented CDI bean in
your test, and customize it in the test method.

#### Example

Mocking using @MockBean:

<!--@mdc ::code-callout{collapsed} -->
```java
@HelidonTest
@AddBean(MyResource.class)
@AddBean(MyService.class)
class MyTest {

    @MockBean(answer = Answers.CALLS_REAL_METHODS) // <1>
    MyService myService;

    @Inject
    WebTarget target;

    @Test
    void testService() {
        Mockito.when(myService.test()).thenReturn("Mocked"); // <2>
        String response = target.path("/test").request().get(String.class);
        assertThat(response, is("Mocked"));
    }
}

@Path("/test")
class MyResource {

    @Inject
    MyService myService;

    @GET
    String test() {
        return myService.test();
    }
}

@ApplicationScoped
class MyService {

    String test() {
        return "Not Mocked";
    }
}
```
1. Instrument `MyService` using `Answers.CALLS_REAL_METHODS`
2. Customize the behavior
<!--@mdc :: -->

### Using CDI Alternative

[`@Alternative`][alternative] can be used to replace a CDI bean with an
instrumented instance.

Mocking using CDI Alternative:

<!--@mdc ::code-callout{collapsed} -->
```java
@HelidonTest
@Priority(1) // <3>
class MyTest {

    @Inject
    WebTarget target;

    MyService myService;

    @BeforeEach
    void initMock() {
        myService = Mockito.mock(MyService.class, Answers.CALLS_REAL_METHODS); // <1>
    }

    @Produces
    @Alternative // <2>
    MyService mockService() {
        return myService;
    }

    @Test
    void testService() {
        Mockito.when(myService.test()).thenReturn("Mocked"); // <4>
        Response response = target.path("/test").request().get();
        assertThat(response, is("Mocked"));
    }
}

@Path("/test")
class MyResource {

    @Inject
    MyService myService;

    @GET
    String test() {
        return myService.test();
    }
}

@ApplicationScoped
class MyService {

    String test() {
        return "Not Mocked";
    }
}
```
1. Create the mock instance in the test class
2. Create a CDI producer method annotated with `@Alternative`
3. Set priority to 1 (required by `@Alternative`)
4. Customize the behavior
<!--@mdc :: -->

## Virtual Threads

Virtual Threads pinning can be detected during tests.

A virtual thread is "pinning" when it blocks its carrier thread in a way that
prevents the virtual thread scheduler from scheduling other virtual threads.

This can happen when blocking in native code, or prior to JDK24 when a blocking
IO operation happens in a synchronized block.

Pinning can in some cases negatively affect application performance.

Enable pinning detection:

```java
@HelidonTest(pinningDetection = true)
class MyTest {
}
```

Pinning is considered harmful when it takes longer than 20 milliseconds, that is
also the default when detecting it within tests.

Pinning threshold can be changed with:

Configure pinning threshold:

<!--@mdc ::code-callout -->
```java
@HelidonTest(pinningDetection = true, pinningThreshold = 50) // <1>
class MyTest {
}
```
1. Change pinning threshold from default(20) to 50 milliseconds.
<!--@mdc :: -->

When pinning is detected, the test fails with a stacktrace pointing at the
culprit.

## Additional Information

- [Official blog article about Helidon and JUnit usage][official-blog-ar]

## Reference

- [JUnit 5 User Guide][junit-5-user-gui]

[addbean]: https://helidon.io/docs/v4/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/AddBean.html
[disablediscovery]: https://helidon.io/docs/v4/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/DisableDiscovery.html
[addjaxrs]: https://helidon.io/docs/v4/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/AddJaxRs.html
[configcdiextensi]: https://helidon.io/docs/v4/apidocs/io.helidon.microprofile.config/io/helidon/microprofile/config/ConfigCdiExtension.html
[servercdiextensi]: https://helidon.io/docs/v4/apidocs/io.helidon.microprofile.server/io/helidon/microprofile/server/ServerCdiExtension.html
[jaxrscdiextensio]: https://helidon.io/docs/v4/apidocs/io.helidon.microprofile.server/io/helidon/microprofile/server/JaxRsCdiExtension.html
[configuration]: https://helidon.io/docs/v4/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/Configuration.html
[addconfig]: https://helidon.io/docs/v4/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/AddConfig.html
[addconfigblock]: https://helidon.io/docs/v4/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/AddConfigBlock.html
[addconfigsource]: https://helidon.io/docs/v4/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/AddConfigSource.html
[microprofile-con]: https://download.eclipse.org/microprofile/microprofile-config-3.1/microprofile-config-spec-3.1.html#_configsource_ordering
[webtarget]: https://jakarta.ee/specifications/restful-ws/3.1/apidocs/jakarta.ws.rs/jakarta/ws/rs/client/webtarget
[socket]: https://helidon.io/docs/v4/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/Socket.html
[container]: https://jakarta.ee/specifications/cdi/4.0/apidocs/jakarta.cdi/jakarta/enterprise/inject/se/SeContainer.html
[helidontest]: https://helidon.io/docs/v4/apidocs/io.helidon.microprofile.testing.junit5/io/helidon/microprofile/testing/junit5/HelidonTest.html
[addextension]: https://helidon.io/docs/v4/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/AddExtension.html
[afterstop]: https://helidon.io/docs/v4/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/AfterStop.html
[configproperty]: https://download.eclipse.org/microprofile/microprofile-config-3.1/apidocs/org/eclipse/microprofile/config/inject/ConfigProperty.html
[requestscoped]: https://jakarta.ee/specifications/cdi/4.0/apidocs/jakarta.cdi/jakarta/enterprise/context/RequestScoped.html
[mockbean]: https://helidon.io/docs/v4/apidocs/io.helidon.microprofile.testing.mocking/io/helidon/microprofile/testing/mocking/MockBean.html
[alternative]: https://jakarta.ee/specifications/cdi/4.0/apidocs/jakarta.cdi/jakarta/enterprise/inject/Alternative.html
[official-blog-ar]: https://medium.com/helidon/testing-helidon-9df2ea14e22
[junit-5-user-gui]: https://junit.org/junit5/docs/current/user-guide/
