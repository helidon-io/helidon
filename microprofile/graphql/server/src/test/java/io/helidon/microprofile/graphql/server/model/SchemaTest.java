/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import graphql.scalars.ExtendedScalars;
import io.helidon.microprofile.graphql.server.AbstractGraphQLTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static graphql.introspection.Introspection.DirectiveLocation.FIELD_DEFINITION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link Schema} class.
 */
class SchemaTest extends AbstractGraphQLTest {

    @Test
    public void testEmptySchemaAsString() {
        Schema schema = new Schema();
        assertThat(schema.getSchemaAsString(), is("schema {\n}\n\n"));
    }

    @Test
    public void testTopLevelSchemaAsString() {
        Schema schema = new Schema();
        schema.addType(new Type("Query", null, null));
        assertResultsMatch(schema.getSchemaAsString(), "test-results/schema-test-01.txt");

        schema.addType(new Type("Mutation", null, null));
        assertResultsMatch(schema.getSchemaAsString(), "test-results/schema-test-02.txt");

        schema.addType(new Type("Subscription", "", ""));
        assertResultsMatch(schema.getSchemaAsString(), "test-results/schema-test-03.txt");

        Directive directive = new Directive("format");
        directive.addLocation(FIELD_DEFINITION.name());
        directive.addArgument(new Argument("dateFormat", "String", true, null));
        schema.addDirective(directive);

        assertResultsMatch(schema.getSchemaAsString(), "test-results/schema-test-04.txt");
    }

    @Test
    public void testScalars() {
        Schema schema = new Schema();
        assertThat(schema.getScalars().size(), is(0));

        Scalar scalar1 = new Scalar("Test", Date.class.getName(), ExtendedScalars.Date);
        Scalar scalar2 = new Scalar("Test2", Date.class.getName(), ExtendedScalars.Date);
        schema.addScalar(scalar1);
        schema.addScalar(scalar2);

        assertThat(schema.getScalars().contains(scalar1), is(true));
        assertThat(schema.getScalars().contains(scalar2), is(true));
    }

    @Test
    public void testDirectives() {
        Schema schema = new Schema();
        assertThat(schema.getDirectives().size(), is(0));

        Directive directive1 = new Directive("directive1");
        Directive directive2 = new Directive("directive2");
        schema.addDirective(directive1);
        schema.addDirective(directive2);
        assertThat(schema.getDirectives().contains(directive1), is(true));
        assertThat(schema.getDirectives().contains(directive2), is(true));
    }

    @Test
    public void testInputTypes() {
        Schema schema = new Schema();
        assertThat(schema.getInputTypes().size(), is(0));
        InputType type1 = new InputType("name", "keyClass", "valueClass" );
        InputType type2 = new InputType("name2", "keyClass2", "valueClass2");
        schema.addInputType(type1);
        schema.addInputType(type2);
        assertThat(schema.getInputTypes().size(), is(2));
        assertThat(schema.getInputTypes().contains(type1), is(true));
        assertThat(schema.getInputTypes().contains(type2), is(true));
    }

    @Test
    public void tesTypes() {
        Schema schema = new Schema();
        assertThat(schema.getInputTypes().size(), is(0));
        Type type1 = new Type("name", "keyClass", "valueClass");
        Type type2 = new Type("name2", "keyClass2", "valueClass2");
        schema.addType(type1);
        schema.addType(type2);
        assertThat(schema.getTypes().size(), is(2));
        assertThat(schema.getTypes().contains(type1), is(true));
        assertThat(schema.getTypes().contains(type2), is(true));
    }

    @Test
    public void testEnums() {
        Schema schema = new Schema();
        assertThat(schema.getEnums().size(), is(0));
        List<String> listString = new ArrayList<>();
        listString.add("NEWHOPE");
        listString.add("JEDI");
        listString.add("EMPIRE");
        Enum enum1 = new Enum("Episode");
        enum1.getValues().addAll(listString);

        schema.addEnum(enum1);
        assertThat(schema.getEnums().size(), is(1));
        assertThat(schema.getEnums().contains(enum1), is(true));

    }

    @Test
    @Disabled
    public void testInvalidRuntimeWiring() {
        Schema schema = new Schema();
        assertThrows(IllegalStateException.class, () -> schema.getRuntimeWiring());
    }
}