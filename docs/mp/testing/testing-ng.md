# Testing with TestNG

## Overview

Helidon provides a TestNG listener that integrates CDI to support testing with Helidon MP.

The test class is added as a CDI bean to support injection and the CDI container is started lazily during test execution.

## Maven Coordinates

To enable Testing with TestNG, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

```xml
<dependency>
    <groupId>io.helidon.microprofile.testing</groupId>
    <artifactId>helidon-microprofile-testing-testng</artifactId>
    <scope>test</scope>
</dependency>
```

## Usage

*Basic usage*

```java
@HelidonTest 
class MyTest {
}
```

- Enable the test class

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
> Customization of the CDI container on a test method changes the CDI container affinity.
>
> I.e. The test method will use a dedicated CDI container.

> [!NOTE]
> It is not recommended to provide a `beans.xml` along the test classes, as it would combine beans from all tests.
>
> Instead, you should use [`@AddBean`](/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/AddBean.html) to specify the beans per test or method.

CDI discovery can be disabled using [`@DisableDiscovery`](/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/DisableDiscovery.html).

*Disable discovery*

```java
@DisableDiscovery 
@AddBean(MyBean.class) 
@HelidonTest
class MyTest {
}
```

- Disable CDI discovery
- Add a bean class

When disabling discovery, it can be difficult to identify the CDI extensions needed to activate the desired features.

JAXRS (Jersey) support can be added easily using [`@AddJaxRs`](/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/AddJaxRs.html).

*Add JAX-RS (Jersey)*

```java
@DisableDiscovery
@AddJaxRs 
@AddBean(MyResource.class) 
@HelidonTest
class MyTest {
}
```

- Add JAX-RS (Jersey) support
- Add a resource class to the CDI container

Note the following Helidon CDI extensions:

| Extension | Note |
|----|----|
| [`ConfigCdiExtension`](/apidocs/io.helidon.microprofile.config/io/helidon/microprofile/config/ConfigCdiExtension.html) | Add MicroProfile Config injection support |
| [`ServerCdiExtension`](/apidocs/io.helidon.microprofile.server/io/helidon/microprofile/server/ServerCdiExtension.html) | Optional if using [`@AddJaxRs`](/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/AddJaxRs.html) |
| [`JaxRsCdiExtension`](/apidocs/io.helidon.microprofile.server/io/helidon/microprofile/server/JaxRsCdiExtension.html) | Optional if using [`@AddJaxRs`](/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/AddJaxRs.html) |

### CDI Container Afinity

By default, one CDI container is created per test class and is shared by all test methods.

However, test methods can also require a dedicated CDI container:

- By forcing a reset of the CDI container between methods
- By customizing the CDI container per test method

*Reset the CDI container between methods*

```java
@HelidonTest(resetPerTest = true)
class MyTest {

    @Test
    void testOne() { 
    }

    @Test
    void testTwo() { 
    }
}
```

- `testOne` executes in a dedicated CDI container
- `testTwo` also executes in a dedicated CDI container

*Customize the CDI container per method*

```java
@HelidonTest
class MyTest {

    @Test
    void testOne() { 
    }

    @Test
    @DisableDiscovery
    @AddBean(MyBean.class)
    void testTwo() { 
    }
}
```

- `testOne` executes in the shared CDI container
- `testTwo` executes in a dedicated CDI container

### Configuration

The test configuration can be set up in two exclusive ways:

- Using the "synthetic" configuration expressed with annotations (default)
- Using the "existing" configuration of the current environment

Use [`@Configuration`](/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/Configuration.html) to switch to the "existing" configuration.

*Switch to the existing configuration*

```java
@Configuration(useExisting = true)
@HelidonTest
class MyTest {
}
```

> [!NOTE]
> Customization of the test configuration on a test method changes the CDI container affinity.
>
> I.e. The test method will use a dedicated CDI container.

#### Synthetic Configuration

The "synthetic" configuration can be expressed using the following annotations:

| Type | Usage |
|----|----|
| [`@AddConfig`](/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/AddConfig.html) | Key value pair |
| [`@AddConfigBlock`](/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/AddConfigBlock.html) | Formatted text block |
| [`@AddConfigSource`](/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/AddConfigSource.html) | Programmatic config source |
| [`@Configuration`](/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/Configuration.html) | Classpath resources using |

*Add a key value pair*

```java
@AddConfig(key = "foo", value = "bar")
@HelidonTest
class MyTest {
}
```

*Add a properties text block*

```java
@AddConfigBlock("""
        foo=bar
        bob=alice
        """)
@HelidonTest
class MyTest {
}
```

*Add a YAML text block*

```java
@AddConfigBlock(type = "yaml", value = """
        my-test:
          foo: bar
          bob: alice
        """)
@HelidonTest
class MyTest {
}
```

*Add config programmatically*

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

*Add classpath resources*

```java
@Configuration(configSources = {
        "my-test1.yaml",
        "my-test2.yaml"
})
@HelidonTest
class MyTest {
}
```

#### Configuration Ordering

The ordering of the test configuration can be controlled using the mechanism defined by the [MicroProfile Config specification](https://download.eclipse.org/microprofile/microprofile-config-3.1/microprofile-config-spec-3.1.html#_configsource_ordering).

*Add a properties text block with ordinal*

```java
@AddConfigBlock(value = """
        config_ordinal=120
        foo=bar
        """)
@HelidonTest
class MyTest {
}
```

The default ordering is the following

| Annotation | Ordinal |
|----|----|
| [`@AddConfig`](/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/AddConfig.html) | 1000 |
| [`@AddConfigBlock`](/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/AddConfigBlock.html) | 900 |
| [`@AddConfigSource`](/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/AddConfigSource.html) | 800 |
| [`@Configuration`](/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/Configuration.html) | 700 |

### Injectable Types

Helidon provides injection support for types that reflect the current server. E.g. JAXRS client.

Here are all the built-in types that can be injected:

| Type | Usage |
|----|----|
| [`WebTarget`](https://jakarta.ee/specifications/restful-ws/3.1/apidocs/jakarta/ws/rs/client/WebTarget.html) | A JAX-RS client configured for the current server. |
| `URI` | A URI representing the current server |
| `String` | A raw URI representing the current server |

> [!NOTE]
> Types that reflect the current server require [`ServerCdiExtension`](/apidocs/io.helidon.microprofile.server/io/helidon/microprofile/server/ServerCdiExtension.html)

*Inject a JAX-RS client for the default socket*

```java
@HelidonTest
class MyTest {

    @Inject
    WebTarget target;
}
```

Use [`@Socket`](/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/Socket.html) to specify the socket for the clients and URIs.

*Inject a JAX-RS client for the admin socket*

```java
@HelidonTest
class MyTest {

    @Inject
    @Socket("admin")
    WebTarget target;
}
```

> [!NOTE]
> Except [`WebTarget`](https://jakarta.ee/specifications/restful-ws/3.1/apidocs/jakarta/ws/rs/client/WebTarget.html), all types require the [`@Socket`](/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/Socket.html) annotation

*Inject a URI for the default socket*

```java
@HelidonTest
class MyTest {

    @Inject
    @Socket("@default")
    URI uri;
}
```

### Test Instance Lifecyle

The test instance lifecycle is a pseudo singleton that follows the lifecycle of the CDI container.

I.e. By default, the test instance is re-used between test methods.

> [!NOTE]
> The test instance is not re-used between CDI container, using a dedicated CDI container implies a new test instance

### Using meta-annotations

Meta-annotations are supported on both test classes and test methods and can be used as a composition mechanism.

*Class-level meta-annotation*

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

*Method-level meta-annotation*

```java
@AddBean(FirstBean.class)
@AddBean(SecondBean.class)
@DisableDiscovery
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MyTestMethod {
}

@HelidonTest
class AnnotationOnMethod {

    @Test 
    @MyTestMethod
    void testOne() {
    }

    @Test 
    @MyTestMethod
    void testTwo() {
    }
}
```

1.  `org.testng.annotations.Test` is not inheritable and should be placed on methods

## API

Here is a brief overview of the MicroProfile testing annotations:

| Annotation | Usage |
|----|----|
| [`@AddBean`](/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/AddBean.html) | Add a CDI bean class to the CDI container |
| [`@AddExtension`](/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/AddExtension.html) | Add a CDI extension to the CDI container |
| [`@DisableDiscovery`](/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/DisableDiscovery.html) | Disable automated discovery of beans and extensions |
| [`@AddJaxRs`](/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/AddJaxRs.html) | Shorthand to add JAX-RS (Jersey) support |
| [`@AddConfig`](/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/AddConfig.html) | Define a key value pair in the "synthetic" configuration |
| [`@AddConfigBlock`](/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/AddConfigBlock.html) | Define a formatted text block in the "synthetic" configuration |
| [`@AddConfigSource`](/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/AddConfigSource.html) | Add a programmatic config source to the "synthetic" configuration |
| [`@Configuration`](/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/Configuration.html) | Switch between "synthetic" and "existing" ; Add classpath resources to the "synthetic" configuration |
| [`@Socket`](/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/Socket.html) | CDI qualifier to inject a JAX-RS client or URI for a named socket |
| [`@AfterStop`](/apidocs/io.helidon.microprofile.testing/io/helidon/microprofile/testing/AfterStop.html) | Mark a static method to be executed after the container is stopped |

## Examples

### Config Injection Example

The following example demonstrates how to enable the use of [`@ConfigProperty`](https://download.eclipse.org/microprofile/microprofile-config-3.1/apidocs/org/eclipse/microprofile/config/inject/ConfigProperty.html) without CDI discovery.

*Config Injection Example*

```java
@HelidonTest
@DisableDiscovery 
@AddBean(MyBean.class) 
@AddExtension(ConfigCdiExtension.class) 
@AddConfig(key = "app.greeting", value = "TestHello") 
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

    @ConfigProperty(name = "app.greeting") 
    String greeting;

    String greeting() {
        return greeting;
    }
}
```

- CDI discovery is disabled
- Add `MyBean` to the CDI container
- Add [`ConfigCdiExtension`](/apidocs/io.helidon.microprofile.config/io/helidon/microprofile/config/ConfigCdiExtension.html) to the CDI container
- Define test configuration
- Inject the configuration

### Request Scope Example

The following example demonstrates how to use [`@RequestScoped`](https://jakarta.ee/specifications/cdi/4.0/apidocs/jakarta.cdi/jakarta/enterprise/context/RequestScoped.html) with JAXRS without CDI discovery.

*Request Scope Example*

```java
@HelidonTest
@DisableDiscovery 
@AddJaxRs 
@AddBean(MyResource.class) 
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

- CDI discovery is disabled
- Add JAXRS (Jersey) support
- Add `MyResource` to the CDI container

## Mock Support

Mocking in Helidon MP is all about replacing CDI beans with instrumented mock classes.

This can be done using CDI alternatives, however Helidon provides an annotation to make it easy.

### Maven Coordinates

To enable mock mupport add the following dependency to your project’s pom.xml.

```xml
<dependency>
    <groupId>io.helidon.microprofile.testing</groupId>
    <artifactId>helidon-microprofile-testing-mocking</artifactId>
    <scope>test</scope>
</dependency>
```

### Usage

Use the [`@MockBean`](/apidocs/io.helidon.microprofile.testing.mocking/io/helidon/microprofile/testing/mocking/MockBean.html) annotation to inject an instrumented CDI bean in your test, and customize it in the test method.

#### Example

*Mocking using `@MockBean`*

```java
@HelidonTest
@AddBean(MyResource.class)
@AddBean(MyService.class)
class MyTest {

    @MockBean(answer = Answers.CALLS_REAL_METHODS) 
    MyService myService;

    @Inject
    WebTarget target;

    @Test
    void testService() {
        Mockito.when(myService.test()).thenReturn("Mocked"); 
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

- Instrument `MyService` using `Answers.CALLS_REAL_METHODS`
- Customize the behavior

### Using CDI Alternative

[`@Alternative`](https://jakarta.ee/specifications/cdi/4.0/apidocs/jakarta.cdi/jakarta/enterprise/inject/Alternative.html) can be used to replace a CDI bean with an instrumented instance.

*Mocking using CDI Alternative*

```java
@HelidonTest
@Priority(1) 
class MyTest {

    @Inject
    WebTarget target;

    MyService myService;

    @BeforeMethod
    void initMock() {
        myService = Mockito.mock(MyService.class, Answers.CALLS_REAL_METHODS); 
    }

    @Produces
    @Alternative 
    MyService mockService() {
        return myService;
    }

    @Test
    void testService() {
        Mockito.when(myService.test()).thenReturn("Mocked"); 
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

- Create the mock instance in the test class
- Create a CDI producer method annotated with `@Alternative`
- Set priority to 1 (required by `@Alternative`)
- Customize the behavior

## Virtual Threads

Virtual Threads pinning can be detected during tests.

A virtual thread is "pinning" when it blocks its carrier thread in a way that prevents the virtual thread scheduler from scheduling other virtual threads.

This can happen when blocking in native code, or prior to JDK24 when a blocking IO operation happens in a synchronized block.

Pinning can in some cases negatively affect application performance.

*Enable pinning detection*

```java
@HelidonTest(pinningDetection = true)
class MyTest {
}
```

Pinning is considered harmful when it takes longer than 20 milliseconds, that is also the default when detecting it within tests.

Pinning threshold can be changed with:

*Configure pinning threshold*

```java
@HelidonTest(pinningDetection = true, pinningThreshold = 50) 
class MyTest {
}
```

- Change pinning threshold from default(20) to 50 milliseconds.

When pinning is detected, the test fails with a stacktrace pointing at the culprit.

## Reference

- [TestNG Documentation](https://testng.org)
