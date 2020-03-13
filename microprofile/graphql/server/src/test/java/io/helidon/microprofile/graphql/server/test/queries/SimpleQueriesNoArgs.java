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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.annotation.JsonbProperty;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.enums.EnumTestWithEnumName;
import io.helidon.microprofile.graphql.server.test.types.DateTimePojo;
import io.helidon.microprofile.graphql.server.test.types.MultiLevelListsAndArrays;
import io.helidon.microprofile.graphql.server.test.types.Person;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactWithSelf;
import io.helidon.microprofile.graphql.server.test.types.TypeWithIDs;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

/**
 * Class that holds simple query definitions with no-argument.
 */
@GraphQLApi
@ApplicationScoped
public class SimpleQueriesNoArgs {

    @Inject
    private TestDB testDB;

    public SimpleQueriesNoArgs() {
    }

    @Query
    public String hero() {
        return "R2-D2";
    }

    @Query
    @Name("episodeCount")
    public int getNumberOfEpisodes() {
        return 9;
    }

    @Query
    @JsonbProperty("numberOfStars")
    public Long getTheNumberOfStars() {
        return Long.MAX_VALUE;
    }

    @Query("badGuy")
    public String getVillain() {
        return "Darth Vader";
    }

    @Query("allPeople")
    public Collection<Person> findAllPeople() {
        return testDB.getAllPeople();
    }

    @Query
    public LocalDate returnCurrentDate() {
        return LocalDate.now();
    }

    @Query
    @Name("returnMediumSize")
    public EnumTestWithEnumName getEnumMedium() {
        return EnumTestWithEnumName.M;
    }

    @Query
    public TypeWithIDs returnTypeWithIDs() {
        return new TypeWithIDs(1, 2, "string", 10L, 10L, UUID.randomUUID());
    }

    @Query
    SimpleContactWithSelf returnSimpleContactWithSelf() {
        SimpleContactWithSelf spouse = new SimpleContactWithSelf("c1", "contact1", 30);
        SimpleContactWithSelf contact = new SimpleContactWithSelf("c2", "contact2", 33);
        contact.setSpouse(spouse);
        return contact;
    }
    
    @Query
    @Name("getMultiLevelList")
    public MultiLevelListsAndArrays returnLists() {
        List<List<BigDecimal>> listListBigDecimal = new ArrayList<>();
        listListBigDecimal.add(Collections.singletonList(new BigDecimal(100)));
        int[][] intMultiLevelArray = new int[2][];
        intMultiLevelArray[0] = new int[] { 1, 2, 3 };
        intMultiLevelArray[1] = new int[] { 4, 5, 6 };
        Person[][] personMultiLevelArray = new Person[3][];
        personMultiLevelArray[0] = new Person[] { testDB.generatePerson(1), testDB.generatePerson(2)};
        personMultiLevelArray[1] = new Person[] { testDB.generatePerson(3), testDB.generatePerson(4)};
        List<String[]> listOfStringArrays = new ArrayList<>();
        listOfStringArrays.add(new String[] {"one", "two", "three"});
        listOfStringArrays.add(new String[] {"four", "five"});
        String[][][] multiStringArray =  { { { "one", "two" }, { "three", "four" } }, { { "five", "six" }, { "seven", "eight" } } };
        Collection<Collection<Collection<String>>> colColColString = new ArrayList<>();
        colColColString.add(Collections.singletonList(Collections.singleton("a")));

        return new MultiLevelListsAndArrays(listListBigDecimal, null, null, intMultiLevelArray,
                                            personMultiLevelArray, listOfStringArrays, multiStringArray, colColColString);
    }

    @Query("dateAndTimePOJOQuery")
    public DateTimePojo DateTimePojo() {
        return new DateTimePojo(LocalDate.now(), LocalTime.now(), OffsetTime.now(),
                                LocalDateTime.now(), OffsetDateTime.now(), ZonedDateTime.now());
    }
}
