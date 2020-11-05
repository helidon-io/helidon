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

import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.enums.EnumTestWithNameAnnotation;
import io.helidon.microprofile.graphql.server.test.queries.SimpleQueriesWithArgs;
import io.helidon.microprofile.graphql.server.test.types.AbstractVehicle;
import io.helidon.microprofile.graphql.server.test.types.Car;
import io.helidon.microprofile.graphql.server.test.types.ContactRelationship;
import io.helidon.microprofile.graphql.server.test.types.SimpleContact;

import io.helidon.microprofile.graphql.server.test.types.SimpleContactWithSelf;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Tests for simple queries.
 */
@ExtendWith(WeldJunit5Extension.class)
public class SimpleQueriesIT extends AbstractGraphQLIT {

    @WeldSetup
    private final WeldInitiator weld = WeldInitiator.of(WeldInitiator.createWeld()
                                                                .addBeanClass(SimpleQueriesWithArgs.class)
                                                                .addBeanClass(Car.class)
                                                                .addBeanClass(SimpleContactWithSelf.class)
                                                                .addBeanClass(AbstractVehicle.class)
                                                                .addBeanClass(TestDB.class)
                                                                .addExtension(new GraphQLCdiExtension()));

    @Test
    public void testSimpleContactWithSelf() throws IOException {
        setupIndex(indexFileName, SimpleContactWithSelf.class);
        ExecutionContext executionContext =  new ExecutionContext(defaultContext);
        executionContext.execute("query { hero }");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSimpleQueryGenerationWithArgs() throws IOException {
        setupIndex(indexFileName, SimpleQueriesWithArgs.class, Car.class, AbstractVehicle.class);
        ExecutionContext executionContext = new ExecutionContext(defaultContext);

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
}
