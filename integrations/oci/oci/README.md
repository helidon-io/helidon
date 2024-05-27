# OCI integration module

This Helidon module requires Service Registry.

The module uses (internally) a service of type `OciConfig`. This instance is used to configure OCI integration.
Note that this service can be customized, if a provider with higher weight is created.
The default service implementation uses environment variables, system properties, and a configuration file `oci-config.yaml` on file system, or on classpath (Weight: default - 10). 

This module provides two services that can be used by other modules.

## Authentication Details Provider

Any service can have a dependency on `com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider`, and it can be
looked up using Service Registry lookup methods.

The provider is looked up by using instances of `io.helidon.integrations.oci.spi.OciAtnStrategy`. The first service instance to provide an authentication provider is used, and no other service instance is called.

The following out-of-the-box implementations exist:

- Config based provider: uses `OciConfig` to create authentication provider; Weight: default - 10
- Config file based provider: uses OCI config file (i.e. `~/.oci/config`) to create authentication provider; both file location and profile can be configured through `OciConfig`, Weight: default - 20
- Resource principal provider: uses resource principal authentication provider; Weight: default - 30
- Instance principal provider: uses instance principal authentication provider; Weight: default - 40

To create a custom instance of authentication details provider, just create a new service for service registry
with default or higher weight that provides an instance of the `AbstractAuthenticationDetailsProvider` 
(ServiceRegistry requires setup of annotation processors, see this module's pom file).

## Region

Any service can have a dependency on `com.oracle.bmc.Region`, and it can be looked up using Service Registry
lookup methods.

Region is discovered by using instances of `io.helidon.integrations.oci.spi.OciRegion`. The first service instance to provide a
region is used, and no other service instance is called.

The following out-of-the-box implementations exists:

- Config based region provider: uses `OciConfig` to find region (expected key is `oci.region`); Weight: default - 10
- Authentication provider based region provider: uses authentication provider if it implements `RegionProvider` to find region; Weight: default - 20
- OCI SDK based region provider: uses `Region.registerFromInstanceMetadataService()` to find region (this has timeout of 30
  seconds, so if we reach this provider, it may block for a while - but only once); Weight: default - 100

