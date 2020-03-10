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

    private static final Class<?> STRING = String.class;
    
    @Test
    public void testEmptySchemaAsString() {
        Schema schema = new Schema();
        assertThat(schema.getSchemaAsString(), is("schema {\n}\n\n"));
    }

    @Test
    public void testTopLevelSchemaAsString() {
        Schema schema = new Schema();
        schema.addType(new SchemaType("Query", null));
        assertResultsMatch(schema.getSchemaAsString(), "test-results/schema-test-01.txt");

        schema.addType(new SchemaType("Mutation", null));
        assertResultsMatch(schema.getSchemaAsString(), "test-results/schema-test-02.txt");

        schema.addType(new SchemaType("Subscription", ""));
        assertResultsMatch(schema.getSchemaAsString(), "test-results/schema-test-03.txt");

        SchemaDirective schemaDirective = new SchemaDirective("format");
        schemaDirective.addLocation(FIELD_DEFINITION.name());
        schemaDirective.addArgument(new SchemaArgument("dateFormat", "String", true, null, STRING));
        schema.addDirective(schemaDirective);

        assertResultsMatch(schema.getSchemaAsString(), "test-results/schema-test-04.txt");
    }

    @Test
    public void testScalars() {
        Schema schema = new Schema();
        assertThat(schema.getScalars().size(), is(0));

        SchemaScalar schemaScalar1 = new SchemaScalar("Test", Date.class.getName(), ExtendedScalars.Date);
        SchemaScalar schemaScalar2 = new SchemaScalar("Test2", Date.class.getName(), ExtendedScalars.Date);
        schema.addScalar(schemaScalar1);
        schema.addScalar(schemaScalar2);

        assertThat(schema.getScalars().contains(schemaScalar1), is(true));
        assertThat(schema.getScalars().contains(schemaScalar2), is(true));
    }

    @Test
    public void testDirectives() {
        Schema schema = new Schema();
        assertThat(schema.getDirectives().size(), is(0));

        SchemaDirective schemaDirective1 = new SchemaDirective("directive1");
        SchemaDirective schemaDirective2 = new SchemaDirective("directive2");
        schema.addDirective(schemaDirective1);
        schema.addDirective(schemaDirective2);
        assertThat(schema.getDirectives().contains(schemaDirective1), is(true));
        assertThat(schema.getDirectives().contains(schemaDirective2), is(true));
    }

    @Test
    public void testInputTypes() {
        Schema schema = new Schema();
        assertThat(schema.getInputTypes().size(), is(0));
        SchemaInputType type1 = new SchemaInputType("name", "valueClass" );
        SchemaInputType type2 = new SchemaInputType("name2", "valueClass2");
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
        SchemaType schemaType1 = new SchemaType("name", "valueClass");
        SchemaType schemaType2 = new SchemaType("name2", "valueClass2");
        schema.addType(schemaType1);
        schema.addType(schemaType2);
        assertThat(schema.getTypes().size(), is(2));
        assertThat(schema.getTypes().contains(schemaType1), is(true));
        assertThat(schema.getTypes().contains(schemaType2), is(true));
    }

    @Test
    public void testEnums() {
        Schema schema = new Schema();
        assertThat(schema.getEnums().size(), is(0));
        List<String> listString = new ArrayList<>();
        listString.add("NEWHOPE");
        listString.add("JEDI");
        listString.add("EMPIRE");
        SchemaEnum schemaEnum1 = new SchemaEnum("Episode");
        schemaEnum1.getValues().addAll(listString);

        schema.addEnum(schemaEnum1);
        assertThat(schema.getEnums().size(), is(1));
        assertThat(schema.getEnums().contains(schemaEnum1), is(true));

    }

    @Test
    @Disabled
    public void testInvalidRuntimeWiring() {
        Schema schema = new Schema();
        assertThrows(IllegalStateException.class, schema::getRuntimeWiring);
    }
}