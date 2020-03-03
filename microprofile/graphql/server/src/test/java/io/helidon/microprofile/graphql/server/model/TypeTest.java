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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Tests for {@link Type} and {@link InputType} classes.
 */
class TypeTest {

    @Test
    public void testConstructors() {
        Type type = new Type("Name", "com.oracle.test.Key", "com.oracle.test.Value");
        assertThat(type.getName(), is("Name"));
        assertThat(type.getKeyClassName(), is("com.oracle.test.Key"));
        assertThat(type.getValueClassName(), is("com.oracle.test.Value"));
        assertThat(type.getFieldDefinitions(), is(notNullValue()));

        type.addFieldDefinition(new FieldDefinition("orderId", "Integer", false, true));
        type.addFieldDefinition(new FieldDefinition("personId", "Integer", false, true));
        type.addFieldDefinition(new FieldDefinition("personId", "Integer", false, true));
        FieldDefinition fieldDefinition = new FieldDefinition("orders", "Order", true, true);
        fieldDefinition.addArgument(new Argument("filter", "String", false, null));
        type.addFieldDefinition(fieldDefinition);

        assertThat(type.getFieldDefinitions().size(), is(4));
        assertThat(type.getFieldDefinitions().contains(fieldDefinition), is(true));

        type.setKeyClassName("name");
        assertThat(type.getKeyClassName(), is("name"));

        assertThat(type.getImplementingInterface(), is(nullValue()));
        type.setImplementingInterface("Contact");
        assertThat(type.getImplementingInterface(), is("Contact"));
    }

    @Test
    public void testImplementingInterface() {
        Type type = new Type("Person", "java.lang.Integer", "com.oracle.Person");
        FieldDefinition fieldDefinition = new FieldDefinition("person", "Person", false, true);
        fieldDefinition.addArgument(new Argument("filter", "String", false, null));
        fieldDefinition.addArgument(new Argument("age", "Int", true, null));
        type.addFieldDefinition(fieldDefinition);
        type.setImplementingInterface("Contact");

        assertThat(type.getSchemaAsString(), is("type Person implements Contact {\n" +
                                                        "person(filter: String, age: Int!): Person!\n" +
                                                        "}\n"));
    }

    @Test
    public void testTypeSchemaOutput() {
        Type type = new Type("Person", "java.lang.Integer", "com.oracle.Person");
        FieldDefinition fieldDefinition = new FieldDefinition("person", "Person", false, true);
        fieldDefinition.addArgument(new Argument("filter", "String", false, null));
        type.addFieldDefinition(fieldDefinition);

        assertThat(type.getFieldDefinitions().get(0).equals(fieldDefinition), is(true));

        assertThat(type.getSchemaAsString(), is("type Person {\n" +
                                                        "person(filter: String): Person!\n" +
                                                        "}\n"));
        assertThat(type.getGraphQLName(), is("type"));
    }

    @Test
    public void testTypeSchemaOutputWithDescription() {
        Type type = new Type("Person", "java.lang.Integer", "com.oracle.Person");
        FieldDefinition fieldDefinition = new FieldDefinition("person", "Person", false, true);
        fieldDefinition.addArgument(new Argument("filter", "String", false, null));
        type.addFieldDefinition(fieldDefinition);
        type.setDescription("Type Description");

        assertThat(type.getFieldDefinitions().get(0).equals(fieldDefinition), is(true));

        assertThat(type.getSchemaAsString(), is("# Type Description\ntype Person {\n" +
                                                        "person(filter: String): Person!\n" +
                                                        "}\n"));
        assertThat(type.getGraphQLName(), is("type"));
    }

    @Test
    public void testTypeStringOutputWith2Arguments() {
        Type type = new Type("Person", "java.lang.Integer", "com.oracle.Person");
        FieldDefinition fieldDefinition = new FieldDefinition("person", "Person", false, true);
        fieldDefinition.addArgument(new Argument("filter", "String", false, null));
        fieldDefinition.addArgument(new Argument("age", "Int", true, null));
        type.addFieldDefinition(fieldDefinition);

        assertThat(type.getSchemaAsString(), is("type Person {\n" +
                                                        "person(filter: String, age: Int!): Person!\n" +
                                                        "}\n"));
    }

    @Test
    public void testTypeStringOutputWith2ArgumentsWithArgumentDescriptions() {
        Type type = new Type("Person", "java.lang.Integer", "com.oracle.Person");
        FieldDefinition fieldDefinition = new FieldDefinition("person", "Person", false, true);
        Argument argument1 = new Argument("filter", "String", false, null);
        argument1.setDescription("Argument1 Description");
        fieldDefinition.addArgument(argument1);

        Argument argument2 = new Argument("age", "Int", true, null);
        argument2.setDescription("Argument 2 Description");
        fieldDefinition.addArgument(argument2);
        type.addFieldDefinition(fieldDefinition);

        assertThat(type.getSchemaAsString(),
                   is("type Person {\nperson(\n# Argument1 Description\nfilter: String,\n# Argument 2 Description\nage: Int!\n)"
                              + ": Person!\n}\n"));
    }

    @Test
    public void testTypeInterfaceStringOutputWith2Arguments() {
        Type type = new Type("Person", "java.lang.Integer", "com.oracle.Person");
        FieldDefinition fieldDefinition = new FieldDefinition("person", "Person", false, true);
        fieldDefinition.addArgument(new Argument("filter", "String", false, null));
        fieldDefinition.addArgument(new Argument("age", "Int", true, 30));
        type.addFieldDefinition(fieldDefinition);
        type.setIsInterface(true);
        assertThat(type.getGraphQLName(), is("interface"));

        assertThat(type.getSchemaAsString(), is("interface Person {\n" +
                                                        "person(filter: String, age: Int! = 30): Person!\n" +
                                                        "}\n"));
    }

    @Test
    public void testTypeStringOutputWith2Fields() {
        Type type = new Type("Person", "java.lang.Integer", "com.oracle.Person");

        FieldDefinition fieldDefinition = new FieldDefinition("person", "Person", false, true);
        fieldDefinition.addArgument(new Argument("personId", "String", true, null));
        type.addFieldDefinition(fieldDefinition);

        FieldDefinition fieldDefinition2 = new FieldDefinition("people", "Person", true, false);
        fieldDefinition2.addArgument(new Argument("filter", "String", false, null));
        fieldDefinition2.addArgument(new Argument("age", "Int", true, null));
        type.addFieldDefinition(fieldDefinition2);

        assertThat(type.getSchemaAsString(), is("type Person {\n" +
                                                        "person(personId: String!): Person!\n" +
                                                        "people(filter: String, age: Int!): [Person]\n" +
                                                        "}\n"));
    }

    @Test
    public void testCreatingInputTypeFromType() {
        Type type = new Type("Person", "java.lang.Integer", "com.oracle.Person");

        FieldDefinition fieldDefinition = new FieldDefinition("person", "Person", false, true);
        fieldDefinition.addArgument(new Argument("personId", "String", true, null));
        type.addFieldDefinition(fieldDefinition);

        InputType inputType = type.createInputType("Input");
        assertThat(inputType, is(notNullValue()));
        assertThat(inputType.getFieldDefinitions().size(), is(1));
        assertThat(inputType.getFieldDefinitions().get(0).getArguments().size(), is(0));
        assertThat(inputType.getSchemaAsString(), is("input PersonInput {\n" +
                                                             "person: Person!\n" +
                                                             "}\n"));
    }

    @Test
    public void testToStringInternal() {
        Type type = new Type("Person", "java.lang.Integer", "com.oracle.Person");
        assertThat(type.toStringInternal(), is(notNullValue()));
        assertThat(type.toString(), is(notNullValue()));
    }

}