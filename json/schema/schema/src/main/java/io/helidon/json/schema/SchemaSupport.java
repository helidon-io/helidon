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
import io.helidon.json.schema.spi.JsonSchemaProvider;
import io.helidon.metadata.hson.Hson;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Services;

final class SchemaSupport {
    private SchemaSupport() {
    }

    @SuppressWarnings("removal")
    static // until we have a full JSON implementation
    class SchemaCustomMethods {

        private SchemaCustomMethods() {
        }

        /**
         * Take the root type of the provided schema and add it to the current builder.
         *
         * @param target builder
         * @param schema schema
         */
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
                generateObject(schema).write(writer, true);
            }
            return baos.toString(StandardCharsets.UTF_8);
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
                generateObjectNoKeywords(schema).write(writer, true);
            }
            return baos.toString(StandardCharsets.UTF_8);
        }

        /**
         * Generate the {@link io.helidon.metadata.hson.Hson.Struct} object of this schema.
         *
         * @param schema schema
         * @return struct object
         */
        static Hson.Struct generateObject(Schema schema) {
            Hson.Struct.Builder builder = Hson.structBuilder();
            builder.set("$schema", "https://json-schema.org/draft/2020-12/schema");
            schema.id().ifPresent(id -> builder.set("$id", id.toString()));
            schema.root().generate(builder);
            return builder.build();
        }

        /**
         * Generate the {@link io.helidon.metadata.hson.Hson.Struct} object of this schema without schema keywords.
         * Keywords like {@code $schema} or {@code $id} will not be included.
         *
         * @param schema schema
         * @return struct object
         */
        static Hson.Struct generateObjectNoKeywords(Schema schema) {
            Hson.Struct.Builder builder = Hson.structBuilder();
            schema.root().generate(builder);
            return builder.build();
        }

        /**
         * Return a Schema related to the given class.
         * This schema is obtained from the service registry via {@link io.helidon.json.schema.spi.JsonSchemaProvider} instance.
         * One has to either put {@link io.helidon.json.schema.JsonSchema.Schema} annotation on the requested type or
         * implement {@link io.helidon.json.schema.spi.JsonSchemaProvider} class manually.
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
            try (InputStream is = new ByteArrayInputStream(jsonSchema.getBytes(StandardCharsets.UTF_8))) {
                Hson.Value<?> parsed = Hson.parse(is);
                Hson.Struct struct = parsed.asStruct();
                struct.stringValue("$id").map(URI::create).ifPresent(builder::id);
                SchemaType type = type(struct);
                switch (type) {
                case STRING -> builder.rootString(stringBuilder -> parseString(stringBuilder, struct));
                case INTEGER -> builder.rootInteger(integerBuilder -> parseInteger(integerBuilder, struct));
                case NUMBER -> builder.rootNumber(numberBuilder -> parseNumber(numberBuilder, struct));
                case BOOLEAN -> builder.rootBoolean(booleanBuilder -> parseCommon(booleanBuilder, struct));
                case OBJECT -> builder.rootObject(objectBuilder -> parseObject(objectBuilder, struct));
                case ARRAY -> builder.rootArray(arrayBuilder -> parseArray(arrayBuilder, struct));
                case NULL -> builder.rootNull(nullBuilder -> parseCommon(nullBuilder, struct));
                default -> throw new JsonSchemaException("Unsupported root type: " + type);
                }
            } catch (IOException e) {
                throw new JsonSchemaException("Failed to parse JSON Schema", e);
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
                        SchemaType type = type(items);
                        switch (type) {
                        case STRING -> arrayBuilder.itemsString(stringBuilder -> parseString(stringBuilder, items));
                        case INTEGER -> arrayBuilder.itemsInteger(integerBuilder -> parseInteger(integerBuilder, items));
                        case NUMBER -> arrayBuilder.itemsNumber(numberBuilder -> parseNumber(numberBuilder, items));
                        case BOOLEAN -> arrayBuilder.itemsBoolean(booleanBuilder -> parseCommon(booleanBuilder, items));
                        case OBJECT -> arrayBuilder.itemsObject(objectBuilder -> parseObject(objectBuilder, items));
                        case ARRAY -> arrayBuilder.itemsArray(arrayBuilder2 -> parseArray(arrayBuilder2, items));
                        case NULL -> arrayBuilder.itemsNull(nullBuilder -> parseCommon(nullBuilder, items));
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
                        properties.keys()
                                .forEach(key -> {
                                    Hson.Struct object = properties.structValue(key)
                                            .orElseThrow(() -> new JsonSchemaException("Missing required property '"
                                                                                               + key + "'"));
                                    SchemaType type = type(object);
                                    switch (type) {
                                    case OBJECT -> objectBuilder.addObjectProperty(key, objectBuilder2 -> {
                                        parseObject(objectBuilder2, object);
                                        objectBuilder2.required(requiredProperties.contains(key));
                                    });
                                    case ARRAY -> objectBuilder.addArrayProperty(key, arrayBuilder -> {
                                        parseArray(arrayBuilder, object);
                                        arrayBuilder.required(requiredProperties.contains(key));
                                    });
                                    case STRING -> objectBuilder.addStringProperty(key, stringBuilder -> {
                                        parseString(stringBuilder, object);
                                        stringBuilder.required(requiredProperties.contains(key));
                                    });
                                    case NUMBER -> objectBuilder.addNumberProperty(key, numberBuilder -> {
                                        parseNumber(numberBuilder, object);
                                        numberBuilder.required(requiredProperties.contains(key));
                                    });
                                    case INTEGER -> objectBuilder.addIntegerProperty(key, integerBuilder -> {
                                        parseInteger(integerBuilder, object);
                                        integerBuilder.required(requiredProperties.contains(key));
                                    });
                                    case BOOLEAN -> objectBuilder.addBooleanProperty(key, booleanBuilder -> {
                                        parseCommon(booleanBuilder, object);
                                        booleanBuilder.required(requiredProperties.contains(key));
                                    });
                                    case NULL -> objectBuilder.addNullProperty(key, nullBuilder -> {
                                        parseCommon(nullBuilder, object);
                                        nullBuilder.required(requiredProperties.contains(key));
                                    });
                                    default -> throw new JsonSchemaException("Unsupported type: " + type);
                                    }
                                });
                    });
        }

        private static SchemaType type(Hson.Struct struct) {
            return struct.stringValue("type")
                    .map(SchemaType::fromType)
                    .orElseThrow(() -> new JsonSchemaException(
                            "Required property 'type' missing in an object property."));
        }
    }

    static class SchemaDecorator implements Prototype.BuilderDecorator<Schema.BuilderBase<?, ?>> {

        @Override
        public void decorate(Schema.BuilderBase<?, ?> target) {
            target.rootObject().ifPresent(target::root);
            addRoot(target, target.rootArray());
            addRoot(target, target.rootInteger());
            addRoot(target, target.rootNumber());
            addRoot(target, target.rootString());
            addRoot(target, target.rootBoolean());
            addRoot(target, target.rootNull());
        }

        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        private static void addRoot(Schema.BuilderBase<?, ?> target, Optional<? extends SchemaItem> item) {
            if (target.root().isEmpty()) {
                item.ifPresent(target::root);
            } else if (item.isPresent()) {
                throw new JsonSchemaException("Only one root type is supported");
            }
        }

    }

    static class SchemaArrayDecorator implements Prototype.BuilderDecorator<SchemaArray.BuilderBase<?, ?>> {

        @Override
        public void decorate(SchemaArray.BuilderBase<?, ?> target) {
            target.schemaType(SchemaType.ARRAY);
            target.itemsObject().ifPresent(target::items);
            addRoot(target, target.itemsArray());
            addRoot(target, target.itemsInteger());
            addRoot(target, target.itemsNumber());
            addRoot(target, target.itemsString());
            addRoot(target, target.itemsBoolean());
            addRoot(target, target.itemsNull());

            Optional<Integer> minItems = target.minItems();
            Optional<Integer> maxItems = target.maxItems();
            if (minItems.isPresent() && maxItems.isPresent()) {
                if (minItems.get() > maxItems.get()) {
                    throw new JsonSchemaException("Minimum items value cannot be greater than the maximum value");
                }
            }
            if (minItems.isPresent() && minItems.get() < 0) {
                throw new JsonSchemaException("Minimum items cannot be lower than 0");
            }
            if (maxItems.isPresent() && maxItems.get() < 0) {
                throw new JsonSchemaException("Maximum items cannot be lower than 0");
            }
        }

        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        private static void addRoot(SchemaArray.BuilderBase<?, ?> target, Optional<? extends SchemaItem> item) {
            if (target.items().isEmpty()) {
                item.ifPresent(target::items);
            } else if (item.isPresent()) {
                throw new JsonSchemaException("Only one array items type is supported");
            }
        }

    }

    static class SchemaIntegerCustomMethods {

        private SchemaIntegerCustomMethods() {
        }

        /**
         * Value restriction to be a multiple of a given integer number.
         *
         * @param value multiple value restriction
         * @see io.helidon.json.schema.SchemaInteger.BuilderBase#multipleOf()
         */
        @Prototype.BuilderMethod
        static void multipleOf(SchemaInteger.BuilderBase<?, ?> builder, int value) {
            builder.multipleOf((long) value);
        }

        /**
         * Minimal value of the integer number.
         * Cannot be higher than maximal configured value.
         * Mutually exclusive to {@link io.helidon.json.schema.SchemaInteger.BuilderBase#exclusiveMinimum()}.
         *
         * @param value minimal value
         * @see io.helidon.json.schema.SchemaInteger.BuilderBase#minimum()
         */
        @Prototype.BuilderMethod
        static void minimum(SchemaInteger.BuilderBase<?, ?> builder, int value) {
            builder.minimum((long) value);
        }

        /**
         * Maximal value of the integer number.
         * Cannot be lower than minimal configured value.
         * Mutually exclusive to {@link io.helidon.json.schema.SchemaInteger.BuilderBase#exclusiveMaximum()}.
         *
         * @param value maximal value
         * @see io.helidon.json.schema.SchemaInteger.BuilderBase#maximum()
         */
        @Prototype.BuilderMethod
        static void maximum(SchemaInteger.BuilderBase<?, ?> builder, int value) {
            builder.maximum((long) value);
        }

        /**
         * Maximal exclusive value of the integer number.
         * Cannot be lower than minimal configured value.
         * Mutually exclusive to {@link io.helidon.json.schema.SchemaInteger.BuilderBase#maximum()}.
         *
         * @param value maximal exclusive value
         * @see io.helidon.json.schema.SchemaInteger.BuilderBase#exclusiveMaximum()
         */
        @Prototype.BuilderMethod
        static void exclusiveMaximum(SchemaInteger.BuilderBase<?, ?> builder, int value) {
            builder.exclusiveMaximum((long) value);
        }

        /**
         * Minimal exclusive value of the integer number.
         * Cannot be lower than maximal configured value.
         * Mutually exclusive to {@link io.helidon.json.schema.SchemaInteger.BuilderBase#minimum()}.
         *
         * @param value minimal exclusive value
         * @see io.helidon.json.schema.SchemaInteger.BuilderBase#exclusiveMinimum()
         */
        @Prototype.BuilderMethod
        static void exclusiveMinimum(SchemaInteger.BuilderBase<?, ?> builder, int value) {
            builder.exclusiveMinimum((long) value);
        }

    }

    static class SchemaIntegerDecorator implements Prototype.BuilderDecorator<SchemaInteger.BuilderBase<?, ?>> {

        @Override
        public void decorate(SchemaInteger.BuilderBase<?, ?> target) {
            Optional<Long> minimum = target.minimum();
            Optional<Long> exclusiveMinimum = target.exclusiveMinimum();
            Optional<Long> maximum = target.maximum();
            Optional<Long> exclusiveMaximum = target.exclusiveMaximum();
            if (minimum.isPresent() && exclusiveMinimum.isPresent()) {
                throw new JsonSchemaException("Both minimum and exclusive minimum cannot be set at the same time");
            }
            if (maximum.isPresent() && exclusiveMaximum.isPresent()) {
                throw new JsonSchemaException("Both maximum and exclusive maximum cannot be set at the same time");
            }
            Optional<Long> minimumNumber = minimum.or(() -> exclusiveMinimum);
            Optional<Long> maximumNumber = maximum.or(() -> exclusiveMaximum);
            if (minimumNumber.isPresent() && maximumNumber.isPresent()) {
                if (minimumNumber.get() > maximumNumber.get()) {
                    throw new JsonSchemaException("Minimum value cannot be greater than the maximum value");
                }
            }
            if (minimumNumber.isPresent() && minimumNumber.get() < 0) {
                throw new JsonSchemaException("Minimum value cannot be lower than 0");
            }
            if (maximumNumber.isPresent() && maximumNumber.get() < 0) {
                throw new JsonSchemaException("Maximum value cannot be lower than 0");
            }
        }

    }

    static class SchemaNumberDecorator implements Prototype.BuilderDecorator<SchemaNumber.BuilderBase<?, ?>> {

        @Override
        public void decorate(SchemaNumber.BuilderBase<?, ?> target) {
            Optional<Double> minimum = target.minimum();
            Optional<Double> exclusiveMinimum = target.exclusiveMinimum();
            Optional<Double> maximum = target.maximum();
            Optional<Double> exclusiveMaximum = target.exclusiveMaximum();
            if (minimum.isPresent() && exclusiveMinimum.isPresent()) {
                throw new JsonSchemaException("Both minimum and exclusive minimum cannot be set at the same time");
            }
            if (maximum.isPresent() && exclusiveMaximum.isPresent()) {
                throw new JsonSchemaException("Both maximum and exclusive maximum cannot be set at the same time");
            }
            Optional<Double> minimumNumber = minimum.or(() -> exclusiveMinimum);
            Optional<Double> maximumNumber = maximum.or(() -> exclusiveMaximum);
            if (minimumNumber.isPresent() && maximumNumber.isPresent()) {
                if (minimumNumber.get() > maximumNumber.get()) {
                    throw new JsonSchemaException("Minimum value cannot be greater than the maximum value");
                }
            }
            if (minimumNumber.isPresent() && minimumNumber.get() < 0) {
                throw new JsonSchemaException("Minimum value cannot be lower than 0");
            }
            if (maximumNumber.isPresent() && maximumNumber.get() < 0) {
                throw new JsonSchemaException("Maximum value cannot be lower than 0");
            }
        }

    }

    static class SchemaObjectCustomMethods {

        private SchemaObjectCustomMethods() {
        }

        /**
         * Add JSON schema property based on the provided schema root type.
         *
         * @param target builder
         * @param name   property name
         * @param schema schema
         */
        @Prototype.BuilderMethod
        static void addSchema(SchemaObject.BuilderBase<?, ?> target, String name, Schema schema) {
            SchemaItem root = schema.root();
            switch (root.schemaType()) {
            case OBJECT -> target.addObjectProperty(name, (SchemaObject) root);
            case ARRAY -> target.addArrayProperty(name, (SchemaArray) root);
            case NUMBER -> target.addNumberProperty(name, (SchemaNumber) root);
            case INTEGER -> target.addIntegerProperty(name, (SchemaInteger) root);
            case BOOLEAN -> target.addBooleanProperty(name, (SchemaBoolean) root);
            case STRING -> target.addStringProperty(name, (SchemaString) root);
            case NULL -> target.addNullProperty(name, (SchemaNull) root);
            default -> throw new JsonSchemaException("Unsupported schema type: " + root.schemaType());
            }
        }

    }

    static class SchemaObjectDecorator implements Prototype.BuilderDecorator<SchemaObject.BuilderBase<?, ?>> {

        @Override
        public void decorate(SchemaObject.BuilderBase<?, ?> target) {
            target.integerProperties().forEach(target.properties()::putIfAbsent);
            target.numberProperties().forEach(target.properties()::putIfAbsent);
            target.stringProperties().forEach(target.properties()::putIfAbsent);
            target.booleanProperties().forEach(target.properties()::putIfAbsent);
            target.objectProperties().forEach(target.properties()::putIfAbsent);
            target.arrayProperties().forEach(target.properties()::putIfAbsent);
            target.nullProperties().forEach(target.properties()::putIfAbsent);
        }

    }
}
