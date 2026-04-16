# JSON Processing in Helidon SE

## Overview

Helidon provides comprehensive JSON processing capabilities through two core modules that work together to offer efficient, streaming JSON processing optimized for virtual threads and modern Java applications.

## JSON Binding

The JSON Binding module (`helidon-json-binding`) provides high-level object serialization and deserialization.

### Maven Coordinates

``` xml
<dependency>
    <groupId>io.helidon.json</groupId>
    <artifactId>helidon-json-binding</artifactId>
</dependency>
```

### Features

- Automatic conversion between Java objects and JSON
- Custom serializers and deserializers for complex types
- Type-safe binding with generic support
- Extensive configuration and customizations

### Code Generation

#### What Code Generation Does and When to Use It

Code generation is how Helidon automatically supports your POJOs annotated with JSON binding annotations. At compile time, the processor generates efficient serializers/deserializers for classes annotated with [`@Json.Entity`](/apidocs/io.helidon.json.binding/io/helidon/json/binding/Json.Entity.html).

Use code generation if you want an automatic, annotation-driven mapping for your POJOs. If you do not enable code generation, you can still use JSON binding by implementing the conversion yourself (implement [`JsonSerializer`](/apidocs/io.helidon.json.binding/io/helidon/json/binding/JsonSerializer.html), [`JsonDeserializer`](/apidocs/io.helidon.json.binding/io/helidon/json/binding/JsonDeserializer.html), [`JsonConverter`](/apidocs/io.helidon.json.binding/io/helidon/json/binding/JsonConverter.html), or a [`JsonBindingFactory`](/apidocs/io.helidon.json.binding/io/helidon/json/binding/JsonBindingFactory.html)).

#### Enabling Code Generation

For automatic code generation, you can either add individual annotation processors or use the Helidon bundles APT dependency that includes all necessary processors.

#### Option 1: Using Helidon Bundles APT

Alternatively, you can use the `helidon-bundles-apt` dependency which includes the JSON code generation processor along with other Helidon annotation processors:

*Annotation processor configuration with bundles*

``` xml
<build>
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
</build>
```

#### Option 2: Individual Annotation Processors

Configure the annotation processors in your Maven build:

*Annotation processor configuration*

``` xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>io.helidon.codegen</groupId>
                        <artifactId>helidon-codegen-apt</artifactId>
                        <version>${helidon.version}</version>
                    </path>
                    <!-- To enable automatic converter discovery -->
                    <path>
                        <groupId>io.helidon.service</groupId>
                        <artifactId>helidon-service-codegen</artifactId>
                        <version>${helidon.version}</version>
                    </path>
                    <path>
                        <groupId>io.helidon.json.codegen</groupId>
                        <artifactId>helidon-json-codegen</artifactId>
                        <version>${helidon.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### JSON Binding

The main entry point for JSON binding is the [`JsonBinding`](/apidocs/io.helidon.json.binding/io/helidon/json/binding/JsonBinding.html) class.

The [`JsonBinding`](/apidocs/io.helidon.json.binding/io/helidon/json/binding/JsonBinding.html) API provides common serialization and deserialization methods:

*JsonBinding usage*

``` java
JsonBinding binding = JsonBinding.create();

// Deserialize JSON string to object
Person person = binding.deserialize("{\"name\":\"John\",\"age\":30}", Person.class);

// Serialize object to JSON string
String json = binding.serialize(person);
```

### Annotations

Helidon JSON binding provides annotations to control serialization and deserialization behavior.

#### @Json.Entity

Marks a class/record as a JSON entity that can be serialized/deserialized.

This annotation also lets you control how the binder discovers properties using the `accessorStyle` attribute. This is useful if your code uses fluent methods, record-style accessors, or non-standard naming.

*Using @Json.Entity*

``` java
@Json.Entity
class Person {
    private String name;
    private int age;

    // getters and setters
}
```

Why use it: Required for classes to participate in JSON binding. Without this annotation, automatic converter generation will not trigger.

#### @Json.Property

Customizes the JSON property name for a field or method. This affects how fields are named in JSON output and input, providing control over JSON structure and API compatibility.

*Basic property name customization*

``` java
@Json.Entity
class Person {
    private String firstName;

    @Json.Property("last_name")
    private String lastName;

    // getters and setters
}
```

This produces the following JSON output:

``` json
{"firstName":"John","last_name":"Doe"}
```

*Method-level property naming*

``` java
@Json.Entity
class Person {
    private String firstName;
    private String lastName;

    @Json.Property("fullName")
    public String getDisplayName() {
        return firstName + " " + lastName;
    }

    // getters and setters
}
```

This produces the following JSON output:

``` json
{"firstName":"John","lastName":"Doe","fullName":"John Doe"}
```

Why use it: Allows mapping between Java field names and JSON property names, enabling better JSON structure control and compatibility with existing APIs. Essential for maintaining API contracts when Java field names don’t match desired JSON structure.

#### @Json.Ignore

Excludes fields or methods from serialization/deserialization. Fields marked as `transient` are ignored automatically, or you can explicitly use this annotation. This affects field visibility in JSON output and input processing.

*Basic field exclusion*

``` java
@Json.Entity
class Person {
    private String name;
    private int age;

    @Json.Ignore
    private String password;

    // getters and setters
}
```

This produces the following JSON output:

``` json
{"name":"John","age":30}
```

*Automatic transient field exclusion*

``` java
@Json.Entity
class Person {
    private String name;
    private transient String temp;
    private String data;

    // getters and setters
}
```

This produces the following JSON output:

``` json
{"name":"John","data":"value"}
```

*Method-level exclusion*

``` java
@Json.Entity
class Person {
    private String firstName;
    private String lastName;

    @Json.Ignore
    public String getFirstName() {
        return firstName;
    }

    // other getters and setters
}
```

This produces the following JSON output:

``` json
{"lastName":"Doe"}
```

Why use it: Prevents sensitive data, computed fields, or internal state from being included in JSON output. Critical for security (excluding passwords, tokens) and performance (excluding large internal data structures).

#### @Json.Required

Marks properties as required during deserialization.

*Using @Json.Required*

``` java
@Json.Entity
class Person {
    @Json.Required
    private String name;

    private Integer age; // not required

    // getters and setters
}
```

Why use it: Ensures critical properties are present in JSON input, failing deserialization if they’re missing.

#### @Json.SerializeNulls

Controls whether null values are included in JSON output. Null values are omitted from JSON output, unless this annotation is used. This affects the visibility and size of generated JSON.

*Default behavior - nulls omitted*

``` java
@Json.Entity
class PersonDefault {
    private String name;
    private Integer age;

    // getters and setters
}
```

This produces the following JSON output:

``` json
{"name":"John"}
```

*Class-level null serialization*

``` java
@Json.Entity
@Json.SerializeNulls
class PersonWithNulls {
    private String name;
    private Integer age;

    // getters and setters
}
```

This produces the following JSON output:

``` json
{"name":"John","age":null}
```

*Field-level null serialization*

``` java
@Json.Entity
class PersonSelective {
    private String name;

    @Json.SerializeNulls
    private String city;

    // getters and setters
}
```

This produces the following JSON output:

``` json
{"name":"John","city":null}
```

*Mixed scenarios - different null handling*

``` java
@Json.Entity
@Json.SerializeNulls
class PersonMixed {
    private String name;

    @Json.SerializeNulls(false)
    private String city;

    // getters and setters
}
```

This produces the following JSON output:

``` json
{"name":"John"}
```

Why use it: Provides control over JSON size and API contract.

#### @Json.Creator

Marks constructors or factory methods for object creation during deserialization.

*Using @Json.Creator constructor*

``` java
@Json.Entity
class Person {
    private final String name;
    private final int age;

    @Json.Creator
    public Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    // getters
}
```

*Using @Json.Creator factory method*

``` java
@Json.Entity
static class PersonWithCreator {
    private final String name;

    private PersonWithCreator(String name) {
        this.name = name;
    }

    @Json.Creator
    public static PersonWithCreator create(String name) {
        return new PersonWithCreator(name);
    }
}
```

Why use it: Enables deserialization of immutable objects or objects requiring specific construction logic.

#### @Json.PropertyOrder

Controls the order of properties in JSON output. By default, the order is undefined, so the properties can appear in any order.

*Undefined/Any declaration order (default)*

``` java
@Json.Entity
class PersonDefault {
    private String zebra;
    private String alpha;
    private String beta;
}
```

This produces the following JSON output:

``` json
{"zebra":"value","alpha":"value","beta":"value"}
```

*Alphabetical ordering*

``` java
@Json.Entity
@Json.PropertyOrder(Order.ALPHABETICAL)
class PersonAlphabetical {
    private String zebra;
    private String alpha;
    private String beta;
}
```

This produces the following JSON output:

``` json
{"alpha":"value","beta":"value","zebra":"value"}
```

*Reverse alphabetical ordering*

``` java
@Json.Entity
@Json.PropertyOrder(Order.REVERSE_ALPHABETICAL)
class PersonReverse {
    private String alpha;
    private String beta;
    private String zebra;
}
```

This produces the following JSON output:

``` json
{"zebra":"value","beta":"value","alpha":"value"}
```

Why use it: Ensures consistent JSON structure for APIs, when order matters for processing.

#### @Json.Deserializer, @Json.Serializer, @Json.Converter

Specify custom serialization/deserialization logic using `JsonSerializer` and `JsonDeserializer` implementations.

*Using @Json.Deserializer*

``` java
record MyType() { }

class CustomDeserializer implements JsonDeserializer<MyType> {
    @Override
    public MyType deserialize(JsonParser parser) {
        // custom deserialization logic
        return new MyType();
    }

    @Override
    public GenericType<MyType> type() {
        return GenericType.create(MyType.class);
    }
}

@Json.Entity
class CustomType {

    @Json.Deserializer(CustomDeserializer.class)
    private MyType value;

    // getters and setters
}
```

Why use it: Enables handling of complex types, legacy formats, or types requiring special conversion logic.

#### @Json.BuilderInfo

Provides information about a builder class for object construction.

*Using @Json.BuilderInfo*

``` java
class PersonBuilder {
    private String name;
    private int age;

    PersonBuilder name(String name) {
        this.name = name;
        return this;
    }

    PersonBuilder age(int age) {
        this.age = age;
        return this;
    }

    PersonWithBuilder build() {
        return new PersonWithBuilder(this);
    }
}

@Json.Entity
@Json.BuilderInfo(PersonBuilder.class)
class PersonWithBuilder {
    private final String name;
    private final int age;

    PersonWithBuilder(PersonBuilder personBuilder) {
        this.name = personBuilder.name;
        this.age = personBuilder.age;
    }

    // getters
}
```

Why use it: Specifies custom builder classes for object construction during deserialization, enabling more complex instantiation patterns.

#### @Json.FailOnUnknown

Controls behavior when unknown properties are encountered during deserialization.

*Using @Json.FailOnUnknown*

``` java
@Json.Entity
@Json.FailOnUnknown
class StrictPerson {
    private String name;

    // getters and setters
}
```

Why use it: Provides strict validation of JSON input, failing if unexpected properties are present.

### Optional Handling

Helidon provides special handling for Java Optional types (`Optional<T>`, `OptionalInt`, `OptionalLong`, `OptionalDouble`) in JSON serialization and deserialization.

#### Empty Optional Behavior

By default, empty Optional fields are omitted from JSON output. When `@Json.SerializeNulls` is applied (either at class or field level), empty Optional fields are included in JSON output as `null` values.

*Default behavior - empty optionals omitted*

``` java
@Json.Entity
class PersonDefault {
    private String name;
    private Optional<String> middleName;

    // getters and setters
}
```

This produces the following JSON output:

``` json
{"name":"John"}
```

*With @Json.SerializeNulls - empty optionals included as null*

``` java
@Json.Entity
@Json.SerializeNulls
class PersonWithNulls {
    private String name;
    private Optional<String> middleName;

    // getters and setters
}
```

This produces the following JSON output:

``` json
{"name":"John","middleName":null}
```

During the deserialization process, null values are always converted into the empty instance of the deserialized Optional type.

**Supported Optional Types:**

- `Optional<T>` - For any object type
- `OptionalInt` - For primitive int values
- `OptionalLong` - For primitive long values
- `OptionalDouble` - For primitive double values

### Automatic Custom Converter Registration

In addition to using annotations directly on classes, custom serializers, deserializers, and converters can be automatically registered with the JSON binding system through Helidon’s Service Registry. So they get automatically discovered and one does not need to register them manually at runtime.

See [Helidon Declarative](../injection/declarative.md#Overview)

### Binding Factories

For more complex scenarios where you need to create serializers and deserializers for entire type families or generic types, you can implement custom [`JsonBindingFactory`](/apidocs/io.helidon.json.binding/io/helidon/json/binding/JsonBindingFactory.html) instances. Binding factories are particularly useful for handling parameterized types, collections, or types that require special instantiation logic.

#### What is a Binding Factory?

A [`JsonBindingFactory`](/apidocs/io.helidon.json.binding/io/helidon/json/binding/JsonBindingFactory.html) is responsible for creating type-specific serializers and deserializers for a family of related types. Unlike individual converters that handle specific types, binding factories can create converters dynamically for various subtypes or parameterized versions of a base type.

#### How Binding Factories Work

Binding factories implement the `JsonBindingFactory<T>` interface, which requires:

- `createDeserializer(Class<? extends T> type)` - Creates a deserializer for a specific class type
- `createDeserializer(GenericType<? extends T> type)` - Creates a deserializer for a generic type
- `createSerializer(Class<? extends T> type)` - Creates a serializer for a specific class type
- `createSerializer(GenericType<? extends T> type)` - Creates a serializer for a generic type
- `supportedTypes()` - Returns the set of types this factory can handle

Typical example for the binding factory would be a handling of the Collection. We would have a converter, which has some common logic for this Collection, but we cant hardcode any specific type this common logic should handle, because Collection has a generic parameter and it could be more or less anything. Because of that, this common logic serves as a template and waits till runtime to have some specific type converter assigned based on the runtime type it received.

New instance of the converter must be created for each runtime type. JSON Binding implementation handles the caching of these converters, so they are also getting reused.

## JSON Processor

The JSON module (`helidon-json`) provides fundamental JSON parsing and generation capabilities.

### Maven Coordinates

``` xml
<dependency>
    <groupId>io.helidon.json</groupId>
    <artifactId>helidon-json</artifactId>
</dependency>
```

### Features

- Streaming JSON parser for efficient processing of large documents
- JSON generator for building JSON output
- Support for all JSON data types
- Memory-efficient processing without loading entire documents

### JsonParser

[`JsonParser`](/apidocs/io.helidon.json/io/helidon/json/JsonParser.html) is a streaming JSON parser that provides efficient, low-level access to JSON data without loading the entire document into memory. It’s designed for processing large JSON documents or streaming data sources.

#### What it’s used for

- Parsing large JSON documents efficiently
- Streaming JSON processing from files, network streams, or other sources
- Token-by-token JSON parsing with fine-grained control
- Memory-efficient processing of JSON data

#### How to use it

*Creating and using JsonParser*

``` java
JsonParser parser = JsonParser.create("{\"name\":\"John\",\"age\":30}");

JsonObject object = parser.readJsonObject();

String name = object.stringValue("name", "DefaultName"); // "John"
int age = object.intValue("age", 0); // 30
```

For more control, JsonParser also supports manual token-by-token parsing:

*Manual token-by-token parsing with JsonParser*

``` java
JsonParser parser = JsonParser.create("{\"name\":\"John\",\"age\":30,\"active\":true}");

// Manual parsing - check for object start
byte lastByte = parser.currentByte(); //
if (lastByte != '{') {
    throw parser.createException("Expected object start");
}
lastByte = parser.nextToken(); //Get the next non-empty character

String name = null;
int age = 0;
boolean active = false;
if (lastByte == '}') {
    //Object end detected
}

while (true) {
    // Expect a string token (field name)
    if (lastByte != '"') {
        throw parser.createException("Expected field name");
    }
    String fieldName = parser.readString();

    // Expect a colon after field name
    if (parser.nextToken() != ':') {
        throw parser.createException("Expected ':' after field name");
    }
    parser.nextToken(); // Move to value
    if ("name".equals(fieldName)) {
        name = parser.readString(); //read the value as a String
    } else if ("age".equals(fieldName)) {
        age = parser.readInt(); //read the value as an int
    } else if ("active".equals(fieldName)) {
        active = parser.readBoolean(); //read the value as a boolean
    } else {
        parser.skip(); // Skip unknown fields
    }
    lastByte = parser.nextToken();
    if (lastByte == ',') {
        //Continue reading, if the next token is comma
        lastByte = parser.nextToken();
        continue;
    } else if (lastByte == '}') {
        //Object end detected
        break;
    } else {
        //Unexpected token detected
        throw parser.createException("Expected ',' or '}'", lastByte);
    }
}
//Object is fully read now.
```

This approach provides fine-grained control over parsing, allowing you to handle different field types and skip unknown fields efficiently.

### JsonGenerator

[`JsonGenerator`](/apidocs/io.helidon.json/io/helidon/json/JsonGenerator.html) is a streaming JSON generator that builds JSON output efficiently. It provides a fluent API for constructing JSON documents without building intermediate representations.

#### What it’s used for

- Building JSON documents programmatically
- Streaming JSON generation to files or network streams
- Memory-efficient JSON construction
- Building complex nested JSON structures

#### How to use it

*Basic JSON generation*

``` java
JsonGenerator generator = JsonGenerator.create(outputStream);

generator.writeObjectStart();
generator.write("name", "John");
generator.write("age", 30);
generator.writeObjectEnd();
```

This generates the following JSON output:

``` json
{"name":"John","age":30}
```

*Complex JSON structure generation*

``` java
JsonGenerator generator = JsonGenerator.create(outputStream);

generator.writeObjectStart();
generator.writeKey("person");
generator.writeObjectStart();
generator.write("name", "John");
generator.write("age", 30);
generator.writeObjectEnd();

generator.writeKey("hobbies");
generator.writeArrayStart();
generator.write("reading");
generator.write("coding");
generator.writeArrayEnd();
generator.writeObjectEnd();
```

This generates the following JSON output:

``` json
{
  "person": {
    "name": "John",
    "age": 30
  },
  "hobbies": ["reading", "coding"]
}
```

### JsonValue Types

[`JsonValue`](/apidocs/io.helidon.json/io/helidon/json/JsonValue.html) is the base class for all JSON value types in Helidon. It provides a type-safe representation of JSON data with specific implementations for different JSON data types.

#### JsonObject

[`JsonObject`](/apidocs/io.helidon.json/io/helidon/json/JsonObject.html) represents a JSON object (key-value pairs enclosed in `{}`). It’s used for structured data with named properties.

*Using JsonObject*

``` java
JsonObject person = JsonObject.builder()
    .set("name", "John")
    .set("age", 30)
    .set("active", true)
    .build();

String name = person.stringValue("name", "");
int age = person.intValue("age", 0);
boolean active = person.booleanValue("active", false);

// Nested objects
JsonObject address = JsonObject.builder()
    .set("street", "123 Main St")
    .set("city", "Springfield")
    .build();

JsonObject personWithAddress = JsonObject.builder()
    .set("name", "John")
    .set("address", address)
    .build();
```

#### JsonArray

[`JsonArray`](/apidocs/io.helidon.json/io/helidon/json/JsonArray.html) represents a JSON array (ordered list of values enclosed in `[]`). It’s used for collections of values.

*Using JsonArray*

``` java
JsonArray hobbies = JsonArray.createStrings(List.of("reading", "coding", "gaming"));
JsonArray numbers = JsonArray.createNumbers(List.of(
        new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3")));

// Access elements - JsonArray doesn't provide direct indexed access
// Use values() to get the list and then access elements
List<JsonValue> hobbyValues = hobbies.values();
List<JsonValue> numberValues = numbers.values();
```

#### JsonString

[`JsonString`](/apidocs/io.helidon.json/io/helidon/json/JsonString.html) represents a JSON string value (text enclosed in `"`). It’s used for textual data.

*Using JsonString*

``` java
JsonString name = JsonString.create("John Doe");
String value = name.value(); // "John Doe"

// From parser
JsonParser parser = JsonParser.create("\"Hello World\"");
JsonString greeting = parser.readJsonString();
```

#### JsonNumber

[`JsonNumber`](/apidocs/io.helidon.json/io/helidon/json/JsonNumber.html) represents a JSON number value. It’s used for numeric data and provides access to different numeric types.

*Using JsonNumber*

``` java
JsonNumber age = JsonNumber.create(new BigDecimal("30"));
int intValue = age.intValue();
double doubleValue = age.doubleValue();
BigDecimal bigDecimalValue = age.bigDecimalValue();

// From parser
JsonParser parser = JsonParser.create("123.45");
JsonNumber number = parser.readJsonNumber();
```

#### JsonBoolean

[`JsonBoolean`](/apidocs/io.helidon.json/io/helidon/json/JsonBoolean.html) represents a JSON boolean value (`true` or `false`). It’s used for logical values.

*Using JsonBoolean*

``` java
JsonBoolean active = JsonBoolean.create(true);
boolean value = active.value(); // true

JsonBoolean inactive = JsonBoolean.FALSE; // Predefined constants
```

#### JsonNull

[`JsonNull`](/apidocs/io.helidon.json/io/helidon/json/JsonNull.html) represents a JSON null value. It’s used when a value is absent or undefined.

*Using JsonNull*

``` java
JsonNull nullValue = JsonNull.instance();

// In collections
JsonArray array = JsonArray.create(List.of(
    JsonString.create("value1"),
    JsonNull.instance(), // null value
    JsonString.create("value3")));
```
