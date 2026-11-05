<!--@frontmatter
description: "Additional Supported Formats and Sources"
-->
# Supported Formats

## Overview

Helidon Config provides several extension modules that support other
configuration formats (parsers) and sources. This document describes how to
include them and use them in your project. In each case you need to add module
dependencies to your project and, in some cases, write your application
accordingly.

## Additional Formats

### File Type Handling

With each of the parsers described here, your application can either

1.  explicitly add a parser of the correct implementation to the
    `Config.Builder`, or
2.  rely on Java service loading and the config system’s matching of file types
    and media types to parsers.

If your application creates a `Config.Builder` with parser services *disabled*
(see [`disableParserServices`][disableparserser] then that builder will not find
the Java services for the various parsers and so will be unable to match the
file type or media type of sources with the corresponding parser automatically.
So if you want to use automatic type matching with a given builder, do not
invoke `Config.Builder.disableParserServices()`.

### YAML

#### Maven Coordinates

Add the following dependency in your project:

Config YAML Dependency in pom.xml:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.config</groupId>
  <artifactId>helidon-config-yaml</artifactId>
</dependency>
```

#### Using the YAML Parser

The YAML parser handles the following media type:

- `application/x-yaml` - YAML format (file type `.yaml`)

Automatic selection:

<!--@mdc ::code-callout -->
```java
Config config = Config.create(classpath("application.yaml")); // <1>
```
1. The config system automatically maps the file type `.yaml` to the media type
   `application/x-yaml` which the Helidon YAML parser matches.
<!--@mdc :: -->

<!--@mdc ::code-callout -->
```java
Config config = Config.create(classpath("my-config") // <1>
    .parser(YamlConfigParser.create())); // <2>
```
1. The media type of the source `my-config` is unknown, so the config system
   cannot choose a parser automatically.
2. The config system will parse the resource `my-config` on the runtime
   classpath using the YAML parser instance created by the `YamlConfigParser`.
   The `create()` method creates a config parser with default behavior.
<!--@mdc :: -->

<!--@mdc ::code-callout -->
```java
Config config = Config.create(classpath("my-config") // <1>
    .mediaType(MediaTypes.APPLICATION_X_YAML)); // <2>
```
1. The media type of the source `my-config` is unknown, so the config system
   cannot choose a parser automatically.
2. Specifying the media type for the config source allows the config system to
   use its matching algorithm with the available parsers to choose a parser for
   that type.
<!--@mdc :: -->

<!--@mdc ::code-callout -->
```java
Config config = Config.builder(classpath("application.yaml"))
    .disableParserServices() // <1>
    .addParser(YamlConfigParser.create()) // <2>
    .build();
```
1. Disables automatic parser lookup and registration.
2. Explicit registration of the YAML parser is therefore required.
<!--@mdc :: -->

#### Literal YAML Keys

By default, the YAML parser interprets map keys as Helidon configuration paths.
For example, both `a.b: value` and `"a.b": value` create a node `b` below `a`.
The `~1` and `~0` sequences represent a dot and a tilde, respectively. Quoting
a YAML key does not change this behavior.

To preserve map keys as literal names, enable `literalKeys` on the
[`YamlConfigParser`][yamlconfigparser] builder and register the configured parser:

```java
YamlConfigParser parser = YamlConfigParser.builder()
        .literalKeys(true)
        .build();
Config config = Config.create(ConfigSources.classpath("application.yaml")
        .parser(parser));
```

Alternatively, register it with `Config.builder().addParser(parser)` when
building configuration from YAML sources. The option defaults to `false`;
the constructor, `create()`, and service-loaded parser retain their existing
path interpretation.

Literal keys apply recursively to all YAML maps, regardless of quoting or
whether a key contains a scalar, map, or list. Normal YAML nesting and list
indexes still define the tree structure:

```yaml [application.yaml]
a.b: literal dot
a~1b: literal escape sequence
a:
  b: nested value
group.name:
  child.name: nested literal name
items.list:
  - item.name: list value
```

With literal keys enabled, read these values using encoded Helidon lookup keys:

```java
config.get("a~1b").asString().get();                 // literal dot
config.get("a~01b").asString().get();               // literal escape sequence
config.get("a.b").asString().get();                 // nested value
config.get("group~1name.child~1name").asString().get(); // nested literal name
config.get("items~1list.0.item~1name").asString().get(); // list value
```

The parser escapes each source name once. Write the actual name in YAML;
use `~1` for a dot and `~0` for a tilde in lookup keys. For example, a literal
source name `a~b` is retrieved with `config.get("a~0b")`. See
[Source Names and Lookup Keys](hierarchical-features.md#source-names-and-lookup-keys).

The builder also accepts a configuration node containing `literal-keys`:

```properties [parser.properties]
yaml.literal-keys=true
```

```java
Config bootstrap = Config.create(ConfigSources.classpath("parser.properties"));
YamlConfigParser parser = YamlConfigParser.builder()
        .config(bootstrap.get("yaml"))
        .build();
Config config = Config.create(ConfigSources.classpath("application.yaml")
        .parser(parser));
```

The application must load these bootstrap settings separately and pass the
component node to the parser builder. A YAML source cannot configure its own
parser, and metaconfiguration does not forward this option automatically.
See the [YAML parser configuration reference](../../config/io.helidon.config.yaml.YamlConfigParser.md).

### HOCON/JSON

The Helidon HOCON config module handles sources in the HOCON and JSON formats.

#### Maven Coordinates

Add the following dependency in your project:

Config HOCON Dependency in pom.xml:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.config</groupId>
  <artifactId>helidon-config-hocon</artifactId>
</dependency>
```

#### Using the HOCON/JSON Parser

The parser handles the following media types:

- `application/hocon` - HOCON format (file type `.conf`)
- `application/json` - JSON format (file type `.json`)

Automatic selection:

<!--@mdc ::code-callout -->
```java
Config config = Config.create(classpath("application.conf")); // <1>
```
1. The config system automatically maps the file type `.conf` to the media type
   `application/hocon` which the Helidon HOCON parser matches.
<!--@mdc :: -->

The same module and parser supports file type `.json` and the media type
`application/json`.

HOCON parser specified - no file type on source:

<!--@mdc ::code-callout -->
```java
Config config = Config.create(classpath("my-config") // <1>
    .parser(HoconConfigParser.create())); // <2>
```
1. the media type of the source `my-config` is unknown, so the config system
   cannot choose a parser automatically.
2. The config system will parse the resource `my-config` using the HOCON parser
   created by the [HoconConfigParser][hoconconfigparse]. The `create()` method
   creates a config parser with default behavior.
<!--@mdc :: -->

Media type specified:

<!--@mdc ::code-callout -->
```java
Config config = Config.create(classpath("my-config") // <1>
    .mediaType(MediaTypes.APPLICATION_HOCON)); // <2>
```
1. The media type of the source `my-config` is unknown, so the config system
   cannot choose a parser automatically.
2. Specifying the media type for the config source allows the config system to
   use its matching algorithm with the available parsers to choose a parser for
   that type.
<!--@mdc :: -->

HOCON parser specified because parser services disabled:

<!--@mdc ::code-callout -->
```java
Config config = Config.builder(classpath("application.conf"))
    .disableParserServices() // <1>
    .addParser(HoconConfigParser.create()) // <2>
    .build();
```
1. Disables automatic parser lookup and registration.
2. Explicit registration of the HOCON parser is therefore required.
<!--@mdc :: -->

Customized HOCON parser:

<!--@mdc ::code-callout -->
```java
Config config = Config.builder(classpath("application.conf"))
    .disableParserServices()
    .addParser(HoconConfigParser.builder() // <1>
        .resolvingEnabled(false) // <2>
        .build()) // <3>
    .build();
```
1. Creates new instance of the parser builder.
2. Disables full HOCON substitution resolution. The parser might still use
   local HOCON resolution to materialize merges and self-references from the
   parsed source and its includes; unresolved required substitutions are
   preserved for later Helidon Config resolution. (See the [HOCON
   documentation][hocon-documentat].)
3. Builds a new instance of the HOCON config parser.
<!--@mdc :: -->

You can also specify `ConfigResolveOptions` using the
`HoconConfigParser.builder().resolveOptions` method.


[disableparserser]: https://helidon.io/docs/core/v28/apidocs/io.helidon.config/io/helidon/config/Config.Builder.html#disableParserServices()
[yamlconfigparser]: https://helidon.io/docs/core/v28/apidocs/io.helidon.config.yaml/io/helidon/config/yaml/YamlConfigParser.html
[hoconconfigparse]: https://helidon.io/docs/core/v28/apidocs/io.helidon.config.hocon/io/helidon/config/hocon/HoconConfigParser.html
[hocon-documentat]: https://github.com/lightbend/config/blob/master/HOCON.md#substitutions
[configresolveopt]: https://github.com/lightbend/config/blob/master/config/src/main/java/com/typesafe/config/ConfigResolveOptions.java
