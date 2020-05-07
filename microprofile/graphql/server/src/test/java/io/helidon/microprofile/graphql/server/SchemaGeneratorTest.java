/*
 * Copyright (c) 2020 and/or its affiliates.
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import io.helidon.microprofile.graphql.server.test.enums.EnumTestNoEnumName;
import io.helidon.microprofile.graphql.server.test.enums.EnumTestWithEnumName;
import io.helidon.microprofile.graphql.server.test.enums.EnumTestWithNameAndNameAnnotation;
import io.helidon.microprofile.graphql.server.test.enums.EnumTestWithNameAnnotation;
import io.helidon.microprofile.graphql.server.test.queries.OddNamedQueriesAndMutations;
import io.helidon.microprofile.graphql.server.test.queries.SimpleQueriesNoArgs;
import io.helidon.microprofile.graphql.server.test.queries.SimpleQueriesWithSource;
import io.helidon.microprofile.graphql.server.test.types.Address;
import io.helidon.microprofile.graphql.server.test.types.Car;
import io.helidon.microprofile.graphql.server.test.types.DefaultValuePOJO;
import io.helidon.microprofile.graphql.server.test.types.InnerClass;
import io.helidon.microprofile.graphql.server.test.types.InterfaceWithTypeValue;
import io.helidon.microprofile.graphql.server.test.types.Level0;
import io.helidon.microprofile.graphql.server.test.types.Level1;
import io.helidon.microprofile.graphql.server.test.types.Motorbike;
import io.helidon.microprofile.graphql.server.test.types.MultiLevelListsAndArrays;
import io.helidon.microprofile.graphql.server.test.types.ObjectWithIgnorableFieldsAndMethods;
import io.helidon.microprofile.graphql.server.test.types.Person;
import io.helidon.microprofile.graphql.server.test.types.PersonWithName;
import io.helidon.microprofile.graphql.server.test.types.PersonWithNameValue;
import io.helidon.microprofile.graphql.server.test.types.SimpleContact;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactWithNumberFormats;
import io.helidon.microprofile.graphql.server.test.types.TypeWithIDs;
import io.helidon.microprofile.graphql.server.test.types.TypeWithIdOnField;
import io.helidon.microprofile.graphql.server.test.types.TypeWithIdOnMethod;
import io.helidon.microprofile.graphql.server.test.types.TypeWithNameAndJsonbProperty;

import io.helidon.microprofile.graphql.server.test.types.Vehicle;
import io.helidon.microprofile.graphql.server.test.types.VehicleIncident;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.BIG_DECIMAL;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.BIG_INTEGER;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.DEFAULT_LOCALE;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.FLOAT;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.INT;
import static io.helidon.microprofile.graphql.server.FormattingHelper.getNumberFormatAnnotation;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getDefaultDescription;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.stripMethodName;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for {@link SchemaGenerator}.
 */
public class SchemaGeneratorTest extends AbstractGraphQLTest {

    private static final String ADDRESS = Address.class.getName();
    private static final String STRING = String.class.getName();
    private static final String BIGDECIMAL = BigDecimal.class.getName();
    private static final String COLLECTION = Collection.class.getName();
    private static final String LIST = List.class.getName();
    private static final String LOCALDATE = LocalDate.class.getName();
    private static final String ID = "ID";

    private List<String[]> listStringArray = new ArrayList<>();
    private List<String> listString = new ArrayList<>();
    private List<List<List<String>>> listListString = new ArrayList<>();

    public List<String[]> getListStringArray() {
        return listStringArray;
    }

    public List<String> getListString() {
        return listString;
    }

    public List<List<List<String>>> getListListString() {
        return listListString;
    }

    private SchemaGenerator schemaGenerator;
    private Context defaultContext;

    @BeforeEach
    public void beforeEach() {
        defaultContext = ExecutionContext.getDefaultContext();
        schemaGenerator = new SchemaGenerator(defaultContext);
    }

    @Test
    public void testEnumGeneration() throws IntrospectionException, ClassNotFoundException {
        testEnum(EnumTestNoEnumName.class, EnumTestNoEnumName.class.getSimpleName());
        testEnum(EnumTestWithEnumName.class, "TShirtSize");
        testEnum(EnumTestWithNameAnnotation.class, "TShirtSize");
        testEnum(EnumTestWithNameAndNameAnnotation.class, "ThisShouldWin");
    }

    @Test
    public void testGettersPerson() throws IntrospectionException {
        Map<String, SchemaGenerator.DiscoveredMethod> mapMethods = schemaGenerator.retrieveGetterBeanMethods(Person.class, false);
        assertThat(mapMethods, is(notNullValue()));
        assertThat(mapMethods.size(), is(13));

        assertDiscoveredMethod(mapMethods.get("personId"), "personId", "int", null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("name"), "name", STRING, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("homeAddress"), "homeAddress", ADDRESS, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("workAddress"), "workAddress", ADDRESS, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("creditLimit"), "creditLimit", BIGDECIMAL, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("listQualifications"), "listQualifications", STRING, COLLECTION, false, true,
                               false);
        assertDiscoveredMethod(mapMethods.get("previousAddresses"), "previousAddresses", ADDRESS, LIST, false, true, false);
        assertDiscoveredMethod(mapMethods.get("intArray"), "intArray", "int", null, true, false, false);
        assertDiscoveredMethod(mapMethods.get("stringArray"), "stringArray", STRING, null, true, false, false);
        assertDiscoveredMethod(mapMethods.get("addressMap"), "addressMap", ADDRESS, null, false, false, true);
        assertDiscoveredMethod(mapMethods.get("localDate"), "localDate", LOCALDATE, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("longValue"), "longValue", long.class.getName(), null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("bigDecimal"), "bigDecimal", BigDecimal.class.getName(), null, false, false, false);
    }

    @Test
    public void testMultipleLevels() throws IntrospectionException {
        Map<String, SchemaGenerator.DiscoveredMethod> mapMethods = schemaGenerator.retrieveGetterBeanMethods(Level0.class, false);
        assertThat(mapMethods, is(notNullValue()));
        assertThat(mapMethods.size(), is(2));
        assertDiscoveredMethod(mapMethods.get("id"), "id", STRING, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("level1"), "level1", Level1.class.getName(), null, false, false, false);
    }

    @Test
    public void testTypeWithIdOnMethod() throws IntrospectionException {
        Map<String, SchemaGenerator.DiscoveredMethod> mapMethods = schemaGenerator
                .retrieveGetterBeanMethods(TypeWithIdOnMethod.class, false);
        assertThat(mapMethods, is(notNullValue()));
        assertThat(mapMethods.size(), is(2));
        assertDiscoveredMethod(mapMethods.get("name"), "name", STRING, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("id"), "id", ID, null, false, false, false);
    }

    @Test
    public void testTypeWithIdOnField() throws IntrospectionException {
        Map<String, SchemaGenerator.DiscoveredMethod> mapMethods = schemaGenerator
                .retrieveGetterBeanMethods(TypeWithIdOnField.class, false);
        assertThat(mapMethods, is(notNullValue()));
        assertThat(mapMethods.size(), is(2));
        assertDiscoveredMethod(mapMethods.get("name"), "name", STRING, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("id"), "id", ID, null, false, false, false);
    }

    @Test
    public void testTypeWithNameAndJsonbProperty() throws IntrospectionException {
        Map<String, SchemaGenerator.DiscoveredMethod> mapMethods = schemaGenerator
                .retrieveGetterBeanMethods(TypeWithNameAndJsonbProperty.class, false);
        assertThat(mapMethods, is(notNullValue()));
        assertThat(mapMethods.size(), is(6));
        assertDiscoveredMethod(mapMethods.get("newFieldName1"), "newFieldName1", STRING, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("newFieldName2"), "newFieldName2", STRING, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("newFieldName3"), "newFieldName3", STRING, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("newFieldName4"), "newFieldName4", STRING, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("newFieldName5"), "newFieldName5", STRING, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("newFieldName6"), "newFieldName6", STRING, null, false, false, false);
    }

    @Test
    public void testInterfaceDiscovery() throws IntrospectionException {
        Map<String, SchemaGenerator.DiscoveredMethod> mapMethods = schemaGenerator.retrieveGetterBeanMethods(Vehicle.class, false);
        assertThat(mapMethods, is(notNullValue()));
        assertThat(mapMethods.size(), is(6));
        assertDiscoveredMethod(mapMethods.get("plate"), "plate", STRING, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("make"), "make", STRING, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("model"), "model", STRING, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("numberOfWheels"), "numberOfWheels", "int", null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("manufactureYear"), "manufactureYear", "int", null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("incidents"), "incidents", VehicleIncident.class.getName(), COLLECTION, false, true,
                               false);
    }

    @Test
    public void testInterfaceImplementorDiscovery1() throws IntrospectionException {
        Map<String, SchemaGenerator.DiscoveredMethod> mapMethods = schemaGenerator.retrieveGetterBeanMethods(Car.class, false);
        assertThat(mapMethods, is(notNullValue()));
        assertThat(mapMethods.size(), is(7));
        assertDiscoveredMethod(mapMethods.get("plate"), "plate", STRING, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("make"), "make", STRING, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("model"), "model", STRING, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("numberOfWheels"), "numberOfWheels", "int", null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("manufactureYear"), "manufactureYear", "int", null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("numberOfDoors"), "numberOfDoors", "int", null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("incidents"), "incidents", VehicleIncident.class.getName(), COLLECTION, false, true,
                               false);
    }

    @Test
    public void testInterfaceImplementorDiscovery2() throws IntrospectionException {
        Map<String, SchemaGenerator.DiscoveredMethod> mapMethods = schemaGenerator.retrieveGetterBeanMethods(Motorbike.class, false);
        assertThat(mapMethods, is(notNullValue()));
        assertThat(mapMethods.size(), is(7));
        assertDiscoveredMethod(mapMethods.get("plate"), "plate", STRING, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("make"), "make", STRING, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("model"), "model", STRING, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("numberOfWheels"), "numberOfWheels", "int", null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("manufactureYear"), "manufactureYear", "int", null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("hasSideCar"), "hasSideCar", "boolean", null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("incidents"), "incidents", VehicleIncident.class.getName(), COLLECTION, false, true,
                               false);
    }

    @Test
    public void testObjectWithIgnorableFields() throws IntrospectionException {
        Map<String, SchemaGenerator.DiscoveredMethod> mapMethods = schemaGenerator.retrieveGetterBeanMethods(
                ObjectWithIgnorableFieldsAndMethods.class, false);
        assertThat(mapMethods, is(notNullValue()));
        assertThat(mapMethods.size(), is(3));
        assertDiscoveredMethod(mapMethods.get("id"), "id", STRING, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("dontIgnore"), "dontIgnore", boolean.class.getName(), null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("value"), "value", STRING, null, false, false, false);

    }

    @Test
    public void testValidNames() {
        assertThat(SchemaGeneratorHelper.isValidGraphQLName("Name"), is(true));
        assertThat(SchemaGeneratorHelper.isValidGraphQLName("1Name"), is(false));
        assertThat(SchemaGeneratorHelper.isValidGraphQLName("Name#"), is(false));
        assertThat(SchemaGeneratorHelper.isValidGraphQLName("InvalidName#"), is(false));
        assertThat(SchemaGeneratorHelper.isValidGraphQLName("valid_name"), is(true));
        assertThat(SchemaGeneratorHelper.isValidGraphQLName("_valid_name"), is(true));
        assertThat(SchemaGeneratorHelper.isValidGraphQLName("__valid_name"), is(false));
    }

    @Test
    public void testTypeNames() {
        assertThat(SchemaGeneratorHelper.getTypeName(Address.class), is("Address"));
        assertThat(SchemaGeneratorHelper.getTypeName(PersonWithName.class), is("Person"));
        assertThat(SchemaGeneratorHelper.getTypeName(Person.class), is("Person"));
        assertThat(SchemaGeneratorHelper.getTypeName(VehicleIncident.class), is("Incident"));
        assertThat(SchemaGeneratorHelper.getTypeName(PersonWithNameValue.class), is("Person"));
        assertThat(SchemaGeneratorHelper.getTypeName(Vehicle.class), is("Vehicle"));
        assertThat(SchemaGeneratorHelper.getTypeName(EnumTestNoEnumName.class), is("EnumTestNoEnumName"));
        assertThat(SchemaGeneratorHelper.getTypeName(EnumTestWithEnumName.class), is("TShirtSize"));
        assertThat(SchemaGeneratorHelper.getTypeName(EnumTestWithNameAndNameAnnotation.class), is("ThisShouldWin"));
        assertThat(SchemaGeneratorHelper.getTypeName(EnumTestWithNameAnnotation.class), is("TShirtSize"));
        assertThat(SchemaGeneratorHelper.getTypeName(InnerClass.AnInnerClass.class), is("AnInnerClass"));
        assertThat(SchemaGeneratorHelper.getTypeName(InnerClass.class), is("InnerClass"));
        assertThat(SchemaGeneratorHelper.getTypeName(InterfaceWithTypeValue.class), is("NewName"));
    }

    @Test
    public void testGetSimpleName() throws ClassNotFoundException {
        assertThat(SchemaGeneratorHelper.getSimpleName(InnerClass.AnInnerClass.class.getName()), is("AnInnerClass"));
        assertThat(SchemaGeneratorHelper.getSimpleName(int.class.getName()), is(int.class.getName()));
        assertThat(SchemaGeneratorHelper.getSimpleName(SchemaGeneratorHelper.ID), is(SchemaGeneratorHelper.ID));
        assertThat(SchemaGeneratorHelper.getSimpleName(SchemaGeneratorHelper.BOOLEAN), is(SchemaGeneratorHelper.BOOLEAN));
        assertThat(SchemaGeneratorHelper.getSimpleName(SchemaGeneratorHelper.STRING), is(SchemaGeneratorHelper.STRING));
    }

    @Test
    public void testAllMethods() throws IntrospectionException {
        Map<String, SchemaGenerator.DiscoveredMethod> mapMethods = schemaGenerator
                .retrieveAllAnnotatedBeanMethods(SimpleQueriesNoArgs.class);
        assertThat(mapMethods, is(notNullValue()));
        assertThat(mapMethods.size(), is(14));
        assertDiscoveredMethod(mapMethods.get("hero"), "hero", STRING, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("episodeCount"), "episodeCount", "int", null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("numberOfStars"), "numberOfStars", Long.class.getName(), null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("badGuy"), "badGuy", STRING, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("allPeople"), "allPeople", Person.class.getName(), COLLECTION, false, true, false);
        assertDiscoveredMethod(mapMethods.get("returnCurrentDate"), "returnCurrentDate", LocalDate.class.getName(), null, false,
                               false, false);
        assertDiscoveredMethod(mapMethods.get("returnMediumSize"), "returnMediumSize", EnumTestWithEnumName.class.getName(), null,
                               false, false, false);
        assertDiscoveredMethod(mapMethods.get("returnTypeWithIDs"), "returnTypeWithIDs", TypeWithIDs.class.getName(), null,
                               false, false, false);
        assertDiscoveredMethod(mapMethods.get("getMultiLevelList"), "getMultiLevelList", MultiLevelListsAndArrays.class.getName(),
                               null,
                               false, false, false);
        assertDiscoveredMethod(mapMethods.get("idQuery"), "idQuery", ID, null,
                               false, false, false);
        assertDiscoveredMethod(mapMethods.get("booleanObject"), "booleanObject", Boolean.class.getName(), null,
                               false, false, false);
        assertDiscoveredMethod(mapMethods.get("booleanPrimitive"), "booleanPrimitive", boolean.class.getName(), null,
                               false, false, false);
    }

    @Test
    public void testStripMethodName() throws NoSuchMethodException {
        Method method = SimpleQueriesWithSource.class.getMethod("getCurrentJob", SimpleContact.class);
        assertThat(stripMethodName(method, false), is("currentJob"));
        method = DefaultValuePOJO.class.getMethod("getId");
        assertThat(stripMethodName(method, true), is("id"));
        method = OddNamedQueriesAndMutations.class.getMethod("settlement");
        assertThat(stripMethodName(method, false), is("settlement"));
        method = OddNamedQueriesAndMutations.class.getMethod("getaway");
        assertThat(stripMethodName(method, false), is("getaway"));
    }

    @Test
    public void testDefaultDescription() {
        assertThat(getDefaultDescription(null, null), is(nullValue()));
        assertThat(getDefaultDescription(new String[] {"format","locale"} , null), is("format locale"));
        assertThat(getDefaultDescription(null, "desc"), is("desc"));
        assertThat(getDefaultDescription(new String[] {"format","locale"}, "desc"), is("desc (format locale)"));
    }

    @Test
    public void testArrayDiscoveredMethods() throws IntrospectionException {
        Map<String, SchemaGenerator.DiscoveredMethod> mapMethods = schemaGenerator
                .retrieveGetterBeanMethods(MultiLevelListsAndArrays.class, false);
        assertThat(mapMethods, is(notNullValue()));
        assertThat(mapMethods.size(), is(8));
        assertDiscoveredMethod(mapMethods.get("multiStringArray"), "multiStringArray", STRING, null, true, false, false);
    }

    private void assertDiscoveredMethod(SchemaGenerator.DiscoveredMethod discoveredMethod,
                                        String name,
                                        String returnType,
                                        String collectionType,
                                        boolean isArrayReturnType,
                                        boolean isCollectionType,
                                        boolean isMap) {
        assertThat(discoveredMethod, is(notNullValue()));
        assertThat(discoveredMethod.isCollectionType(), is(isCollectionType));
        assertThat(discoveredMethod.isArrayReturnType(), is(isArrayReturnType));
        assertThat(discoveredMethod.isMap(), is(isMap));
        assertThat(discoveredMethod.getName(), is(name));
        assertThat(discoveredMethod.getReturnType(), is(returnType));
        assertThat(discoveredMethod.getCollectionType(), is(collectionType));
    }

    @Test
    public void testMultipleLevelsOfGenerics() throws IntrospectionException, ClassNotFoundException {
        Map<String, SchemaGenerator.DiscoveredMethod> mapMethods = schemaGenerator
                .retrieveGetterBeanMethods(MultiLevelListsAndArrays.class, false);
        assertThat(mapMethods, is(notNullValue()));
        assertThat(mapMethods.size(), is(8));
        Schema schema = schemaGenerator.generateSchemaFromClasses(MultiLevelListsAndArrays.class);
        generateGraphQLSchema(schema);
    }

    @Test
    public void testArrayLevelsAndRootArray() {
        String[] oneLevelString = new String[1];
        String[][] twoLevelString = new String[2][1];
        String[][][] threeLevelString = new String[2][1][2];
        int[] oneLevelInt = new int[1];
        int[][] twoLevelInt = new int[1][1];
        int[][][] threeLevelInt = new int[1][1][2];

        assertThat(SchemaGeneratorHelper.getArrayLevels(oneLevelString.getClass().getName()), is(1));
        assertThat(SchemaGeneratorHelper.getArrayLevels(twoLevelString.getClass().getName()), is(2));
        assertThat(SchemaGeneratorHelper.getArrayLevels(threeLevelString.getClass().getName()), is(3));
        assertThat(SchemaGeneratorHelper.getArrayLevels(oneLevelInt.getClass().getName()), is(1));
        assertThat(SchemaGeneratorHelper.getArrayLevels(twoLevelInt.getClass().getName()), is(2));
        assertThat(SchemaGeneratorHelper.getArrayLevels(threeLevelInt.getClass().getName()), is(3));

        assertThat(SchemaGeneratorHelper.getRootArrayClass(oneLevelString.getClass().getName()), is(String.class.getName()));
        assertThat(SchemaGeneratorHelper.getRootArrayClass(twoLevelString.getClass().getName()), is(String.class.getName()));
        assertThat(SchemaGeneratorHelper.getRootArrayClass(threeLevelString.getClass().getName()), is(String.class.getName()));
        assertThat(SchemaGeneratorHelper.getRootArrayClass(oneLevelInt.getClass().getName()), is(int.class.getName()));
        assertThat(SchemaGeneratorHelper.getRootArrayClass(twoLevelInt.getClass().getName()), is(int.class.getName()));
        assertThat(SchemaGeneratorHelper.getRootArrayClass(threeLevelInt.getClass().getName()), is(int.class.getName()));
    }

    @Test
    public void testGetRootType() throws NoSuchFieldException, NoSuchMethodException {
        SchemaGenerator schemaGenerator = new SchemaGenerator(ExecutionContext.getDefaultContext());
        ParameterizedType stringArrayListType = getParameterizedType("listStringArray");
        SchemaGenerator.RootTypeResult rootTypeName =
                schemaGenerator.getRootTypeName(stringArrayListType.getActualTypeArguments()[0], 0,
                                                -1,
                                                SchemaGeneratorTest.class.getMethod("getListStringArray"));
        assertThat(rootTypeName.getRootTypeName(), is(String[].class.getName()));
        assertThat(rootTypeName.getLevels(), is(1));

        ParameterizedType stringListType = getParameterizedType("listString");
        rootTypeName = schemaGenerator.getRootTypeName(stringListType.getActualTypeArguments()[0], 0,
                                                       -1,
                                                        SchemaGeneratorTest.class.getMethod("getListStringArray"));
        assertThat(rootTypeName.getRootTypeName(), is(String.class.getName()));
        assertThat(rootTypeName.getLevels(), is(1));

        ParameterizedType listListStringType = getParameterizedType("listListString");
        rootTypeName = schemaGenerator.getRootTypeName(listListStringType.getActualTypeArguments()[0], 0,
                                                       -1,
                                                       SchemaGeneratorTest.class.getMethod("getListListString"));
        assertThat(rootTypeName.getRootTypeName(), is(String.class.getName()));
        assertThat(rootTypeName.getLevels(), is(2));
    }

    @Test
    public void testGetCorrectFormat() {
        NumberFormat numberFormat = FormattingHelper.getCorrectNumberFormat(INT, "");
        assertThat(numberFormat, is(notNullValue()));
        assertThat(numberFormat.getMaximumFractionDigits(), is(0));

        numberFormat = FormattingHelper.getCorrectNumberFormat(BIG_INTEGER, "");
        assertThat(numberFormat, is(notNullValue()));
        assertThat(numberFormat.getMaximumFractionDigits(), is(0));

        numberFormat = FormattingHelper.getCorrectNumberFormat(FLOAT, "");
        assertThat(numberFormat, is(notNullValue()));
        assertThat(numberFormat.getMaximumFractionDigits() > 0, is(true));

        numberFormat = FormattingHelper.getCorrectNumberFormat(BIG_DECIMAL, "");
        assertThat(numberFormat, is(notNullValue()));
        assertThat(numberFormat.getMaximumFractionDigits() > 0, is(true));
    }

    @Test
    public void testFormatting() {
        assertFormat(FLOAT, "en-ZA", "¤ 000.00", 100.0d, "R 100,00");
        assertFormat(FLOAT, "en-AU", "¤ 000.00", 100.0d, "$ 100.00");
        assertFormat(FLOAT, "en-AU", "000.00 'ml'", 125.12d, "125.12 ml");
        assertFormat(INT, "en-AU", "0 'years'", 52, "52 years");
        assertFormat(INT, "en-AU", "0 'years old'", 12, "12 years old");
        assertFormat(BIG_DECIMAL, "en-AU", "###,###.###", 123456.789, "123,456.789");
        assertFormat(BIG_DECIMAL, "en-AU", "000000.000", 123.78, "000123.780");
    }

    @Test
    public void testGetFormatAnnotation() throws NoSuchFieldException, NoSuchMethodException {
        assertAnnotation(SimpleContactWithNumberFormats.class, "field", "age", 2, "0 'years old'", DEFAULT_LOCALE);
        assertAnnotation(SimpleContactWithNumberFormats.class, "field", "bankBalance", 2, "¤ 000.00", "en-AU");
        assertAnnotation(SimpleContactWithNumberFormats.class, "field", "value", 2, "0 'value'", DEFAULT_LOCALE);
        assertAnnotation(SimpleContactWithNumberFormats.class, "field", "name", 0, null, null);

        assertAnnotation(SimpleContactWithNumberFormats.class, "methodParam", "getFormatMethod", 2, "0 'years old'",
                         DEFAULT_LOCALE,
                         int.class);
        assertAnnotation(SimpleContactWithNumberFormats.class, "methodParam", "getName", 0, null, null);

        assertAnnotation(SimpleContactWithNumberFormats.class, "method", "getId", 2, "0 'id'", DEFAULT_LOCALE);
        assertAnnotation(SimpleContactWithNumberFormats.class, "method", "getName", 0, null, null);
    }

    @Test
    public void testFormatAnnotationFromSchema() throws IntrospectionException, ClassNotFoundException {
        SchemaGenerator schemaGenerator = new SchemaGenerator(ExecutionContext.getDefaultContext());
        Schema schema = schemaGenerator.generateSchemaFromClasses(SimpleContactWithNumberFormats.class);
        assertThat(schema, is(notNullValue()));
    }

    /**
     * Assert that a {@link org.eclipse.microprofile.graphql.NumberFormat} or {@link javax.json.bind.annotation.JsonbNumberFormat}
     * annotation is correctly applied.
     *
     * @param clazz          {@link Class} to apply to
     * @param type           type to check, "field", "method" or "methodParam"
     * @param name           field name or method name
     * @param expectedLength expected length of format array
     * @param expectedFormat expected value for format
     * @param expectedLocale expected value for locale
     * @param methodArgs     arguments of the method
     * @throws NoSuchFieldException
     */
    private void assertAnnotation(Class<?> clazz,
                                  String type,
                                  String name,
                                  int expectedLength,
                                  String expectedFormat,
                                  String expectedLocale,
                                  Class<?>... methodArgs)
            throws NoSuchFieldException, NoSuchMethodException {
        if (expectedLength != 0 && expectedLength != 2) {
            throw new IllegalArgumentException("Expected length should be 0 or 2");
        }
        String[] annotation = new String[0];
        if ("field".equals(type)) {
            Field field = clazz.getDeclaredField(name);
            assertThat(field, is(notNullValue()));
            annotation = getNumberFormatAnnotation(field);
        } else if ("methodParam".equals(type)) {
            Method method = clazz.getMethod(name, methodArgs);
            assertThat(method, is(notNullValue()));
            if (expectedLength == 2) {
                annotation = getNumberFormatAnnotation(method.getParameters()[0]);
            }
        } else if ("method".equals(type)) {
            Method method = clazz.getMethod(name, methodArgs);
            assertThat(method, is(notNullValue()));
            if (expectedLength == 2) {
                annotation = getNumberFormatAnnotation(method);
            }
        } else {
            throw new IllegalArgumentException("Unknown type of " + type);
        }
        assertThat(annotation, is(notNullValue()));
        assertThat(annotation.length, is(expectedLength));
        if (expectedLength == 2) {
            assertThat("Format should be " + expectedFormat + " but is " + annotation[0], annotation[0], is(expectedFormat));
            assertThat("locale should be " + expectedLocale + " but is " + annotation[1], annotation[1], is(expectedLocale));
        }
    }

    /**
     * Assert formatting is correct.
     *
     * @param type           type to format
     * @param locale         locale to use
     * @param format         format to apply
     * @param value          value to format
     * @param expectedResult expected result
     */
    private void assertFormat(String type, String locale, String format, Object value, String expectedResult) {
        NumberFormat numberFormat = FormattingHelper.getCorrectNumberFormat(type, locale, format);
        assertThat(numberFormat, is(notNullValue()));
        String formatted = numberFormat.format(value);
        assertThat(formatted, is(notNullValue()));
        assertThat(formatted, is(expectedResult));
    }

    private ParameterizedType getParameterizedType(String fieldName) throws NoSuchFieldException {
        Field listStringArray = SchemaGeneratorTest.class.getDeclaredField(fieldName);
        return (ParameterizedType) listStringArray.getGenericType();
    }

    private void testEnum(Class<?> clazz, String expectedName) throws IntrospectionException, ClassNotFoundException {
        SchemaGenerator schemaGenerator = new SchemaGenerator(ExecutionContext.getDefaultContext());
        Schema schema = schemaGenerator.generateSchemaFromClasses(clazz);
        assertThat(schema, is(notNullValue()));

        assertThat(schema.getEnums().size(), is(1));
        SchemaEnum schemaEnumResult = schema.getEnumByName(expectedName);

        assertThat(schemaEnumResult, is(notNullValue()));
        assertThat(schemaEnumResult.getValues().size(), is(6));

        Arrays.stream(new String[] { "S", "M", "L", "XL", "XXL", "XXXL" })
                .forEach(v -> assertThat(schemaEnumResult.getValues().contains(v), is(true)));
    }

}
