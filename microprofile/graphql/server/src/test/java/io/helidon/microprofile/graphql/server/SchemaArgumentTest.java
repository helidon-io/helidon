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
        assertThat(schemaArgument.getArgumentName(), is("name"));
        assertThat(schemaArgument.getArgumentType(), is("Int"));
        assertThat(schemaArgument.isMandatory(), is(true));
        assertThat(schemaArgument.getDefaultValue(), is(nullValue()));
        assertThat(schemaArgument.getOriginalType().getName(), is(Integer.class.getName()));

        schemaArgument = new SchemaArgument("name2", "String", false, "Default", STRING);
        assertThat(schemaArgument.getArgumentName(), is("name2"));
        assertThat(schemaArgument.getArgumentType(), is("String"));
        assertThat(schemaArgument.isMandatory(), is(false));
        assertThat(schemaArgument.getDefaultValue(), is("Default"));
        assertThat(schemaArgument.getOriginalType().getName(), is(STRING.getName()));

        schemaArgument.setArgumentType("XYZ");
        assertThat(schemaArgument.getArgumentType(), is("XYZ"));

        assertThat(schemaArgument.getDescription(), is(nullValue()));
        schemaArgument.setDescription("description");
        assertThat(schemaArgument.getDescription(), is("description"));

        assertThat(schemaArgument.isSourceArgument(), is(false));
        schemaArgument.setSourceArgument(true);
        assertThat(schemaArgument.isSourceArgument(), is(true));

        assertThat(schemaArgument.getFormat(), is(nullValue()));
        schemaArgument.setFormat(new String[] { "value-1", "value-2"});
        String[] format = schemaArgument.getFormat();
        assertThat(format, is(notNullValue()));
        assertThat(format.length, is(2));
        assertThat(format[0], is("value-1"));
        assertThat(format[1], is("value-2"));

        schemaArgument.setDefaultValue("hello");
        assertThat(schemaArgument.getDefaultValue(), is("hello"));

        schemaArgument.setDefaultValue("1");
        assertThat(schemaArgument.getDefaultValue(), is("1"));
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
        schemaArgument.setDescription("Description");
        assertThat(schemaArgument.getSchemaAsString(), is("\"Description\"\nname: Int = 10"));
    }
}
