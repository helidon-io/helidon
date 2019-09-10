# Helidon OCI Object Storage CDI Integration

The Helidon OCI Object Storage CDI Integration project supplies a
[CDI portable extension](http://docs.jboss.org/cdi/spec/2.0/cdi-spec.html#spi)
 that lets the end user inject an `ObjectStorage` client into her CDI application.

## Installation

Ensure that the Helidon OCI Object Storage CDI Integration project and
its runtime dependencies are present on your application's runtime
classpath.

For Maven users, your `<dependency>` stanza should look like this:

```xml
<dependency>
  <groupId>io.helidon.integrations.cdi</groupId>
  <artifactId>helidon-integrations-cdi-oci-objectstorage</artifactId>
  <version>1.0.0</version>
  <scope>runtime</scope>
</dependency>
```

## Usage

If you want to use an `ObjectStorage` client
in your application code, simply inject it in the
[usual, idiomatic CDI way](http://docs.jboss.org/cdi/spec/2.0/cdi-spec.html#injection_and_resolution).
 Here is a field injection example:

```java
@Inject
private ObjectStorage client;
```

And here is a constructor injection example:

```java
private final ObjectStorage client;

@Inject
public YourConstructor(@Named("orders") ObjectStorage client) {
  super();
  this.client = client;
}
```

The Helidon OCI Object Storage CDI Integration project will satisfy
this injection point with an `ObjectStorageClient` in
[application scope](http://docs.jboss.org/cdi/api/2.0/javax/enterprise/context/ApplicationScoped.html).

To create it, the Helidon OCI Object Storage CDI Integration project
will use [MicroProfile Config](https://static.javadoc.io/org.eclipse.microprofile.config/microprofile-config-api/1.3/index.html?overview-summary.html)
 to locate its configuration.  The following
 [Property names](https://static.javadoc.io/org.eclipse.microprofile.config/microprofile-config-api/1.3/org/eclipse/microprofile/config/Config.html#getPropertyNames--)
 will be used to establish a connection to the OCI Object Storage service:

* `oci.auth.fingerprint`
* `oci.auth.keyFile`
* `oci.auth.passphraseCharacters`
* `oci.auth.user`
* `oci.auth.tenancy`
* `oci.objectstorage.region`

These properties are [documented in the OCI Object Storage Java SDK documentation](https://docs.cloud.oracle.com/iaas/Content/API/SDKDocs/javasdk.htm#Configur).
