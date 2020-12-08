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

import java.util.Map;
import java.util.TreeMap;

import io.helidon.microprofile.graphql.server.test.enums.EnumTestWithEnumName;
import io.helidon.microprofile.graphql.server.test.types.SimpleContact;
import io.helidon.microprofile.graphql.server.test.types.TypeWithMap;

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

/**
 * Class that holds queries that have {@link Map} as return values or types with {@link Map}s.
 */
@GraphQLApi
@ApplicationScoped
public class MapQueries {

    @Query("query1")
    public TypeWithMap generateTypeWithMap() {
        Map<Integer, String> mapValues = new TreeMap<>();
        mapValues.put(1, "one");
        mapValues.put(2, "two");

        Map<String, SimpleContact> mapContacts = new TreeMap<>();

        SimpleContact contact1 = new SimpleContact("c1", "Tim", 55, EnumTestWithEnumName.XL);
        SimpleContact contact2 = new SimpleContact("c2", "James", 75, EnumTestWithEnumName.XXL);
        mapContacts.put(contact1.getId(), contact1);
        mapContacts.put(contact2.getId(), contact2);
        return new TypeWithMap("id1", mapValues, mapContacts);
    }

    @Query("query2")
    public TypeWithMap echoTypeWithMap(@Name("value") TypeWithMap value) {
        return value;
    }

    @Query("query3")
    public Map<String, String> thisShouldRaiseError(@Name("value") Map<String, String> value) {
        return value;
    }
}
