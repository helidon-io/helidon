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

import static graphql.introspection.Introspection.DirectiveLocation.FIELD;
import static graphql.introspection.Introspection.DirectiveLocation.FIELD_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.QUERY;
import static io.helidon.microprofile.graphql.server.TestHelper.createArgument;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Tests for {@link SchemaScalar} class.
 */
class SchemaDirectiveTest {
    
    private static final Class<?> STRING = String.class;
    private static final Class<?> INTEGER = Integer.class;
    
    @Test
    public void testConstructors() {
        SchemaDirective schemaDirective = SchemaDirective.builder().name("auth").build();
        assertThat(schemaDirective.name(), is("auth"));
        assertThat(schemaDirective.arguments().size(), is(0));
        assertThat(schemaDirective.locations().size(), is(0));

        SchemaArgument arg = createArgument("name", "String", true, null, STRING);
        schemaDirective.addArgument(arg);
        assertThat(schemaDirective.arguments().contains(arg), is(true));

        schemaDirective.addLocation(FIELD_DEFINITION.name());
        assertThat(schemaDirective.locations().contains(FIELD_DEFINITION.name()), is(true));

        assertThat(schemaDirective.getSchemaAsString(), is(notNullValue()));
    }

    @Test
    public void testDirectiveWith0Argument1Location() {
        SchemaDirective schemaDirective = SchemaDirective.builder()
                .name("directiveName")
                .addLocation(FIELD_DEFINITION.name()).build();
        assertThat(schemaDirective.getSchemaAsString(), is("directive @directiveName on " + FIELD_DEFINITION.name()));
    }

    @Test
    public void testDirectiveWith1Argument1Location() {
        SchemaArgument arg = createArgument("name", "String", true, null, STRING);
        SchemaDirective schemaDirective = SchemaDirective.builder()
                .name("directiveName")
                .addArgument(arg)
                .addLocation(FIELD_DEFINITION.name())
                .build();
        assertThat(schemaDirective.getSchemaAsString(), is("directive @directiveName(name: String!) on " + FIELD_DEFINITION.name()));
    }

    @Test
    public void testDirectiveWith2Argument1Location() {
        SchemaArgument arg1 = createArgument("name", "String", true, null, STRING);
        SchemaArgument arg2 = createArgument("name1", "Int", false, null, INTEGER);
        SchemaDirective schemaDirective = SchemaDirective.builder()
                .name("directiveName")
                .addArgument(arg1)
                .addArgument(arg2)
                .addLocation(FIELD_DEFINITION.name())
                .build();
        assertThat(schemaDirective.getSchemaAsString(),
                   is("directive @directiveName(name: String!, name1: Int) on " + FIELD_DEFINITION.name()));
    }

    @Test
    public void testDirectiveWith2Argument2Location() {
        SchemaArgument arg1 = createArgument("name", "String", true, null, STRING);
        SchemaArgument arg2 = createArgument("name1", "Int", false, null,INTEGER);
        SchemaDirective schemaDirective = SchemaDirective.builder()
                .name("directiveName")
                .addArgument(arg1)
                .addArgument(arg2)
                .addLocation(FIELD_DEFINITION.name())
                .addLocation(FIELD.name())
                .build();

        assertThat(schemaDirective.getSchemaAsString(),
                   is("directive @directiveName(name: String!, name1: Int) on " + FIELD_DEFINITION.name() + "|" + FIELD.name()));
    }

    @Test
    public void testDirectiveWith0Argument2Location() {
        SchemaDirective schemaDirective = SchemaDirective.builder()
                .name("directiveName")
                .addLocation(FIELD_DEFINITION.name())
                .addLocation(FIELD.name())
                .build();
        
        assertThat(schemaDirective.getSchemaAsString(),
                   is("directive @directiveName on " + FIELD_DEFINITION.name() + "|" + FIELD.name()));
    }

    @Test
    public void testDirectiveWith0Argument3Location() {
        SchemaDirective schemaDirective = SchemaDirective.builder()
                .name("directiveName")
                .addLocation(FIELD_DEFINITION.name())
                .addLocation(FIELD.name())
                .addLocation(QUERY.name())
                .build();
        assertThat(schemaDirective.getSchemaAsString(),
                   is("directive @directiveName on " + FIELD_DEFINITION.name() + "|" + FIELD.name() + "|" + QUERY.name()));
    }
}
