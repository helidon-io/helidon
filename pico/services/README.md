# pico (the reference implementation of the Pico API/SPI)

This module represents the "The Default Reference Implementation" for Pico.
Most of the information pertaining to this module can be found on the [parent page](../README.md).

## Pico is a Jsr-330 Compliant Implementation
The default configuration (see the javadoc for <i>PicoServicesConfig</i> for a complete set of configuration attributes that are available to be overridden) and assumes the following:

* Compile-time only with no reflection or dynamic mutability of the DI set of services at runtime.
* No static methods or fields will be injected or be injectable (note that this is optional characteristic within the Jsr-330 specification and Pico opt'ed to leave this unsupported).
* No private methods or fields will be injected or be injectable ("" "" "" "" "" "").
* Only @Inject(able) setter methods taking one injection point/argument is supported.

### Extensions to the base Jsr-330 Specification

* [Optional](https://docs.oracle.com/javase/8/docs/api/java/util/Optional.html) injection points.
* <i>java.util.List<jakarta.inject.Provider<>>/i> injection points.
* [PostConstruct](https://docs.oracle.com/javaee/7/api/javax/annotation/PostConstruct.html) support.
* [PreDestroy](https://docs.oracle.com/javaee/7/api/javax/annotation/PreDestroy.html) support.

### Special notes about Jsr-330 "Strict mode"

* [Annotation](https://docs.oracle.com/javaee/7/api/index.html) are not [inheritable](https://docs.oracle.com/javase/8/docs/api/java/lang/annotation/Inherited.html) according to the Jsr-330 specification. One thing this means is if a subclass overrides an injectable setter method from an injectable base class, then the inherited method will NOT be injected on the derived class unless the [Inject](https://docs.oracle.com/javaee/6/api/javax/inject/Inject.html)
  annotation is also applied on the derived class setter method(s) also.  If this choice seems odd then you are not alone.  However, to be in strict compliance to the Jsr-330 specification, then turn <i>supportsJsr330Strict=true</i> and these rules will be enforced, and code generation will look slightly different then what is shown by the Pico examples.  Feel free to turn this option on to be in strict accordance to the Jsr-330 specification in your application (but this is not our recommendation).
