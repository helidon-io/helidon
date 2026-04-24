# Oracle Cloud Infrastructure

## Overview

You can quickly and easily deploy Helidon applications on Oracle Cloud Infrastructure (OCI) and integrate them with OCI services using the OCI Java SDK and the Helidon OCI SDK Integration.

[The Oracle Cloud Infrastructure SDK for Java](https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/javasdk.htm) enables you to write code to manage Oracle Cloud Infrastructure resources. The Helidon OCI SDK Integration provides support for integrating [Oracle Cloud Infrastructure SDK clients](https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/javasdk.htm) into your Helidon applications.

> [!NOTE]
> This Helidon module requires [ServiceRegistry](../injection/injection.md#Programmatic Lookup). ServiceRegistry requires the use of annotation processors, see [Why are annotation processors needed?](../injection/injection.md#Build time)

> [!NOTE]
> If you are interested in our MP based support of OCI than this [Guide for Helidon MP application on OCI](../../mp/guides/oci-guide.md), describes the basics of developing and deploying a Helidon MP application based on our [Project Starter](https://helidon.io/starter/4.4.0-SNAPSHOT).

## Maven Coordinates

To enable OCI Integration, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

*Adding the Helidon OCI SDK Integration dependency for Config, Config File and Session Token*

```xml
<dependency>
     <groupId>io.helidon.integrations.oci</groupId>
     <artifactId>helidon-integrations-oci</artifactId>
</dependency>
```

*Adding the Helidon OCI SDK Integration dependency for Resource Principal*

```xml
<dependency>
     <groupId>io.helidon.integrations.oci.authentication</groupId>
     <artifactId>helidon-integrations-oci-authentication-resource</artifactId>
</dependency>
```

*Adding the Helidon OCI SDK Integration dependency for Instance Principal*

```xml
<dependency>
     <groupId>io.helidon.integrations.oci.authentication</groupId>
     <artifactId>helidon-integrations-oci-authentication-instance</artifactId>
</dependency>
```

*Adding the Helidon OCI SDK Integration dependency for OKE Workload*

```xml
<dependency>
     <groupId>io.helidon.integrations.oci.authentication</groupId>
     <artifactId>helidon-integrations-oci-authentication-oke-workload</artifactId>
</dependency>
```

## Usage

All you need to do is configure and create an OCI SDK Client object. The configuration primarily consists of setting up authentication with OCI. It is recommended that you configure authentication first and then verify your configuration by using the [OCI CLI](https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/javasdk.htm) to access the service. Once you have authentication with OCI configured, you can use it to access any OCI service supported by the OCI SDK.

### Configuring Authentication

OCI provides a few authentication methods that can be used when connecting to services. Available methods depend on where your application runs. The Helidon OCI SDK Integration provides following authentication integrations out-of-the-box:

| Provider | Weight | Description |
|----|----|----|
| Config | 90 | Uses the user authentication specified in the Helidon `oci-config.yaml` file. |
| Session Token | 85 | Uses config or config file, if it contains session token. |
| Config File | 80 | Uses the user authentication specified in `~/.oci/config`. |
| Resource Principal | 70 | Uses OCI resources and services as the authentication and authorization principal, such as serverless functions. See [About Using Resource Principal to Access Oracle Cloud Infrastructure Resources](https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/resource-principal.html). |
| Instance Principal | 60 | Uses the OCI Compute instance as the authentication and authorization principal. See [Calling Services from an Instance](https://docs.oracle.com/en-us/iaas/Content/Identity/Tasks/callingservicesfrominstances.htm). |
| OKE Workload Identity | 50 | Identity of the OKE Workload |

These providers configure authentication with OCI by picking up OCI credentials from your environment variables, system properties, and a configuration file named `oci-config.yaml` on the file system, or on the runtime classpath.

To configure authentication, add the `helidon.oci.authentication-method` property to `/server/src/main/resources/META-INF/oci-config.yaml`. This property specifies the OCI client authentication method that should be used.

*oci-config.yaml helidon.oci.authentication-method example*

```yaml
helidon.oci:
  authentication-method: "auto"
  allowed-authentication-methods: ["config", "config-file", "session-token", "resource-principal", "instance-principal", "oke-workload-identity"]
```

- `auto`(default value): cycles through the list of authentication types from `helidon.oci.allowed-authentication-methods` and chooses the first one capable of providing data. In case the `helidon.oci.allowed-authentication-methods` list is empty, Helidon tries all available strategies, ordered by decreasing Weight.
- If the configured method is not available, an exception is thrown for OCI related services.

If your environment is already set up to work with the OCI SDK or the OCI CLI, then it is likely you do not need to perform any additional configuration for this integration. When the provider is added as a dependency, it will self-configure.

```java
ServiceRegistryManager registryManager = ServiceRegistryManager.create();
ServiceRegistry registry = registryManager.registry();
BasicAuthenticationDetailsProvider authProvider = registry.get(BasicAuthenticationDetailsProvider.class);
```

Authentication with OCI is abstracted through `AuthenticationDetailsProvider`. The Helidon OCI SDK Integration configures and constructs the object for you. The configuration primarily consists of initializing an OCI `AuthenticationDetailsProvider`. By default, the extension examines your environment and selects the best `AuthenticationDetailsProvider` and configures it for you.

If you require greater control over the OCI configuration, the `helidon.oci.authentication-method` property lets you control which `AuthenticationDetailsProvider` will be used.

### Accessing OCI Services

Once you have authentication with OCI configured, you can use it to access any [OCI service supported by the OCI SDK](https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/javasdk.htm#Services_Supported). You need to add dependencies for the specific ODI SDK clients you will use.

#### Object Storage

The OCI Object Storage service is an internet-scale, high-performance storage platform that offers reliable and cost-efficient data durability. See [OCI Object Storage Overview](https://docs.oracle.com/en-us/iaas/Content/Object/Concepts/objectstorageoverview.htm) in OCI documentation.

To enable the OCI Object Storage integration, add the following dependency to your project’s `pom.xml`:

*Adding the dependency for OCI Object Storage*

```xml
<dependency>
    <groupId>com.oracle.oci.sdk</groupId>
    <artifactId>oci-java-sdk-objectstorage</artifactId>
</dependency>
```

##### Creating an Object Storage Client

Now you can create OCI SDK clients for Object Storage.

```java
BasicAuthenticationDetailsProvider authProvider = Services.get(BasicAuthenticationDetailsProvider.class);
ObjectStorage objectStorageClient = ObjectStorageClient.builder().build(authProvider);
```

##### Using the Object Storage client

Once you have created an ObjectStorage client you can use it as described in:

- [OCI SDK Object Storage Javadocs](https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/objectstorage/package-summary.html)
- [OCI Object Storage Overview](https://docs.oracle.com/en-us/iaas/Content/Object/Concepts/objectstorageoverview.htm)

## Region Information

Services can have a dependency on `com.oracle.bmc.Region`, and your service can look up the region where it is running using ServiceRegistry lookup methods. This can be used to determine the current region where the service is running.

> [!NOTE]
> Authentication details providers MUST NOT have a dependency on `Region`, as it may be provided by authentication details provider (this would create a cyclic dependency).

Region is discovered by using following out-of-the-box order region provider:

- Config based: Based on the value of `helidon.oci.region`. Weight: default - 10
- Authentication provider based: Based on an available OCI authentication method, if it yields an authentication details provider that implements a region provider. Weight: default - 20
- OCI SDK based: Uses `com.oracle.bmc.Region.registerFromInstanceMetadataService()` to find region. Weight: default - 100

## Instance Metadata Service Instance Information

Services may need some information about its running environment. The Instance Metadata Service (IMDS) provides such information. Services can look up `io.helidon.integrations.oci.ImdsInstanceInfo` using ServiceRegistry lookup methods.

The following information is made available from IMDS in `io.helidon.integrations.oci.ImdsInstanceInfo`:

- displayName - Display Name of the Instance.
- hostName - Host Name of the Instance.
- canonicalRegionName - Canonical Region Name of where the Instance exists.
- region - Short Region Name of where the Instance exists.
- ociAdName - Physical Availaibility Domain Name where the Instance exists.
- faultDomain - Fault Domain Name where the Instance exists.
- tenantId - Tenant Id where the Instance was provisioned.
- compartmentId - Compartment Id where the Instance was provisioned.
- jsonObject - A JsonObject containing full information about the Instance from IMDS.

## Configuration

*oci-config.yaml auth config*

```yaml
helidon.oci:
  authentication-method: "auto" # can select a specific authentication method to use, defaults to auto to choose from allowed.
  allowed-authentication-methods: ["config", "config-file", "session-token", "resource-principal", "instance-principal", "oke-workload-identity"] # limit the list of authentication methods to try with auto
  authentication: # specific configuration of authentication methods
    config-file: # all details in a config file
      profile: "SOME-PROFILE" # optional, defaults to "DEFAULT"
      path: "/custom/path/.oci/config" # optional, defaults to ~/.oci/config
    config: # all details here in config
      region: "some-region-id"
      fingerprint: "0A0B..."
      tenant-id: "ocid"
      user-id: "ocid"
      private-key: # optional
        resource-path: "/on/classpath/private-key.pem"
      passphrase: "some-pass-phrase" # optional
    session-token: # same as config + session token and some additional configuration
      region: "some-region-id"
      fingerprint: "0A0B..."
      tenant-id: "ocid"
      user-id: "ocid"
      private-key-path: "/on/classpath/private-key.pem"
      passphrase: "some-pass-phrase" # optional
      session-token: "token"
      session-token-path: "token-path" # If both this value, and session-token is defined, the value of session-token is used.
      session-lifetime-hours: 8 #  optional, defaults to 24 hours
      initial-refresh-delay: 5 # optional, defaults to 0 to refresh immediately
      refresh-period: 30 # optional, defaults to 55 minutes
      scheduler: #optional, defaults to a single thread executor service
  region: "some-region-id" # Explicit region. The configured region will be used by region provider.
  imds-base-uri: "http://localhost:%d/opc/v2/" # optional, OCI IMDS URI (http URL pointing to the metadata service, if customization needed)
  imds-timeout: "PT5S" # optional, defaults to PT1S. IMDS connection timeout used to auto-detect availability.
  imds-detect-retries: 3 # optional, number of retries to contact IMDS service
  authentication-timeout: "PT1M" # optional, defaults to PT10S. Timeout of authentication operations.
  federation-endpoint: "https://auth.us-myregion-1.oraclecloud.com" # optional, federation endpoint for authentication providers.
  tenant-id: "ocid1.tenancy.oc1..mytenancyid" # optional, OCI tenant id for Instance Principal, Resource Principal or OKE Workload.
```

## References

- [OCI SDK Usage Examples](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/integrations/oci)
- [OCI Documentation](https://docs.oracle.com/en-us/iaas/Content/home.htm)
