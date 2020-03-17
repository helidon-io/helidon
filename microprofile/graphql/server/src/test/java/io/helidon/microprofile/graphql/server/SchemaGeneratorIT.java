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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;

import graphql.ExecutionResult;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.enums.EnumTestWithNameAnnotation;
import io.helidon.microprofile.graphql.server.test.mutations.SimpleMutations;
import io.helidon.microprofile.graphql.server.test.mutations.VoidMutations;
import io.helidon.microprofile.graphql.server.test.queries.ArrayAndListQueries;
import io.helidon.microprofile.graphql.server.test.queries.SimpleQueriesNoArgs;
import io.helidon.microprofile.graphql.server.test.queries.SimpleQueriesWithArgs;
import io.helidon.microprofile.graphql.server.test.queries.SimpleQueriesWithSource;
import io.helidon.microprofile.graphql.server.test.queries.VoidQueries;
import io.helidon.microprofile.graphql.server.test.types.AbstractVehicle;
import io.helidon.microprofile.graphql.server.test.types.Car;
import io.helidon.microprofile.graphql.server.test.types.ContactRelationship;
import io.helidon.microprofile.graphql.server.test.types.DateTimePojo;
import io.helidon.microprofile.graphql.server.test.types.Level0;
import io.helidon.microprofile.graphql.server.test.types.Motorbike;
import io.helidon.microprofile.graphql.server.test.types.MultiLevelListsAndArrays;
import io.helidon.microprofile.graphql.server.test.types.ObjectWithIgnorableFields;
import io.helidon.microprofile.graphql.server.test.types.Person;
import io.helidon.microprofile.graphql.server.test.types.PersonWithName;
import io.helidon.microprofile.graphql.server.test.types.SimpleContact;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactWithSelf;
import io.helidon.microprofile.graphql.server.test.types.TypeWithIDs;
import io.helidon.microprofile.graphql.server.test.types.Vehicle;
import io.helidon.microprofile.graphql.server.test.types.VehicleIncident;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for {@link SchemaGeneratorTest}.
 */
public class SchemaGeneratorIT extends AbstractGraphQLTest {

    private String indexFileName = null;
    private File indexFile = null;
    private DummyContext dummyContext = new DummyContext("Dummy");

    private static SeContainer container;

    @BeforeAll
    public static void initialize() {
        container = SeContainerInitializer.newInstance().initialize();
    }

    @AfterAll
    public static void teardown() {
        container.close();
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

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        Schema schema = schemaGenerator.generateSchema();
        assertThat(schema.getTypeByName("Person"), is(notNullValue()));
        assertThat(schema.getTypeByName("Address"), is(notNullValue()));
        assertThat(schema.containsScalarWithName("Date"), is(notNullValue()));
        assertThat(schema.containsScalarWithName("BigDecimal"), is(notNullValue()));
        generateGraphQLSchema(schema);
    }

    @Test
    public void testLevel0() throws IOException, IntrospectionException, ClassNotFoundException {
        setupIndex(indexFileName, Level0.class);
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        Schema schema = schemaGenerator.generateSchema();
        assertThat(schema.containsTypeWithName("Level0"), is(true));
        assertThat(schema.containsTypeWithName("Level1"), is(true));
        assertThat(schema.containsTypeWithName("Level2"), is(true));
        generateGraphQLSchema(schema);
    }

    @Test
    public void testMultipleLevelsOfGenerics() throws IntrospectionException, ClassNotFoundException, IOException {
        setupIndex(indexFileName, MultiLevelListsAndArrays.class);
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        Schema schema = schemaGenerator.generateSchema();
        assertThat(schema.containsTypeWithName("MultiLevelListsAndArrays"), is(true));
        assertThat(schema.containsTypeWithName("Person"), is(true));
        assertThat(schema.containsScalarWithName("BigDecimal"), is(true));
        generateGraphQLSchema(schema);
    }

    /**
     * Test generation of Type with a different name then class name.
     */
    @Test
    public void testPersonWithName() throws IOException, IntrospectionException, ClassNotFoundException {
        setupIndex(indexFileName, PersonWithName.class);
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        Schema schema = schemaGenerator.generateSchema();

        assertThat(schema, is(notNullValue()));
        assertThat(schema.getTypeByName("Person"), is(notNullValue()));
        generateGraphQLSchema(schema);
    }

    @Test
    public void testMultipleLevels() throws IOException, IntrospectionException, ClassNotFoundException {
        setupIndex(indexFileName, Level0.class);
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        Schema schema = schemaGenerator.generateSchema();

        assertThat(schema, is(notNullValue()));
        assertThat(schema.getTypes().size(), is(6));
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
    public void testObjectWithIgnorableFieldsf() throws IOException {
        setupIndex(indexFileName, ObjectWithIgnorableFields.class);
        ExecutionContext<DummyContext> executionContext = new ExecutionContext<>(dummyContext);
        ExecutionResult result = executionContext.execute("query { hero }");
    }

    @Test
    public void testVoidMutations() throws IOException {
        setupIndex(indexFileName, VoidMutations.class);
        assertThrows(RuntimeException.class, () -> new ExecutionContext<>(dummyContext));
    }

    @Test
    public void testVoidQueries() throws IOException {
        setupIndex(indexFileName, VoidQueries.class);
        assertThrows(RuntimeException.class, () -> new ExecutionContext<>(dummyContext));
    }

    @Test
    public void testSimpleContactWithSelf() throws IOException {
        setupIndex(indexFileName, SimpleContactWithSelf.class);
        ExecutionContext<DummyContext> executionContext = new ExecutionContext<>(dummyContext);
        ExecutionResult result = executionContext.execute("query { hero }");
    }

    @Test
    public void testDateAndTime() throws IOException {
        setupIndex(indexFileName, DateTimePojo.class, SimpleQueriesNoArgs.class);
        ExecutionContext<DummyContext> executionContext = new ExecutionContext<>(dummyContext);

        ExecutionResult result = executionContext.execute("query { dateAndTimePOJOQuery { offsetDateTime } }");
        Map<String, Object> mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));

        result = executionContext.execute("query { dateAndTimePOJOQuery { offsetTime } }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));

        result = executionContext.execute("query { dateAndTimePOJOQuery { zonedDateTime } }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));

        result = executionContext.execute("query { dateAndTimePOJOQuery { localDate } }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));

        result = executionContext.execute("query { dateAndTimePOJOQuery { localTime } }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));

        result = executionContext.execute("query { dateAndTimePOJOQuery { localDateTime } }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSimpleMutations() throws IOException {
        setupIndex(indexFileName, SimpleMutations.class);
        ExecutionContext<DummyContext> executionContext = new ExecutionContext<>(dummyContext);

        ExecutionResult result = executionContext.execute("mutation { createNewContact { id name age } }");
        Map<String, Object> mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        Map<String, Object> mapResults2 = (Map<String, Object>) mapResults.get("createNewContact");

        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.get("id"), is(notNullValue()));
        assertThat(mapResults2.get("name"), is(notNullValue()));
        assertThat(((String) mapResults2.get("name")).startsWith("Name"), is(true));
        assertThat(mapResults2.get("age"), is(notNullValue()));

        result = executionContext.execute("mutation { createContactWithName(name: \"tim\") { id name age } }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        mapResults2 = (Map<String, Object>) mapResults.get("createContactWithName");

        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.get("id"), is(notNullValue()));
        assertThat(mapResults2.get("name"), is("tim"));
        ;
        assertThat(mapResults2.get("age"), is(notNullValue()));

        result = executionContext.execute("mutation { echoStringValue(value: \"echo\") }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("echoStringValue"), is("echo"));
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

        result = executionContext
                .execute("query { returnTypeWithIDs { intId integerId longId longPrimitiveId stringId uuidId } }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        Map<String, Object> results = (Map<String, Object>) mapResults.get("returnTypeWithIDs");
        TypeWithIDs value = (TypeWithIDs) JsonUtils.convertFromJson(JsonUtils.convertMapToJson(results), TypeWithIDs.class);
        assertThat(value, is(notNullValue()));
        assertThat(value.getIntegerId(), is(2));
        assertThat(value.getIntId(), is(1));
        assertThat(value.getLongId(), is(10L));
        assertThat(value.getLongPrimitiveId(), is(10L));
        assertThat(value.getStringId(), is("string"));
        assertThat(value.getUuidId(), is(notNullValue()));

        result = executionContext.execute("query { getMultiLevelList { listOfListOfBigDecimal } }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("getMultiLevelList"), is(notNullValue()));

        result = executionContext.execute("query { testIgnorableFields { id dontIgnore } }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));

        Map<String, Object> mapResults2 = (Map<String, Object>) mapResults.get("testIgnorableFields");
        assertThat(mapResults2.size(), is(2));
        assertThat(mapResults2.get("id"), is("id"));
        assertThat(mapResults2.get("dontIgnore"), is(true));

        // ensure getting the fields generates an error that is caught by the getAndAssertResult
        final ExecutionResult result2 = executionContext
                .execute("query { testIgnorableFields { id dontIgnore pleaseIgnore ignoreThisAsWell } }");
        assertThrows(AssertionFailedError.class, () -> getAndAssertResult(result2));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSimpleQueriesWithSource() throws IOException {
        setupIndex(indexFileName, SimpleQueriesWithSource.class, SimpleContact.class);
        ExecutionContext<DummyContext> executionContext = new ExecutionContext<>(dummyContext);

        // since there is a @Source annotation in SimpleQueriesWithSource, then this should add a field
        // idAndName to the SimpleContact type
        ExecutionResult result = executionContext.execute("query { findContact { id idAndName } }");

        Map<String, Object> mapResults = getAndAssertResult(result);

        assertThat(mapResults.size(), is(1));
        Map<String, Object> mapResults2 = (Map<String, Object>) mapResults.get("findContact");
        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.get("id"), is(notNullValue()));
        assertThat(mapResults2.get("idAndName"), is(notNullValue()));

        // test the query at the top level
        SimpleContact contact1 = new SimpleContact("c1", "Contact 1", 50);

        String json = "contact: " + getContactAsQueryInput(contact1);

        result = executionContext.execute("query { currentJob (" + json + ") }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        String currentJob = (String) mapResults.get("currentJob");
        assertThat(currentJob, is(notNullValue()));

        // test the query from the object
        result = executionContext.execute("query { findContact { id idAndName currentJob } }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        mapResults2 = (Map<String, Object>) mapResults.get("findContact");
        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.get("id"), is(notNullValue()));
        assertThat(mapResults2.get("idAndName"), is(notNullValue()));
        assertThat(mapResults2.get("currentJob"), is(notNullValue()));

        // test the query from the object
        result = executionContext.execute("query { findContact { id lastNAddress(count: 1) { city } } }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        mapResults2 = (Map<String, Object>) mapResults.get("findContact");
        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.get("id"), is(notNullValue()));
        assertThat(mapResults2.get("lastNAddress"), is(notNullValue()));

        // lastNAddress on top level query
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testMultiLevelListsAndArraysQueries() throws IOException {
        setupIndex(indexFileName, ArrayAndListQueries.class, MultiLevelListsAndArrays.class);
        ExecutionContext<DummyContext> executionContext = new ExecutionContext<>(dummyContext);

        ExecutionResult result = executionContext.execute("query { getMultiLevelList { intMultiLevelArray } }");
        Map<String, Object> mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        Map<String, Object> mapResults2 = (Map<String, Object>) mapResults.get("getMultiLevelList");
        ArrayList<ArrayList<Integer>> intArrayList = (ArrayList<ArrayList<Integer>>) mapResults2.get("intMultiLevelArray");
        assertThat(intArrayList, is(notNullValue()));
        ArrayList<Integer> integerArrayList1 = intArrayList.get(0);
        assertThat(integerArrayList1, is(notNullValue()));
        assertThat(integerArrayList1.contains(1), is(true));
        assertThat(integerArrayList1.contains(2), is(true));
        assertThat(integerArrayList1.contains(3), is(true));

        ArrayList<Integer> integerArrayList2 = intArrayList.get(1);
        assertThat(integerArrayList2, is(notNullValue()));
        assertThat(integerArrayList2.contains(4), is(true));
        assertThat(integerArrayList2.contains(5), is(true));
        assertThat(integerArrayList2.contains(6), is(true));

        result = executionContext.execute("query { returnListOfStringArrays }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        ArrayList<ArrayList<String>> stringArrayList = (ArrayList<ArrayList<String>>) mapResults.get("returnListOfStringArrays");
        assertThat(stringArrayList, is(notNullValue()));

        List<String> stringList1 = stringArrayList.get(0);
        assertThat(stringList1, is(notNullValue()));
        assertThat(stringList1.contains("one"), is(true));
        assertThat(stringList1.contains("two"), is(true));
        List<String> stringList2 = stringArrayList.get(1);
        assertThat(stringList2, is(notNullValue()));
        assertThat(stringList2.contains("three"), is(true));
        assertThat(stringList2.contains("four"), is(true));
        assertThat(stringList2.contains("five"), is(true));
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

        result = executionContext.execute("query { findLocalDates(numberOfValues: 10) }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("findLocalDates"), is(notNullValue()));
        List<LocalDate> listLocalDate = (List<LocalDate>) mapResults.get("findLocalDates");
        assertThat(listLocalDate.size(), is(10));

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

        result = executionContext.execute("query { findEnums(arg0: S) }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("findEnums"), is(notNullValue()));

        result = executionContext.execute("query { getMonthFromDate(date: \"2020-12-20\") }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("getMonthFromDate"), is("DECEMBER"));

        result = executionContext.execute("query { findOneEnum(enum: XL) }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        Collection<String> listEnums = (Collection<String>) mapResults.get("findOneEnum");
        assertThat(listEnums.size(), is(1));
        assertThat(listEnums.iterator().next(), is(EnumTestWithNameAnnotation.XL.toString()));

        result = executionContext.execute("query { returnIntegerAsId(param1: 123) }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("returnIntegerAsId"), is(123));

        result = executionContext.execute("query { returnIntAsId(param1: 124) }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("returnIntAsId"), is(124));

        result = executionContext.execute("query { returnStringAsId(param1: \"StringValue\") }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("returnStringAsId"), is("StringValue"));

        result = executionContext.execute("query { returnLongAsId(param1: " + Long.MAX_VALUE + ") }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("returnLongAsId"), is(BigInteger.valueOf(Long.MAX_VALUE)));

        result = executionContext.execute("query { returnLongPrimitiveAsId(param1: " + Long.MAX_VALUE + ") }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("returnLongPrimitiveAsId"), is(BigInteger.valueOf(Long.MAX_VALUE)));

        UUID uuid = UUID.randomUUID();
        result = executionContext.execute("query { returnUUIDAsId(param1: \"" + uuid.toString() + "\") }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("returnUUIDAsId"), is(uuid.toString()));

        result = executionContext.execute(
                "query { findPeopleFromState(state: \"MA\") { personId creditLimit workAddress { city state zipCode } } }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("findPeopleFromState"), is(notNullValue()));
        ArrayList<Map<String, Object>> arrayList = (ArrayList<Map<String, Object>>) mapResults.get("findPeopleFromState");
        assertThat(arrayList, is(notNullValue()));
        // since its random data we can't be sure if anyone was created in MA
        assertThat(arrayList.size() >= 0, is(true));

        result = executionContext.execute(
                "query { findPeopleFromState(state: \"MA\") { personId creditLimit workAddress { city state zipCode } } }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));

        SimpleContact contact1 = new SimpleContact("c1", "Contact 1", 50);
        SimpleContact contact2 = new SimpleContact("c2", "Contact 2", 53);
        ContactRelationship relationship = new ContactRelationship(contact1, contact2, "married");

        String json = "relationship: {"
                + "   contact1: " + getContactAsQueryInput(contact1)
                + "   contact2: " + getContactAsQueryInput(contact2)
                + "   relationship: \"married\""
                + "}";
        result = executionContext.execute("query { canFindContactRelationship( "
                                                  + json +
                                                  ") }");
        mapResults = getAndAssertResult(result);
        assertThat(mapResults.size(), is(1));
    }

    private String getContactAsQueryInput(SimpleContact contact) {
        return new StringBuilder("{")
                .append("id: \"").append(contact.getId()).append("\" ")
                .append("name: \"").append(contact.getName()).append("\" ")
                .append("age: ").append(contact.getAge())
                .append("} ").toString();
    }

    private void assertInterfaceResults() throws IntrospectionException, ClassNotFoundException {
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        Schema schema = schemaGenerator.generateSchema();
        assertThat(schema, is(notNullValue()));
        assertThat(schema.getTypes().size(), is(6));
        assertThat(schema.getTypeByName("Vehicle"), is(notNullValue()));
        assertThat(schema.getTypeByName("Car"), is(notNullValue()));
        assertThat(schema.getTypeByName("Motorbike"), is(notNullValue()));
        assertThat(schema.getTypeByName("Incident"), is(notNullValue()));
        assertThat(schema.getTypeByName("Query"), is(notNullValue()));
        generateGraphQLSchema(schema);
    }
}
