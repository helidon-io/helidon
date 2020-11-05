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
import java.util.List;
import java.util.Map;

import io.helidon.microprofile.graphql.server.test.queries.MapQueries;
import io.helidon.microprofile.graphql.server.test.types.SimpleContact;
import io.helidon.microprofile.graphql.server.test.types.TypeWithMap;
import io.helidon.microprofile.tests.junit5.AddBean;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for handling a {@link Map}.
 */
@AddBean(TypeWithMap.class)
@AddBean(MapQueries.class)
@AddBean(SimpleContact.class)
public class MapIT extends AbstractGraphQLIT {

    @Test
    @SuppressWarnings("unchecked")
    public void testMap() throws IOException {
        setupIndex(indexFileName, TypeWithMap.class, MapQueries.class, SimpleContact.class);
        ExecutionContext executionContext = createContext(defaultContext);

        Map<String, Object> mapResults = getAndAssertResult(
                executionContext.execute("query { query1 { id mapValues mapContacts { id name } } }"));
        assertThat(mapResults.size(), is(1));

        Map<String, Object> mapResults2 = (Map<String, Object>) mapResults.get("query1");
        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.size(), is(3));
        assertThat(mapResults2.get("id"), is("id1"));
        List<String> listString = (List<String>) mapResults2.get("mapValues");
        assertThat(listString.size(), is(2));
        assertThat(listString.get(0), is("one"));
        assertThat(listString.get(1), is("two"));

        List<Map<String, Object>> listMapResults3 = (List<Map<String, Object>>) mapResults2.get("mapContacts");
        assertThat(listMapResults3.size(), is(2));

        Map<String, Object> mapContact1 = listMapResults3.get(0);
        Map<String, Object> mapContact2 = listMapResults3.get(1);
        assertThat(mapContact1, is(notNullValue()));
        assertThat(mapContact1.get("id"), is("c1"));
        assertThat(mapContact1.get("name"), is("Tim"));
        assertThat(mapContact2, is(notNullValue()));
        assertThat(mapContact2.get("id"), is("c2"));
        assertThat(mapContact2.get("name"), is("James"));
    }

    @Test
    public void testMapAsInput() throws IOException {
        setupIndex(indexFileName, TypeWithMap.class, MapQueries.class, SimpleContact.class);
        ExecutionContext executionContext = createContext(defaultContext);
        String input = "{"
                + "id: \"id-1\" "
                + "mapValues: [ \"a\" \"b\" ] "
                + "mapContacts: [{ id: \"c1\" age: 10 name: \"Tim\" tShirtSize: XL } "
                + "              { id: \"c3\" age: 10 name: \"Tim\" tShirtSize: XL } ] "
                + "}";
        Map<String, Object> mapResults = executionContext.execute(
                "query { query2(value: " + input + ") { id mapValues mapContacts { id } } }");
        assertThat(mapResults.size(), is(2));
        assertThat(mapResults.get("errors"), is(notNullValue()));
    }

    @Test
    public void testMapAsInput2() throws IOException {
        setupIndex(indexFileName, TypeWithMap.class, MapQueries.class, SimpleContact.class);
        ExecutionContext executionContext = createContext(defaultContext);
        Map<String, Object> mapResults = executionContext.execute("query { query3 (value: [ \"a\" \"b\" ]) }");
        assertThat(mapResults.size(), is(2));
        assertThat(mapResults.get("errors"), is(notNullValue()));
    }
}
