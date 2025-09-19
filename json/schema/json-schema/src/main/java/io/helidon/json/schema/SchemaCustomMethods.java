/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.json.schema;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.metadata.hson.Hson;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Services;

class SchemaCustomMethods {

    private SchemaCustomMethods() {
    }

    @Prototype.BuilderMethod
    static void rootFromSchema(Schema.BuilderBase<?, ?> target, Schema schema) {
        SchemaItem root = schema.root();
        switch (root.schemaType()) {
        case OBJECT -> target.rootObject((SchemaObject) root);
        case ARRAY -> target.rootArray((SchemaArray) root);
        case NUMBER -> target.rootNumber((SchemaNumber) root);
        case INTEGER -> target.rootInteger((SchemaInteger) root);
        case BOOLEAN -> target.rootBoolean((SchemaBoolean) root);
        case STRING -> target.rootString((SchemaString) root);
        case NULL -> target.rootNull((SchemaNull) root);
        default -> throw new JsonSchemaException("Unsupported schema type: " + root.schemaType());
        }
    }

    /**
     * Generate the String representation of the schema.
     *
     * @param schema schema
     * @return Json schema in a String format
     */
    @Prototype.PrototypeMethod
    static String generate(Schema schema) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(baos, true, StandardCharsets.UTF_8)) {
            generateObject(schema).writeFormatted(writer);
        }
        return baos.toString();
    }

    /**
     * Generate the String representation of the schema without schema keywords.
     * Keywords like {@code $schema} or {@code $id} will not be included.
     *
     * @param schema schema
     * @return Json schema in a String format
     */
    @Prototype.PrototypeMethod
    static String generateNoKeywords(Schema schema) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(baos, true, StandardCharsets.UTF_8)) {
            generateObjectNoKeywords(schema).writeFormatted(writer);
        }
        return baos.toString();
    }

    /**
     * Generate the {@link Hson.Struct} object of this schema.
     *
     * @param schema schema
     * @return struct object
     */
    @Prototype.PrototypeMethod
    static Hson.Struct generateObject(Schema schema) {
        Hson.Struct.Builder builder = Hson.structBuilder();
        builder.set("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.id().ifPresent(id -> builder.set("$id", id.toString()));
        schema.root().generate(builder);
        return builder.build();
    }

    /**
     * Generate the {@link Hson.Struct} object of this schema without schema keywords.
     * Keywords like {@code $schema} or {@code $id} will not be included.
     *
     * @param schema schema
     * @return struct object
     */
    @Prototype.PrototypeMethod
    static Hson.Struct generateObjectNoKeywords(Schema schema) {
        Hson.Struct.Builder builder = Hson.structBuilder();
        schema.root().generate(builder);
        return builder.build();
    }

    /**
     * Return a Schema related to the given class.
     * This schema is obtained from the service registry via {@link JsonSchemaProvider} instance.
     * One has to either put {@link JsonSchema.Schema} annotation on the requested type or
     * implement {@link JsonSchemaProvider} class manually.
     * If no schema was found for the given class, empty optional is returned.
     *
     * @param clazz schema class
     * @return schema related to the provided class or empty
     */
    @Prototype.FactoryMethod
    static Optional<Schema> find(Class<?> clazz) {
        return Services.first(JsonSchemaProvider.class, Qualifier.createNamed(clazz))
                .map(JsonSchemaProvider::schema);
    }

    /**
     * Parse a schema object from the String format of the Json schema.
     *
     * @param jsonSchema source json schema
     * @return parsed schema object
     */
    @Prototype.FactoryMethod
    static Schema parse(String jsonSchema) {
        Schema.Builder builder = Schema.builder();
        try (InputStream is = new ByteArrayInputStream(jsonSchema.getBytes())) {
            Hson.Value<?> parsed = Hson.parse(is);
            Hson.Struct struct = parsed.asStruct();
            struct.stringValue("$id").map(URI::create).ifPresent(builder::id);
            String type = struct.stringValue("type")
                    .orElseThrow(() -> new JsonSchemaException("Missing required property 'type' missing in the schema root."));
            switch (type) {
            case "string" -> builder.rootString(stringBuilder -> parseString(stringBuilder, struct));
            case "integer" -> builder.rootInteger(integerBuilder -> parseInteger(integerBuilder, struct));
            case "number" -> builder.rootNumber(numberBuilder -> parseNumber(numberBuilder, struct));
            case "boolean" -> builder.rootBoolean(booleanBuilder -> parseCommon(booleanBuilder, struct));
            case "object" -> builder.rootObject(objectBuilder -> parseObject(objectBuilder, struct));
            case "array" -> builder.rootArray(arrayBuilder -> parseArray(arrayBuilder, struct));
            case "null" -> builder.rootNull(nullBuilder -> parseCommon(nullBuilder, struct));
            default -> throw new JsonSchemaException("Unsupported root type: " + type);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return builder.build();
    }

    private static void parseCommon(SchemaItem.BuilderBase<?, ?> itemBuilder, Hson.Struct jsonObject) {
        jsonObject.stringValue("description").ifPresent(itemBuilder::description);
        jsonObject.stringValue("title").ifPresent(itemBuilder::title);
    }

    private static void parseString(SchemaString.Builder stringBuilder, Hson.Struct jsonObject) {
        parseCommon(stringBuilder, jsonObject);
        jsonObject.numberValue("maxLength").ifPresent(it -> stringBuilder.maxLength(it.longValue()));
        jsonObject.numberValue("minLength").ifPresent(it -> stringBuilder.minLength(it.longValue()));
        jsonObject.stringValue("pattern").ifPresent(stringBuilder::pattern);
    }

    private static void parseInteger(SchemaInteger.Builder integerBuilder, Hson.Struct jsonObject) {
        parseCommon(integerBuilder, jsonObject);
        jsonObject.numberValue("multipleOf").ifPresent(it -> integerBuilder.multipleOf(it.longValue()));
        jsonObject.numberValue("minimum").ifPresent(it -> integerBuilder.minimum(it.longValue()));
        jsonObject.numberValue("maximum").ifPresent(it -> integerBuilder.maximum(it.longValue()));
        jsonObject.numberValue("exclusiveMaximum").ifPresent(it -> integerBuilder.exclusiveMaximum(it.longValue()));
        jsonObject.numberValue("exclusiveMinimum").ifPresent(it -> integerBuilder.exclusiveMinimum(it.longValue()));
    }

    private static void parseNumber(SchemaNumber.Builder numberBuilder, Hson.Struct jsonObject) {
        parseCommon(numberBuilder, jsonObject);
        jsonObject.doubleValue("multipleOf").ifPresent(numberBuilder::multipleOf);
        jsonObject.doubleValue("minimum").ifPresent(numberBuilder::minimum);
        jsonObject.doubleValue("maximum").ifPresent(numberBuilder::maximum);
        jsonObject.doubleValue("exclusiveMinimum").ifPresent(numberBuilder::exclusiveMinimum);
        jsonObject.doubleValue("exclusiveMaximum").ifPresent(numberBuilder::exclusiveMaximum);
    }

    private static void parseArray(SchemaArray.Builder arrayBuilder, Hson.Struct jsonObject) {
        parseCommon(arrayBuilder, jsonObject);
        jsonObject.intValue("maxItems").ifPresent(arrayBuilder::maxItems);
        jsonObject.intValue("minItems").ifPresent(arrayBuilder::minItems);
        jsonObject.booleanValue("uniqueItems").ifPresent(arrayBuilder::uniqueItems);

        jsonObject.structValue("items")
                .ifPresent(items -> {
                    String type = items.stringValue("type")
                            .orElseThrow(() -> new JsonSchemaException(
                                    "Missing required property 'type' missing in the object property"
                                            + "."));
                    switch (type) {
                    case "string" -> arrayBuilder.itemsString(stringBuilder -> parseString(stringBuilder, items));
                    case "integer" -> arrayBuilder.itemsInteger(integerBuilder -> parseInteger(integerBuilder, items));
                    case "number" -> arrayBuilder.itemsNumber(numberBuilder -> parseNumber(numberBuilder, items));
                    case "boolean" -> arrayBuilder.itemsBoolean(booleanBuilder -> parseCommon(booleanBuilder, items));
                    case "object" -> arrayBuilder.itemsObject(objectBuilder -> parseObject(objectBuilder, items));
                    case "array" -> arrayBuilder.itemsArray(arrayBuilder2 -> parseArray(arrayBuilder2, items));
                    case "null" -> arrayBuilder.itemsNull(nullBuilder -> parseCommon(nullBuilder, items));
                    default -> throw new JsonSchemaException("Unsupported type: " + type);
                    }
                });
    }

    private static void parseObject(SchemaObject.Builder objectBuilder, Hson.Struct jsonObject) {
        parseCommon(objectBuilder, jsonObject);
        jsonObject.intValue("maxProperties").ifPresent(objectBuilder::maxProperties);
        jsonObject.intValue("minProperties").ifPresent(objectBuilder::minProperties);
        jsonObject.booleanValue("additionalProperties").ifPresent(objectBuilder::additionalProperties);
        jsonObject.structValue("properties")
                .ifPresent(properties -> {
                    List<String> requiredProperties = jsonObject.stringArray("required").orElse(List.of());
                    properties.values()
                            .forEach((key, value) -> {
                                Hson.Struct object = value.asStruct();
                                String type = object.stringValue("type")
                                        .orElseThrow(() -> new JsonSchemaException(
                                                "Missing required property 'type' missing in the object property"
                                                        + "."));
                                switch (type) {
                                case "string" -> objectBuilder.addStringProperty(key, stringBuilder -> {
                                    parseString(stringBuilder, object);
                                    stringBuilder.required(requiredProperties.contains(key));
                                });
                                case "integer" -> objectBuilder.addIntegerProperty(key, integerBuilder -> {
                                    parseInteger(integerBuilder, object);
                                    integerBuilder.required(requiredProperties.contains(key));
                                });
                                case "number" -> objectBuilder.addNumberProperty(key, numberBuilder -> {
                                    parseNumber(numberBuilder, object);
                                    numberBuilder.required(requiredProperties.contains(key));
                                });
                                case "boolean" -> objectBuilder.addBooleanProperty(key, booleanBuilder -> {
                                    parseCommon(booleanBuilder, object);
                                    booleanBuilder.required(requiredProperties.contains(key));
                                });
                                case "object" -> objectBuilder.addObjectProperty(key, objectBuilder2 -> {
                                    parseObject(objectBuilder2, object);
                                    objectBuilder2.required(requiredProperties.contains(key));
                                });
                                case "array" -> objectBuilder.addArrayProperty(key, arrayBuilder -> {
                                    parseArray(arrayBuilder, object);
                                    arrayBuilder.required(requiredProperties.contains(key));
                                });
                                case "null" -> objectBuilder.addNullProperty(key, nullBuilder -> {
                                    parseCommon(nullBuilder, object);
                                    nullBuilder.required(requiredProperties.contains(key));
                                });
                                default -> throw new JsonSchemaException("Unsupported type: " + type);
                                }
                            });
                });
    }

}
