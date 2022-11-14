# test-support

Developers can include this as test scope for tools to aid in unit testing pico-based applications.

## API
See the [testsupport package](./src/main/java/io/helidon/pico/testsupport).

## Examples

```java
public class APicoStyleTest {
    TestableServices services;
    TestablePicoServices picoServices;
    TestablePicoServicesConfig config = new TestablePicoServicesConfig();

    @BeforeEach
    public void setUp() {
        // override "default" pico behavior to avoid loading Application.java compile-time bindings
//        config.setValue(PicoServicesConfig.KEY_BIND_APPLICATION, false);
        this.services = new TestableServices(config);
        this.picoServices = new TestablePicoServices(config, services);
    }

    @Test
    public void testSomething() {
        // bind a "fake" service provider into the test services registry
        services.bind(picoServices, BasicSingletonServiceProvider
                              .createBasicServiceProvider(FakeService.class,
                                                          DefaultServiceInfo.builder()
                                                                  .serviceTypeName(FakeService.class.getName())
                                                                  .named("fake")
                                                                  .externalContractTypeImplemented(FakeContract.class)
                                                                  .build()));
        List<ServiceProvider<FakeService>> providers = picoServices.getServices().lookup(FakeService.class);
        assertEquals("...", ServiceProvider.toDescriptions(providers));
    }
}
```

Also see [maven-plugin's picoApplication and picotestApplication](../maven-plugin/README.md#picoApplication-and-picotestApplication).
