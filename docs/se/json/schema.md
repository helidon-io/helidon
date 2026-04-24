# JSON Schema

## Overview

JSON Schema is a specification for describing the structure and validation rules of JSON data. It lets you define what properties are required, their types, allowed values, and more. By using JSON Schema, you can validate that incoming or outgoing JSON matches the expected contract, provide clear documentation for APIs, and enable tooling support such as code generation and auto-completion.

Helidon provides two complementary ways to work with JSON Schema.

- In the declarative approach, you describe the schema using annotations in a [`JsonSchema`](/apidocs/io.helidon.json.schema/io/helidon/json/schema/JsonSchema.html) class.
- In the imperative approach, you build the schema programmatically with the fluent [`Schema`](/apidocs/io.helidon.json.schema/io/helidon/json/schema/Schema.html) builder API.

Helidon currently supports only schema generation.

## Maven Coordinates

To enable JSON Schema, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

```xml
<dependency>
    <groupId>io.helidon.json.schema</groupId>
    <artifactId>helidon-json-schema</artifactId>
</dependency>
```

## Usage

### Imperative Schema Creation

The entry point for each runtime JSON schema creation is a [`Schema`](/apidocs/io.helidon.json.schema/io/helidon/json/schema/Schema.html) class. The imperative approach gives you full programmatic control over JSON Schema creation. Using the fluent Schema builder API, you can construct schemas step by step, configure properties, and apply constraints directly in code. This is useful when schemas need to be generated dynamically or when fine-grained customization is required.

```java
Schema.builder()
        .rootObject(builder -> builder.description("Example JSON Schema")
                .addIntegerProperty("exampleProperty", intBuilder -> intBuilder.minimum(0)))
        .build();
```

Once the [`Schema`](/apidocs/io.helidon.json.schema/io/helidon/json/schema/Schema.html) object is created, you can generate the JSON Schema as a String. The result looks like this:

```json
{
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "description": "Example JSON Schema",
    "type": "object",
    "properties": {
        "exampleProperty": {
            "type": "integer",
            "minimum": 0
        }
    }
}
```

### Declarative Schema Creation

The declarative approach lets you define JSON Schema through annotations in a [`JsonSchema`](/apidocs/io.helidon.json.schema/io/helidon/json/schema/JsonSchema.html) class. At compile time, the `helidon-json-schema-codegen` generator processes these annotations and produces a class containing the schema definition. This approach keeps your schema definitions close to your data model and ensures schemas are generated automatically without manual coding.

```java
@JsonSchema.Schema 
@JsonSchema.Description("Example JSON Schema")
public record ExampleSchema(@JsonSchema.Integer.Minimum(0) int exampleProperty) {
}
```

- Schema defining annotation. Without this annotation the class/record will not be processed as a JSON schema

In addition, the following section must be added to the `build` of the Maven `pom.xml` to enable annotation processors that generate the necessary code:

```xml
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

Once compiled, the class with the following name will be generated `ExampleSchema__JsonSchema`. This class contains the String format of the schema and is automatically discovered via ServiceRegistry. Because of that, it is possible to inject the [`Schema`](/apidocs/io.helidon.json.schema/io/helidon/json/schema/Schema.html) with the [`@Service.Named`](/apidocs/io.helidon.service.registry/io/helidon/service/registry/Service.Named.html) and desired class (such as `ExampleSchema.class`) as a value.

```java
public void myMethod(@Service.Named(ExampleSchema.class) Schema schema) {
    //...
}
```

Or obtain it over the static `find` method on the [`Schema`](/apidocs/io.helidon.json.schema/io/helidon/json/schema/Schema.html) class. This methods searches the ServiceRegistry for a Schema bound to the provided class over the parameter.

```java
Schema.find(MyClass.class);
```
