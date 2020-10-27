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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Tests for {@link SchemaType} and {@link SchemaInputType} classes.
 */
class SchemaTypeTest {

    private static final Class<?> STRING = String.class;
    private static final Class<?> INTEGER = Integer.class;

    @Test
    public void testConstructors() {
        SchemaType schemaType = new SchemaType("Name", "com.oracle.test.Value");
        assertThat(schemaType.name(), is("Name"));
        assertThat(schemaType.valueClassName(), is("com.oracle.test.Value"));
        assertThat(schemaType.fieldDefinitions(), is(notNullValue()));

        schemaType.addFieldDefinition(new SchemaFieldDefinition("orderId", "Integer", false, true, 0));
        schemaType.addFieldDefinition(new SchemaFieldDefinition("personId", "Integer", false, true, 0));
        schemaType.addFieldDefinition(new SchemaFieldDefinition("personId", "Integer", false, true, 0));
        SchemaFieldDefinition schemaFieldDefinition = new SchemaFieldDefinition("orders", "Order", true, true, 0);
        schemaFieldDefinition.addArgument(new SchemaArgument("filter", "String", false, null, STRING));
        schemaType.addFieldDefinition(schemaFieldDefinition);

        assertThat(schemaType.fieldDefinitions().size(), is(4));
        assertThat(schemaType.fieldDefinitions().contains(schemaFieldDefinition), is(true));

        assertThat(schemaType.implementingInterface(), is(nullValue()));
        schemaType.implementingInterface("Contact");
        assertThat(schemaType.implementingInterface(), is("Contact"));

        schemaType.name("Name");
        assertThat(schemaType.name(), is("Name"));
    }

    @Test
    public void testImplementingInterface() {
        SchemaType schemaType = new SchemaType("Person", "com.oracle.Person");
        SchemaFieldDefinition schemaFieldDefinition = new SchemaFieldDefinition("person", "Person", false, true, 0);
        schemaFieldDefinition.addArgument(new SchemaArgument("filter", "String", false, null, STRING));
        schemaFieldDefinition.addArgument(new SchemaArgument("age", "Int", true, null, INTEGER));
        schemaType.addFieldDefinition(schemaFieldDefinition);
        schemaType.implementingInterface("Contact");

        assertThat(schemaType.getSchemaAsString(), is("type Person implements Contact {\n" +
                                                              "person(\nfilter: String, \nage: Int!\n): Person!\n" +
                                                              "}\n"));
    }

    @Test
    public void testTypeSchemaOutput() {
        SchemaType schemaType = new SchemaType("Person", "com.oracle.Person");
        SchemaFieldDefinition schemaFieldDefinition = new SchemaFieldDefinition("person", "Person", false, true, 0);
        schemaFieldDefinition.addArgument(new SchemaArgument("filter", "String", false, null, STRING));
        schemaType.addFieldDefinition(schemaFieldDefinition);

        assertThat(schemaType.fieldDefinitions().get(0).equals(schemaFieldDefinition), is(true));

        assertThat(schemaType.getSchemaAsString(), is("type Person {\n" +
                                                              "person(\nfilter: String\n): Person!\n" +
                                                              "}\n"));
        assertThat(schemaType.getGraphQLName(), is("type"));
    }

    @Test
    public void testTypeSchemaOutputWithDescription() {
        SchemaType schemaType = new SchemaType("Person", "com.oracle.Person");
        SchemaFieldDefinition schemaFieldDefinition = new SchemaFieldDefinition("person", "Person", false, true, 0);
        schemaFieldDefinition.addArgument(new SchemaArgument("filter", "String", false, null, STRING));
        schemaType.addFieldDefinition(schemaFieldDefinition);
        schemaType.description("Type Description");

        assertThat(schemaType.fieldDefinitions().get(0).equals(schemaFieldDefinition), is(true));

        assertThat(schemaType.getSchemaAsString(), is("\"Type Description\"\ntype Person {\n" +
                                                              "person(\nfilter: String\n): Person!\n" +
                                                              "}\n"));
        assertThat(schemaType.getGraphQLName(), is("type"));
    }

    @Test
    public void testTypeStringOutputWith2Arguments() {
        SchemaType schemaType = new SchemaType("Person", "com.oracle.Person");
        SchemaFieldDefinition schemaFieldDefinition = new SchemaFieldDefinition("person", "Person", false, true, 0);
        schemaFieldDefinition.addArgument(new SchemaArgument("filter", "String", false, null, STRING));
        schemaFieldDefinition.addArgument(new SchemaArgument("age", "Int", true, null, INTEGER));
        schemaType.addFieldDefinition(schemaFieldDefinition);

        assertThat(schemaType.getSchemaAsString(), is("type Person {\n" +
                                                              "person(\nfilter: String, \nage: Int!\n): Person!\n" +
                                                              "}\n"));
    }

    @Test
    public void testTypeStringOutputWith2ArgumentsWithArgumentDescriptions() {
        SchemaType schemaType = new SchemaType("Person", "com.oracle.Person");
        SchemaFieldDefinition schemaFieldDefinition = new SchemaFieldDefinition("person", "Person", false, true, 0);
        SchemaArgument schemaArgument1 = new SchemaArgument("filter", "String", false, null, STRING);
        schemaArgument1.description("Argument1 Description");
        schemaFieldDefinition.addArgument(schemaArgument1);

        SchemaArgument schemaArgument2 = new SchemaArgument("age", "Int", true, null, INTEGER);
        schemaArgument2.description("Argument 2 Description");
        schemaFieldDefinition.addArgument(schemaArgument2);
        schemaType.addFieldDefinition(schemaFieldDefinition);

        assertThat(schemaType.getSchemaAsString(),
                   is("type Person {\nperson(\n\"Argument1 Description\"\nfilter: String, \n\"Argument 2 Description\"\nage: "
                              + "Int!\n)"
                              + ": Person!\n}\n"));
    }

    @Test
    public void testTypeInterfaceStringOutputWith2Arguments() {
        SchemaType schemaType = new SchemaType("Person", "com.oracle.Person");
        SchemaFieldDefinition schemaFieldDefinition = new SchemaFieldDefinition("person", "Person", false, true, 0);
        schemaFieldDefinition.addArgument(new SchemaArgument("filter", "String", false, null, STRING));
        schemaFieldDefinition.addArgument(new SchemaArgument("age", "Int", true, 30, INTEGER));
        schemaType.addFieldDefinition(schemaFieldDefinition);
        schemaType.isInterface(true);
        assertThat(schemaType.getGraphQLName(), is("interface"));

        assertThat(schemaType.getSchemaAsString(), is("interface Person {\n" +
                                                              "person(\nfilter: String, \nage: Int! = 30\n): Person!\n" +
                                                              "}\n"));
    }

    @Test
    public void testTypeInterfaceStringOutputWith2ArgumentsAndStringDefault() {
        SchemaType schemaType = new SchemaType("Person", "com.oracle.Person");
        SchemaFieldDefinition schemaFieldDefinition = new SchemaFieldDefinition("person", "Person", false, true, 0);
        schemaFieldDefinition.addArgument(new SchemaArgument("filter", "String", false, "hello", STRING));
        schemaFieldDefinition.addArgument(new SchemaArgument("age", "Int", true, 30, INTEGER));
        schemaType.addFieldDefinition(schemaFieldDefinition);
        schemaType.isInterface(true);
        assertThat(schemaType.getGraphQLName(), is("interface"));

        assertThat(schemaType.getSchemaAsString(), is("interface Person {\n" +
                                                              "person(\nfilter: String = \"hello\", \nage: Int! = 30\n): Person!\n" +
                                                              "}\n"));
    }

    @Test
    public void testTypeStringOutputWith2Fields() {
        SchemaType schemaType = new SchemaType("Person", "com.oracle.Person");

        SchemaFieldDefinition schemaFieldDefinition = new SchemaFieldDefinition("person", "Person", false, true, 0);
        schemaFieldDefinition.addArgument(new SchemaArgument("personId", "String", true, null, STRING));
        schemaType.addFieldDefinition(schemaFieldDefinition);

        SchemaFieldDefinition schemaFieldDefinition2 = new SchemaFieldDefinition("people", "Person", true, false, 1);
        schemaFieldDefinition2.addArgument(new SchemaArgument("filter", "String", false, null, STRING));
        schemaFieldDefinition2.addArgument(new SchemaArgument("age", "Int", true, null, INTEGER));
        schemaType.addFieldDefinition(schemaFieldDefinition2);

        assertThat(schemaType.getSchemaAsString(), is("type Person {\n" +
                                                              "person(\npersonId: String!\n): Person!\n" +
                                                              "people(\nfilter: String, \nage: Int!\n): [Person]\n" +
                                                              "}\n"));
    }

    @Test
    public void testCreatingInputTypeFromType() {
        SchemaType schemaType = new SchemaType("Person", "com.oracle.Person");

        SchemaFieldDefinition schemaFieldDefinition = new SchemaFieldDefinition("person", "Person", false, true, 0);
        schemaFieldDefinition.addArgument(new SchemaArgument("personId", "String", true, null, STRING));
        schemaType.addFieldDefinition(schemaFieldDefinition);

        SchemaInputType inputType = schemaType.createInputType("Input");
        assertThat(inputType, is(notNullValue()));
        assertThat(inputType.fieldDefinitions().size(), is(1));
        assertThat(inputType.fieldDefinitions().get(0).arguments().size(), is(0));
        assertThat(inputType.getSchemaAsString(), is("input PersonInput {\n" +
                                                             "person: Person!\n" +
                                                             "}\n"));
    }

    @Test
    public void testToStringInternal() {
        SchemaType schemaType = new SchemaType("Person", "com.oracle.Person");
        assertThat(schemaType.toStringInternal(), is(notNullValue()));
        assertThat(schemaType.toString(), is(notNullValue()));
    }

}
