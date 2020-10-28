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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import graphql.scalars.ExtendedScalars;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static graphql.introspection.Introspection.DirectiveLocation.FIELD_DEFINITION;
import static io.helidon.microprofile.graphql.server.TestHelper.createArgument;
import static io.helidon.microprofile.graphql.server.TestHelper.createSchemaType;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
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
        Schema schema = Schema.create();
        assertThat(schema.getSchemaAsString(), is("schema {\n}\n\n"));
    }

    @Test
    public void testTopLevelSchemaAsString() {
        Schema schema = Schema.create();
        schema.addType(createSchemaType("Query", null));
        assertResultsMatch(schema.getSchemaAsString(), "test-results/schema-test-01.txt");

        schema.addType(createSchemaType("Mutation", null));
        assertResultsMatch(schema.getSchemaAsString(), "test-results/schema-test-02.txt");

        schema.addType(createSchemaType("Subscription", ""));
        assertResultsMatch(schema.getSchemaAsString(), "test-results/schema-test-03.txt");

        SchemaArgument argument = createArgument("dateFormat", "String", true, null, STRING);

        SchemaDirective schemaDirective = SchemaDirective.builder()
                .name("format")
                .addLocation(FIELD_DEFINITION.name())
                .addArgument(argument).build();
        
        schema.addDirective(schemaDirective);

        assertResultsMatch(schema.getSchemaAsString(), "test-results/schema-test-04.txt");
    }

    @Test
    public void testScalars() {
        Schema schema = Schema.create();
        assertThat(schema.getScalars().size(), is(0));

        SchemaScalar schemaScalar1 = new SchemaScalar("Test", Date.class.getName(), ExtendedScalars.Date, null);
        SchemaScalar schemaScalar2 = new SchemaScalar("Test2", Date.class.getName(), ExtendedScalars.Date, "XYZ");
        schema.addScalar(schemaScalar1);
        schema.addScalar(schemaScalar2);

        assertThat(schema.getScalars().contains(schemaScalar1), is(true));
        assertThat(schema.getScalars().contains(schemaScalar2), is(true));

        assertThat(schema.getScalarByActualClass(Date.class.getName()), is(notNullValue()));
    }

    @Test
    public void testDirectives() {
        Schema schema = Schema.create();
        assertThat(schema.getDirectives().size(), is(0));

        SchemaDirective schemaDirective1 = SchemaDirective.builder().name("directive1").build();
        SchemaDirective schemaDirective2 = SchemaDirective.builder().name("directive2").build();
        schema.addDirective(schemaDirective1);
        schema.addDirective(schemaDirective2);
        assertThat(schema.getDirectives().contains(schemaDirective1), is(true));
        assertThat(schema.getDirectives().contains(schemaDirective2), is(true));
    }

    @Test
    public void tesTypes() {
        Schema schema = Schema.create();
        assertThat(schema.getInputTypes().size(), is(0));
        SchemaType schemaType1 = createSchemaType("name", "valueClass");
        SchemaType schemaType2 = createSchemaType("name2", "valueClass2");
        schema.addType(schemaType1);
        schema.addType(schemaType2);
        assertThat(schema.getTypes().size(), is(2));
        assertThat(schema.getTypes().contains(schemaType1), is(true));
        assertThat(schema.getTypes().contains(schemaType2), is(true));

        assertThat(schema.getTypeByName("nothing"), is(nullValue()));
    }

    @Test
    public void testEnums() {
        Schema schema = Schema.create();
        assertThat(schema.getEnums().size(), is(0));
        List<String> listString = new ArrayList<>();
        listString.add("NEWHOPE");
        listString.add("JEDI");
        listString.add("EMPIRE");
        SchemaEnum schemaEnum1 = SchemaEnum.builder().name("Episode").build();
        schemaEnum1.values().addAll(listString);

        schema.addEnum(schemaEnum1);
        assertThat(schema.getEnums().size(), is(1));
        assertThat(schema.getEnums().contains(schemaEnum1), is(true));

        assertThat(schema.getEnumByName("nothing"), is(nullValue()));
        assertThat(schema.containsEnumWithName("nothing"), is(false));
        assertThat(schema.containsEnumWithName("Episode"), is(true));
    }

    @Test
    @Disabled
    public void testInvalidRuntimeWiring() {
        Schema schema = Schema.create();
        assertThrows(IllegalStateException.class, schema::getRuntimeWiring);
    }
}
