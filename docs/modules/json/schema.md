<!--@frontmatter
description: "Helidon JSON Schema"
-->
# JSON Schema

## Overview

JSON Schema is a specification for describing the structure and validation rules
of JSON data. It lets you define what properties are required, their types,
allowed values, and more. By using JSON Schema, you can validate that incoming
or outgoing JSON matches the expected contract, provide clear documentation for
APIs, and enable tooling support such as code generation and auto-completion.

Helidon provides two complementary ways to work with JSON Schema.

- In the declarative approach, you describe the schema using annotations in a
  [`JsonSchema`][jsonschema] class.
- In the imperative approach, you build the schema programmatically with the
  fluent [`Schema`][schema] builder API.

Helidon currently supports only schema generation.

## Maven Coordinates

To enable JSON Schema, add the following dependency to your project’s `pom.xml`
(see [Managing Dependencies](../../dependency-management.md)).

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.json.schema</groupId>
  <artifactId>helidon-json-schema</artifactId>
</dependency>
```

## Usage

### Imperative Schema Creation

The entry point for each runtime JSON schema creation is a [`Schema`][schema]
class. The imperative approach gives you full programmatic control over JSON
Schema creation. Using the fluent Schema builder API, you can construct schemas
step by step, configure properties, and apply constraints directly in code. This
is useful when schemas need to be generated dynamically or when fine-grained
customization is required.

```java
Schema.builder()
        .rootObject(builder -> builder.description("Example JSON Schema")
                .addIntegerProperty("exampleProperty", intBuilder -> intBuilder
                        .minimum(0)
                        .defaultValue(JsonNumber.create(0))))
        .build();
```

Once the [`Schema`][schema] object is created, you can generate the JSON Schema
as a String. The result looks like this:

```json
{
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "description": "Example JSON Schema",
    "type": "object",
    "properties": {
        "exampleProperty": {
            "default": 0,
            "type": "integer",
            "minimum": 0
        }
    }
}
```

The JSON Schema 2020-12
[`default` keyword](https://json-schema.org/draft/2020-12/draft-bhutton-json-schema-validation-01#section-9.2)
is metadata associated with a schema. Helidon preserves the value in the
generated schema, but does not use it to populate a missing value in a JSON
instance or change whether a property is required. A default can be any JSON
value, including `null`, arrays, and objects. The specification recommends, but
does not require, that the default validate against its schema.

With the imperative API, pass an appropriate [`JsonValue`][jsonvalue] to the
`defaultValue` builder method.

### Declarative Schema Creation

The declarative approach lets you define JSON Schema through annotations in a
[`JsonSchema`][jsonschema] class. At compile time, the
`helidon-json-schema-codegen` generator processes these annotations and produces
a class containing the schema definition. This approach keeps your schema
definitions close to your data model and ensures schemas are generated
automatically without manual coding.

<!--@mdc ::code-callout -->
```java
@JsonSchema.Schema // <1>
@JsonSchema.Description("Example JSON Schema")
public record ExampleSchema(@JsonSchema.DefaultInt(0) @JsonSchema.Integer.Minimum(0) int exampleProperty) {
}
```
1. Schema defining annotation. Without this annotation the class/record will not
   be processed as a JSON schema
<!--@mdc :: -->

The declarative API uses typed annotations so the generated JSON type is clear
from the Java source. Use `@JsonSchema.Default` for strings and the
`DefaultInt`, `DefaultLong`, `DefaultDouble`, and `DefaultBoolean` variants for
the corresponding Java primitive types. For example,
`@JsonSchema.Default("0")` produces the JSON string `"0"`, while
`@JsonSchema.DefaultInt(0)` produces the JSON number `0`.

Defaults such as `null`, arrays, objects, and numbers outside the ranges of the
typed annotations can use `@JsonSchema.DefaultJson`. Its value must contain
exactly one valid JSON value; for example,
`@JsonSchema.DefaultJson("{\"enabled\":true}")`.

In addition, the following section must be added to the `build` of the Maven
`pom.xml` to enable annotation processors that generate the necessary code:

```xml [pom.xml]
<plugins>
  <plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
      <annotationProcessorPaths>
        <path>
          <groupId>io.helidon.bundles</groupId>
          <artifactId>helidon-bundles-apt</artifactId>
          <version>${helidon.version}</version>
        </path>
      </annotationProcessorPaths>
    </configuration>
  </plugin>
</plugins>
```

Once compiled, the class with the following name will be generated
`ExampleSchema__JsonSchema`. This class contains the String format of the schema
and is automatically discovered via ServiceRegistry. Because of that, it is
possible to inject the [`Schema`][schema] with the
[`@Service.Named`][service-named] and desired class (such as
`ExampleSchema.class`) as a value.

```java
public void myMethod(@Service.Named(ExampleSchema.class) Schema schema) {
    //...
}
```

Or obtain it over the static `find` method on the [`Schema`][schema] class. This
methods searches the ServiceRegistry for a Schema bound to the provided class
over the parameter.

```java
Schema.find(MyClass.class);
```

[jsonschema]: https://helidon.io/docs/v27/apidocs/io.helidon.json.schema/io/helidon/json/schema/JsonSchema.html
[jsonvalue]: https://helidon.io/docs/v27/apidocs/io.helidon.json/io/helidon/json/JsonValue.html
[schema]: https://helidon.io/docs/v27/apidocs/io.helidon.json.schema/io/helidon/json/schema/Schema.html
[service-named]: https://helidon.io/docs/v27/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.Named.html
