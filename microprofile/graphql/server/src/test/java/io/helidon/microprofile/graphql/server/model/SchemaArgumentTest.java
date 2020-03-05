/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.graphql.server.model;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for {@link SchemaArgument} class.
 */
class SchemaArgumentTest {

    @Test
    public void testConstructors() {
        SchemaArgument schemaArgument = new SchemaArgument("name", "Integer", true, null);
        assertThat(schemaArgument.getArgumentName(), is("name"));
        assertThat(schemaArgument.getArgumentType(), is("Integer"));
        assertThat(schemaArgument.isMandatory(), is(true));
        assertThat(schemaArgument.getDefaultValue(), is(nullValue()));

        schemaArgument = new SchemaArgument("name2", "String", false, "Default");
        assertThat(schemaArgument.getArgumentName(), is("name2"));
        assertThat(schemaArgument.getArgumentType(), is("String"));
        assertThat(schemaArgument.isMandatory(), is(false));
        assertThat(schemaArgument.getDefaultValue(), is("Default"));

        schemaArgument.setArgumentType("XYZ");
        assertThat(schemaArgument.getArgumentType(), is("XYZ"));

        assertThat(schemaArgument.getDescription(), is(nullValue()));
        schemaArgument.setDescription("description");
         assertThat(schemaArgument.getDescription(), is("description"));
    }

    @Test
    public void testSchemaGeneration() {
        SchemaArgument schemaArgument = new SchemaArgument("name", "Integer", true, null);
        assertThat(schemaArgument.getSchemaAsString(), is("name: Integer!"));

        schemaArgument = new SchemaArgument("name", "Integer", true, 10);
        assertThat(schemaArgument.getSchemaAsString(), is("name: Integer! = 10"));

        schemaArgument = new SchemaArgument("name", "Integer", false, null);
        assertThat(schemaArgument.getSchemaAsString(), is("name: Integer"));

        schemaArgument = new SchemaArgument("name", "Integer", false, 10);
        assertThat(schemaArgument.getSchemaAsString(), is("name: Integer = 10"));

        schemaArgument = new SchemaArgument("name", "String", false, "The Default Value");
        assertThat(schemaArgument.getSchemaAsString(), is("name: String = \"The Default Value\""));

        schemaArgument = new SchemaArgument("name", "String", true, "The Default Value");
        assertThat(schemaArgument.getSchemaAsString(), is("name: String! = \"The Default Value\""));

        schemaArgument = new SchemaArgument("name", "Integer", false, 10);
        schemaArgument.setDescription("Description");
        assertThat(schemaArgument.getSchemaAsString(), is("\"Description\"\nname: Integer = 10"));
    }
}