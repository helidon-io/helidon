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

import org.junit.jupiter.api.Test;

import static io.helidon.microprofile.graphql.server.TestHelper.createArgument;
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
        SchemaFieldDefinition schemaFieldDefinition = SchemaFieldDefinition.builder()
                .name("name")
                .returnType("Integer")
                .arrayReturnType(true)
                .returnTypeMandatory(true)
                .arrayLevels(1)
                .build();

        assertThat(schemaFieldDefinition.name(), is("name"));
        assertThat(schemaFieldDefinition.returnType(), is("Integer"));
        assertThat(schemaFieldDefinition.arguments(), is(notNullValue()));
        assertThat(schemaFieldDefinition.isArrayReturnType(), is(true));
        assertThat(schemaFieldDefinition.arrayLevels(), is(1));

        SchemaArgument schemaArgument = createArgument("filter", "String", false, null, STRING);
        schemaFieldDefinition.addArgument(schemaArgument);
        assertThat(schemaFieldDefinition.arguments().size(), is(1));
        assertThat(schemaFieldDefinition.arguments().get(0), is(schemaArgument));

        SchemaArgument schemaArgument2 = createArgument("filter2", "Integer", true, null, INTEGER);
        schemaFieldDefinition.addArgument(schemaArgument2);
        assertThat(schemaFieldDefinition.arguments().size(), is(2));
        assertThat(schemaFieldDefinition.arguments().contains(schemaArgument2), is(true));

        schemaFieldDefinition = SchemaFieldDefinition.builder()
                .name("name2")
                .returnType("String")
                .arrayReturnType(false)
                .returnTypeMandatory(false)
                .arrayLevels(0)
                .build();
        assertThat(schemaFieldDefinition.name(), is("name2"));
        assertThat(schemaFieldDefinition.returnType(), is("String"));
        assertThat(schemaFieldDefinition.isArrayReturnType(), is(false));
        assertThat(schemaFieldDefinition.isReturnTypeMandatory(), is(false));
        assertThat(schemaFieldDefinition.arrayLevels(), is(0));

        schemaFieldDefinition.returnType("BLAH");
        assertThat(schemaFieldDefinition.returnType(), is("BLAH"));

        assertThat(schemaFieldDefinition.format(), is(nullValue()));
        schemaFieldDefinition.format(new String[] { "a", "b" });
        String[] format = schemaFieldDefinition.format();
        assertThat(format, is(notNullValue()));
        assertThat(format.length, is(2));
        assertThat(format[0], is("a"));
        assertThat(format[1], is("b"));

        assertThat(schemaFieldDefinition.isArrayReturnTypeMandatory(), is(false));
        schemaFieldDefinition.arrayReturnTypeMandatory(true);
        assertThat(schemaFieldDefinition.isArrayReturnTypeMandatory(), is(true));

        assertThat(schemaFieldDefinition.isDefaultFormatApplied(), is(false));
        schemaFieldDefinition.defaultFormatApplied(true);
        assertThat(schemaFieldDefinition.isDefaultFormatApplied(), is(true));

        assertThat(schemaFieldDefinition.isJsonbFormat(), is(false));
        schemaFieldDefinition.jsonbFormat(true);
        assertThat(schemaFieldDefinition.isJsonbFormat(), is(true));
    }

    @Test
    public void testFieldDefinitionWithNoArguments() {
        SchemaFieldDefinition schemaFieldDefinition = SchemaFieldDefinition.builder()
                .name("person")
                .returnType("Person")
                .arrayReturnType(false)
                .returnTypeMandatory(true)
                .arrayLevels(0)
                .build();

        assertThat(schemaFieldDefinition.getSchemaAsString(), is("person: Person!"));
    }

    @Test
    public void testFieldDefinitionWithNoArgumentsAndDescription() {
        SchemaFieldDefinition schemaFieldDefinition = SchemaFieldDefinition.builder()
                .name("person")
                .returnType("Person")
                .arrayReturnType(false)
                .returnTypeMandatory(true)
                .arrayLevels(0)
                .description("Description")
                .build();

        assertThat(schemaFieldDefinition.getSchemaAsString(), is("\"Description\"\nperson: Person!"));
    }

    @Test
    public void testFieldDefinitionWithNoArgumentsAndArrayType1ArrayLevel() {
        SchemaFieldDefinition schemaFieldDefinition = SchemaFieldDefinition.builder()
                .name("person")
                .returnType("Person")
                .arrayReturnType(true)
                .returnTypeMandatory(true)
                .arrayLevels(1)
                .build();

        assertThat(schemaFieldDefinition.getSchemaAsString(), is("person: [Person]!"));
    }

    @Test
    public void testFieldDefinitionWithNoArgumentsAndArrayType2ArrayLevels() {
        SchemaFieldDefinition schemaFieldDefinition = SchemaFieldDefinition.builder()
                .name("person")
                .returnType("Person")
                .arrayReturnType(true)
                .returnTypeMandatory(true)
                .arrayLevels(2)
                .build();

        assertThat(schemaFieldDefinition.getSchemaAsString(), is("person: [[Person]]!"));
    }

    @Test
    public void testFieldDefinitionWithNoArgumentsAndArrayType3ArrayLevels() {
        SchemaFieldDefinition schemaFieldDefinition = SchemaFieldDefinition.builder()
                .name("result")
                .returnType("String")
                .arrayReturnType(true)
                .returnTypeMandatory(true)
                .arrayLevels(3)
                .build();

        assertThat(schemaFieldDefinition.getSchemaAsString(), is("result: [[[String]]]!"));
    }

    @Test
    public void testFieldDefinitionWith1Argument() {
        SchemaFieldDefinition schemaFieldDefinition = SchemaFieldDefinition.builder()
                .name("person")
                .returnType("Person")
                .arrayReturnType(false)
                .returnTypeMandatory(true)
                .arrayLevels(0)
                .addArgument(createArgument("filter", "String", false, null, STRING))
                .build();

        assertThat(schemaFieldDefinition.getSchemaAsString(), is("person(\nfilter: String\n): Person!"));
    }

    @Test
    public void testFieldDefinitionWith1ArgumentAndDescription() {
        SchemaArgument schemaArgument = createArgument("filter", "String", false, null, STRING);
        schemaArgument.description("Optional Filter");
        SchemaFieldDefinition schemaFieldDefinition = SchemaFieldDefinition.builder()
                .name("person")
                .returnType("Person")
                .arrayReturnType(false)
                .returnTypeMandatory(true)
                .arrayLevels(0)
                .addArgument(schemaArgument)
                .build();

        assertThat(schemaFieldDefinition.getSchemaAsString(), is("person(\n\"Optional Filter\"\nfilter: String\n): Person!"));
    }

    @Test
    public void testFieldDefinitionWith1ArgumentAndArrayType() {
        SchemaFieldDefinition schemaFieldDefinition = SchemaFieldDefinition.builder()
                .name("person")
                .returnType("Person")
                .arrayReturnType(true)
                .returnTypeMandatory(true)
                .arrayLevels(1)
                .addArgument(createArgument("filter", "String", false, null, STRING))
                .build();

        assertThat(schemaFieldDefinition.getSchemaAsString(), is("person(\nfilter: String\n): [Person]!"));
    }

    @Test
    public void testFieldDefinitionWithNoArgumentsAndMandatoryArrayType() {
        SchemaFieldDefinition schemaFieldDefinition = SchemaFieldDefinition.builder()
                .name("superPowers")
                .returnType("String")
                .arrayReturnType(true)
                .returnTypeMandatory(false)
                .arrayLevels(1)
                .arrayReturnTypeMandatory(true)
                .build();

        assertThat(schemaFieldDefinition.getSchemaAsString(), is("superPowers: [String!]"));
    }

    @Test
    public void testFieldDefinitionWith1MandatoryArgument() {
        SchemaFieldDefinition schemaFieldDefinition = SchemaFieldDefinition.builder()
                .name("person")
                .returnType("Person")
                .arrayReturnType(false)
                .returnTypeMandatory(true)
                .arrayLevels(0)
                .addArgument(createArgument("filter", "String", true, null, STRING))
                .build();

        assertThat(schemaFieldDefinition.getSchemaAsString(), is("person(\nfilter: String!\n): Person!"));
    }

    @Test
    public void testFieldDefinitionWith1MandatoryArgumentAndArrayType() {
        SchemaFieldDefinition schemaFieldDefinition = SchemaFieldDefinition.builder()
                .name("person")
                .returnType("Person")
                .arrayReturnType(true)
                .returnTypeMandatory(true)
                .arrayLevels(1)
                .addArgument(createArgument("filter", "String", false, null, STRING))
                .build();

        assertThat(schemaFieldDefinition.getSchemaAsString(), is("person(\nfilter: String\n): [Person]!"));
    }

    @Test
    public void testFieldDefinitionWithMultipleArguments() {
        SchemaFieldDefinition schemaFieldDefinition = SchemaFieldDefinition.builder()
                .name("person")
                .returnType("Person")
                .arrayReturnType(false)
                .returnTypeMandatory(true)
                .arrayLevels(0)
                .addArgument(createArgument("filter", "String", false, null, STRING))
                .addArgument(createArgument("age", "Int", true, null, INTEGER))
                .build();

        assertThat(schemaFieldDefinition.getSchemaAsString(), is("person(\nfilter: String, \nage: Int!\n): Person!"));
    }

    @Test
    public void testFieldDefinitionWithMultipleArgumentsAndDescription() {
        SchemaFieldDefinition schemaFieldDefinition = SchemaFieldDefinition.builder()
                .name("person")
                .returnType("Person")
                .arrayReturnType(false)
                .returnTypeMandatory(true)
                .arrayLevels(0)
                .build();

        SchemaArgument schemaArgument1 = createArgument("filter", "String", false, null, STRING);
        schemaArgument1.description("Optional filter");
        SchemaArgument schemaArgument2 = createArgument("age", "Int", true, null, INTEGER);
        schemaArgument2.description("Mandatory age");
        schemaFieldDefinition.addArgument(schemaArgument1);
        schemaFieldDefinition.addArgument(schemaArgument2);
        assertThat(schemaFieldDefinition.getSchemaAsString(),
                   is("person(\n\"Optional filter\"\nfilter: String, \n\"Mandatory age\"\nage: Int!\n): Person!"));
    }

    @Test
    public void testFieldDefinitionWithMultipleArgumentsAndBothDescriptions() {
        SchemaFieldDefinition schemaFieldDefinition = SchemaFieldDefinition.builder()
                .name("person")
                .returnType("Person")
                .arrayReturnType(false)
                .returnTypeMandatory(true)
                .arrayLevels(0)
                .description("Description of field definition")
                .build();

        SchemaArgument schemaArgument1 = createArgument("filter", "String", false, null, STRING);
        schemaArgument1.description("Optional filter");
        SchemaArgument schemaArgument2 = createArgument("age", "Int", true, null, INTEGER);
        schemaArgument2.description("Mandatory age");
        schemaFieldDefinition.addArgument(schemaArgument1);
        schemaFieldDefinition.addArgument(schemaArgument2);
        assertThat(schemaFieldDefinition.getSchemaAsString(),
                   is("\"Description of field definition\"\nperson(\n\"Optional filter\"\nfilter: String, \n\"Mandatory "
                              + "age\"\nage: "
                              + "Int!\n): Person!"));
    }

    @Test
    public void testFieldDefinitionWithMultipleArgumentsWithArray() {
        SchemaFieldDefinition schemaFieldDefinition = SchemaFieldDefinition.builder()
                .name("person")
                .returnType("Person")
                .arrayReturnType(true)
                .returnTypeMandatory(false)
                .arrayLevels(1)
                .addArgument(createArgument("filter", "String", false, null, STRING))
                .addArgument(createArgument("age", "Int", true, null, INTEGER))
                .addArgument(createArgument("job", "String", false, null, STRING))
                .build();

        assertThat(schemaFieldDefinition.getSchemaAsString(),
                   is("person(\nfilter: String, \nage: Int!, \njob: String\n): [Person]"));
    }

    @Test
    public void testDataFetchers() {
        SchemaFieldDefinition schemaFieldDefinition = SchemaFieldDefinition.builder()
                .name("person")
                .returnType("Person")
                .arrayReturnType(true)
                .returnTypeMandatory(false)
                .arrayLevels(0)
                .build();

        assertThat(schemaFieldDefinition.dataFetcher(), is(nullValue()));
        schemaFieldDefinition.dataFetcher(new StaticDataFetcher("Value"));
        DataFetcher dataFetcher = schemaFieldDefinition.dataFetcher();
        assertThat(dataFetcher, is(notNullValue()));
        assertThat(dataFetcher instanceof StaticDataFetcher, is(true));
    }

}
