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

import graphql.schema.DataFetcher;
import graphql.schema.StaticDataFetcher;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Tests for {@link Argument} class.
 */
class FieldDefinitionTest {
    @Test
    public void testConstructors() {
        FieldDefinition fieldDefinition = new FieldDefinition("name", "Integer", true, true);
        assertThat(fieldDefinition.getName(), is("name"));
        assertThat(fieldDefinition.getReturnType(), is("Integer"));
        assertThat(fieldDefinition.getArguments(), is(notNullValue()));
        assertThat(fieldDefinition.isArrayReturnType(), is(true));

        Argument argument = new Argument("filter", "String", false, null);
        fieldDefinition.addArgument(argument);
        assertThat(fieldDefinition.getArguments().size(), is(1));
        assertThat(fieldDefinition.getArguments().get(0), is(argument));

        Argument argument2 = new Argument("filter2", "Integer", true, null);
        fieldDefinition.addArgument(argument2);
        assertThat(fieldDefinition.getArguments().size(), is(2));
        assertThat(fieldDefinition.getArguments().contains(argument2), is(true));

        fieldDefinition = new FieldDefinition("name2", "String", false, false);
        assertThat(fieldDefinition.getName(), is("name2"));
        assertThat(fieldDefinition.getReturnType(), is("String"));
        assertThat(fieldDefinition.isArrayReturnType(), is(false));
        assertThat(fieldDefinition.isReturnTypeMandatory(), is(false));

        fieldDefinition.setReturnType("BLAH");
        assertThat(fieldDefinition.getReturnType(), is("BLAH"));
    }

    @Test
    public void testFieldDefinitionWithNoArguments() {
        FieldDefinition fieldDefinition = new FieldDefinition("person", "Person", false, true);
        assertThat(fieldDefinition.getSchemaAsString(), is("person: Person!"));
    }

    @Test
    public void testFieldDefinitionWithNoArgumentsAndDescription() {
        FieldDefinition fieldDefinition = new FieldDefinition("person", "Person", false, true);
        fieldDefinition.setDescription("Description");
        assertThat(fieldDefinition.getSchemaAsString(), is("# Description\nperson: Person!"));
    }

    @Test
    public void testFieldDefinitionWithNoArgumentsAndArrayType() {
        FieldDefinition fieldDefinition = new FieldDefinition("person", "Person", true, true);
        assertThat(fieldDefinition.getSchemaAsString(), is("person: [Person]!"));
    }

    @Test
    public void testFieldDefinitionWith1Argument() {
        FieldDefinition fieldDefinition = new FieldDefinition("person", "Person", false, true);
        fieldDefinition.addArgument(new Argument("filter", "String", false, null));
        assertThat(fieldDefinition.getSchemaAsString(), is("person(filter: String): Person!"));
    }

    @Test
    public void testFieldDefinitionWith1ArgumentAndDescription() {
        FieldDefinition fieldDefinition = new FieldDefinition("person", "Person", false, true);
        Argument argument = new Argument("filter", "String", false, null);
        argument.setDescription("Optional Filter");
        fieldDefinition.addArgument(argument);
        assertThat(fieldDefinition.getSchemaAsString(), is("person(\n# Optional Filter\nfilter: String\n): Person!"));
    }

    @Test
    public void testFieldDefinitionWith1ArgumentAndArrayType() {
        FieldDefinition fieldDefinition = new FieldDefinition("person", "Person", true, true);
        fieldDefinition.addArgument(new Argument("filter", "String", false, null));
        assertThat(fieldDefinition.getSchemaAsString(), is("person(filter: String): [Person]!"));
    }

    @Test
    public void testFieldDefinitionWith1MandatoryArgument() {
        FieldDefinition fieldDefinition = new FieldDefinition("person", "Person", false, true);
        fieldDefinition.addArgument(new Argument("filter", "String", true, null));
        assertThat(fieldDefinition.getSchemaAsString(), is("person(filter: String!): Person!"));
    }

    @Test
    public void testFieldDefinitionWith1MandatoryArgumentAndArrayType() {
        FieldDefinition fieldDefinition = new FieldDefinition("person", "Person", true, true);
        fieldDefinition.addArgument(new Argument("filter", "String", false, null));
        assertThat(fieldDefinition.getSchemaAsString(), is("person(filter: String): [Person]!"));
    }

    @Test
    public void testFieldDefinitionWithMultipleArguments() {
        FieldDefinition fieldDefinition = new FieldDefinition("person", "Person", false, true);
        fieldDefinition.addArgument(new Argument("filter", "String", false, null));
        fieldDefinition.addArgument(new Argument("age", "Int", true, null));
        assertThat(fieldDefinition.getSchemaAsString(), is("person(filter: String, age: Int!): Person!"));
    }

    @Test
    public void testFieldDefinitionWithMultipleArgumentsAndDescription() {
        FieldDefinition fieldDefinition = new FieldDefinition("person", "Person", false, true);
        Argument argument1 = new Argument("filter", "String", false, null);
        argument1.setDescription("Optional filter");
        Argument argument2 = new Argument("age", "Int", true, null);
        argument2.setDescription("Mandatory age");
        fieldDefinition.addArgument(argument1);
        fieldDefinition.addArgument(argument2);
        assertThat(fieldDefinition.getSchemaAsString(),
                   is("person(\n# Optional filter\nfilter: String,\n# Mandatory age\nage: Int!\n): Person!"));
    }

    @Test
    public void testFieldDefinitionWithMultipleArgumentsAndBothDescriptions() {
        FieldDefinition fieldDefinition = new FieldDefinition("person", "Person", false, true);
        fieldDefinition.setDescription("Description of field definition");
        Argument argument1 = new Argument("filter", "String", false, null);
        argument1.setDescription("Optional filter");
        Argument argument2 = new Argument("age", "Int", true, null);
        argument2.setDescription("Mandatory age");
        fieldDefinition.addArgument(argument1);
        fieldDefinition.addArgument(argument2);
        assertThat(fieldDefinition.getSchemaAsString(),
                   is("# Description of field definition\nperson(\n# Optional filter\nfilter: String,\n# Mandatory age\nage: "
                              + "Int!\n): Person!"));
    }

    @Test
    public void testFieldDefinitionWithMultipleArgumentsWithArray() {
        FieldDefinition fieldDefinition = new FieldDefinition("person", "Person", true, false);
        fieldDefinition.addArgument(new Argument("filter", "String", false, null));
        fieldDefinition.addArgument(new Argument("age", "Int", true, null));
        fieldDefinition.addArgument(new Argument("job", "String", false, null));
        assertThat(fieldDefinition.getSchemaAsString(), is("person(filter: String, age: Int!, job: String): [Person]"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDataFetchers() {
        FieldDefinition fieldDefinition = new FieldDefinition("person", "Person", true, false);
        assertThat(fieldDefinition.getDataFetcher(), is(nullValue()));
        fieldDefinition.setDataFetcher(new StaticDataFetcher("Value"));
        DataFetcher dataFetcher = fieldDefinition.getDataFetcher();
        assertThat(dataFetcher, is(notNullValue()));
        assertThat(dataFetcher instanceof StaticDataFetcher, is(true));
    }

}