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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

import org.eclipse.microprofile.graphql.NonNull;

import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for {@link SchemaGeneratorTest}.
 */
@SuppressWarnings("unchecked")
@ExtendWith(WeldJunit5Extension.class)
public class SchemaGeneratorIT extends AbstractGraphQLIT {

    @WeldSetup
    private final WeldInitiator weld = WeldInitiator.of(WeldInitiator.createWeld()
                                                          .addBeanClass(Person.class)
                                                          .addExtension(new GraphQLCdiExtension()));

    @Test
    public void testJandexUtils() throws IOException {
        setupIndex(indexFileName, NullPOJO.class, QueriesWithNulls.class);
        SchemaGenerator schemaGenerator = new SchemaGenerator(defaultContext);
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

    @Test
    public void testVoidMutations() throws IOException {
        setupIndex(indexFileName, VoidMutations.class);
        assertThrows(RuntimeException.class, () ->  new ExecutionContext(defaultContext));
    }

    @Test
    public void testInvalidNamedType() throws IOException {
        setupIndex(indexFileName, InvalidNamedTypes.InvalidNamedPerson.class);
        assertThrows(RuntimeException.class, () ->  new ExecutionContext(defaultContext));
    }

    @Test
    public void testInvalidNamedInputType() throws IOException {
        setupIndex(indexFileName, InvalidNamedTypes.InvalidInputType.class);
        assertThrows(RuntimeException.class, () ->  new ExecutionContext(defaultContext));
    }

    @Test
    public void testInvalidNamedInterface() throws IOException {
        setupIndex(indexFileName, InvalidNamedTypes.InvalidInterface.class, InvalidNamedTypes.InvalidClass.class);
        assertThrows(RuntimeException.class, () ->  new ExecutionContext(defaultContext));
    }

    @Test
    public void testInvalidNamedQuery() throws IOException {
        setupIndex(indexFileName, InvalidNamedTypes.ClassWithInvalidQuery.class);
        assertThrows(RuntimeException.class, () ->  new ExecutionContext(defaultContext));
    }

    @Test
    public void testInvalidNamedMutation() throws IOException {
        setupIndex(indexFileName, InvalidNamedTypes.ClassWithInvalidMutation.class);
        assertThrows(RuntimeException.class, () ->  new ExecutionContext(defaultContext));
    }

    @Test
    public void testInvalidNameEnum() throws IOException {
        setupIndex(indexFileName, InvalidNamedTypes.Size.class);
        assertThrows(RuntimeException.class, () ->  new ExecutionContext(defaultContext));
    }

    @Test
    public void testVoidQueries() throws IOException {
        setupIndex(indexFileName, VoidQueries.class);
        assertThrows(RuntimeException.class, () ->  new ExecutionContext(defaultContext));
    }

    @Test
    public void testInvalidQueries() throws IOException {
        setupIndex(indexFileName, InvalidQueries.class);
        assertThrows(RuntimeException.class, () ->  new ExecutionContext(defaultContext));
    }

    @Test
    public void testDuplicateQueryOrMutationNames() throws IOException {
        setupIndex(indexFileName, DuplicateNameQueries.class);
        assertThrows(RuntimeException.class, () ->  new ExecutionContext(defaultContext));
    }
}
