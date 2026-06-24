# Config Sources

## Creating MicroProfile Config Sources for Manual Setup of Config

You can use the following methods to create MicroProfile Config Sources to
manually set up the Config from
`org.eclipse.microprofile.config.spi.ConfigProviderResolver#getBuilder()` on
`io.helidon.config.mp.MpConfigSources` class:

<table>
<thead>
<tr>
<th>Method</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>systemProperties()</code></td>
<td>System properties config source.</td>
</tr>
<tr>
<td><code>environmentVariables()</code></td>
<td>Environment variables config source.</td>
</tr>
<tr>
<td><code>create(<wbr>java.<wbr>nio.<wbr>file.<wbr>Path)</code></td>
<td>Loads a properties file from file system.<br />
To load the properties file from file system with custom name, use <code>create(String, java.nio.file.Path)</code></td>
</tr>
<tr>
<td><code>create(<wbr>java.<wbr>util.<wbr>Map)</code></td>
<td>Creates an in-memory source from map.<br />
To create an in-memory source from map with custom name, use <code>create(String, java.util.Map)</code>.</td>
</tr>
<tr>
<td><code>create(<wbr>java.<wbr>util.<wbr>Properties)</code></td>
<td>Creates an in-memory source from properties.<br />
To create an in-memory source from properties with custom name, use <code>create(<wbr>String,<wbr> java.util.Properties)</code></td>
</tr>
</tbody>
</table>

### Create Custom Map MicroProfile Config Source

You can create MicroProfile Config Source from a map.

Create MicroProfile Config Source based on Environment Variables and Custom Map:

<!--@mdc ::code-callout -->
```java
ConfigProviderResolver resolver = ConfigProviderResolver.instance();

Config config = resolver.getBuilder() // <1>
        .withSources(MpConfigSources.environmentVariables()) // <2>
        .withSources(MpConfigSources.create(Map.of("key", "value"))) // <3>
        .build(); // <4>

resolver.registerConfig(config, null); // <5>
```
1. Creates MicroProfile Config Source builder.
2. Adds environment variables.
3. Adds a custom map.
4. Builds the MicroProfile Config Source.
5. Registers the config, so it can be used by other components
<!--@mdc :: -->

### Create YAML MicroProfile Config Source

You can create YAML MicroProfile Config Source from a path or a URL. When you
create a MicroProfile instance from the builder, the `YamlMpConfigSource` allows
you to create a custom Config Source and register it with the builder.

Create YamlMPConfigSource from a path:

```java
ConfigProviderResolver.instance().getBuilder()
        .withSources(YamlMpConfigSource.create(path))
        .build();
```

## Creating Custom Config Sources

Custom Config Sources are loaded using the Java Service Loader pattern, by
implementing either `org.eclipse.microprofile.config.spi.ConfigSource`, or
`org.eclipse.microprofile.config.spi.ConfigSourceProvider` SPI and registering
it as a service (Using `META-INF/services/${class-name}` file when using
classpath, or using the `provides` statement in `module-info.java` when using
module path).

The interface `org.eclipse.microprofile.config.spi.ConfigSource` requires
implementation of the following methods:

- `String getName()`
- `Map<String, String> getProperties()`
- `String getValue(String key)`
- `getOrdinal()`

### Example of a Custom Config Source

<!--@mdc ::code-callout{collapsed} -->
```java
public class CustomConfigSource implements ConfigSource {
    private static final String NAME = "MyConfigSource";
    private static final int ORDINAL = 200; // Default for MP is 100
    private static final Map<String, String> PROPERTIES = Map.of("app.greeting", "Hi");

    @Override
    public String getName() {
        return NAME; // <1>
    }

    @Override
    public Map<String, String> getProperties() {
        return PROPERTIES; // <2>
    }

    @Override
    public Set<String> getPropertyNames() {
        return PROPERTIES.keySet();
    }

    @Override
    public String getValue(String key) {
        return PROPERTIES.get(key); // <3>
    }

    @Override
    public int getOrdinal() {
        return ORDINAL; // <4>
    }
}
```
1. Returns the name of the Config Source to use for logging or analysis of
   configured values.
2. Returns the properties in this Config Source as a map.
3. Returns the value of the requested key, or `null` if the key is not available
4. Returns the ordinal of this Config Source.
<!--@mdc :: -->

## Creating MicroProfile Config Sources from meta-config

Instead of directly specifying the configuration sources in your code, you can
use meta-configuration in a file that declares the configuration sources, and
their attributes as mentioned in [MicroProfile Config](introduction.md).

When used, the MicroProfile Config uses configuration sources and flags
configured in the meta configuration file.

If a file named `mp-meta-config.yaml`, or `mp-meta-config.properties` is in the
current directory or on the classpath, and there is no explicit setup of
configuration in the code, the configuration will be loaded from the
`meta-config` file. The location of the file can be overridden using system
property `io.helidon.config.mp.meta-config`, or environment variable
`HELIDON_MP_META_CONFIG`.

> [!IMPORTANT]
> Do not use custom files named `meta-config.*`, to avoid conflicting with
> Helidon SE Meta Configuration.

Example of a YAML meta configuration file:

<!--@mdc ::code-callout{collapsed} -->
```yaml [mp-meta-config.yaml]
add-discovered-sources: true # <1>
add-discovered-converters: false # <2>
add-default-sources: false # <3>

sources:
  - type: "environment-variables" # <4>
  - type: "system-properties" # <5>
  - type: "properties" # <6>
    path: "/conf/prod.properties" # <7>
    ordinal: 50 # <8>
    optional: true # <9>
  - type: "yaml" # <10>
    classpath: "META-INF/database.yaml" # <11>
  - type: "hocon" # <12>
    classpath: "custom-application.conf" # <13>
  - type: "json" # <14>
    path: "path: conf/custom-application.json" # <15>
```
1. If configured to `true`, config sources discovered through service loader will
   be added
2. If configured to `true`, converters discovered through service loader will be
   added
3. If configured to `true`, default config sources (system properties,
   environment variables, and `META-INF/microprofile-config.properties`) will be
   added
4. Loads the environment variables config source.
5. Loads the system properties config source.
6. Loads a properties file
7. Location of the file: `/conf/prod.properties` on the file system
8. Custom ordinal, if not defined, the value defined in the file, or default
   value is used. The source precedence order is the order of appearance in the
   file. The default is 100.
9. The file is optional (if not optional and no file is found, the bootstrap
   fails)
10. Loads a YAML file
11. Location of the file: `META-INF/database.yaml` on the classpath
12. Loads a HOCON file
13. Location of the file: `custom-application.conf` on the classpath
14. Loads a JSON file
15. Location of the file: `conf/custom-application.json` relative to the directory
    of where the app was executed on the file system.
<!--@mdc :: -->

**Important Note:** To enable support for `HOCON` and `JSON` types, add the
following dependency to your project’s pom.xml.

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.config</groupId>
  <artifactId>helidon-config-hocon-mp</artifactId>
</dependency>
```

## Extending Meta-Config to Create a Custom Config Source Type

Helidon meta-config by default supports the following types:
environment-variables, system-properties, properties, yaml, hocon and json.
Users can also extend meta-config to create a custom config source type by
loading it using the Java Service Loader pattern. This is achieved by
implementing `io.helidon.config.mp.spi.MpMetaConfigProvider` SPI and registering
it as a service (Using `META-INF/services/${class-name}` file when using
classpath, or using the `provides` statement in `module-info.java` when using
module path).

The interface `io.helidon.config.mp.spi.MpMetaConfigProvider` requires
implementation of the following methods:

- `Set<String> supportedTypes()`
- `List<? extends ConfigSource> create(String type, Config metaConfig, String
  profile);`

### Example of a Meta-Config Custom Type

<!--@mdc ::code-callout{collapsed} -->
```java
public class CustomMpMetaConfigProvider implements MpMetaConfigProvider {

    @Override
    public Set<String> supportedTypes() {
        return Set.of("custom"); // <1>
    }

    @Override
    public List<? extends ConfigSource> create(String type, io.helidon.config.Config metaConfig, String profile) {
        ConfigValue<Path> pathConfig = metaConfig.get("path").as(Path.class);
        String location;
        if (pathConfig.isPresent()) { // <2>
            Path path = pathConfig.get();
            List<ConfigSource> sources = sourceFromPath(path, profile); // <3>
            if (sources != null && !sources.isEmpty()) {
                return sources;
            }
            location = "path " + path.toAbsolutePath();
        } else {
            ConfigValue<String> classpathConfig = metaConfig.get("classpath").as(String.class);
            if (classpathConfig.isPresent()) { // <4>
                String classpath = classpathConfig.get();
                List<ConfigSource> sources = sourceFromClasspath(classpath, profile); // <5>
                if (sources != null && !sources.isEmpty()) {
                    return sources;
                }
                location = "classpath " + classpath;
            } else {
                ConfigValue<URL> urlConfig = metaConfig.get("url").as(URL.class);
                if (urlConfig.isPresent()) { // <6>
                    URL url = urlConfig.get();
                    List<ConfigSource> sources = sourceFromUrlMeta(url, profile); // <7>
                    if (sources != null && !sources.isEmpty()) {
                        return sources;
                    }
                    location = "url " + url;
                } else {
                    throw new ConfigException("No config source location for " + metaConfig.key());
                }
            }
        }
        if (metaConfig.get("optional").asBoolean().orElse(false)) {
            return List.of(); // <8>
        }
        throw new ConfigException("Meta configuration could not find non-optional config source on " + location); // <9>
    }
}
```
1. Returns the names of the types that will be supported in this meta-config.
2. Processes config source from file system if `path` is provided.
3. Method to parse config source from a specified `path`
4. Processes config source from classpath location if `classpath` is provided.
5. Method to parse config source from a specified `classpath`
6. Processes config source from URL location if `location` is provided.
7. Method to parse config source from a specified `url`
8. Returns an empty result if set to `optional` and config source is not found.
9. Throws a ConfigException if not set to `optional` and config source is not
   found.
<!--@mdc :: -->

## Creating MicroProfile Config Source from Helidon SE Config Source

To use the Helidon SE features in Helidon MP, create MicroProfile Config Source
from Helidon SE Config Source. The Config Source is immutable regardless of
configured polling strategy or change watchers.

<!--@mdc ::code-callout -->
```java
Config config = ConfigProviderResolver.instance()
        .getBuilder()
        .withSources(MpConfigSources.create(helidonConfigSource)) // <1>
        .build();
```
1. Creates a MicroProfile config instance using Helidon Config Source.
<!--@mdc :: -->

## Creating MicroProfile Config Source from Helidon SE Config Instance

To use advanced Helidon SE features in Helidon MP, create MicroProfile Config
Source from Helidon SE Config. The Config Source is mutable if the config uses
either polling strategy and change watchers, or polling strategy or change
watchers. The latest config version is queried each time
`org.eclipse.microprofile.config.spi.ConfigSource#getValue(String)` is called.

<!--@mdc ::code-callout -->
```java
io.helidon.config.Config helidonConfig = io.helidon.config.Config.builder()
        .addSource(ConfigSources.create(Map.of("key", "value"))) // <1>
        .build();
ConfigProviderResolver.instance();
Config config = ConfigProviderResolver.instance()
        .getBuilder()
        .withSources(MpConfigSources.create(helidonConfig)) // <2>
        .build();
```
1. Creates a config source from Helidon Config.
2. Creates a MicroProfile config instance using Helidon Config.
<!--@mdc :: -->
