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

package io.helidon.microprofile.graphql.server.test.queries;

import java.time.LocalDate;
import java.util.ArrayList;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.annotation.JsonbProperty;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.enums.EnumTestWithNameAnnotation;
import io.helidon.microprofile.graphql.server.test.types.ContactRelationship;
import io.helidon.microprofile.graphql.server.test.types.Person;
import io.helidon.microprofile.graphql.server.test.types.SimpleContact;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Id;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

/**
 * Class that holds simple query definitions with various numbers of arguments.
 */
@GraphQLApi
@ApplicationScoped
public class SimpleQueriesWithArgs {

    public SimpleQueriesWithArgs() {
    }

    @Inject
    private TestDB testDB;

    // tests for ID
    @Query
    public Integer returnIntegerAsId(@Name("param1") @Id Integer value) {
        return value;
    }

    @Query
    public Integer returnIntAsId(@Name("param1") @Id int value) {
        return value;
    }

    @Query
    public String returnStringAsId(@Name("param1") @Id String value) {
        return value;
    }

    @Query
    public Long returnLongAsId(@Name("param1") @Id Long value) {
        return value;
    }

    @Query
    public long returnLongPrimitiveAsId(@Name("param1") @Id long value) {
        return value;
    }

    @Query
    public UUID returnUUIDAsId(@Name("param1") @Id UUID value) {
        return value;
    }

    @Query
    public String hero(@Name("heroType") String heroType) {
        return "human".equalsIgnoreCase(heroType)
                ? "Luke"
                : "R2-D2";
    }

    @Query("multiply")
    public long multiplyFunction(int number1, int number2) {
        return number1 * number2;
    }

    @Query
    @Name("findAPerson")
    public Person findPerson(@Name("personId") int personId) {
        return testDB.getPerson(personId);
    }

    @Query
    public Collection<Person> findPeopleFromState(@Name("state") String state) {
        return testDB.getAllPeople()
                .stream().filter(p -> p.getHomeAddress().getState().equals(state))
                .collect(Collectors.toList());
    }

    @Query
    public List<LocalDate> findLocalDates(@Name("numberOfValues") int count) {
        List<LocalDate> localDates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            localDates.add(LocalDate.now());
        }
        return localDates;
    }

    @Query("getMonthFromDate")
    public String returnDateAsLong(@Name("date") LocalDate localDate) {
        return localDate.getMonth().toString();
    }

    @Query
    public Collection<EnumTestWithNameAnnotation> findOneEnum(@Name("enum") EnumTestWithNameAnnotation enum1) {
        return Collections.singleton(enum1);
    }

    @Query
    public boolean canFindContact(@Name("contact") SimpleContact contact) {
        return false;
    }

    @Query
    @JsonbProperty("findEnums")
    public String findEnumValue(EnumTestWithNameAnnotation enum1) {
        return enum1.name();
    }

    @Query("canFindContactRelationship")
    public boolean getContactRelationships(@Name("relationship") ContactRelationship relationship) {
        return false;
    }

    @Query("additionQuery")
    public int addNumbers(@Name("value1") int value1, @Name("value2") int value2) {
        return value1 + value2;
    }
}
