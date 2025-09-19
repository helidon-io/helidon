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

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class SchemaParseTest {

    @Test
    void testParseInteger() {
        String jsonSchema = """
                {
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Test integer",
                    "description": "This integer is intended for a test",
                    "type": "integer",
                    "multipleOf": 2,
                    "minimum": 0,
                    "maximum": 4
                }
                """;
        Schema schema = Schema.parse(jsonSchema);
        assertThat(schema.root().schemaType(), is(SchemaType.INTEGER));
        SchemaInteger schemaInteger = (SchemaInteger) schema.root();

        assertThat(schemaInteger.title().isPresent(), is(true));
        assertThat(schemaInteger.title().get(), is("Test integer"));
        assertThat(schemaInteger.description().isPresent(), is(true));
        assertThat(schemaInteger.description().get(), is("This integer is intended for a test"));
        assertThat(schemaInteger.multipleOf().isPresent(), is(true));
        assertThat(schemaInteger.multipleOf().get(), is(2L));
        assertThat(schemaInteger.minimum().isPresent(), is(true));
        assertThat(schemaInteger.minimum().get(), is(0L));
        assertThat(schemaInteger.maximum().isPresent(), is(true));
        assertThat(schemaInteger.maximum().get(), is(4L));
        assertThat(schemaInteger.exclusiveMaximum().isPresent(), is(false));
        assertThat(schemaInteger.exclusiveMinimum().isPresent(), is(false));
    }

    @Test
    void testParseNumber() {
        String jsonSchema = """
                {
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Test number",
                    "description": "This number is intended for a test",
                    "type": "number",
                    "multipleOf": 2,
                    "minimum": 0,
                    "maximum": 4
                }
                """;
        Schema schema = Schema.parse(jsonSchema);
        assertThat(schema.root().schemaType(), is(SchemaType.NUMBER));
        SchemaNumber schemaNumber = (SchemaNumber) schema.root();

        assertThat(schemaNumber.title().isPresent(), is(true));
        assertThat(schemaNumber.title().get(), is("Test number"));
        assertThat(schemaNumber.description().isPresent(), is(true));
        assertThat(schemaNumber.description().get(), is("This number is intended for a test"));
        assertThat(schemaNumber.multipleOf().isPresent(), is(true));
        assertThat(schemaNumber.multipleOf().get(), is(2.0));
        assertThat(schemaNumber.minimum().isPresent(), is(true));
        assertThat(schemaNumber.minimum().get(), is(0.0));
        assertThat(schemaNumber.maximum().isPresent(), is(true));
        assertThat(schemaNumber.maximum().get(), is(4.0));
        assertThat(schemaNumber.exclusiveMaximum().isPresent(), is(false));
        assertThat(schemaNumber.exclusiveMinimum().isPresent(), is(false));
    }

    @Test
    void testParseString() {
        String jsonSchema = """
                {
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Test string",
                    "description": "This string is intended for a test",
                    "type": "string",
                    "minLength": 0,
                    "maxLength": 5,
                    "pattern": "myPattern"
                }
                """;
        Schema schema = Schema.parse(jsonSchema);
        assertThat(schema.root().schemaType(), is(SchemaType.STRING));
        SchemaString schemaString = (SchemaString) schema.root();

        assertThat(schemaString.title().isPresent(), is(true));
        assertThat(schemaString.title().get(), is("Test string"));
        assertThat(schemaString.description().isPresent(), is(true));
        assertThat(schemaString.description().get(), is("This string is intended for a test"));
        assertThat(schemaString.minLength().isPresent(), is(true));
        assertThat(schemaString.minLength().get(), is(0L));
        assertThat(schemaString.maxLength().isPresent(), is(true));
        assertThat(schemaString.maxLength().get(), is(5L));
        assertThat(schemaString.pattern().isPresent(), is(true));
        assertThat(schemaString.pattern().get(), is("myPattern"));
    }

    @Test
    void testParseBoolean() {
        String jsonSchema = """
                {
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Test boolean",
                    "description": "This boolean is intended for a test",
                    "type": "boolean"
                }
                """;
        Schema schema = Schema.parse(jsonSchema);
        assertThat(schema.root().schemaType(), is(SchemaType.BOOLEAN));
        SchemaBoolean schemaBoolean = (SchemaBoolean) schema.root();

        assertThat(schemaBoolean.title().isPresent(), is(true));
        assertThat(schemaBoolean.title().get(), is("Test boolean"));
        assertThat(schemaBoolean.description().isPresent(), is(true));
        assertThat(schemaBoolean.description().get(), is("This boolean is intended for a test"));
    }

    @Test
    void testParseNull() {
        String jsonSchema = """
                {
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Test null",
                    "description": "This null is intended for a test",
                    "type": "null"
                }
                """;
        Schema schema = Schema.parse(jsonSchema);
        assertThat(schema.root().schemaType(), is(SchemaType.NULL));
        SchemaNull schemaNull = (SchemaNull) schema.root();

        assertThat(schemaNull.title().isPresent(), is(true));
        assertThat(schemaNull.title().get(), is("Test null"));
        assertThat(schemaNull.description().isPresent(), is(true));
        assertThat(schemaNull.description().get(), is("This null is intended for a test"));
    }

    @Test
    void testParseArray() {
        String jsonSchema = """
                {
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Test array",
                    "description": "This array is intended for a test",
                    "type": "array",
                    "maxItems": 20,
                    "minItems": 0,
                    "items": {
                        "type": "string"
                    },
                    "uniqueItems": true
                }
                """;
        Schema schema = Schema.parse(jsonSchema);
        assertThat(schema.root().schemaType(), is(SchemaType.ARRAY));
        SchemaArray schemaArray = (SchemaArray) schema.root();

        assertThat(schemaArray.title().isPresent(), is(true));
        assertThat(schemaArray.title().get(), is("Test array"));
        assertThat(schemaArray.description().isPresent(), is(true));
        assertThat(schemaArray.description().get(), is("This array is intended for a test"));
        assertThat(schemaArray.maxItems().isPresent(), is(true));
        assertThat(schemaArray.maxItems().get(), is(20));
        assertThat(schemaArray.minItems().isPresent(), is(true));
        assertThat(schemaArray.minItems().get(), is(0));
        assertThat(schemaArray.items().isPresent(), is(true));
        assertThat(schemaArray.items().get().schemaType(), is(SchemaType.STRING));
        assertThat(schemaArray.uniqueItems().isPresent(), is(true));
        assertThat(schemaArray.uniqueItems().get(), is(true));
    }

    @Test
    void testParseObject() {
        String jsonSchema = """
                {
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Test object",
                    "description": "This object is intended for a test",
                    "type": "object",
                    "maxProperties": 20,
                    "minProperties": 0,
                    "additionalProperties": true,
                    "properties": {
                        "test": {
                            "type": "string"
                        },
                        "test2": {
                            "type": "integer"
                        }
                    }
                }
                """;
        Schema schema = Schema.parse(jsonSchema);
        assertThat(schema.root().schemaType(), is(SchemaType.OBJECT));
        SchemaObject schemaObject = (SchemaObject) schema.root();

        assertThat(schemaObject.title().isPresent(), is(true));
        assertThat(schemaObject.title().get(), is("Test object"));
        assertThat(schemaObject.description().isPresent(), is(true));
        assertThat(schemaObject.description().get(), is("This object is intended for a test"));
        assertThat(schemaObject.maxProperties().isPresent(), is(true));
        assertThat(schemaObject.maxProperties().get(), is(20));
        assertThat(schemaObject.minProperties().isPresent(), is(true));
        assertThat(schemaObject.minProperties().get(), is(0));
        assertThat(schemaObject.additionalProperties().isPresent(), is(true));
        assertThat(schemaObject.additionalProperties().get(), is(true));
        assertThat(schemaObject.properties().size(), is(2));
        assertThat(schemaObject.stringProperties().size(), is(1));
        assertThat(schemaObject.integerProperties().size(), is(1));
    }

}
