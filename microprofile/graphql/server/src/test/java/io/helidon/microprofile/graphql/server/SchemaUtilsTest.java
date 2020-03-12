/*
 * Copyright (c) 2020 and/or its affiliates. All rights reserved.
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
import java.lang.reflect.ParameterizedType;

import java.math.BigDecimal;
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
import io.helidon.microprofile.graphql.server.test.queries.SimpleQueriesNoArgs;
import io.helidon.microprofile.graphql.server.test.types.Address;
import io.helidon.microprofile.graphql.server.test.types.Car;
import io.helidon.microprofile.graphql.server.test.types.InnerClass;
import io.helidon.microprofile.graphql.server.test.types.InterfaceWithTypeValue;
import io.helidon.microprofile.graphql.server.test.types.Level0;
import io.helidon.microprofile.graphql.server.test.types.Level1;
import io.helidon.microprofile.graphql.server.test.types.Motorbike;
import io.helidon.microprofile.graphql.server.test.types.MultiLevelListsAndArrays;
import io.helidon.microprofile.graphql.server.test.types.Person;
import io.helidon.microprofile.graphql.server.test.types.PersonWithName;
import io.helidon.microprofile.graphql.server.test.types.PersonWithNameValue;
import io.helidon.microprofile.graphql.server.test.types.TypeWithIDs;
import io.helidon.microprofile.graphql.server.test.types.TypeWithIdOnField;
import io.helidon.microprofile.graphql.server.test.types.TypeWithIdOnMethod;
import io.helidon.microprofile.graphql.server.test.types.TypeWithNameAndJsonbProperty;

import io.helidon.microprofile.graphql.server.test.types.Vehicle;
import io.helidon.microprofile.graphql.server.test.types.VehicleIncident;
import org.junit.jupiter.api.Test;

import static io.helidon.microprofile.graphql.server.SchemaUtilsHelper.getRootTypeName;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for {@link SchemaUtils}.
 */
public class SchemaUtilsTest extends AbstractGraphQLTest {

    private static final String ADDRESS = Address.class.getName();
    private static final String STRING = String.class.getName();
    private static final String BIGDECIMAL = BigDecimal.class.getName();
    private static final String COLLECTION = Collection.class.getName();
    private static final String LIST = List.class.getName();
    private static final String LOCALDATE = LocalDate.class.getName();
    private static final String ID = "ID";

    @Test
    public void testEnumGeneration() throws IntrospectionException, ClassNotFoundException {
        testEnum(EnumTestNoEnumName.class, EnumTestNoEnumName.class.getSimpleName());
        testEnum(EnumTestWithEnumName.class, "TShirtSize");
        testEnum(EnumTestWithNameAnnotation.class, "TShirtSize");
        testEnum(EnumTestWithNameAndNameAnnotation.class, "ThisShouldWin");
    }

    @Test
    public void testGettersPerson() throws IntrospectionException {
        Map<String, SchemaUtils.DiscoveredMethod> mapMethods = SchemaUtils.retrieveGetterBeanMethods(Person.class);
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
        Map<String, SchemaUtils.DiscoveredMethod> mapMethods = SchemaUtils.retrieveGetterBeanMethods(Level0.class);
        assertThat(mapMethods, is(notNullValue()));
        assertThat(mapMethods.size(), is(2));
        assertDiscoveredMethod(mapMethods.get("id"), "id", STRING, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("level1"), "level1", Level1.class.getName(), null, false, false, false);
    }

    @Test
    public void testTypeWithIdOnMethod() throws IntrospectionException {
        Map<String, SchemaUtils.DiscoveredMethod> mapMethods = SchemaUtils.retrieveGetterBeanMethods(TypeWithIdOnMethod.class);
        assertThat(mapMethods, is(notNullValue()));
        assertThat(mapMethods.size(), is(2));
        assertDiscoveredMethod(mapMethods.get("name"), "name", STRING, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("id"), "id", ID, null, false, false, false);
    }

    @Test
    public void testTypeWithIdOnField() throws IntrospectionException {
        Map<String, SchemaUtils.DiscoveredMethod> mapMethods = SchemaUtils.retrieveGetterBeanMethods(TypeWithIdOnField.class);
        assertThat(mapMethods, is(notNullValue()));
        assertThat(mapMethods.size(), is(2));
        assertDiscoveredMethod(mapMethods.get("name"), "name", STRING, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("id"), "id", ID, null, false, false, false);
    }

    @Test
    public void testTypeWithNameAndJsonbProperty() throws IntrospectionException {
        Map<String, SchemaUtils.DiscoveredMethod> mapMethods = SchemaUtils
                .retrieveGetterBeanMethods(TypeWithNameAndJsonbProperty.class);
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
        Map<String, SchemaUtils.DiscoveredMethod> mapMethods = SchemaUtils.retrieveGetterBeanMethods(Vehicle.class);
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
        Map<String, SchemaUtils.DiscoveredMethod> mapMethods = SchemaUtils.retrieveGetterBeanMethods(Car.class);
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
        Map<String, SchemaUtils.DiscoveredMethod> mapMethods = SchemaUtils.retrieveGetterBeanMethods(Motorbike.class);
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
    public void testTypeNames() {
        assertThat(SchemaUtilsHelper.getTypeName(Address.class), is("Address"));
        assertThat(SchemaUtilsHelper.getTypeName(PersonWithName.class), is("Person"));
        assertThat(SchemaUtilsHelper.getTypeName(Person.class), is("Person"));
        assertThat(SchemaUtilsHelper.getTypeName(VehicleIncident.class), is("Incident"));
        assertThat(SchemaUtilsHelper.getTypeName(PersonWithNameValue.class), is("Person"));
        assertThat(SchemaUtilsHelper.getTypeName(Vehicle.class), is("Vehicle"));
        assertThat(SchemaUtilsHelper.getTypeName(EnumTestNoEnumName.class), is("EnumTestNoEnumName"));
        assertThat(SchemaUtilsHelper.getTypeName(EnumTestWithEnumName.class), is("TShirtSize"));
        assertThat(SchemaUtilsHelper.getTypeName(EnumTestWithNameAndNameAnnotation.class), is("ThisShouldWin"));
        assertThat(SchemaUtilsHelper.getTypeName(EnumTestWithNameAnnotation.class), is("TShirtSize"));
        assertThat(SchemaUtilsHelper.getTypeName(InnerClass.AnInnerClass.class), is("AnInnerClass"));
        assertThat(SchemaUtilsHelper.getTypeName(InnerClass.class), is("InnerClass"));
        assertThat(SchemaUtilsHelper.getTypeName(InterfaceWithTypeValue.class), is("NewName"));
    }

    @Test
    public void testGetSimpleName() throws ClassNotFoundException {
        assertThat(SchemaUtilsHelper.getSimpleName(InnerClass.AnInnerClass.class.getName()), is("AnInnerClass"));
        assertThat(SchemaUtilsHelper.getSimpleName(SchemaUtilsHelper.INT), is((SchemaUtilsHelper.INT)));
        assertThat(SchemaUtilsHelper.getSimpleName(SchemaUtilsHelper.ID), is(SchemaUtilsHelper.ID));
        assertThat(SchemaUtilsHelper.getSimpleName(SchemaUtilsHelper.BOOLEAN), is(SchemaUtilsHelper.BOOLEAN));
        assertThat(SchemaUtilsHelper.getSimpleName(SchemaUtilsHelper.FLOAT), is(SchemaUtilsHelper.FLOAT));
        assertThat(SchemaUtilsHelper.getSimpleName(SchemaUtilsHelper.STRING), is(SchemaUtilsHelper.STRING));
    }

    @Test
    public void testAllMethods() throws IntrospectionException {
        Map<String, SchemaUtils.DiscoveredMethod> mapMethods = SchemaUtils
                .retrieveAllAnnotatedBeanMethods(SimpleQueriesNoArgs.class);
        assertThat(mapMethods, is(notNullValue()));
        assertThat(mapMethods.size(), is(9));
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
    }

    @Test
    public void testArrayDiscoveredMethods() throws IntrospectionException {
        Map<String, SchemaUtils.DiscoveredMethod> mapMethods = SchemaUtils
                .retrieveGetterBeanMethods(MultiLevelListsAndArrays.class);
        assertThat(mapMethods, is(notNullValue()));
        assertThat(mapMethods.size(), is(8));
        assertDiscoveredMethod(mapMethods.get("multiStringArray"), "multiStringArray", STRING, null, true, false, false);
    }

    private void assertDiscoveredMethod(SchemaUtils.DiscoveredMethod discoveredMethod,
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
        SchemaUtils schemaUtils = new SchemaUtils();
        Map<String, SchemaUtils.DiscoveredMethod> mapMethods = SchemaUtils
                .retrieveGetterBeanMethods(MultiLevelListsAndArrays.class);
        assertThat(mapMethods, is(notNullValue()));
        assertThat(mapMethods.size(), is(8));
        Schema schema = schemaUtils.generateSchemaFromClasses(MultiLevelListsAndArrays.class);
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

        assertThat(SchemaUtilsHelper.getArrayLevels(oneLevelString.getClass().getName()), is(1));
        assertThat(SchemaUtilsHelper.getArrayLevels(twoLevelString.getClass().getName()), is(2));
        assertThat(SchemaUtilsHelper.getArrayLevels(threeLevelString.getClass().getName()), is(3));
        assertThat(SchemaUtilsHelper.getArrayLevels(oneLevelInt.getClass().getName()), is(1));
        assertThat(SchemaUtilsHelper.getArrayLevels(twoLevelInt.getClass().getName()), is(2));
        assertThat(SchemaUtilsHelper.getArrayLevels(threeLevelInt.getClass().getName()), is(3));

        assertThat(SchemaUtilsHelper.getRootArrayClass(oneLevelString.getClass().getName()), is(String.class.getName()));
        assertThat(SchemaUtilsHelper.getRootArrayClass(twoLevelString.getClass().getName()), is(String.class.getName()));
        assertThat(SchemaUtilsHelper.getRootArrayClass(threeLevelString.getClass().getName()), is(String.class.getName()));
        assertThat(SchemaUtilsHelper.getRootArrayClass(oneLevelInt.getClass().getName()), is(int.class.getName()));
        assertThat(SchemaUtilsHelper.getRootArrayClass(twoLevelInt.getClass().getName()), is(int.class.getName()));
        assertThat(SchemaUtilsHelper.getRootArrayClass(threeLevelInt.getClass().getName()), is(int.class.getName()));
    }

    private List<String[]> listStringArray = new ArrayList<>();
    private List<String> listString = new ArrayList<>();
    private List<List<List<String>>> listListString = new ArrayList<>();

    @Test
    public void testGetRootType() throws NoSuchFieldException {
        ParameterizedType stringArrayListType = getParameterizedType("listStringArray");
        SchemaUtilsHelper.RootTypeResult rootTypeName = getRootTypeName(stringArrayListType.getActualTypeArguments()[0], 0);
        assertThat(rootTypeName.getRootTypeName(), is(String[].class.getName()));
        assertThat(rootTypeName.getLevels(), is(1));

        ParameterizedType stringListType = getParameterizedType("listString");
                rootTypeName = getRootTypeName(stringListType.getActualTypeArguments()[0], 0);
        assertThat(rootTypeName.getRootTypeName(), is(String.class.getName()));
        assertThat(rootTypeName.getLevels(), is(1));

        ParameterizedType listListStringType = getParameterizedType("listListString");
                rootTypeName = getRootTypeName(listListStringType.getActualTypeArguments()[0], 0);
        assertThat(rootTypeName.getRootTypeName(), is(String.class.getName()));
        assertThat(rootTypeName.getLevels(), is(2));
    }

    private ParameterizedType getParameterizedType(String fieldName) throws NoSuchFieldException {
        Field listStringArray = SchemaUtilsTest.class.getDeclaredField(fieldName);
        return (ParameterizedType) listStringArray.getGenericType();
    }

    private void testEnum(Class<?> clazz, String expectedName) throws IntrospectionException, ClassNotFoundException {
        SchemaUtils schemaUtils = new SchemaUtils();
        Schema schema = schemaUtils.generateSchemaFromClasses(clazz);
        assertThat(schema, is(notNullValue()));

        assertThat(schema.getEnums().size(), is(1));
        SchemaEnum schemaEnumResult = schema.getEnumByName(expectedName);

        assertThat(schemaEnumResult, is(notNullValue()));
        assertThat(schemaEnumResult.getValues().size(), is(6));

        Arrays.stream(new String[] { "S", "M", "L", "XL", "XXL", "XXXL" })
                .forEach(v -> assertThat(schemaEnumResult.getValues().contains(v), is(true)));
    }

}
