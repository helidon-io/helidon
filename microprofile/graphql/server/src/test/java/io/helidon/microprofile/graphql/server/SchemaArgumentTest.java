/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.graphql.server;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for {@link SchemaArgument} class.
 */
class SchemaArgumentTest {

    private static final Class<?> STRING = String.class;
    private static final Class<?> INTEGER = Integer.class;

    @Test
    public void testConstructors() {
        SchemaArgument schemaArgument = new SchemaArgument("name", "Int", true, null, INTEGER);
        assertThat(schemaArgument.argumentName(), is("name"));
        assertThat(schemaArgument.argumentType(), is("Int"));
        assertThat(schemaArgument.mandatory(), is(true));
        assertThat(schemaArgument.defaultValue(), is(nullValue()));
        assertThat(schemaArgument.originalType().getName(), is(Integer.class.getName()));

        schemaArgument = new SchemaArgument("name2", "String", false, "Default", STRING);
        assertThat(schemaArgument.argumentName(), is("name2"));
        assertThat(schemaArgument.argumentType(), is("String"));
        assertThat(schemaArgument.mandatory(), is(false));
        assertThat(schemaArgument.defaultValue(), is("Default"));
        assertThat(schemaArgument.originalType().getName(), is(STRING.getName()));

        schemaArgument.argumentType("XYZ");
        assertThat(schemaArgument.argumentType(), is("XYZ"));

        assertThat(schemaArgument.description(), is(nullValue()));
        schemaArgument.description("description");
        assertThat(schemaArgument.description(), is("description"));

        assertThat(schemaArgument.isSourceArgument(), is(false));
        schemaArgument.sourceArgument(true);
        assertThat(schemaArgument.isSourceArgument(), is(true));

        assertThat(schemaArgument.format(), is(nullValue()));
        schemaArgument.format(new String[] { "value-1", "value-2"});
        String[] format = schemaArgument.format();
        assertThat(format, is(notNullValue()));
        assertThat(format.length, is(2));
        assertThat(format[0], is("value-1"));
        assertThat(format[1], is("value-2"));

        schemaArgument.defaultValue("hello");
        assertThat(schemaArgument.defaultValue(), is("hello"));

        schemaArgument.defaultValue("1");
        assertThat(schemaArgument.defaultValue(), is("1"));

        assertThat(schemaArgument.originalArrayType(), is(nullValue()));
        schemaArgument.originalArrayType(String.class);
        assertThat(schemaArgument.originalArrayType().getName(), is(String.class.getName()));
    }

    @Test
    public void testSchemaArgumentArrayTypes() {
        SchemaArgument schemaArgument = new SchemaArgument("name", "Int", true, null, INTEGER);
        assertThat(schemaArgument.isArrayReturnType(), is(false));
        assertThat(schemaArgument.isArrayReturnTypeMandatory(), is(false));
        assertThat(schemaArgument.arrayLevels(), is(0));
    }

    @Test
    public void testSchemaGenerationWithArrays() {
       SchemaArgument schemaArgument = new SchemaArgument("name", "String", false, null, STRING);
       schemaArgument.arrayLevels(1);
       schemaArgument.arrayReturnTypeMandatory(true);
       schemaArgument.arrayReturnType(true);
       assertThat(schemaArgument.getSchemaAsString(), is("name: [String!]"));
    }

    @Test
    public void testSchemaGeneration() {
        SchemaArgument schemaArgument = new SchemaArgument("name", "Int", true, null, INTEGER);
        assertThat(schemaArgument.getSchemaAsString(), is("name: Int!"));

        schemaArgument = new SchemaArgument("name", "Int", true, 10, INTEGER);
        assertThat(schemaArgument.getSchemaAsString(), is("name: Int! = 10"));

        schemaArgument = new SchemaArgument("name", "Int", false, null, INTEGER);
        assertThat(schemaArgument.getSchemaAsString(), is("name: Int"));

        schemaArgument = new SchemaArgument("name", "Int", false, 10, INTEGER);
        assertThat(schemaArgument.getSchemaAsString(), is("name: Int = 10"));

        schemaArgument = new SchemaArgument("name", "String", false, "The Default Value", STRING);
        assertThat(schemaArgument.getSchemaAsString(), is("name: String = \"The Default Value\""));

        schemaArgument = new SchemaArgument("name", "String", true, "The Default Value", STRING);
        assertThat(schemaArgument.getSchemaAsString(), is("name: String! = \"The Default Value\""));

        schemaArgument = new SchemaArgument("name", "Int", false, 10, INTEGER);
        schemaArgument.description("Description");
        assertThat(schemaArgument.getSchemaAsString(), is("\"Description\"\nname: Int = 10"));

        // test array return types
        schemaArgument = new SchemaArgument("name", "Int", false, null, INTEGER);
        schemaArgument.arrayReturnType(true);
        schemaArgument.arrayLevels(1);
        assertThat(schemaArgument.getSchemaAsString(), is("name: [Int]"));

        schemaArgument.arrayReturnTypeMandatory(true);
        assertThat(schemaArgument.getSchemaAsString(), is("name: [Int!]"));

        schemaArgument = new SchemaArgument("name", "Int", true, null, INTEGER);
        schemaArgument.arrayReturnType(true);
        schemaArgument.arrayLevels(1);
        schemaArgument.arrayReturnTypeMandatory(true);
        assertThat(schemaArgument.getSchemaAsString(), is("name: [Int!]!"));

        schemaArgument = new SchemaArgument("name", "String", true, "Hello", STRING);
        schemaArgument.arrayReturnType(true);
        schemaArgument.arrayLevels(3);
        schemaArgument.arrayReturnTypeMandatory(true);
        assertThat(schemaArgument.getSchemaAsString(), is("name: [[[String!]]]! = \"Hello\""));

    }
}
