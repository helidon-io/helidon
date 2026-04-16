# MicroProfile Config

## Overview

Helidon MicroProfile Config is an implementation of [Eclipse MicroProfile Config](https://github.com/eclipse/microprofile-config/). You can configure your applications using MicroProfile’s config configuration sources and APIs. You can also extend the configuration using MicroProfile SPI to add custom `ConfigSource` and `Converter`.

## Maven Coordinates

To enable MicroProfile Config, either add a dependency on the [helidon-microprofile bundle](../../mp/introduction/microprofile.md) or add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

``` xml
<dependency>
    <groupId>io.helidon.microprofile.config</groupId>
    <artifactId>helidon-microprofile-config</artifactId>
</dependency>
```

## Usage

### MicroProfile Config Features

#### MicroProfile Config Sources

A Config Source provides configuration values from different sources such as property files and user classes that are registered by the application.

By default, the following configuration sources are used to retrieve the configuration:

| Source | Description |
|----|----|
| System properties | A mutable source that uses `System.getProperties()` to obtain configuration values. |
| Environment variables | An immutable source that uses `System.env()` to obtain configuration values and resolves aliases as defined by the MicroProfile Config specification. |
| `META-INF/microprofile-config.properties` | The properties config source as defined by MicroProfile Config specification. |

MicroProfile Config uses `ConfigSource` SPI to load configuration data, either from default configuration sources or from custom `ConfigSource` located by Java Service Loader.

#### Using MicroProfile Config API

You can use MicroProfile Config API to get configuration properties by using a `Config` instance programmatically or injecting configuration values with `@ConfigProperty`.

*Using `Config`*

``` java
Config config = ConfigProvider.getConfig();
config.getOptionalValue("app.greeting", String.class).orElse("Hello");
```

*Injecting configured properties into a constructor*

``` java
@Inject
public GreetingProvider(
        @ConfigProperty(name = "app.greeting",
                        defaultValue = "Hello") String message) {
    this.message = message;
}
```

MicroProfile Config provides typed access to configuration values, using built-in converters, and `Converter` implementations located by Java Service Loader.

#### Ordering of Default Config Sources

In order to properly configure your application using configuration sources, you need to understand the precedence rules used to merge your configuration data. The default MicroProfile Config Sources ordering is:

- System properties (ordinal=400)
- Environment variables (ordinal=300)
- /META-INF/microprofile-config.properties (ordinal=100)

Each Config Source has an ordinal that determines the priority of the Config Source. A Config Source with higher ordinal has higher priority as compared to the Config Source with lower ordinal. The values taken from the high-priority Config Source overrides the values from low-priority Config Source. The default value is 100.

> [!NOTE]
> In MP, the ordering is not defined for sources that have the same ordinal.

This helps to customize the configuration of Config Sources using external Config Source if an external Config Source has higher ordinal values than the built-in Config Sources of the application.

The example below shows how the MicroProfile configuration file `microprofile-config.properties` can be used to modify the server listen port property.

``` properties
# Application properties. This is the default greeting
app.greeting=Hello

# Microprofile server properties
server.port=8080
server.host=0.0.0.0
```

#### MicroProfile Config Profiles

MicroProfile Config supports a concept of configuration profiles. You can define a profile using the configuration property `mp.config.profile` This can be defined as a system property, environment variable or as a property in `microprofile-config.properties` (when default configuration is used). When a profile is defined, an additional config source is loaded: `microprofile-config-<profile_name>.properties` and properties in the profile specific config source will override properties set in the default config source.

You can also use profiles on a per property level. Profile specific properties are defined using `%<profile_name>` prefix, such as `%dev.server.port`. This will override the plain property `server.port`. For more details see [How Config Profiles work](https://download.eclipse.org/microprofile/microprofile-config-3.1/microprofile-config-spec-3.1.html#_how_config_profile_works)

### Helidon MicroProfile Config Features

Helidon MicroProfile Config offers the following features on top of the specification:

### Helidon MicroProfile Config Sources

Helidon configuration sources can use different formats for the configuration data. You can specify the format on a per source bases, mixing and matching formats as required.

The following configuration sources can be used to retrieve the configuration:

|  |  |
|----|----|
| Source | Description |
| File | Creates the source from a properties file on the file system with `MpConfigSources.create(Path)`. |
| URL | Creates the source from properties from a URL with `MpConfigSources.create(URL)`. |
| `Map<String, String>` | Creates the source from a Map with `MpConfigSources.create(Map)`. |
| `Properties` | Creates the source directly from Properties with `MpConfigSources.create(Properties)`. |
| File on classpath | Creates the source from a properties file on classpath with `MpConfigSources.classpath(String)`. |
| YAML | Creates the source from YAML using `YamlMpConfigSource.create(Path)` or `YamlMpConfigSource.create(URL)`. |

See [manual setup of config](advanced-configuration.md#_creating_microprofile_config_sources_for_manual_setup_of_config) section for more information.

#### References

You can use `${reference}` to reference another configuration key in a key value. This allows to configure a single key to be reused in multiple other keys.

*Example*

``` yaml
uri: "http://localhost:8080"
service-1: "${uri}/service1"
service-2: "${uri}/service2"
```

#### Change support

Polling (or change watching) for file based config sources (not classpath based).

To enable polling for a config source created using meta configuration (see below), or using `MpConfigSources.create(Path)`, or `YamlMpConfigSource.create(Path)`, use the following properties:

<table>
<colgroup>
<col style="width: 37%" />
<col style="width: 62%" />
</colgroup>
<thead>
<tr>
<th style="text-align: left;">Property</th>
<th style="text-align: left;">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td style="text-align: left;"><p><code>helidon.config.polling.enabled</code></p></td>
<td style="text-align: left;"><p>To enable polling file for changes, uses timestamp to identify a change.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>helidon.config.polling.duration</code></p></td>
<td style="text-align: left;"><p>Polling period duration, defaults to 10 seconds ('PT10S`)<br />
See <a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/Duration.html#parse(java.lang.CharSequence)">javadoc</a></p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>helidon.config.watcher.enabled</code></p></td>
<td style="text-align: left;"><p>To enable watching file for changes using the Java <code>WatchService</code>.<br />
See link:https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/WatchService.html</p></td>
</tr>
</tbody>
</table>

#### Encryption

You can encrypt secrets using a master password and store them in a configuration file. The config encryption filter in MicroProfile Config is enabled by default. For more information, see [Configuration Secrets](../../mp/security/configuration-secrets.md).

*Example of encrypted secrets*

``` properties
# Password encrypted using a master password
client_secret=${GCM=mYRkg+4Q4hua1kvpCCI2hg==}
# Password encrypted using public key (there are length limits when using RSA)
client_secret_pke=${RSA=mYRkg+4Q4hua1kvpCCI2hg==}
# Password in clear text, can be used in development
# The system needs to be configured to accept clear text
client_secret_clear=${CLEAR=known_password}
```

#### Meta Configuration

You can configure the Config using Helidon MP Config meta configuration feature. The meta-config allows configuration of config sources and other configuration options, including addition of discovered sources and converters.

See [Microprofile Config Sources](advanced-configuration.md#_creating_microprofile_config_sources_from_meta_config) for detailed information.

> [!NOTE]
> For backward compatibility, we will support usage of Helidon SE meta-configuration until version 3.0.0. Using this approach causes behavior that is not compatible with MicroProfile Config specification.

## Configuration

Config sources can be configured using the following properties.

The class responsible for configuration is:

### Configuration options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="a2c415-profile"></span> `profile` | `VALUE` | `String` | Configure an explicit profile name |

Current properties may be set in `application.yaml` or in `microprofile-config.properties` with `mp.config` prefix.

See [Config Profiles](#microprofile-config-profiles) for more information.

## Additional Information

| Name | Description |
| --- | --- |
| [MP Config Guide](../guides/config.md) | Step-by-step guide about using MicroProfile Config in your Helidon MP application. |

## Reference

- [MicroProfile Config Specifications](https://download.eclipse.org/microprofile/microprofile-config-3.1/microprofile-config-spec-3.1.html)
- [MicroProfile Config Javadocs](https://download.eclipse.org/microprofile/microprofile-config-3.1/apidocs)
