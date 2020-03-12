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

import java.util.ArrayList;
import java.util.Collection;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.types.MultiLevelListsAndArrays;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

/**
 * Class that holds array, {@link java.util.Collection} and {@link java.util.Map} queries.
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
        return testDB.getMultiLevelListsAndArrays();
    }

    @Query("returnListOfStringArrays")
    public Collection<String[]> getListOfStringArrays() {
        ArrayList<String[]> arrayList = new ArrayList<>();
        arrayList.add(new String[] { "one", "two"});
        arrayList.add(new String[] { "three", "four", "five"});
        return arrayList;
    }
}
