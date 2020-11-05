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
import io.helidon.microprofile.graphql.server.test.types.MultiLevelListsAndArrays;
import io.helidon.microprofile.graphql.server.test.types.Person;
import io.helidon.microprofile.graphql.server.test.types.TypeWithIDs;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

/**
 * Class that holds simple query definitions with no-argument.
 */
@GraphQLApi
@ApplicationScoped
public class ArrayAndListQueries {

    @Inject
    private TestDB testDB;

    public ArrayAndListQueries() {
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
}
