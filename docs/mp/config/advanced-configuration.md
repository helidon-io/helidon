# Microprofile Config Sources

## Creating MicroProfile Config Sources for Manual Setup of Config

You can use the following methods to create MicroProfile Config Sources to manually set up the Config from `org.eclipse.microprofile.config.spi.ConfigProviderResolver#getBuilder()` on `io.helidon.config.mp.MpConfigSources` class:

<table>
<colgroup>
<col style="width: 37%" />
<col style="width: 62%" />
</colgroup>
<thead>
<tr>
<th style="text-align: left;">Method</th>
<th style="text-align: left;">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td style="text-align: left;"><p><code>systemProperties()</code></p></td>
<td style="text-align: left;"><p>System properties config source.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>environmentVariables()</code></p></td>
<td style="text-align: left;"><p>Environment variables config source.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>create(java.nio.file.Path)</code></p></td>
<td style="text-align: left;"><p>Loads a properties file from file system.<br />
To load the properties file from file system with custom name, use <code>create(String, java.nio.file.Path)</code>.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>create(java.util.Map)</code></p></td>
<td style="text-align: left;"><p>Creates an in-memory source from map.<br />
To create an in-memory source from map with custom name, use <code>create(String, java.util.Map)</code>.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>create(java.util.Properties)</code></p></td>
<td style="text-align: left;"><p>Creates an in-memory source from properties.<br />
To create an in-memory source from properties with custom name, use <code>create(String, java.util.Properties)</code>.</p></td>
</tr>
</tbody>
</table>

### Create Custom Map MicroProfile Config Source

You can create Microprofile Config Source from a map.

*Create MicroProfile Config Source based on Environment Variables and Custom Map*

``` java
ConfigProviderResolver resolver = ConfigProviderResolver.instance();

Config config = resolver.getBuilder() 
        .withSources(MpConfigSources.environmentVariables()) 
        .withSources(MpConfigSources.create(Map.of("key", "value"))) 
        .build(); 

resolver.registerConfig(config, null); 
```

- Creates MicroProfile Config Source builder.
- Adds environment variables.
- Adds a custom map.
- Builds the MicroProfile Config Source.
- Registers the config, so it can be used by other components

### Create YAML MicroProfile Config Source

You can create YAML Microprofile Config Source from a path or a URL. When you create a MicroProfile instance from the builder, the `YamlMpConfigSource` allows you to create a custom Config Source and register it with the builder.

*Create YamlMPConfigSource from a path*

``` java
ConfigProviderResolver.instance().getBuilder()
        .withSources(YamlMpConfigSource.create(path))
        .build();
```

## Creating Custom Config Sources

Custom Config Sources are loaded using the Java Service Loader pattern, by implementing either `org.eclipse.microprofile.config.spi.ConfigSource`, or `org.eclipse.microprofile.config.spi.ConfigSourceProvider` SPI and registering it as a service (Using `META-INF/services/${class-name}` file when using classpath, or using the `provides` statement in `module-info.java` when using module path).

The interface `org.eclipse.microprofile.config.spi.ConfigSource` requires implementation of the following methods:

- `String getName()`
- `Map<String, String> getProperties()`
- `String getValue(String key)`
- `getOrdinal()`

### Example of a Custom Config Source

``` java
public class CustomConfigSource implements ConfigSource {
    private static final String NAME = "MyConfigSource";
    private static final int ORDINAL = 200; // Default for MP is 100
    private static final Map<String, String> PROPERTIES = Map.of("app.greeting", "Hi");

    @Override
    public String getName() {
        return NAME; 
    }

    @Override
    public Map<String, String> getProperties() {
        return PROPERTIES; 
    }

    @Override
    public Set<String> getPropertyNames() {
        return PROPERTIES.keySet();
    }

    @Override
    public String getValue(String key) {
        return PROPERTIES.get(key); 
    }

    @Override
    public int getOrdinal() {
        return ORDINAL; 
    }
}
```

- Returns the name of the Config Source to use for logging or analysis of configured values.
- Returns the properties in this Config Source as a map.
- Returns the value of the requested key, or `null` if the key is not available
- Returns the ordinal of this Config Source.

## Creating MicroProfile Config Sources from meta-config

Instead of directly specifying the configuration sources in your code, you can use meta-configuration in a file that declares the configuration sources, and their attributes as mentioned in [Microprofile Config](introduction.md).

When used, the Microprofile Config uses configuration sources and flags configured in the meta configuration file.

If a file named `mp-meta-config.yaml`, or `mp-meta-config.properties` is in the current directory or on the classpath, and there is no explicit setup of configuration in the code, the configuration will be loaded from the `meta-config` file. The location of the file can be overridden using system property `io.helidon.config.mp.meta-config`, or environment variable `HELIDON_MP_META_CONFIG`.

**Important Note:** Do not use custom files named `meta-config.*`, as even when using Micro-Profile, we still use Helidon configuration in some of our components, and this file would be recognized as a Helidon SE Meta Configuration file, which may cause erroneous behavior.

*Example of a YAML meta configuration file:*

``` yaml
add-discovered-sources: true 
add-discovered-converters: false 
add-default-sources: false 

sources:
  - type: "environment-variables" 
  - type: "system-properties" 
  - type: "properties" 
    path: "/conf/prod.properties" 
    ordinal: 50 
    optional: true 
  - type: "yaml" 
    classpath: "META-INF/database.yaml" 
  - type: "hocon" 
    classpath: "custom-application.conf" 
  - type: "json" 
    path: "path: conf/custom-application.json" 
```

- If configured to `true`, config sources discovered through service loader will be added
- If configured to `true`, converters discovered through service loader will be added
- If configured to `true`, default config sources (system properties, environment variables, and \`META-INF/microprofile-config.properties) will be added
- Loads the environment variables config source.
- Loads the system properties config source.
- Loads a properties file
- Location of the file: `/conf/prod.properties` on the file system
- Custom ordinal, if not defined, the value defined in the file, or default value is used. The source precedence order is the order of appearance in the file. The default is 100.
- The file is optional (if not optional and no file is found, the bootstrap fails)
- Loads a YAML file
- Location of the file: `META-INF/database.yaml` on the classpath
- Loads a HOCON file
- Location of the file: `custom-application.conf` on the classpath
- Loads a JSON file
- Location of the file: `conf/custom-application.json` relative to the directory of where the app was executed on the file system.

**Important Note:** To enable support for `HOCON` and `JSON` types, add the following dependency to your project’s pom.xml.

``` xml
<dependency>
    <groupId>io.helidon.config</groupId>
    <artifactId>helidon-config-hocon-mp</artifactId>
</dependency>
```

## Extending Meta-Config to Create a Custom Config Source Type

Helidon meta-config by default supports the following types: environment-variables, system-properties, properties, yaml, hocon and json. Users can also extend meta-config to create a custom config source type by loading it using the Java Service Loader pattern. This is achieved by implementing `io.helidon.config.mp.spi.MpMetaConfigProvider` SPI and registering it as a service (Using `META-INF/services/${class-name}` file when using classpath, or using the `provides` statement in `module-info.java` when using module path).

The interface `io.helidon.config.mp.spi.MpMetaConfigProvider` requires implementation of the following methods:

- `Set<String> supportedTypes()`
- `List<? extends ConfigSource> create(String type, Config metaConfig, String profile);`

### Example of a Meta-Config Custom Type

``` java
public class CustomMpMetaConfigProvider implements MpMetaConfigProvider {

    @Override
    public Set<String> supportedTypes() {
        return Set.of("custom"); 
    }

    @Override
    public List<? extends ConfigSource> create(String type, io.helidon.config.Config metaConfig, String profile) {
        ConfigValue<Path> pathConfig = metaConfig.get("path").as(Path.class);
        String location;
        if (pathConfig.isPresent()) { 
            Path path = pathConfig.get();
            List<ConfigSource> sources = sourceFromPath(path, profile); 
            if (sources != null && !sources.isEmpty()) {
                return sources;
            }
            location = "path " + path.toAbsolutePath();
        } else {
            ConfigValue<String> classpathConfig = metaConfig.get("classpath").as(String.class);
            if (classpathConfig.isPresent()) { 
                String classpath = classpathConfig.get();
                List<ConfigSource> sources = sourceFromClasspath(classpath, profile); 
                if (sources != null && !sources.isEmpty()) {
                    return sources;
                }
                location = "classpath " + classpath;
            } else {
                ConfigValue<URL> urlConfig = metaConfig.get("url").as(URL.class);
                if (urlConfig.isPresent()) { 
                    URL url = urlConfig.get();
                    List<ConfigSource> sources = sourceFromUrlMeta(url, profile); 
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
            return List.of(); 
        }
        throw new ConfigException("Meta configuration could not find non-optional config source on " + location); 
    }
}
```

- Returns the names of the types that will be supported in this meta-config.
- Processes config source from file system if `path` is provided.
- Method to parse config source from a specified `path`
- Processes config source from classpath location if `classpath` is provided.
- Method to parse config source from a specified `classpath`
- Processes config source from URL location if `location` is provided.
- Method to parse config source from a specified `url`
- Returns an empty result if set to `optional` and config source is not found.
- Throws a ConfigException if not set to `optional` and config source is not found.

## Creating MicroProfile Config Source from Helidon SE Config Source

To use the Helidon SE features in Helidon MP, create MicroProfile Config Source from Helidon SE Config Source. The Config Source is immutable regardless of configured polling strategy or change watchers.

``` java
Config config = ConfigProviderResolver.instance()
        .getBuilder()
        .withSources(MpConfigSources.create(helidonConfigSource)) 
        .build();
```

- Creates a MicroProfile config instance using Helidon Config Source.

## Creating MicroProfile Config Source from Helidon SE Config Instance

To use advanced Helidon SE features in Helidon MP, create MicroProfile Config Source from Helidon SE Config. The Config Source is mutable if the config uses either polling strategy and change watchers, or polling strategy or change watchers. The latest config version is queried each time `org.eclipse.microprofile.config.spi.ConfigSource#getValue(String)` is called.

``` java
io.helidon.config.Config helidonConfig = io.helidon.config.Config.builder()
        .addSource(ConfigSources.create(Map.of("key", "value"))) 
        .build();
ConfigProviderResolver.instance();
Config config = ConfigProviderResolver.instance()
        .getBuilder()
        .withSources(MpConfigSources.create(helidonConfig)) 
        .build();
```

- Creates a config source from Helidon Config.
- Creates a MicroProfile config instance using Helidon Config.
