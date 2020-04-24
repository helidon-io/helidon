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

import java.beans.IntrospectionException;
import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import io.helidon.microprofile.graphql.server.test.queries.DuplicateNameQueries;
import io.helidon.microprofile.graphql.server.test.queries.PropertyNameQueries;
import io.helidon.microprofile.graphql.server.test.types.InvalidNamedTypes;
import io.helidon.microprofile.graphql.server.test.types.TypeWithNameAndJsonbProperty;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.enums.EnumTestWithNameAnnotation;
import io.helidon.microprofile.graphql.server.test.mutations.SimpleMutations;
import io.helidon.microprofile.graphql.server.test.mutations.VoidMutations;
import io.helidon.microprofile.graphql.server.test.queries.ArrayAndListQueries;
import io.helidon.microprofile.graphql.server.test.queries.DefaultValueQueries;
import io.helidon.microprofile.graphql.server.test.queries.DescriptionQueries;
import io.helidon.microprofile.graphql.server.test.queries.InvalidQueries;
import io.helidon.microprofile.graphql.server.test.queries.NumberFormatQueriesAndMutations;
import io.helidon.microprofile.graphql.server.test.queries.OddNamedQueriesAndMutations;
import io.helidon.microprofile.graphql.server.test.queries.QueriesWithIgnorable;
import io.helidon.microprofile.graphql.server.test.queries.QueriesWithNulls;
import io.helidon.microprofile.graphql.server.test.queries.SimpleQueriesNoArgs;
import io.helidon.microprofile.graphql.server.test.queries.SimpleQueriesWithArgs;
import io.helidon.microprofile.graphql.server.test.queries.SimpleQueriesWithSource;
import io.helidon.microprofile.graphql.server.test.queries.VoidQueries;
import io.helidon.microprofile.graphql.server.test.types.AbstractVehicle;
import io.helidon.microprofile.graphql.server.test.types.Car;
import io.helidon.microprofile.graphql.server.test.types.ContactRelationship;
import io.helidon.microprofile.graphql.server.test.types.DateTimePojo;
import io.helidon.microprofile.graphql.server.test.types.DefaultValuePOJO;
import io.helidon.microprofile.graphql.server.test.types.DescriptionType;
import io.helidon.microprofile.graphql.server.test.types.Level0;
import io.helidon.microprofile.graphql.server.test.types.Motorbike;
import io.helidon.microprofile.graphql.server.test.types.MultiLevelListsAndArrays;
import io.helidon.microprofile.graphql.server.test.types.NullPOJO;
import io.helidon.microprofile.graphql.server.test.types.ObjectWithIgnorableFieldsAndMethods;
import io.helidon.microprofile.graphql.server.test.types.Person;
import io.helidon.microprofile.graphql.server.test.types.PersonWithName;
import io.helidon.microprofile.graphql.server.test.types.SimpleContact;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactInputType;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactInputTypeWithAddress;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactInputTypeWithName;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactInputTypeWithNameValue;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactWithNumberFormats;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactWithSelf;
import io.helidon.microprofile.graphql.server.test.types.TypeWithIDs;
import io.helidon.microprofile.graphql.server.test.types.Vehicle;
import io.helidon.microprofile.graphql.server.test.types.VehicleIncident;

import javax.swing.text.DateFormatter;
import org.eclipse.microprofile.graphql.NonNull;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for {@link SchemaGeneratorTest}.
 */
@SuppressWarnings("unchecked")
public class SchemaGeneratorIT
        extends AbstractGraphQLIT {

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

    @Test
    public void testJandexUtils() throws IOException {
        setupIndex(indexFileName, NullPOJO.class, QueriesWithNulls.class);
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        String queriesWithNulls = QueriesWithNulls.class.getName();
        String nullPOJO = NullPOJO.class.getName();
        String noNull = NonNull.class.getName();

        JandexUtils jandexUtils = schemaGenerator.getJandexUtils();

        assertThat(jandexUtils.methodParameterHasAnnotation(queriesWithNulls, "query1", 0, noNull), is(false));

        assertThat(jandexUtils.fieldHasAnnotation(nullPOJO, "listNonNullStrings", noNull), is(true));
        assertThat(jandexUtils.fieldHasAnnotation(nullPOJO, "listOfListOfNonNullStrings", noNull), is(true));
        assertThat(jandexUtils.fieldHasAnnotation(nullPOJO, "listOfListOfNullStrings", noNull), is(false));

        assertThat(jandexUtils.methodHasAnnotation(nullPOJO, "getListOfListOfNonNullStrings", noNull), is(false));
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
        System.out.println("TESTING");
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
    public void testObjectWithIgnorableFields() throws IOException {
        setupIndex(indexFileName, ObjectWithIgnorableFieldsAndMethods.class);
        ExecutionContext<DefaultContext> executionContext = new ExecutionContext<>(defaultContext);
        executionContext.execute("query { hero }");
    }

    @Test
    public void testVoidMutations() throws IOException {
        setupIndex(indexFileName, VoidMutations.class);
        assertThrows(RuntimeException.class, () -> new ExecutionContext<>(defaultContext));
    }

    @Test
    public void testInvalidNamedType() throws IOException {
        setupIndex(indexFileName, InvalidNamedTypes.InvalidNamedPerson.class);
        assertThrows(RuntimeException.class, () -> new ExecutionContext<>(defaultContext));
    }

    @Test
    public void testInvalidNamedInputType() throws IOException {
        setupIndex(indexFileName, InvalidNamedTypes.InvalidInputType.class);
        assertThrows(RuntimeException.class, () -> new ExecutionContext<>(defaultContext));
    }

    @Test
    public void testInvalidNamedInterface() throws IOException {
        setupIndex(indexFileName, InvalidNamedTypes.InvalidInterface.class, InvalidNamedTypes.InvalidClass.class);
        assertThrows(RuntimeException.class, () -> new ExecutionContext<>(defaultContext));
    }

    @Test
    public void testInvalidNamedQuery() throws IOException {
        setupIndex(indexFileName, InvalidNamedTypes.ClassWithInvalidQuery.class);
        assertThrows(RuntimeException.class, () -> new ExecutionContext<>(defaultContext));
    }

    @Test
    public void testInvalidNamedMutation() throws IOException {
        setupIndex(indexFileName, InvalidNamedTypes.ClassWithInvalidMutation.class);
        assertThrows(RuntimeException.class, () -> new ExecutionContext<>(defaultContext));
    }

    @Test
    public void testInvalidNameEnum() throws IOException {
        setupIndex(indexFileName, InvalidNamedTypes.Size.class);
        assertThrows(RuntimeException.class, () -> new ExecutionContext<>(defaultContext));
    }

    @Test
    public void testVoidQueries() throws IOException {
        setupIndex(indexFileName, VoidQueries.class);
        assertThrows(RuntimeException.class, () -> new ExecutionContext<>(defaultContext));
    }

    @Test
    public void testInvalidQueries() throws IOException {
        setupIndex(indexFileName, InvalidQueries.class);
        assertThrows(RuntimeException.class, () -> new ExecutionContext<>(defaultContext));
    }

    @Test
    public void testDuplicateQueryOrMutationNames() throws IOException {
        setupIndex(indexFileName, DuplicateNameQueries.class);
        assertThrows(RuntimeException.class, () -> new ExecutionContext<>(defaultContext));
    }

    @Test
    public void testSimpleContactWithSelf() throws IOException {
        setupIndex(indexFileName, SimpleContactWithSelf.class);
        ExecutionContext<DefaultContext> executionContext = new ExecutionContext<>(defaultContext);
        executionContext.execute("query { hero }");
    }

    @Test
    public void testQueriesWithVariables() throws IOException {
        setupIndex(indexFileName, SimpleQueriesWithArgs.class);
        ExecutionContext<DefaultContext> executionContext = new ExecutionContext<>(defaultContext);
        Map<String, Object> mapVariables = Map.of("first", 10, "second", 20);
        Map<String, Object> mapResults = getAndAssertResult(executionContext.execute(
                "query additionQuery($first: Int!, $second: Int!) {"
                        + " additionQuery(value1: $first, value2: $second) }", "additionQuery", mapVariables));
        assertThat(mapResults, is(notNullValue()));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("additionQuery"), is(30));
    }

    @Test
    public void testNumberFormats() throws IOException {
        setupIndex(indexFileName, SimpleContactWithNumberFormats.class, NumberFormatQueriesAndMutations.class);
        ExecutionContext<DefaultContext> executionContext = new ExecutionContext<>(defaultContext);

        Map<String, Object> mapResults = getAndAssertResult(executionContext
                                                                    .execute(
                                                                            "query { simpleFormattingQuery { id name age "
                                                                                    + "bankBalance value longValue } }"));
        assertThat(mapResults.size(), is(1));

        Map<String, Object> mapResults2 = (Map<String, Object>) mapResults.get("simpleFormattingQuery");
        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.get("id"), is("1 id"));
        assertThat(mapResults2.get("name"), is("Tim"));
        assertThat(mapResults2.get("age"), is("50 years old"));
        assertThat(mapResults2.get("bankBalance"), is("$ 1200.00"));
        assertThat(mapResults2.get("value"), is("10 value"));
        assertThat(mapResults2.get("longValue"), is(BigInteger.valueOf(Long.MAX_VALUE)));

        mapResults = getAndAssertResult(executionContext.execute("mutation { generateDoubleValue }"));
        assertThat(mapResults, is(notNullValue()));
        assertThat(mapResults.get("generateDoubleValue"), is("Double-123456789"));

        // create a new contact
        String contactInput =
                "contact: {"
                        + "id: \"1 id\" "
                        + "name: \"Tim\" "
                        + "age: \"20 years old\" "
                        + "bankBalance: \"$ 1000.01\" "
                        + "value: \"9 value\" "
                        + "longValue: 12345"
                        + " } ";

        //        mapResults = getAndAssertResult(
        //                executionContext.execute("mutation { createSimpleContactWithNumberFormats (" + contactInput + ") { id
        //               name } }"));
        //        assertThat(mapResults.size(), is(1));
        //        mapResults2 = (Map<String, Object>) mapResults.get("createSimpleContactWithNumberFormats");
        //        assertThat(mapResults2, is(notNullValue()));
    }

    @Test
    public void testDateAndTime() throws IOException {
        setupIndex(indexFileName, DateTimePojo.class, SimpleQueriesNoArgs.class);
        ExecutionContext<DefaultContext> executionContext = new ExecutionContext<>(defaultContext);

        Schema schema = executionContext.getSchema();
        SchemaType type = schema.getTypeByName("DateTimePojo");

        SchemaFieldDefinition fd = getFieldDefinition(type, "localDate");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.getFormat()[0], is("MM/dd/yyyy"));
        assertThat(fd.getDescription(), is(nullValue()));

        fd = getFieldDefinition(type, "localTime");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.getFormat()[0], is("hh:mm:ss"));
        assertThat(fd.getDescription(), is(nullValue()));

        fd = getFieldDefinition(type, "localDate2");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.getFormat()[0], is("MM/dd/yyyy"));
        assertThat(fd.getDescription(), is(nullValue()));

        // test default values for date and time
        assertDefaultFormat(type, "offsetTime", "HH:mm:ssZ");
        assertDefaultFormat(type, "localTime", "hh:mm:ss");
        assertDefaultFormat(type, "localDateTime", "yyyy-MM-dd'T'HH:mm:ss");
        assertDefaultFormat(type, "offsetDateTime", "yyyy-MM-dd'T'HH:mm:ssZ");
        assertDefaultFormat(type, "zonedDateTime", "yyyy-MM-dd'T'HH:mm:ssZ'['VV']'");
        assertDefaultFormat(type, "localDateNoFormat", "yyyy-MM-dd");

        fd = getFieldDefinition(type, "localDateTime");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.getDescription(), is(nullValue()));
        assertThat(fd.getFormat()[0], is("yyyy-MM-dd'T'HH:mm:ss"));

        Map<String, Object> mapResults = getAndAssertResult(
                executionContext.execute("query { dateAndTimePOJOQuery { offsetDateTime offsetTime zonedDateTime "
                                             + "localDate localDate2 localTime localDateTime } }"));
        assertThat(mapResults.size(), is(1));
        Map<String, Object> mapResults2 = (Map<String, Object>) mapResults.get("dateAndTimePOJOQuery");
        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.size(), is(7));

        assertThat(mapResults2.get("localDate"), is("02/17/1968"));
        assertThat(mapResults2.get("localDate2"), is("08/04/1970"));
        assertThat(mapResults2.get("localTime"), is("10:10:20"));
        assertThat(mapResults2.get("offsetTime"), is("08:10:01+0000"));

    }

    @Test
    public void testNulls() throws IOException {
        setupIndex(indexFileName, NullPOJO.class, QueriesWithNulls.class);
        ExecutionContext<DefaultContext> executionContext = new ExecutionContext<>(defaultContext);
        Schema schema = executionContext.getSchema();
        assertThat(schema, is(notNullValue()));

        // test primitives should be not null be default
        SchemaType type = schema.getTypeByName("NullPOJO");
        assertReturnTypeMandatory(type, "id", true);
        assertReturnTypeMandatory(type, "longValue", false);
        assertReturnTypeMandatory(type, "stringValue", true);
        assertReturnTypeMandatory(type, "testNullWithGet", true);
        assertReturnTypeMandatory(type, "listNonNullStrings", false);
        assertArrayReturnTypeMandatory(type, "listNonNullStrings", true);
        assertArrayReturnTypeMandatory(type, "listOfListOfNonNullStrings", true);
        assertReturnTypeMandatory(type, "listOfListOfNonNullStrings", false);
        assertReturnTypeMandatory(type, "listOfListOfNullStrings", false);
        assertArrayReturnTypeMandatory(type, "listOfListOfNullStrings", false);
        assertReturnTypeMandatory(type, "testNullWithSet", false);
        assertReturnTypeMandatory(type, "listNullStringsWhichIsMandatory", true);
        assertArrayReturnTypeMandatory(type, "listNullStringsWhichIsMandatory", false);
        assertReturnTypeMandatory(type, "testInputOnly", false);
        assertArrayReturnTypeMandatory(type, "testInputOnly", false);
        assertReturnTypeMandatory(type, "testOutputOnly", false);
        assertArrayReturnTypeMandatory(type, "testOutputOnly", true);

        SchemaType query = schema.getTypeByName("Query");
        assertReturnTypeMandatory(query, "method1NotNull", true);
        assertReturnTypeMandatory(query, "method2NotNull", true);
        assertReturnTypeMandatory(query, "method3NotNull", false);

        assertReturnTypeArgumentMandatory(query, "paramShouldBeNonMandatory", "value", false);
        assertReturnTypeArgumentMandatory(query, "paramShouldBeNonMandatory2", "value", false);
        assertReturnTypeArgumentMandatory(query, "paramShouldBeNonMandatory3", "value", false);

        SchemaType input = schema.getInputTypeByName("NullPOJOInput");
        assertReturnTypeMandatory(input, "nonNullForInput", true);
        assertReturnTypeMandatory(input, "testNullWithGet", false);
        assertReturnTypeMandatory(input, "testNullWithSet", true);
        assertReturnTypeMandatory(input, "listNonNullStrings", false);
        assertArrayReturnTypeMandatory(input, "listNonNullStrings", true);

        assertArrayReturnTypeMandatory(input, "listOfListOfNonNullStrings", true);

        assertReturnTypeMandatory(input, "testInputOnly", false);
        assertArrayReturnTypeMandatory(input, "testInputOnly", true);

        assertReturnTypeMandatory(input, "testOutputOnly", false);
        assertArrayReturnTypeMandatory(input, "testOutputOnly", false);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSimpleMutations() throws IOException {
        setupIndex(indexFileName, SimpleMutations.class);
        ExecutionContext<DefaultContext> executionContext = new ExecutionContext<>(defaultContext);

        Map<String, Object> mapResults = getAndAssertResult(
                executionContext.execute("mutation { createNewContact { id name age } }"));
        assertThat(mapResults.size(), is(1));
        Map<String, Object> mapResults2 = (Map<String, Object>) mapResults.get("createNewContact");

        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.get("id"), is(notNullValue()));
        assertThat(mapResults2.get("name"), is(notNullValue()));
        assertThat(((String) mapResults2.get("name")).startsWith("Name"), is(true));
        assertThat(mapResults2.get("age"), is(notNullValue()));

        mapResults = getAndAssertResult(
                executionContext.execute("mutation { createContactWithName(name: \"tim\") { id name age } }"));
        assertThat(mapResults.size(), is(1));
        mapResults2 = (Map<String, Object>) mapResults.get("createContactWithName");

        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.get("id"), is(notNullValue()));
        assertThat(mapResults2.get("name"), is("tim"));
        assertThat(mapResults2.get("age"), is(notNullValue()));

        mapResults = getAndAssertResult(executionContext.execute("mutation { echoStringValue(value: \"echo\") }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("echoStringValue"), is("echo"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSimpleQueryGenerationNoArgs() throws IOException {
        setupIndex(indexFileName, SimpleQueriesNoArgs.class);
        ExecutionContext<DefaultContext> executionContext = new ExecutionContext<>(defaultContext);

        Map<String, Object> mapResults = getAndAssertResult(executionContext.execute("query { hero }"));

        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("hero"), is("R2-D2"));

        mapResults = getAndAssertResult(executionContext.execute("query { episodeCount }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("episodeCount"), is(9));

        mapResults = getAndAssertResult(executionContext.execute("query { badGuy }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("badGuy"), is("Darth Vader"));

        mapResults = getAndAssertResult(executionContext.execute("query { allPeople { personId } }"));
        assertThat(mapResults.size(), is(1));

        ArrayList<Map<String, Object>> arrayList = (ArrayList<Map<String, Object>>) mapResults.get("allPeople");
        assertThat(arrayList.size(), is(TestDB.MAX_PEOPLE));

        mapResults = getAndAssertResult(executionContext.execute("query { returnMediumSize }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("returnMediumSize"), is("M"));

        mapResults = getAndAssertResult(executionContext.execute("query { returnCurrentDate }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("returnCurrentDate"), is(notNullValue()));

        mapResults = getAndAssertResult(
                executionContext.execute("query { returnTypeWithIDs { intId integerId longId longPrimitiveId "
                                                + "stringId uuidId } }"));

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

        mapResults = getAndAssertResult(executionContext.execute("query { getMultiLevelList { listOfListOfBigDecimal } }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("getMultiLevelList"), is(notNullValue()));

        Schema schema = executionContext.getSchema();
        SchemaType query = schema.getTypeByName("Query");
        assertThat(query, is(notNullValue()));
        SchemaFieldDefinition fd = getFieldDefinition(query, "idQuery");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.getReturnType(), is(ID));

        assertThat(getFieldDefinition(query, "booleanObject"), is(notNullValue()));
        assertThat(getFieldDefinition(query, "booleanPrimitive"), is(notNullValue()));
    }

    @Test
    public void testDifferentPropertyNames() throws IOException {
        setupIndex(indexFileName, PropertyNameQueries.class, TypeWithNameAndJsonbProperty.class);
        ExecutionContext<DefaultContext> executionContext = new ExecutionContext<>(defaultContext);

        Map<String, Object> mapResults = getAndAssertResult(
                executionContext.execute("query { query1 { newFieldName1 newFieldName2 "
                                                 + " newFieldName3 newFieldName4 newFieldName5 newFieldName6 } }"));
        assertThat(mapResults.size(), is(1));

        Map<String, Object> mapResults2 = (Map<String, Object>) mapResults.get("query1");
        assertThat(mapResults2.size(), is(6));
        for (int i = 1; i <= 6; i++) {
            assertThat(mapResults2.get("newFieldName" + i), is("name" + i));
        }
    }

    @Test
    public void testIgnorable() throws IOException {
        setupIndex(indexFileName, QueriesWithIgnorable.class);
        ExecutionContext<DefaultContext> executionContext = new ExecutionContext<>(defaultContext);
        Map<String, Object> mapResults = getAndAssertResult(
                executionContext.execute("query { testIgnorableFields { id dontIgnore } }"));
        assertThat(mapResults.size(), is(1));

        Map<String, Object> mapResults2 = (Map<String, Object>) mapResults.get("testIgnorableFields");
        assertThat(mapResults2.size(), is(2));
        assertThat(mapResults2.get("id"), is("id"));
        assertThat(mapResults2.get("dontIgnore"), is(true));

        // ensure getting the fields generates an error that is caught by the getAndAssertResult
        assertThrows(AssertionFailedError.class, () -> getAndAssertResult(executionContext
                                                                                  .execute(
                                                                                          "query { testIgnorableFields { id "
                                                                                                  + "dontIgnore pleaseIgnore "
                                                                                                  + "ignoreThisAsWell } }")));

        Schema schema = executionContext.getSchema();
        SchemaType type = schema.getTypeByName("ObjectWithIgnorableFieldsAndMethods");
        assertThat(type, is(notNullValue()));
        assertThat(type.getFieldDefinitions().stream().filter(fd -> fd.getName().equals("ignoreGetMethod")).count(), is(0L));

        SchemaInputType inputType = schema.getInputTypeByName("ObjectWithIgnorableFieldsAndMethodsInput");
        assertThat(inputType, is(notNullValue()));
        assertThat(inputType.getFieldDefinitions().stream().filter(fd -> fd.getName().equals("ignoreBecauseOfMethod")).count(),
                   is(0L));
        assertThat(inputType.getFieldDefinitions().stream().filter(fd -> fd.getName().equals("valueSetter")).count(), is(1L));
    }

    @Test
    public void testDescriptions() throws IOException {
        setupIndex(indexFileName, DescriptionType.class, DescriptionQueries.class);
        ExecutionContext<DefaultContext> executionContext = new ExecutionContext<>(defaultContext);

        Schema schema = executionContext.getSchema();
        assertThat(schema, is(notNullValue()));
        SchemaType type = schema.getTypeByName("DescriptionType");
        assertThat(type, is(notNullValue()));
        type.getFieldDefinitions().forEach(fd -> {
            if (fd.getName().equals("id")) {
                assertThat(fd.getDescription(), is("this is the description"));
            }
            if (fd.getName().equals("value")) {
                assertThat(fd.getDescription(), is("description of value"));
            }
            if (fd.getName().equals("longValue1")) {
                // no description so include the format
                assertThat(fd.getDescription(), is(nullValue()));
                assertThat(fd.getFormat()[0], is("L-########"));
            }
            if (fd.getName().equals("longValue2")) {
                // both description and formatting
                assertThat(fd.getDescription(), is("Description"));
            }
        });

        SchemaInputType inputType = schema.getInputTypeByName("DescriptionTypeInput");
        assertThat(inputType, is(notNullValue()));
        inputType.getFieldDefinitions().forEach(fd -> {
            if (fd.getName().equals("value")) {
                assertThat(fd.getDescription(), is("description on set for input"));
            }
        });

        SchemaType query = schema.getTypeByName("Query");
        assertThat(query, is(notNullValue()));
        SchemaFieldDefinition fd = getFieldDefinition(query, "descriptionOnParam");
        assertThat(fd, (is(notNullValue())));

        fd.getArguments().forEach(a -> {
            if (a.getArgumentName().equals("param1")) {
                assertThat(a.getDescription(), is("Description for param1"));
            }
        });
    }

    @Test
    public void testDefaultValues() throws IOException {
        setupIndex(indexFileName, DefaultValuePOJO.class, DefaultValueQueries.class);
        ExecutionContext<DefaultContext> executionContext = new ExecutionContext<>(defaultContext);

        // test with both fields as default
        Map<String, Object> mapResults = getAndAssertResult(
                executionContext.execute("mutation { generateDefaultValuePOJO { id value } }"));
        assertThat(mapResults.size(), is(1));
        Map<String, Object> results = (Map<String, Object>) mapResults.get("generateDefaultValuePOJO");
        assertThat(results, is(notNullValue()));
        assertThat(results.get("id"), is("ID-1"));
        assertThat(results.get("value"), is(1000));

        // test with a field overridden
        mapResults = getAndAssertResult(
                executionContext.execute("mutation { generateDefaultValuePOJO(id: \"ID-123\") { id value } }"));
        assertThat(mapResults.size(), is(1));
        results = (Map<String, Object>) mapResults.get("generateDefaultValuePOJO");
        assertThat(results, is(notNullValue()));
        assertThat(results.get("id"), is("ID-123"));
        assertThat(results.get("value"), is(1000));

        mapResults = getAndAssertResult(executionContext.execute("query { echoDefaultValuePOJO { id value } }"));
        assertThat(mapResults.size(), is(1));
        results = (Map<String, Object>) mapResults.get("echoDefaultValuePOJO");
        assertThat(results, is(notNullValue()));
        assertThat(results.get("id"), is("ID-1"));
        assertThat(results.get("value"), is(1000));

        mapResults = getAndAssertResult(
                executionContext.execute("query { echoDefaultValuePOJO(input: {id: \"X123\" value: 1}) { id value } }"));
        assertThat(mapResults.size(), is(1));
        results = (Map<String, Object>) mapResults.get("echoDefaultValuePOJO");
        assertThat(results, is(notNullValue()));
        assertThat(results.get("id"), is("X123"));
        assertThat(results.get("value"), is(1));

        mapResults = getAndAssertResult(
                executionContext.execute("query { echoDefaultValuePOJO(input: {value: 1}) { id value } }"));
        assertThat(mapResults.size(), is(1));
        results = (Map<String, Object>) mapResults.get("echoDefaultValuePOJO");
        assertThat(results, is(notNullValue()));
        assertThat(results.get("id"), is("ID-123"));
        assertThat(results.get("value"), is(1));

        Schema schema = executionContext.getSchema();
        SchemaType type = schema.getInputTypeByName("DefaultValuePOJOInput");
        assertReturnTypeDefaultValue(type, "id", "ID-123");
        assertReturnTypeDefaultValue(type, "booleanValue", "false");
        assertReturnTypeMandatory(type, "booleanValue", false);

        SchemaFieldDefinition fd = getFieldDefinition(type, "value");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.getDefaultValue(), is("111222"));
    }

    @Test
    public void setOddNamedQueriesAndMutations() throws IOException {
        setupIndex(indexFileName, DefaultValuePOJO.class, OddNamedQueriesAndMutations.class);
        ExecutionContext<DefaultContext> executionContext = new ExecutionContext<>(defaultContext);

        Schema schema = executionContext.getSchema();
        assertThat(schema, is(notNullValue()));
        SchemaType query = schema.getTypeByName("Query");
        SchemaType mutation = schema.getTypeByName("Mutation");
        assertThat(query, is(notNullValue()));
        assertThat(query.getFieldDefinitions().stream().filter(fd -> fd.getName().equals("settlement")).count(), is(1L));
        assertThat(mutation.getFieldDefinitions().stream().filter(fd -> fd.getName().equals("getaway")).count(), is(1L));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSimpleQueriesWithSource() throws IOException {
        setupIndex(indexFileName, SimpleQueriesWithSource.class, SimpleContact.class);
        ExecutionContext<DefaultContext> executionContext = new ExecutionContext<>(defaultContext);

        // since there is a @Source annotation in SimpleQueriesWithSource, then this should add a field
        // idAndName to the SimpleContact type
        Map<String, Object> mapResults = getAndAssertResult(executionContext.execute("query { findContact { id idAndName } }"));

        assertThat(mapResults.size(), is(1));
        Map<String, Object> mapResults2 = (Map<String, Object>) mapResults.get("findContact");
        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.get("id"), is(notNullValue()));
        assertThat(mapResults2.get("idAndName"), is(notNullValue()));

        // test the query at the top level
        SimpleContact contact1 = new SimpleContact("c1", "Contact 1", 50);

        String json = "contact: " + getContactAsQueryInput(contact1);

        mapResults = getAndAssertResult(executionContext.execute("query { currentJob (" + json + ") }"));
        assertThat(mapResults.size(), is(1));
        String currentJob = (String) mapResults.get("currentJob");
        assertThat(currentJob, is(notNullValue()));

        // test the query from the object
        mapResults = getAndAssertResult(executionContext.execute("query { findContact { id idAndName currentJob } }"));
        assertThat(mapResults.size(), is(1));
        mapResults2 = (Map<String, Object>) mapResults.get("findContact");
        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.get("id"), is(notNullValue()));
        assertThat(mapResults2.get("idAndName"), is(notNullValue()));
        assertThat(mapResults2.get("currentJob"), is(notNullValue()));

        // test the query from the object
        mapResults = getAndAssertResult(executionContext.execute("query { findContact { id lastNAddress(count: 1) { city } } }"));
        assertThat(mapResults.size(), is(1));
        mapResults2 = (Map<String, Object>) mapResults.get("findContact");
        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.get("id"), is(notNullValue()));
        assertThat(mapResults2.get("lastNAddress"), is(notNullValue()));
    }

    @Test
    public void testInputType() throws IOException {
        setupIndex(indexFileName, SimpleContactInputType.class, SimpleContactInputTypeWithName.class,
                   SimpleContactInputTypeWithNameValue.class, SimpleContactInputTypeWithAddress.class);
        ExecutionContext<DefaultContext> executionContext = new ExecutionContext<>(defaultContext);
        Schema schema = executionContext.getSchema();
        assertThat(schema.getInputTypes().size(), is(5));
        assertThat(schema.containsInputTypeWithName("MyInputType"), is(true));
        assertThat(schema.containsInputTypeWithName("SimpleContactInputTypeInput"), is(true));
        assertThat(schema.containsInputTypeWithName("NameInput"), is(true));
        assertThat(schema.containsInputTypeWithName("SimpleContactInputTypeWithAddressInput"), is(true));
        assertThat(schema.containsInputTypeWithName("AddressInput"), is(true));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testMultiLevelListsAndArraysQueries() throws IOException {
        setupIndex(indexFileName, ArrayAndListQueries.class, MultiLevelListsAndArrays.class);
        ExecutionContext<DefaultContext> executionContext = new ExecutionContext<>(defaultContext);

        Map<String, Object> mapResults = getAndAssertResult(
                executionContext.execute("query { getMultiLevelList { intMultiLevelArray } }"));
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

        mapResults = getAndAssertResult(executionContext.execute("query { returnListOfStringArrays }"));
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
        ExecutionContext<DefaultContext> executionContext = new ExecutionContext<>(defaultContext);

        Map<String, Object> mapResults = getAndAssertResult(executionContext.execute("query { hero(heroType: \"human\") }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("hero"), is("Luke"));

        mapResults = getAndAssertResult(executionContext.execute("query { findLocalDates(numberOfValues: 10) }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("findLocalDates"), is(notNullValue()));
        List<LocalDate> listLocalDate = (List<LocalDate>) mapResults.get("findLocalDates");
        assertThat(listLocalDate.size(), is(10));

        mapResults = getAndAssertResult(
                executionContext.execute("query { canFindContact(contact: { id: \"10\" name: \"tim\" age: 52 }) }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("canFindContact"), is(false));

        mapResults = getAndAssertResult(executionContext.execute("query { hero(heroType: \"droid\") }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("hero"), is("R2-D2"));

        mapResults = getAndAssertResult(executionContext.execute("query { multiply(arg0: 10, arg1: 10) }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("multiply"), is(BigInteger.valueOf(100)));

        mapResults = getAndAssertResult(executionContext
                                                .execute(
                                                        "query { findAPerson(personId: 1) { personId creditLimit workAddress { "
                                                                + "city state zipCode } } }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("findAPerson"), is(notNullValue()));

        mapResults = getAndAssertResult(executionContext.execute("query { findEnums(arg0: S) }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("findEnums"), is(notNullValue()));

        mapResults = getAndAssertResult(executionContext.execute("query { getMonthFromDate(date: \"2020-12-20\") }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("getMonthFromDate"), is("DECEMBER"));

        mapResults = getAndAssertResult(executionContext.execute("query { findOneEnum(enum: XL) }"));
        assertThat(mapResults.size(), is(1));
        Collection<String> listEnums = (Collection<String>) mapResults.get("findOneEnum");
        assertThat(listEnums.size(), is(1));
        assertThat(listEnums.iterator().next(), is(EnumTestWithNameAnnotation.XL.toString()));

        mapResults = getAndAssertResult(executionContext.execute("query { returnIntegerAsId(param1: 123) }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("returnIntegerAsId"), is(123));

        mapResults = getAndAssertResult(executionContext.execute("query { returnIntAsId(param1: 124) }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("returnIntAsId"), is(124));

        mapResults = getAndAssertResult(executionContext.execute("query { returnStringAsId(param1: \"StringValue\") }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("returnStringAsId"), is("StringValue"));

        mapResults = getAndAssertResult(executionContext.execute("query { returnLongAsId(param1: " + Long.MAX_VALUE + ") }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("returnLongAsId"), is(BigInteger.valueOf(Long.MAX_VALUE)));

        mapResults = getAndAssertResult(
                executionContext.execute("query { returnLongPrimitiveAsId(param1: " + Long.MAX_VALUE + ") }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("returnLongPrimitiveAsId"), is(BigInteger.valueOf(Long.MAX_VALUE)));

        UUID uuid = UUID.randomUUID();
        mapResults = getAndAssertResult(executionContext.execute("query { returnUUIDAsId(param1: \""
                                                                         + uuid.toString() + "\") }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("returnUUIDAsId"), is(uuid.toString()));

        mapResults = getAndAssertResult(executionContext.execute(
                "query { findPeopleFromState(state: \"MA\") { personId creditLimit workAddress { city state zipCode } } }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("findPeopleFromState"), is(notNullValue()));
        ArrayList<Map<String, Object>> arrayList = (ArrayList<Map<String, Object>>) mapResults.get("findPeopleFromState");
        assertThat(arrayList, is(notNullValue()));
        // since its random data we can't be sure if anyone was created in MA
        assertThat(arrayList.size() >= 0, is(true));

        mapResults = getAndAssertResult(executionContext.execute(
                "query { findPeopleFromState(state: \"MA\") { personId creditLimit workAddress { city state zipCode } } }"));
        assertThat(mapResults.size(), is(1));

        SimpleContact contact1 = new SimpleContact("c1", "Contact 1", 50);
        SimpleContact contact2 = new SimpleContact("c2", "Contact 2", 53);
        ContactRelationship relationship = new ContactRelationship(contact1, contact2, "married");

        String json = "relationship: {"
                + "   contact1: " + getContactAsQueryInput(contact1)
                + "   contact2: " + getContactAsQueryInput(contact2)
                + "   relationship: \"married\""
                + "}";
        mapResults = getAndAssertResult(executionContext.execute("query { canFindContactRelationship( "
                                                                         + json +
                                                                         ") }"));
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
        System.out.println(schema.generateGraphQLSchema());
        assertThat(schema, is(notNullValue()));
        schema.getTypes().forEach(t -> System.out.println(t.getName()));
        assertThat(schema.getTypes().size(), is(6));
        assertThat(schema.getTypeByName("Vehicle"), is(notNullValue()));
        assertThat(schema.getTypeByName("Car"), is(notNullValue()));
        assertThat(schema.getTypeByName("Motorbike"), is(notNullValue()));
        assertThat(schema.getTypeByName("Incident"), is(notNullValue()));
        assertThat(schema.getTypeByName("Query"), is(notNullValue()));
        assertThat(schema.getTypeByName("Mutation"), is(notNullValue()));
        generateGraphQLSchema(schema);
    }

    private void assertReturnTypeDefaultValue(SchemaType type, String fdName, String defaultValue) {
        assertThat(type, is(notNullValue()));
        SchemaFieldDefinition fd = getFieldDefinition(type, fdName);
        assertThat(fd, is(notNullValue()));
        assertThat("Default value for " + fdName + " should be " + defaultValue +
                           " but is " + fd.getDefaultValue(), fd.getDefaultValue(), is(defaultValue));
    }

    private void assertReturnTypeMandatory(SchemaType type, String fdName, boolean mandatory) {
        assertThat(type, is(notNullValue()));
        SchemaFieldDefinition fd = getFieldDefinition(type, fdName);
        assertThat(fd, is(notNullValue()));
        assertThat("Return type for " + fdName + " should be mandatory=" + mandatory +
                           " but is " + fd.isReturnTypeMandatory(), fd.isReturnTypeMandatory(), is(mandatory));
    }

    private void assertArrayReturnTypeMandatory(SchemaType type, String fdName, boolean mandatory) {
        assertThat(type, is(notNullValue()));
        SchemaFieldDefinition fd = getFieldDefinition(type, fdName);
        assertThat(fd, is(notNullValue()));
        assertThat("Array return type for " + fdName + " should be mandatory=" + mandatory +
                           " but is " + fd.isArrayReturnTypeMandatory(), fd.isArrayReturnTypeMandatory(), is(mandatory));
    }

    private void assertReturnTypeArgumentMandatory(SchemaType type, String fdName, String argumentName, boolean mandatory) {
        assertThat(type, is(notNullValue()));
        SchemaFieldDefinition fd = getFieldDefinition(type, fdName);
        assertThat(fd, is(notNullValue()));
        SchemaArgument argument = getArgument(fd, argumentName);
        assertThat(argument, is(notNullValue()));
        assertThat("Return type for argument " + argumentName + " should be mandatory="
                           + mandatory + " but is " + argument.isMandatory(), argument.isMandatory(), is(mandatory));
    }

    private void assertDefaultFormat(SchemaType type, String fdName, String defaultFormat) {
        assertThat(type, is(notNullValue()));
        SchemaFieldDefinition fd = getFieldDefinition(type, fdName);
        assertThat(fd, is(notNullValue()));
        String[] format = fd.getFormat();
        assertThat(format, is(notNullValue()));
        assertThat(format.length == 2, is(notNullValue()));
        assertThat(format[0], is(defaultFormat));
    }

    private SchemaFieldDefinition getFieldDefinition(SchemaType type, String name) {
        for (SchemaFieldDefinition fd : type.getFieldDefinitions()) {
            if (fd.getName().equals(name)) {
                return fd;
            }
        }
        return null;
    }

    private SchemaArgument getArgument(SchemaFieldDefinition fd, String name) {
        assertThat(fd, is(notNullValue()));
        for (SchemaArgument argument : fd.getArguments()) {
            if (argument.getArgumentName().equals(name)) {
                return argument;
            }
        }
        return null;
    }

}
