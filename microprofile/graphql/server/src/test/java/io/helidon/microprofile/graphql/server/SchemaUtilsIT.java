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

package io.helidon.microprofile.graphql.server;

import java.beans.IntrospectionException;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import io.helidon.microprofile.graphql.server.model.Schema;
import io.helidon.microprofile.graphql.server.test.types.AbstractVehicle;
import io.helidon.microprofile.graphql.server.test.types.Car;
import io.helidon.microprofile.graphql.server.test.types.Level0;
import io.helidon.microprofile.graphql.server.test.types.Motorbike;
import io.helidon.microprofile.graphql.server.test.types.Person;
import io.helidon.microprofile.graphql.server.test.types.PersonWithName;
import io.helidon.microprofile.graphql.server.test.types.Vehicle;
import io.helidon.microprofile.graphql.server.test.types.VehicleIncident;
import io.helidon.microprofile.graphql.server.util.JandexUtils;
import io.helidon.microprofile.graphql.server.util.SchemaUtils;
import io.helidon.microprofile.graphql.server.util.SchemaUtilsTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Integration tests for {@link SchemaUtilsTest}.
 */
public class SchemaUtilsIT extends AbstractGraphQLTest {

    private String indexFileName = null;
    private File indexFile = null;

    @BeforeEach
    public void setupTest() throws IOException {
        System.clearProperty(JandexUtils.PROP_INDEX_FILE);
        indexFileName = getTempIndexFile();;
        indexFile = null;
    }

    @AfterEach
    public void teardownTest() {
        if (indexFile != null) {
            indexFile.delete();
        }
    }

    /**
     * Test generation of Type with no-name.
     */
    @Test
    public void testTypeGenerationWithNoName() throws IOException, IntrospectionException, ClassNotFoundException {
        setupIndex(indexFileName, Person.class);

        SchemaUtils schemaUtils = new SchemaUtils();
        Schema schema = schemaUtils.generateSchema();
        assertThat(schema.getTypeByName("Person"), is(notNullValue()));
        assertThat(schema.getTypeByName("Address"), is(notNullValue()));
        assertThat(schema.getScalars().contains("Date"), is(notNullValue()));
        assertThat(schema.getScalars().contains("BigDecimal"), is(notNullValue()));
        generateGraphQLSchema(schema);
    }

    /**
     * Test generation of Type with a different name then class name.
     */
    @Test
    public void testPersonWithName() throws IOException, IntrospectionException, ClassNotFoundException {
        setupIndex(indexFileName, PersonWithName.class);
        SchemaUtils schemaUtils = new SchemaUtils();
        Schema schema = schemaUtils.generateSchema();

        assertThat(schema, is(notNullValue()));
        assertThat(schema.getTypeByName("Person"), is(notNullValue()));
        generateGraphQLSchema(schema);
    }

    @Test
    public void testMultipleLevels() throws IOException, IntrospectionException, ClassNotFoundException {
        setupIndex(indexFileName, Level0.class);
        SchemaUtils schemaUtils = new SchemaUtils();
        Schema schema = schemaUtils.generateSchema();

        assertThat(schema, is(notNullValue()));
        assertThat(schema.getTypes().size(), is(5));
        assertThat(schema.getTypeByName("Level0"), is(notNullValue()));
        assertThat(schema.getTypeByName("Level1"), is(notNullValue()));
        assertThat(schema.getTypeByName("Level2"), is(notNullValue()));
        assertThat(schema.getTypeByName("Address"), is(notNullValue()));
        assertThat(schema.getTypeByName("Query"), is(notNullValue()));
        generateGraphQLSchema(schema);
    }

    /**
     * Test discovery of interfaces when only the interface annotated.
     */
    @Test
    public void testInterfaceDiscoveryWithImplementorsWithNoTypeAnnotation()
            throws IOException, IntrospectionException, ClassNotFoundException {
        setupIndex(indexFileName, Vehicle.class, Car.class, Motorbike.class, AbstractVehicle.class);
        assertInterfaceResults();
    }

    /**
     * Test discovery of interfaces and subsequent unresolved type which has a Name annotation .
     */
    @Test
    public void testInterfaceDiscoveryWithUnresolvedType() throws IOException, IntrospectionException, ClassNotFoundException {
        setupIndex(indexFileName, Vehicle.class, Car.class, Motorbike.class, VehicleIncident.class, AbstractVehicle.class);
        assertInterfaceResults();
    }

    /**
     * Test discovery of interfaces when only interface is annotated.
     */
    @Test
    public void testInterfaceDiscoveryWithoutTypes() throws IOException, IntrospectionException, ClassNotFoundException {
        setupIndex(indexFileName, Vehicle.class, Car.class, Motorbike.class, AbstractVehicle.class);
        assertInterfaceResults();
    }

    private void assertInterfaceResults() throws IntrospectionException, ClassNotFoundException {
        SchemaUtils schemaUtils = new SchemaUtils();
        Schema schema = schemaUtils.generateSchema();
        assertThat(schema, is(notNullValue()));
        assertThat(schema.getTypes().size(), is(5));
        assertThat(schema.getTypeByName("Vehicle"), is(notNullValue()));
        assertThat(schema.getTypeByName("Car"), is(notNullValue()));
        assertThat(schema.getTypeByName("Motorbike"), is(notNullValue()));
        assertThat(schema.getTypeByName("Incident"), is(notNullValue()));
        assertThat(schema.getTypeByName("Query"), is(notNullValue()));
        generateGraphQLSchema(schema);
    }
}
