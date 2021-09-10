# Configuration metadata

A new module `helidon-config-metadata` now exists with annotations that can be used in Helidon source code
the document what configuration is used.

These annotations are processed by `helidon-config-metadata-processor`.

## Add metadata to a configurable component

To add meta configuration:

1. Add the following dependency to your `pom.xml`:
```xml
<dependency>
    <groupId>io.helidon.config</groupId>
    <artifactId>helidon-config-metadata</artifactId>
    <scope>provided</scope>
    <optional>true</optional>
</dependency>
```
2. Add the following compiler plugin configuration to add the annotation processor:
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <forceJavacCompilerUse>true</forceJavacCompilerUse>
                <annotationProcessorPaths>
                    <path>
                        <groupId>io.helidon.config</groupId>
                        <artifactId>helidon-config-metadata-processor</artifactId>
                        <version>${helidon.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```
3. Update the `module-info.java` by adding 
```java
requires static io.helidon.config.metadata;
```
4. Annotate the configured class using `@Configured` - usually the builder class. If there is only a factory method, annotate 
     the class containing the factory method
5. Annotate builder methods using `@ConfiguredOption` - the type of the parameter will be used as type of the property, provides 
     full customization using annotation properties
6. In case a factory method is the only one available, annotate it with repeating `@ConfiguredOption` to list all annotations
7. Look at existing examples if in doubt
8. Check the output in `target/classes/META-INF/helidon` to see what was generated

## Output file format

The file is `META-INF/helidon/config-metadata.json`


### Root Element
Root of the file is an array of module objects

```json
[
     {
          "module": ""
     }
]
```

### Module element
Module is equivalent to `module-info.java`. This approach allows merging of multiple metadata files into a single
file.

```json
{
"module": "module name (from module-info.java)",
"types" : []
}
```

### Type element
Each type represents a configurable unit.

"type":
```json
{
     "type": "fully qualified type name of the configured class (the class built by builder, or created by factory method)",
     "standalone": true,
     "prefix": "server",
     "description": "Documentation of this type",
     "producers": ["methods"],
     "inherits": ["fully qualified type names of superclasses/interfaces this type extends/implements"],
     "provides": ["fully qualified type names of provided services"],
     "options": []
}
```

prefix - configuration prefix, if standalone, this is the root configuration key (such as `server`, `tracing`),
     if nested, this is a prefix added before all options of this type (such as each Security provider)
standalone - if set to true, this type is configurable in the root of configuration (such as webserver, security, tracing, metrics),
     otherwise it is a nested type in another configuration (such as WebServerTls, OidcConfig)
     default is `false` (if not defined in document)
producers (methods in general) - format is "fully qualified type name#method name (method parameter types)", example:
     `io.helidon.security.providers.common.OutboundTarget.Builder#build()`

### Option element
Each option is one key in configuration. Option can either be a simple value (String, Long, URI), or a complex value
defined by another type (nested types).

```json
{
     "key": "key in configuration (may be missing, if this merges with parent)",
     "type": "fully qualified type of the configuration option, defaults to java.lang.String",
     "description": "description of this configuration node (expected to be non-empty)",
     "kind": "LIST|MAP|VALUE",
     "method": "annotated method",
     "merge": true,
     "experimental": true,
     "required": true,
     "provider": true,
     "deprecated": true,
     "defaultValue": "string default value (only for value nodes)"
     "allowedValues": []
}
```
key - there is a special case when we need to define a list on a method that does not use a nested type
     in such a case, the key contains `*` to mark a list. Example: `secrets.*.provider`, `secrets.*.config`
     would result in:
     ```yaml
          secrets:
               - provider: "provider"
                 config: "something"
     ```
type - either a type directly mapped to an object (String, Integer, URI etc.), or a nested type defined either in 
     this or in another module. Nested configuration may be in another metadata json.
kind LIST: this is a list of values (either simple values, or objects)
kind MAP: this is a map of values, using keys of the map as keys, and values of the map as values, `type` defines 
     the value type (must be a simple value), key is expected to be `java.lang.String`
kind VALUE: either a simple value (String, Long etc.) or a nested object (this is the default)
method: method to be called to configure this option (may be a static factory method, such as `create(Config)`, or a builder method)
merge: if set to `true` (default is `false`) this option's key is ignored and all its nested keys are inserted
     directly into the parent option
experimental: experimental options may change without warning even between minor releases (such as support for Loom in Java)
required: if set to `true` (default is `false`) this option must be present in configuration if parent option is present
provider: this option expects a type with matching `provides` - such as security providers configuration
deprecated: this option should no longer be used, there is another option allowing the same (usually when renaming options,
     fixing inconsistencies etc.); description should provide more details
defaultValue: for simple values, this defines the string default value

Example of a simple optional element with defa# Configuration metadata

A new module `helidon-config-metadata` now exists with annotations that can be used in Helidon source code
the document what configuration is used.

These annotations are processed by `helidon-config-metadata-processor`.

## Add metadata to a configurable component

To add meta configuration:

1. Add the following dependency to your `pom.xml`:
```xml
<dependency>
    <groupId>io.helidon.config</groupId>
    <artifactId>helidon-config-metadata</artifactId>
    <scope>provided</scope>
    <optional>true</optional>
</dependency>
```
2. Add the following compiler plugin configuration to add the annotation processor:
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <forceJavacCompilerUse>true</forceJavacCompilerUse>
                <annotationProcessorPaths>
                    <path>
                        <groupId>io.helidon.config</groupId>
                        <artifactId>helidon-config-metadata-processor</artifactId>
                        <version>${helidon.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```
3. Update the `module-info.java` by adding
```java
requires static io.helidon.config.metadata;
```
4. Annotate the configured class using `@Configured` - usually the builder class. If there is only a factory method, annotate
   the class containing the factory method
5. Annotate builder methods using `@ConfiguredOption` - the type of the parameter will be used as type of the property, provides
   full customization using annotation properties
6. In case a factory method is the only one available, annotate it with repeating `@ConfiguredOption` to list all annotations
7. Look at existing examples if in doubt
8. Check the output in `target/classes/META-INF/helidon` to see what was generated

## Output file format

The file is `META-INF/helidon/config-metadata.json`


### Root Element
Root of the file is an array of module objects

```json
[
     {
          "module": ""
     }
]
```

### Module element
Module is equivalent to `module-info.java`. This approach allows merging of multiple metadata files into a single
file.

```json
{
"module": "module name (from module-info.java)",
"types" : []
}
```

### Type element
Each type represents a configurable unit.

"type":
```json
{
     "type": "fully qualified type name of the configured class (the class built by builder, or created by factory method)",
     "standalone": true,
     "prefix": "server",
     "description": "Documentation of this type",
     "producers": ["methods"],
     "inherits": ["fully qualified type names of superclasses/interfaces this type extends/implements"],
     "provides": ["fully qualified type names of provided services"],
     "options": []
}
```

prefix - configuration prefix, if standalone, this is the root configuration key (such as `server`, `tracing`),
if nested, this is a prefix added before all options of this type (such as each Security provider)
standalone - if set to true, this type is configurable in the root of configuration (such as webserver, security, tracing, metrics),
otherwise it is a nested type in another configuration (such as WebServerTls, OidcConfig)
default is `false` (if not defined in document)
producers (methods in general) - format is "fully qualified type name#method name (method parameter types)", example:
`io.helidon.security.providers.common.OutboundTarget.Builder#build()`

### Option element
Each option is one key in configuration. Option can either be a simple value (String, Long, URI), or a complex value
defined by another type (nested types).

```json
{
     "key": "key in configuration (may be missing, if this merges with parent)",
     "type": "fully qualified type of the configuration option, defaults to java.lang.String",
     "description": "description of this configuration node (expected to be non-empty)",
     "kind": "LIST|MAP|VALUE",
     "method": "annotated method",
     "merge": true,
     "experimental": true,
     "required": true,
     "provider": true,
     "deprecated": true,
     "defaultValue": "string default value (only for value nodes)"
     "allowedValues": []
}
```
key - there is a special case when we need to define a list on a method that does not use a nested type
in such a case, the key contains `*` to mark a list. Example: `secrets.*.provider`, `secrets.*.config`
would result in:
```yaml
secrets:
- provider: "provider"
config: "something"
```
type - either a type directly mapped to an object (String, Integer, URI etc.), or a nested type defined either in
this or in another module. Nested configuration may be in another metadata json.
kind LIST: this is a list of values (either simple values, or objects)
kind MAP: this is a map of values, using keys of the map as keys, and values of the map as values, `type` defines
the value type (must be a simple value), key is expected to be `java.lang.String`
kind VALUE: either a simple value (String, Long etc.) or a nested object (this is the default)
method: method to be called to configure this option (may be a static factory method, such as `create(Config)`, or a builder method)
merge: if set to `true` (default is `false`) this option's key is ignored and all its nested keys are inserted
directly into the parent option
experimental: experimental options may change without warning even between minor releases (such as support for Loom in Java)
required: if set to `true` (default is `false`) this option must be present in configuration if parent option is present
provider: this option expects a type with matching `provides` - such as security providers configuration
deprecated: this option should no longer be used, there is another option allowing the same (usually when renaming options,
fixing inconsistencies etc.); description should provide more details
defaultValue: for simple values, this defines the string default value

Example of a simple optional option with String type:
```json
{
    "key": "default-authorization-provider",
    "description": "ID of the default authorization provider",
    "method": "io.helidon.security.Security.Builder#authorizationProvider(io.helidon.security.spi.AuthorizationProvider)"
}
```

Example of an option with explicit type and default value:
```json
{
    "key": "enabled",
    "type": "java.lang.Boolean",
    "description": "Security can be disabled using configuration, or explicitly.\n By default, security instance is enabled.\n Disabled security instance will not perform any checks and allow\n all requests.",
    "defaultValue": "true",
    "method": "io.helidon.security.Security.Builder#enabled(boolean)"
}
```

Example of a nested configuration option:
```json
{
    "key": "environment.server-time",
    "type": "io.helidon.security.SecurityTime",
    "description": "Server time to use when evaluating security policies that depend on time.",
    "method": "io.helidon.security.Security.Builder#serverTime(io.helidon.security.SecurityTime)"
}
```

### AllowedValue element
Allowed value defines a fixed set of allowed values for a configuration option.

```json
{
     "value": "a permissible value",
     "description": "description of the value, may be empty if this is a generated value from enum that is not part of this module"
}
```

Example of an option with allowed values:
```json
{
     "key": "provider-policy.type",
     "type": "io.helidon.security.ProviderSelectionPolicyType",
     "description": "Type of the policy.",
     "defaultValue": "FIRST",
     "method": "io.helidon.security.Security.Builder#providerSelectionPolicy(java.util.function.Function<io.helidon.security.spi.ProviderSelectionPolicy.Providers,io.helidon.security.spi.ProviderSelectionPolicy>)",
     "allowedValues": [
          {
               "value": "FIRST",
               "description": "Choose first provider from the list by default.\n Choose provider with the name defined when explicit provider requested."
          },
          {
               "value": "COMPOSITE",
               "description": "Can compose multiple providers together to form a single\n logical provider."
          },
          {
               "value": "CLASS",
               "description": "Explicit class for a custom ProviderSelectionPolicyType."
          }
     ]
}
```ults:
```json
{
    "key": "default-authorization-provider",
    "description": "ID of the default authorization provider",
    "method": "io.helidon.security.Security.Builder#authorizationProvider(io.helidon.security.spi.AuthorizationProvider)"
}
```

### AllowedValue element
Allowed value defines a fixed set of allowed values for a configuration option.

```json
{
     "value": "a permissible value",
     "description": "description of the value, may be empty if this is a generated value from enum that is not part of this module"
}
```

Example of an option with allowed values:
```json
{
    "key": "provider-policy.type",
    "type": "io.helidon.security.ProviderSelectionPolicyType",
    "description": "Type of the policy.",
    "defaultValue": "FIRST",
    "method": "io.helidon.security.Security.Builder#providerSelectionPolicy(java.util.function.Function<io.helidon.security.spi.ProviderSelectionPolicy.Providers,io.helidon.security.spi.ProviderSelectionPolicy>)",
    "allowedValues": [
        {
            "value": "FIRST",
            "description": "Choose first provider from the list by default.\n Choose provider with the name defined when explicit provider requested."
        },
        {
            "value": "COMPOSITE",
            "description": "Can compose multiple providers together to form a single\n logical provider."
        },
        {
            "value": "CLASS",
            "description": "Explicit class for a custom ProviderSelectionPolicyType."
        }
    ]
}
```