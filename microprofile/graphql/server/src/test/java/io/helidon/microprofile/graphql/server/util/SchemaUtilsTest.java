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

package io.helidon.microprofile.graphql.server.util;

import java.beans.IntrospectionException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.microprofile.graphql.server.AbstractGraphQLTest;
import io.helidon.microprofile.graphql.server.model.SchemaEnum;
import io.helidon.microprofile.graphql.server.model.Schema;
import io.helidon.microprofile.graphql.server.test.enums.EnumTestNoEnumName;
import io.helidon.microprofile.graphql.server.test.enums.EnumTestWithEnumName;
import io.helidon.microprofile.graphql.server.test.enums.EnumTestWithNameAndNameAnnotation;
import io.helidon.microprofile.graphql.server.test.enums.EnumTestWithNameAnnotation;
import io.helidon.microprofile.graphql.server.test.types.Address;
import io.helidon.microprofile.graphql.server.test.types.Car;
import io.helidon.microprofile.graphql.server.test.types.InnerClass;
import io.helidon.microprofile.graphql.server.test.types.InterfaceWithTypeValue;
import io.helidon.microprofile.graphql.server.test.types.Level0;
import io.helidon.microprofile.graphql.server.test.types.Level1;
import io.helidon.microprofile.graphql.server.test.types.Motorbike;
import io.helidon.microprofile.graphql.server.test.types.Person;
import io.helidon.microprofile.graphql.server.test.types.PersonWithName;
import io.helidon.microprofile.graphql.server.test.types.PersonWithNameValue;
import io.helidon.microprofile.graphql.server.test.types.TypeWithIdOnField;
import io.helidon.microprofile.graphql.server.test.types.TypeWithIdOnMethod;
import io.helidon.microprofile.graphql.server.test.types.TypeWithNameAndJsonbProperty;

import io.helidon.microprofile.graphql.server.test.types.Vehicle;
import io.helidon.microprofile.graphql.server.test.types.VehicleIncident;
import org.junit.jupiter.api.Test;

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
        Map<String, SchemaUtils.DiscoveredMethod> mapMethods = SchemaUtils.retrieveBeanMethods(Person.class);
        assertThat(mapMethods, is(notNullValue()));
        assertThat(mapMethods.size(), is(11));

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
    }

    @Test
    public void testMultipleLevels() throws IntrospectionException {
        Map<String, SchemaUtils.DiscoveredMethod> mapMethods = SchemaUtils.retrieveBeanMethods(Level0.class);
        assertThat(mapMethods, is(notNullValue()));
        assertThat(mapMethods.size(), is(2));
        assertDiscoveredMethod(mapMethods.get("id"), "id", STRING, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("level1"), "level1", Level1.class.getName(), null, false, false, false);
    }

    @Test
    public void testTypeWithIdOnMethod() throws IntrospectionException {
        Map<String, SchemaUtils.DiscoveredMethod> mapMethods = SchemaUtils.retrieveBeanMethods(TypeWithIdOnMethod.class);
        assertThat(mapMethods, is(notNullValue()));
        assertThat(mapMethods.size(), is(2));
        assertDiscoveredMethod(mapMethods.get("name"), "name", STRING, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("id"), "id", ID, null, false, false, false);
    }

    @Test
    public void testTypeWithIdOnField() throws IntrospectionException {
        Map<String, SchemaUtils.DiscoveredMethod> mapMethods = SchemaUtils.retrieveBeanMethods(TypeWithIdOnField.class);
        assertThat(mapMethods, is(notNullValue()));
        assertThat(mapMethods.size(), is(2));
        assertDiscoveredMethod(mapMethods.get("name"), "name", STRING, null, false, false, false);
        assertDiscoveredMethod(mapMethods.get("id"), "id", ID, null, false, false, false);
    }

    @Test
    public void testTypeWithNameAndJsonbProperty() throws IntrospectionException {
        Map<String, SchemaUtils.DiscoveredMethod> mapMethods = SchemaUtils
                .retrieveBeanMethods(TypeWithNameAndJsonbProperty.class);
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
        Map<String, SchemaUtils.DiscoveredMethod> mapMethods = SchemaUtils.retrieveBeanMethods(Vehicle.class);
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
        Map<String, SchemaUtils.DiscoveredMethod> mapMethods = SchemaUtils.retrieveBeanMethods(Car.class);
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
        Map<String, SchemaUtils.DiscoveredMethod> mapMethods = SchemaUtils.retrieveBeanMethods(Motorbike.class);
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
        SchemaUtils schemaUtils = new SchemaUtils();
        assertThat(schemaUtils.getTypeName(Address.class), is("Address"));
        assertThat(schemaUtils.getTypeName(PersonWithName.class), is("Person"));
        assertThat(schemaUtils.getTypeName(Person.class), is("Person"));
        assertThat(schemaUtils.getTypeName(VehicleIncident.class), is("Incident"));
        assertThat(schemaUtils.getTypeName(PersonWithNameValue.class), is("Person"));
        assertThat(schemaUtils.getTypeName(Vehicle.class), is("Vehicle"));
        assertThat(schemaUtils.getTypeName(EnumTestNoEnumName.class), is("EnumTestNoEnumName"));
        assertThat(schemaUtils.getTypeName(EnumTestWithEnumName.class), is("TShirtSize"));
        assertThat(schemaUtils.getTypeName(EnumTestWithNameAndNameAnnotation.class), is("ThisShouldWin"));
        assertThat(schemaUtils.getTypeName(EnumTestWithNameAnnotation.class), is("TShirtSize"));
        assertThat(schemaUtils.getTypeName(InnerClass.AnInnerClass.class), is("AnInnerClass"));
        assertThat(schemaUtils.getTypeName(InnerClass.class), is("InnerClass"));
        assertThat(schemaUtils.getTypeName(InterfaceWithTypeValue.class), is("NewName"));
    }

    @Test
    public void testGetSimpleName() throws ClassNotFoundException {
        SchemaUtils schemaUtils = new SchemaUtils();
        assertThat(schemaUtils.getSimpleName(InnerClass.AnInnerClass.class.getName()), is("AnInnerClass"));
        assertThat(schemaUtils.getSimpleName(SchemaUtils.INT), is(SchemaUtils.INT));
        assertThat(schemaUtils.getSimpleName(SchemaUtils.ID), is(SchemaUtils.ID));
        assertThat(schemaUtils.getSimpleName(SchemaUtils.BOOLEAN), is(SchemaUtils.BOOLEAN));
        assertThat(schemaUtils.getSimpleName(SchemaUtils.FLOAT), is(SchemaUtils.FLOAT));
        assertThat(schemaUtils.getSimpleName(SchemaUtils.STRING), is(SchemaUtils.STRING));
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

    private void testEnum(Class<?> clazz, String expectedName) throws IntrospectionException, ClassNotFoundException {
        SchemaUtils schemaUtils = new SchemaUtils();
        Schema schema = schemaUtils.generateSchemaFromClasses(clazz);
        assertThat(schema, is(notNullValue()));

        assertThat(schema.getEnums().size(), is(1));
        SchemaEnum schemaEnumResult = schema.getEnumByName(expectedName);

        assertThat(schemaEnumResult, is(notNullValue()));
        assertThat(schemaEnumResult.getValues().size(), is(6));

        Set.of("S", "M", "L", "XL", "XXL", "XXXL").forEach(v -> assertThat(schemaEnumResult.getValues().contains(v), is(true)));
    }

}
