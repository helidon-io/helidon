# pico-integrations

_Integrations_ provide a convenient mechanism to integrate with 3rd party SDKs and libraries.

There are generally two techniques that are used for integrations:
* The [pico-maven-plugin](../maven-plugin), and
* [these modules](#modules) - which primarily can be viewed as a recipe for how one can integrate to 
other 3rd party frameworks. See ["how it works"](#how-it-works) as a general outline for this recipe.

## Modules
* [oci](./oci) - integration with the [OCI SDK](https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/javasdk.htm).

## How it works

1. There are typically two modules for each integration module - _processor_ and _runtime_.

2. The _processor_ module extends the [pico annotation processor](../processor) and observes each and every injection point (aka **@Inject**). When the processor observes an injection point it code-generates an [Activator](../api/src/main/java/io/helidon/pico/api/Activator.java) and [](../api/src/main/java/io/helidon/pico/api/ModuleComponent.java) for those injected services. This _processor_ module is expected to be in the APT classpath during compilation. It is not needed at runtime, however. 

3. The _runtime_ module is expected to be in your runtime classpath. Each runtime module will vary depending upon the nature of the 3rd party library integration.
