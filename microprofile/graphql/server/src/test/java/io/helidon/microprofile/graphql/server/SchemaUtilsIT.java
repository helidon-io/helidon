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
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.enterprise.inject.se.SeContainerInitializer;

import graphql.ExecutionResult;

import io.helidon.microprofile.graphql.server.model.Schema;
import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.enums.EnumTestWithNameAnnotation;
import io.helidon.microprofile.graphql.server.test.queries.SimpleQueriesNoArgs;
import io.helidon.microprofile.graphql.server.test.queries.SimpleQueriesWithArgs;
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
import org.junit.jupiter.api.BeforeAll;
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
    private DummyContext dummyContext = new DummyContext("Dummy");

    @BeforeAll
    public static void initialize() {
        SeContainerInitializer seContainerInitializer = SeContainerInitializer.newInstance();
    }

    @BeforeEach
    public void setupTest() throws IOException {
        System.clearProperty(JandexUtils.PROP_INDEX_FILE);
        indexFileName = getTempIndexFile();
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

    @Test
    @SuppressWarnings("unchecked")
    public void testSimpleQueryGenerationNoArgs() throws IOException {
        setupIndex(indexFileName, SimpleQueriesNoArgs.class);
        ExecutionContext<DummyContext> executionContext = new ExecutionContext<>(dummyContext);
        ExecutionResult result = executionContext.execute("query { hero }");

        Map<String, Object> mapResults = getAndAssertResult(result);

        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("hero"), is("R2-D2"));

        result = executionContext.execute("query { episodeCount }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("episodeCount"), is(9));

        result = executionContext.execute("query { badGuy }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("badGuy"), is("Darth Vader"));

        result = executionContext.execute("query { allPeople { personId } }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));

        ArrayList<Map<String, Object>> arrayList = (ArrayList<Map<String, Object>>) mapResults.get("allPeople");
        assertThat(arrayList.size(), is(TestDB.MAX_PEOPLE));

        result = executionContext.execute("query { returnMediumSize }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("returnMediumSize"), is("M"));

        result = executionContext.execute("query { returnCurrentDate }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("returnCurrentDate"), is(notNullValue()));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSimpleQueryGenerationWithArgs() throws IOException {
        setupIndex(indexFileName, SimpleQueriesWithArgs.class, Car.class, AbstractVehicle.class);
        ExecutionContext<DummyContext> executionContext = new ExecutionContext<>(dummyContext);

        ExecutionResult result = executionContext.execute("query { hero(heroType: \"human\") }");
        Map<String, Object> mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("hero"), is("Luke"));

        result = executionContext.execute("query { canFindContact(contact: { id: \"10\" name: \"tim\" age: 52 }) }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("canFindContact"), is(false));

        result = executionContext.execute("query { hero(heroType: \"droid\") }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("hero"), is("R2-D2"));

        result = executionContext.execute("query { multiply(arg0: 10, arg1: 10) }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("multiply"), is(BigInteger.valueOf(100)));

        result = executionContext
                .execute("query { findAPerson(personId: 1) { personId creditLimit workAddress { city state zipCode } } }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("findAPerson"), is(notNullValue()));

        result = executionContext.execute(
                "query { findPeopleFromState(state: \"MA\") { personId creditLimit workAddress { city state zipCode } } }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("findPeopleFromState"), is(notNullValue()));

        ArrayList<Map<String, Object>> arrayList = (ArrayList<Map<String, Object>>) mapResults.get("findPeopleFromState");
        assertThat(arrayList, is(notNullValue()));
        // since its random data we can't be sure if anyone was created in MA
        assertThat(arrayList.size() >= 0, is(true));

        result = executionContext.execute("query { findLocalDates(numberOfValues: 10) }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("findLocalDates"), is(notNullValue()));
        List<LocalDate> listLocalDate = (List<LocalDate>) mapResults.get("findLocalDates");
        assertThat(listLocalDate.size(), is(10));

        result = executionContext.execute("query { findEnums(arg0: S) }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("findEnums"), is(notNullValue()));

        result = executionContext.execute("query { getMonthFromDate(date: \"2020-12-20\") }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("getMonthFromDate"), is("DECEMBER"));
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
