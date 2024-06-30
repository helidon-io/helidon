# OCI integration module

This Helidon module requires Service Registry.

The module uses (internally) a service of type `OciConfig`. This instance is used to configure OCI integration.
Note that this service can be customized, if a provider with higher weight is created.
The default service implementation uses environment variables, system properties, and a configuration file `oci-config.yaml` on file system, or on classpath (Weight: default - 10). 

The configuration options (main):
```yaml
helidon.oci:
  authentication-method: "auto" # can select a specific authentication method to use, use auto to choose from available
  allowed-authentication-methods: ["config", "config-file"] # limit the list of authentication methods to try with auto
  authentication: # specific configuration of authentication methods
    config-file: # all details in a config file
      profile: "DEFAULT" # optional
      path: "/custom/path/.oci/config" # optional
    config: # all details here in config
      region: "some-region-id"
      fingerprint: "0A0B..."
      tenant-id: "ocid"
      user-id: "ocid"
      private-key:
        resource-path: "/on/classpath/private-key.pem"      
    session-token: # same as config + session token and some additional configuration
      session-token: "token"
      session-lifetime-hours: 8
```

This module provides a few services that can be used by other modules.

## Authentication Details Provider

Any service can have a dependency on `com.oracle.bmc.auth.BasicAuthenticationDetailsProvider`, and it can be
looked up using Service Registry lookup methods.

The provider is looked up by using instances of `io.helidon.integrations.oci.spi.OciAuthenticationMethod`. The first service instance to provide an authentication provider is used, and no other service instance is called.

The following out-of-the-box implementations exist:

| Provider           | Weight | Description                                                   |
|--------------------|--------|---------------------------------------------------------------|
| Config             | 90     | Uses `OciConfig`                                              |
| Session Token      | 85     | Uses `OciConfig` or config file, if it contains session token |
| Config File        | 80     | Uses `~/.oci/config` file                                     |
| Resource Principal | 70     | Resource principal (such as functions)                        |
| Instance Principal | 60     | Principal of the compute instance                             | 

To create a custom instance of authentication details provider, just create a new service for service registry
with default or higher weight that provides an instance of the `BasicAuthenticationDetailsProvider` 
(ServiceRegistry requires setup of annotation processors, see this module's pom file).

## Authentication Details Provider Builders

The following builders can be used as dependencies (and discovered from `ServiceRegistry`). The builders will have as much
information as available filled in:

- `com.oracle.bmc.auth.SessionTokenAuthenticationDetailsProvider.SessionTokenAuthenticationDetailsProviderBuilder`
- `com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider.InstancePrincipalsAuthenticationDetailsProviderBuilder`
- `com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider.ResourcePrincipalAuthenticationDetailsProviderBuilder`
- `com.oracle.bmc.auth.okeworkloadidentity.OkeWorkloadIdentityAuthenticationDetailsProvider.OkeWorkloadIdentityAuthenticationDetailsProviderBuilder` 

## Region

Any service can have a dependency on `com.oracle.bmc.Region`, and it can be looked up using Service Registry
lookup methods.

Important note: authentication details providers MUST NOT have a dependency on `Region`, as it may be provided by authentication
details provider (this would create a cyclic dependency).

Region is discovered by using instances of `io.helidon.integrations.oci.spi.OciRegion`. The first service instance to provide a
region is used, and no other service instance is called.

The following out-of-the-box implementations exists:

- Config based region provider: uses `OciConfig` to find region (expected key is `oci.region`); Weight: default - 10
- Authentication provider based region provider: uses authentication provider if it implements `RegionProvider` to find region; Weight: default - 20
- OCI SDK based region provider: uses `Region.registerFromInstanceMetadataService()` to find region (this has timeout of 30
  seconds, so if we reach this provider, it may block for a while - but only once); Weight: default - 100

