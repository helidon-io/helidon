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

package io.helidon.microprofile.graphql.server.test.queries;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.json.bind.annotation.JsonbProperty;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.enums.EnumTestWithNameAnnotation;
import io.helidon.microprofile.graphql.server.test.types.Car;
import io.helidon.microprofile.graphql.server.test.types.Person;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

/**
 * Class that holds simple query definitions with various numbers of arguments.
 */
@GraphQLApi
public class SimpleQueriesWithArgs {

    @Inject
    private TestDB testDB;

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
        return new TestDB().getPerson(personId);
    }

    // query with arguments as types, enums and scalars
    // query with Collection, List and Map as return type
    // query with ID as param

    @Query
    public Collection<Person> findPeopleFromState(@Name("state") String state) {
        return new TestDB().getAllPeople()
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

//    @Query
//    public boolean canFindCar(@Name("carToFind") Car car) {
//        return false;
//    }
//
//    @Query
//    @JsonbProperty("findEnums")
//    public Collection<EnumTestWithNameAnnotation> findEnumValues(EnumTestWithNameAnnotation enum1) {
//        return Arrays.stream(EnumTestWithNameAnnotation.values()).filter(e -> e.equals(enum1)).collect(Collectors.toList());
//    }


}
