/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
 * Tests for {@link Argument} class.
 */
class ArgumentTest {

    @Test
    public void testConstructors() {
        Argument argument = new Argument("name", "Integer", true, null);
        assertThat(argument.getArgumentName(), is("name"));
        assertThat(argument.getArgumentType(), is("Integer"));
        assertThat(argument.isMandatory(), is(true));
        assertThat(argument.getDefaultValue(), is(nullValue()));

        argument = new Argument("name2", "String", false, "Default");
        assertThat(argument.getArgumentName(), is("name2"));
        assertThat(argument.getArgumentType(), is("String"));
        assertThat(argument.isMandatory(), is(false));
        assertThat(argument.getDefaultValue(), is("Default"));

        argument.setArgumentType("XYZ");
        assertThat(argument.getArgumentType(), is("XYZ"));

        assertThat(argument.getDescription(), is(nullValue()));
        argument.setDescription("description");
         assertThat(argument.getDescription(), is("description"));
    }

    @Test
    public void testSchemaGeneration() {
        Argument argument = new Argument("name", "Integer", true, null);
        assertThat(argument.getSchemaAsString(), is("name: Integer!"));

        argument = new Argument("name", "Integer", true, 10);
        assertThat(argument.getSchemaAsString(), is("name: Integer! = 10"));

        argument = new Argument("name", "Integer", false, null);
        assertThat(argument.getSchemaAsString(), is("name: Integer"));

        argument= new Argument("name", "Integer", false, 10);
        assertThat(argument.getSchemaAsString(), is("name: Integer = 10"));

        argument = new Argument("name", "String", false, "The Default Value");
        assertThat(argument.getSchemaAsString(), is("name: String = \"The Default Value\""));

        argument = new Argument("name", "String", true, "The Default Value");
        assertThat(argument.getSchemaAsString(), is("name: String! = \"The Default Value\""));

        argument = new Argument("name", "Integer", false, 10);
        argument.setDescription("Description");
        assertThat(argument.getSchemaAsString(), is("# Description\nname: Integer = 10"));
    }
}