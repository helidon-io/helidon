# Oracle Cloud Infrastructure

## Overview

You can quickly and easily deploy Helidon applications on Oracle Cloud Infrastructure (OCI) and integrate them with OCI services using the OCI Java SDK and the Helidon OCI SDK Extension.

[The Oracle Cloud Infrastructure SDK for Java](https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/javasdk.htm) enables you to write code to manage Oracle Cloud Infrastructure resources. The Helidon OCI SDK [CDI portable extension](https://jakarta.ee/specifications/cdi/4.0/jakarta-cdi-spec-4.0.html#spi) provides support for injecting [Oracle Cloud Infrastructure SDK Clients](https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/javasdk.htm) into your Helidon applications.

## Maven Coordinates

To enable OCI Integration, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

*Adding the Helidon OCI SDK Extension dependency*

``` xml
<dependency>
     <groupId>io.helidon.integrations.oci.sdk</groupId>
     <artifactId>helidon-integrations-oci-sdk-cdi</artifactId>
</dependency>
```

## Authentication

You must configure authentication between your local environment and the OCI environment. It is recommended that you configure authentication first and then verify your configuration by using the [OCI CLI](https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/javasdk.htm) to access the service.

The Helidon OCI SDK extension authenticates with OCI by picking up OCI credentials from your environment.

To configure authentication, add the `oci.auth-strategies` property to `/server/src/main/resources/META-INF/microprofile-config.properties`.

The `oci.auth-strategies` property specifies the OCI client authentication mechanism that should be used. It can be a single value or a list of authentication types, separated by commas. If you specify a list, it cycles through each type until the authentication is successful.

*oci.auth-strategies example*

``` properties
oci.auth-strategies=config-file,instance_principals,resource_principal
```

OCI supports the following client authentication methods:

- `auto` (default value): Cycles through all of the authentication types until one succeeds. By default, this value is set to `config_file,instance_principals,resource_principal`.
- `config_file`: Uses the user authentication specified in `~/.oci/config`.
- `config`: Uses the user authentication specified in the Helidon `microprofile-config.properties` file.
- `instance_principals`: Uses the OCI Compute instance as the authentication and authorization principal. See [Calling Services from an Instance](https://docs.oracle.com/en-us/iaas/Content/Identity/Tasks/callingservicesfrominstances.htm).
- `resource_principal`: Uses OCI resources and services as the authentication and authorization principal, such as serverless functions. This option is similar to the `instance_principals` authentication type. See [About Using Resource Principal to Access Oracle Cloud Infrastructure Resources](https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/resource-principal.html).

If your environment is already set up to work with the OCI SDK or the OCI CLI, then it is likely you do not need to perform any additional configuration of the extension. When the extension is added as a dependency, it will self-configure.

When you inject an OCI SDK Client object, the Helidon OCI SDK extension configures and constructs the object for you. The configuration primarily consists of initializing an OCI `AuthenticationDetailsProvider`. By default, the extension examines your environment and selects the best `AuthenticationDetailsProvider` and configures it for you.

If you require greater control over the OCI configuration, see [OciExtension](/apidocs/io.helidon.integrations.oci.sdk.cdi/io/helidon/integrations/oci/sdk/cdi/OciExtension.html) in the Helidon Javadocs for more information concerning the extension and its configuration and authentication options. In particular, the `oci.auth-strategies` property lets you control which `AuthenticationDetailsProvider` will be used.

## Accessing OCI Services

The Helidon OCI SDK extension supports injecting the client for any [OCI service supported by the OCI SDK for Java](https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/javasdk.htm#Services_Supported).

After adding the Helidon OCI SDK Extension dependency (as described above), you must add dependencies for each of the specific OCI SDK clients that you plan to use.

> [!NOTE]
> Each time that you update your application to integrate with an OCI service, you must build and redeploy the application to enable the OCI service’s features.

### Object Storage

The OCI Object Storage service is an internet-scale, high-performance storage platform that offers reliable and cost-efficient data durability. See [OCI Object Storage Overview](https://docs.oracle.com/en-us/iaas/Content/Object/Concepts/objectstorageoverview.htm) in OCI documentation.

To enable OCI Object Storage integration, add the following dependency to your project’s `pom.xml`:

*Adding the dependency for OCI Object Storage*

``` xml
<dependency>
    <groupId>com.oracle.oci.sdk</groupId>
    <artifactId>oci-java-sdk-objectstorage</artifactId>
</dependency>
```

#### Injecting an Object Storage Client

Now you can inject OCI SDK Clients for Object Storage.

*Field-injection example*

``` java
@Inject
private ObjectStorage client;
```

*Constructor-injection example*

``` java
public class MyClass {

    private final ObjectStorage client;

    @Inject
    public MyClass(@Named("orders") ObjectStorage client) {
        this.client = client;
    }
}
```

The extension implements this injection point by creating an Object Storage client object in the [singleton scope](https://jakarta.ee/specifications/dependency-injection/2.0/apidocs/jakarta/inject/Singleton.html).

After you have injected an ObjectStorage client, you can use it as described in [OCI SDK Object Storage Javadocs](https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/objectstorage/package-summary.html).

### Vault

The OCI Vault service lets you store and manage encryption keys and secrets to securely access resources. See [Vault](https://docs.oracle.com/en-us/iaas/Content/KeyManagement/home.htm) in OCI documentation.

To enable OCI Vault integration, add the following dependencies to your project’s `pom.xml`:

*Adding the dependency for OCI Vault*

``` xml
<dependency>
    <groupId>com.oracle.oci.sdk</groupId>
    <artifactId>oci-java-sdk-keymanagement</artifactId>
</dependency>
<dependency>
    <groupId>com.oracle.oci.sdk</groupId>
    <artifactId>oci-java-sdk-secrets</artifactId>
</dependency>
<dependency>
    <groupId>com.oracle.oci.sdk</groupId>
    <artifactId>oci-java-sdk-vault</artifactId>
</dependency>
```

#### Injecting a Vault Client

Now you can inject OCI SDK Clients for OCI Vault.

``` java
import com.oracle.bmc.keymanagement.KmsCrypto;
import com.oracle.bmc.secrets.Secrets;
import com.oracle.bmc.vault.Vaults;
@Inject
VaultResource(Secrets secrets,
    KmsCrypto crypto,
    Vaults vaults) {
    this.secrets = secrets;
    this.crypto = crypto;
    this.vaults = vaults;
}
```

## References

- [OciExtension](/apidocs/io.helidon.integrations.oci.sdk.cdi/io/helidon/integrations/oci/sdk/cdi/OciExtension.html) in the Helidon Javadocs
- [OCI SDK Usage Examples](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/integrations/oci) in the Helidon Examples GitHub repository
