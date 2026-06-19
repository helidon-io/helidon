# Configuration Profiles

## Overview

Configuration profiles provide a capability to prepare structure of
configuration for each environment in advance, and then simply switch between
these structures using a system property or an environment variable.

## Profile Options

To choose a configuration profile to use at runtime, you can use:

1.  A system property `config.profile`
2.  An environment variable `HELIDON_CONFIG_PROFILE`

There are two ways to define a profile configuration:

1.  Use a config source with a [profile specific name](#profile-config-sources)
2.  Use a [profile file](#profile-files) defining all configuration sources

Configuration profiles can only be used when config is created using the
`Config.create()` method without parameters. If you explicitly configure
sources, profiles are ignored.

## Profile Config Sources

If a profile is specified, config will load the profile-specific default
configuration source before the "main" source.

Let’s consider the selected profile is `dev`, and we have `yaml` configuration
support on classpath; config will look for the following sources (in this
order):

1.  `application-dev.yaml` on file system
2.  `application-dev.properties` on file system
3.  `application-dev.yaml` on classpath
4.  `application-dev.properties` on classpath
5.  `application.yaml` on file system
6.  `application.properties` on file system
7.  `application.yaml` on classpath
8.  `application.properties` on classpath

## Profile Files

If a profile is specified, config will look for a profile-specific "meta
configuration".

Let’s consider the selected profile is `dev`, and we have `yaml` configuration
support on classpath; config will look for the following profiles (in this
order):

1.  `config-profile-dev.yaml` on file system
2.  `config-profile-dev.properties` on file system
3.  `config-profile-dev.yaml` on classpath
4.  `config-profile-dev.properties` on classpath

If any of these files is discovered, it would be used to set up the
configuration. In case none is found, the config falls back to [profile specific
config sources](#profile-config-sources).

The structure of the file is described below in [profile file
format](#profile-file-format).

In case you need to customize the location of the profile file, you can use the
system property `io.helidon.config.meta-config`. For example if it is configured
to `config/profile.yaml`, config looks for file `config/profile-dev.yaml` when
`dev` profile is configured.

### Profile File Format

Configuration profile provides similar options to the configuration builder. The
profile file must contain at least the list of sources from which configuration
can be loaded.

The root `sources` property contains an array (ordered) of objects defining each
config source to be used. Each element of the array must contain at least the
`type` property, determining the config source type (such as
`system-properties`, `file`). It may also contain a `properties` property with
additional configuration of the config source.

An example development profile using "inlined" configuration:

Config profile `config-profile-dev.yaml`:

```yaml [config-profile-dev.yaml]
sources:
  - type: "inlined"
    properties:
      app.greeting: "Hello World"
```

An example of a profile using environment variables, system properties,
classpath, and file configuration:

Config profile `config-profile-prod.yaml`:

```yaml [config-profile-prod.yaml]
sources:
  - type: "environment-variables"
  - type: "system-properties"
  - type: "file"
    properties:
      path: "config/config-prod.yaml"
  - type: "classpath"
    properties:
      resource: "application.yaml"
```

#### Built-in Types

The config system supports these built-in types:

<table>
<thead>
<tr>
<th>Type</th>
<th>Use</th>
<th>Related <code>Config<wbr>Sources</code> Method</th>
<th>Required Properties</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>system-properties</code></td>
<td>System properties are a config source</td>
<td><code>Config<wbr>Sources.<wbr>system<wbr>Properties()</code></td>
<td>n/a</td>
</tr>
<tr>
<td><code>environment-variables</code></td>
<td>Environment variables are a config source</td>
<td><code>Config<wbr>Sources.<wbr>environment<wbr>Variables()</code></td>
<td>n/a</td>
</tr>
<tr>
<td><code>classpath</code></td>
<td>Specified resource is used as a config source</td>
<td><code>ConfigSources.<wbr>classpath(<wbr>String)</code></td>
<td><code>resource</code> - path to the resource to load</td>
</tr>
<tr>
<td><code>file</code></td>
<td>Specified file is used as a config source</td>
<td><code>Config<wbr>Sources.file(<wbr>Path)</code></td>
<td><code>path</code> - path to the file to load</td>
</tr>
<tr>
<td><code>directory</code></td>
<td>Each file in directory used as config entry, with key = file name and value = file contents</td>
<td><code>Config<wbr>Sources.<wbr>directory(<wbr>String)</code></td>
<td><code>path</code> - path to the directory to use</td>
</tr>
<tr>
<td><code>url</code></td>
<td>Specified URL is read as a config source</td>
<td><code>Config<wbr>Sources.<wbr>url(<wbr>URL)</code></td>
<td><code>url</code> - URL from which to load the config</td>
</tr>
<tr>
<td><code>inlined</code></td>
<td>The whole configuration tree under <code>properties</code> is added as a configuration source (excluding the <code>properties</code> node)</td>
<td>n/a</td>
<td>n/a</td>
</tr>
<tr>
<td><code>prefixed</code></td>
<td>Associated config source is loaded with the specified prefix</td>
<td><code>Config<wbr>Sources.<wbr>prefixed(<wbr>String,<wbr>Supplier)</code></td>
<td><ul>
<li><code>key</code> - key of config element in associated source to load</li>
<li><code>type</code> - associated config source specification</li>
<li><code>properties</code> - as needed to further qualify the associated config source</li>
</ul></td>
</tr>
</tbody>
</table>

Except for the `system-properties` and `environment-variables` types, the
profile `properties` section for a source can also specify any optional settings
for the corresponding config source type. The Javadoc for the related config
source type builders lists the supported properties for each type. (For example,
[`FileConfigSource.Builder`][fileconfigsource].)

Here is an example profile in YAML format. Note how the `properties` sections
are at the same level as the `type` or `class` within a `sources` array entry.

Profile `config-profile.yaml` illustrating all built-in sources available on the
classpath:

<!--@mdc ::code-collapse -->
```yaml [config-profile.yaml]
caching.enabled: false
sources:
  - type: "system-properties"
  - type: "environment-variables"
  - type: "directory"
    properties:
      path: "conf/secrets"
      media-type-mapping:
        yaml: "application/x-yaml"
        password: "application/base64"
      polling-strategy:
        type: "regular"
        properties:
          interval: "PT15S"
  - type: "url"
    properties:
      url: "http://config-service/my-config"
      media-type: "application/hocon"
      optional: true
      retry-policy:
        type: "repeat"
        properties:
          retries: 3
  - type: "file"
    properties:
      optional: true
      path: "conf/env.yaml"
      change-watcher:
        type: "file"
        properties:
          delay-millis: 5000
  - type: "prefixed"
    properties:
      key: "app"
      type: "classpath"
      properties:
        resource: "app.conf"
  - type: "classpath"
    properties:
      resource: "application.conf"
```
<!--@mdc :: -->

Note that the example shows how your profile can configure optional features
such as polling strategies and retry policies for config sources.

#### Support for Custom Sources

Profiles can be used to set up custom config sources as well as the built-in
ones described above.

Implement the `ConfigSourceProvider`

```java
public class MyConfigSourceProvider implements ConfigSourceProvider {
    private static final String TYPE = "my-type";

    @Override
    public boolean supports(String type) {
        return TYPE.equals(type);
    }

    @Override
    public ConfigSource create(String type, Config metaConfig) {
        // as we only support one in this implementation, we can just return it
        return MyConfigSource.create(metaConfig);
    }

    @Override
    public Set<String> supported() {
        return Set.of(TYPE);
    }
}
```

Register it as a java service loader service

File `META-INF/services/io.helidon.config.spi.ConfigSourceProvider`:

```text [META-INF/services/io.helidon.config.spi.ConfigSourceProvider]
io.helidon.examples.MyConfigSourceProvider
```

Now you can use the following profile:

```yaml
sources:
  - type: "system-properties"
  - type: "environment-variables"
  - type: "my-type"
    properties:
        my-property: "some-value"
```

Note that it is the `io.helidon.config.AbstractConfigSource` class that provides
support for polling strategies, change watchers, and retry policies. If you
create custom config sources that should also offer this support be sure they
extend `AbstractConfigSource` and implement appropriate SPI interfaces (such as
`io.helidon.config.spi.WatchableSource`) to support such features.

#### Support for Custom Polling Strategies, Change Watchers, and Retry Policies

Your config profile can include the set-up for polling strategies, change
watchers, and retry policies if the config source supports them. Declare them in
a way similar to how you declare the config sources themselves: by `type` and
with accompanying `properties`.

Config Profile Support for Built-in Polling Strategies:

| Strategy Type | Usage                                                                                         | Properties                                                                                  |
|---------------|-----------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| `regular`     | Periodic polling - See [<code>Polling<wbr>Strategies.regular</code>][pollingstrategie] method | `interval` (`Duration`) - indicating how often to poll; e.g., `PT15S` represents 15 seconds |


Config Profile Support for Built-in Change Watchers:
<table>
<thead>
<tr>
<th>Type</th>
<th>Usage</th>
<th>Properties</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>file</code></td>
<td>Filesystem monitoring - See <a href="https://helidon.io/docs/v4/apidocs/io.helidon.config/io/helidon/config/FileSystemWatcher.html"><code>File<wbr>System<wbr>Watcher</code></a> class</td>
<td><code>initial-delay-millis</code> - delay between the start of the watcher and first check for changes
<code>delay-millis</code> - how often do we check the watcher service for changes</td>
</tr>
</tbody>
</table>

Config Profile Support for Built-in Retry Policies:
<table>
<thead>
<tr>
<th>Policy Type</th>
<th>Usage</th>
<th>Properties</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>repeat</code></td>
<td>Regularly-scheduled - see <a href="https://helidon.io/docs/v4/apidocs/io.helidon.config/io/helidon/config/RetryPolicies.html#repeat(int)"><code>Retry<wbr>Policies.<wbr>repeat</code></a>.</td>
<td><code>retries</code> (<code>int</code>) - number of retries to perform<br />

Optional:
<ul>
<li><code>delay</code> (<code>Duration</code>) - initial delay between retries</li>
<li><code>delay-factor</code> (<code>double</code>) - <code>delay</code> is repeatedly multiplied by this each retry to compute the delay for each successive retry</li>
<li><code>call-timeout</code> (<code>Duration</code>) - timeout for a single invocation to load the source</li>
<li><code>overall-timeout</code> (<code>Duration</code>) - total timeout for all retry calls and delays</li>
</ul></td>
</tr>
</tbody>
</table>

To specify a custom polling strategy or custom retry policy, implement the
interface (`io.helidon.config.spi.PollingStrategy`,
`io.helidon.config.spi.ChangeWatcher`, or `io.helidon.config.spi.RetryPolicy`),
and then implement the provider interface
(`io.helidon.config.spi.PollingStrategyProvider`,
`io.helidon.config.spi.ChangeWatcherProvider`, or
`io.helidon.config.spi.RetryPolicyProvider`) to enable your custom
implementations for profiles. You can then use any custom properties - these are
provided as a `Config` instance to the `create` method of the Provider
implementation.

See [`RetryPolicy`][retrypolicy], [`ChangeWatcher`][changewatcher], and
[`PollingStrategy`][pollingstrategy] Javadoc sections.

## Declarative

When using Helidon Declarative programming model (inversion of control,
injection, annotation based), we may still be interested in the profile feature.

The interaction is as follows:

1.  When config profile is defined, only sources from the profile are added
    (aligned with SE Imperative programming model)
2.  When not using a config profile, `ConfigSource` services are discovered from
    the service registry, i.e. you can create a custom config source as a
    `@Service.Singleton`
3.  If you want to have a config source that works both with a config profile,
    and with the default config instance, there is a solution (see below)
4.  You can also define a `ConfigSourceProvider` as a registry service (and this
    will work the same as in SE imperative)

### Designing a config source that integrates with profiles and default config

Note that this approach will only work if you get a `Config` instance from the
service registry, or if you inject a `Config` instance into your service and the
service registry creates the instance.

Choose a type that does not conflict with existing config source types, let’s
assume `my-config-type`.

Create a `@Service.Singleton` service that is named with the chosen type.

Inject a named `Optional<io.helidon.config.MetaConfig>` into your config source
service - this will contain meta-config from the config profile, or it will be
empty if a profile is not used.

Create your configuration tree as needed in your custom config source.

Example of such a config source:

```java
@Service.Singleton
@Service.Named(MyProfiledConfigSource.MY_TYPE)
public class MyProfiledConfigSource implements NodeConfigSource {
    static final String MY_TYPE = "my-config-type";

    private final String value;

    MyProfiledConfigSource(@Service.Named(MY_TYPE) Optional<MetaConfig> metaConfig) {
        this.value = metaConfig.flatMap(it -> it.metaConfiguration()
                        .get("app.value2")
                        .asString()
                        .asOptional())
                .orElse("meta-config-value-not-found");
    }

    @Override
    public Optional<ConfigContent.NodeContent> load() throws ConfigException {
        return Optional.of(ConfigContent.NodeContent.builder()
                                   .node(ConfigNode.ObjectNode.builder()
                                                 .addValue("app.value2", value)
                                                 .build())
                                   .build());
    }
}
```

[fileconfigsource]: https://helidon.io/docs/v4/apidocs/io.helidon.config/io/helidon/config/FileConfigSource.Builder.html
[pollingstrategie]: https://helidon.io/docs/v4/apidocs/io.helidon.config/io/helidon/config/PollingStrategies.html#regular(java.time.Duration)
[retrypolicy]: https://helidon.io/docs/v4/apidocs/io.helidon.config/io/helidon/config/spi/RetryPolicy.html
[changewatcher]: https://helidon.io/docs/v4/apidocs/io.helidon.config/io/helidon/config/spi/ChangeWatcher.html
[pollingstrategy]: https://helidon.io/docs/v4/apidocs/io.helidon.config/io/helidon/config/spi/PollingStrategy.html
