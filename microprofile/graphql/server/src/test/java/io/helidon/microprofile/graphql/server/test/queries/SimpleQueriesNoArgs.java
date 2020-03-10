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
import java.util.Collection;

import javax.inject.Inject;
import javax.json.bind.annotation.JsonbProperty;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.enums.EnumTestWithEnumName;
import io.helidon.microprofile.graphql.server.test.types.Person;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

/**
 * Class that holds simple query definitions with no-argument.
 */
@GraphQLApi
public class SimpleQueriesNoArgs {

  //  @Inject
    private TestDB testDB = new TestDB();

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
        // TODO: Need to figure out why CDI not working for
        // return testDB.getAllPeople();
    }

    @Query
    public LocalDate returnCurrentDate() {
        return  LocalDate.now();
    }

    @Query
    @Name("returnMediumSize")
    public EnumTestWithEnumName getEnumMedium() {
        return EnumTestWithEnumName.M;
    }
}
