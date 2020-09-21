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

import graphql.schema.DataFetcher;
import graphql.schema.StaticDataFetcher;
import io.helidon.microprofile.graphql.server.SchemaArgument;
import io.helidon.microprofile.graphql.server.SchemaFieldDefinition;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Tests for {@link SchemaArgument} class.
 */
class SchemaFieldDefinitionTest {
    
    private static final Class<?> STRING = String.class;
    private static final Class<?> INTEGER = Integer.class;

    @Test
    public void testConstructors() {
        SchemaFieldDefinition schemaFieldDefinition = new SchemaFieldDefinition("name", "Integer", true, true, 1);
        assertThat(schemaFieldDefinition.getName(), is("name"));
        assertThat(schemaFieldDefinition.getReturnType(), is("Integer"));
        assertThat(schemaFieldDefinition.getArguments(), is(notNullValue()));
        assertThat(schemaFieldDefinition.isArrayReturnType(), is(true));
        assertThat(schemaFieldDefinition.getArrayLevels(), is(1));

        SchemaArgument schemaArgument = new SchemaArgument("filter", "String", false, null, STRING);
        schemaFieldDefinition.addArgument(schemaArgument);
        assertThat(schemaFieldDefinition.getArguments().size(), is(1));
        assertThat(schemaFieldDefinition.getArguments().get(0), is(schemaArgument));

        SchemaArgument schemaArgument2 = new SchemaArgument("filter2", "Integer", true, null, INTEGER);
        schemaFieldDefinition.addArgument(schemaArgument2);
        assertThat(schemaFieldDefinition.getArguments().size(), is(2));
        assertThat(schemaFieldDefinition.getArguments().contains(schemaArgument2), is(true));

        schemaFieldDefinition = new SchemaFieldDefinition("name2", "String", false, false, 0);
        assertThat(schemaFieldDefinition.getName(), is("name2"));
        assertThat(schemaFieldDefinition.getReturnType(), is("String"));
        assertThat(schemaFieldDefinition.isArrayReturnType(), is(false));
        assertThat(schemaFieldDefinition.isReturnTypeMandatory(), is(false));
        assertThat(schemaFieldDefinition.getArrayLevels(), is(0));

        schemaFieldDefinition.setReturnType("BLAH");
        assertThat(schemaFieldDefinition.getReturnType(), is("BLAH"));

        assertThat(schemaFieldDefinition.getFormat(), is(nullValue()));
        schemaFieldDefinition.setFormat(new String[] {"a", "b"});
        String[] format = schemaFieldDefinition.getFormat();
        assertThat(format, is(notNullValue()));
        assertThat(format.length, is(2));
        assertThat(format[0], is("a"));
        assertThat(format[1], is("b"));

        assertThat(schemaFieldDefinition.isArrayReturnTypeMandatory(), is(false));
        schemaFieldDefinition.setArrayReturnTypeMandatory(true);
        assertThat(schemaFieldDefinition.isArrayReturnTypeMandatory(), is(true));

        assertThat(schemaFieldDefinition.isDefaultFormatApplied(), is(false));
        schemaFieldDefinition.setDefaultFormatApplied(true);
        assertThat(schemaFieldDefinition.isDefaultFormatApplied(), is(true));

        assertThat(schemaFieldDefinition.isJsonbFormat(), is(false));
        schemaFieldDefinition.setJsonbFormat(true);
        assertThat(schemaFieldDefinition.isJsonbFormat(), is(true));
    }

    @Test
    public void testFieldDefinitionWithNoArguments() {
        SchemaFieldDefinition schemaFieldDefinition = new SchemaFieldDefinition("person", "Person", false, true, 0);
        assertThat(schemaFieldDefinition.getSchemaAsString(), is("person: Person!"));
    }

    @Test
    public void testFieldDefinitionWithNoArgumentsAndDescription() {
        SchemaFieldDefinition schemaFieldDefinition = new SchemaFieldDefinition("person", "Person", false, true, 0);
        schemaFieldDefinition.setDescription("Description");
        assertThat(schemaFieldDefinition.getSchemaAsString(), is("\"Description\"\nperson: Person!"));
    }

    @Test
    public void testFieldDefinitionWithNoArgumentsAndArrayType1ArrayLevel() {
        SchemaFieldDefinition schemaFieldDefinition = new SchemaFieldDefinition("person", "Person", true, true, 1);
        assertThat(schemaFieldDefinition.getSchemaAsString(), is("person: [Person]!"));
    }

    @Test
    public void testFieldDefinitionWithNoArgumentsAndArrayType2ArrayLevels() {
        SchemaFieldDefinition schemaFieldDefinition = new SchemaFieldDefinition("person", "Person", true, true, 2);
        assertThat(schemaFieldDefinition.getSchemaAsString(), is("person: [[Person]]!"));
    }

    @Test
    public void testFieldDefinitionWithNoArgumentsAndArrayType3ArrayLevels() {
        SchemaFieldDefinition schemaFieldDefinition = new SchemaFieldDefinition("result", "String", true, true, 3);
        assertThat(schemaFieldDefinition.getSchemaAsString(), is("result: [[[String]]]!"));
    }

    @Test
    public void testFieldDefinitionWith1Argument() {
        SchemaFieldDefinition schemaFieldDefinition = new SchemaFieldDefinition("person", "Person", false, true, 0);
        schemaFieldDefinition.addArgument(new SchemaArgument("filter", "String", false, null, STRING));
        assertThat(schemaFieldDefinition.getSchemaAsString(), is("person(\nfilter: String\n): Person!"));
    }

    @Test
    public void testFieldDefinitionWith1ArgumentAndDescription() {
        SchemaFieldDefinition schemaFieldDefinition = new SchemaFieldDefinition("person", "Person", false, true, 0);
        SchemaArgument schemaArgument = new SchemaArgument("filter", "String", false, null, STRING);
        schemaArgument.setDescription("Optional Filter");
        schemaFieldDefinition.addArgument(schemaArgument);
        assertThat(schemaFieldDefinition.getSchemaAsString(), is("person(\n\"Optional Filter\"\nfilter: String\n): Person!"));
    }

    @Test
    public void testFieldDefinitionWith1ArgumentAndArrayType() {
        SchemaFieldDefinition schemaFieldDefinition = new SchemaFieldDefinition("person", "Person", true, true, 1);
        schemaFieldDefinition.addArgument(new SchemaArgument("filter", "String", false, null, STRING));
        assertThat(schemaFieldDefinition.getSchemaAsString(), is("person(\nfilter: String\n): [Person]!"));
    }

    @Test
    public void testFieldDefinitionWithNoArgumentsAndMandatoryArrayType() {
        SchemaFieldDefinition schemaFieldDefinition = new SchemaFieldDefinition("superPowers", "String", true, false, 1);
        schemaFieldDefinition.setArrayReturnTypeMandatory(true);
        assertThat(schemaFieldDefinition.getSchemaAsString(), is("superPowers: [String!]"));
    }

    @Test
    public void testFieldDefinitionWith1MandatoryArgument() {
        SchemaFieldDefinition schemaFieldDefinition = new SchemaFieldDefinition("person", "Person", false, true, 0);
        schemaFieldDefinition.addArgument(new SchemaArgument("filter", "String", true, null, STRING));
        assertThat(schemaFieldDefinition.getSchemaAsString(), is("person(\nfilter: String!\n): Person!"));
    }

    @Test
    public void testFieldDefinitionWith1MandatoryArgumentAndArrayType() {
        SchemaFieldDefinition schemaFieldDefinition = new SchemaFieldDefinition("person", "Person", true, true, 1);
        schemaFieldDefinition.addArgument(new SchemaArgument("filter", "String", false, null, STRING));
        assertThat(schemaFieldDefinition.getSchemaAsString(), is("person(\nfilter: String\n): [Person]!"));
    }

    @Test
    public void testFieldDefinitionWithMultipleArguments() {
        SchemaFieldDefinition schemaFieldDefinition = new SchemaFieldDefinition("person", "Person", false, true, 0);
        schemaFieldDefinition.addArgument(new SchemaArgument("filter", "String", false, null, STRING));
        schemaFieldDefinition.addArgument(new SchemaArgument("age", "Int", true, null, INTEGER));
        assertThat(schemaFieldDefinition.getSchemaAsString(), is("person(\nfilter: String, \nage: Int!\n): Person!"));
    }

    @Test
    public void testFieldDefinitionWithMultipleArgumentsAndDescription() {
        SchemaFieldDefinition schemaFieldDefinition = new SchemaFieldDefinition("person", "Person", false, true, 0);
        SchemaArgument schemaArgument1 = new SchemaArgument("filter", "String", false, null, STRING);
        schemaArgument1.setDescription("Optional filter");
        SchemaArgument schemaArgument2 = new SchemaArgument("age", "Int", true, null, INTEGER);
        schemaArgument2.setDescription("Mandatory age");
        schemaFieldDefinition.addArgument(schemaArgument1);
        schemaFieldDefinition.addArgument(schemaArgument2);
        assertThat(schemaFieldDefinition.getSchemaAsString(),
                   is("person(\n\"Optional filter\"\nfilter: String, \n\"Mandatory age\"\nage: Int!\n): Person!"));
    }

    @Test
    public void testFieldDefinitionWithMultipleArgumentsAndBothDescriptions() {
        SchemaFieldDefinition schemaFieldDefinition = new SchemaFieldDefinition("person", "Person", false, true, 0);
        schemaFieldDefinition.setDescription("Description of field definition");
        SchemaArgument schemaArgument1 = new SchemaArgument("filter", "String", false, null, STRING);
        schemaArgument1.setDescription("Optional filter");
        SchemaArgument schemaArgument2 = new SchemaArgument("age", "Int", true, null, INTEGER);
        schemaArgument2.setDescription("Mandatory age");
        schemaFieldDefinition.addArgument(schemaArgument1);
        schemaFieldDefinition.addArgument(schemaArgument2);
        assertThat(schemaFieldDefinition.getSchemaAsString(),
                   is("\"Description of field definition\"\nperson(\n\"Optional filter\"\nfilter: String, \n\"Mandatory age\"\nage: "
                              + "Int!\n): Person!"));
    }

    @Test
    public void testFieldDefinitionWithMultipleArgumentsWithArray() {
        SchemaFieldDefinition schemaFieldDefinition = new SchemaFieldDefinition("person", "Person", true, false, 1);
        schemaFieldDefinition.addArgument(new SchemaArgument("filter", "String", false, null, STRING));
        schemaFieldDefinition.addArgument(new SchemaArgument("age", "Int", true, null, INTEGER));
        schemaFieldDefinition.addArgument(new SchemaArgument("job", "String", false, null, STRING));
        assertThat(schemaFieldDefinition.getSchemaAsString(), is("person(\nfilter: String, \nage: Int!, \njob: String\n): [Person]"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDataFetchers() {
        SchemaFieldDefinition schemaFieldDefinition = new SchemaFieldDefinition("person", "Person", true, false, 0);
        assertThat(schemaFieldDefinition.getDataFetcher(), is(nullValue()));
        schemaFieldDefinition.setDataFetcher(new StaticDataFetcher("Value"));
        DataFetcher dataFetcher = schemaFieldDefinition.getDataFetcher();
        assertThat(dataFetcher, is(notNullValue()));
        assertThat(dataFetcher instanceof StaticDataFetcher, is(true));
    }

}
