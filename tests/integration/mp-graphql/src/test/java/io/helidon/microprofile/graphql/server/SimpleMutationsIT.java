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
import java.util.Map;

import javax.inject.Inject;

import io.helidon.graphql.server.InvocationHandler;
import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.mutations.SimpleMutations;
import io.helidon.microprofile.tests.junit5.AddBean;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for simple mutations.
 */
@AddBean(SimpleMutations.class)
@AddBean(TestDB.class)
class SimpleMutationsIT extends AbstractGraphQlCdiIT {

    @Inject
    SimpleMutationsIT(GraphQlCdiExtension graphQlCdiExtension) {
        super(graphQlCdiExtension);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSimpleMutations() throws IOException {
        setupIndex(indexFileName, SimpleMutations.class);
        InvocationHandler executionContext = createInvocationHandler();

        Map<String, Object> mapResults = getAndAssertResult(
                executionContext.execute("mutation { createNewContact { id name age } }"));
        assertThat(mapResults.size(), is(1));
        Map<String, Object> mapResults2 = (Map<String, Object>) mapResults.get("createNewContact");

        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.get("id"), is(notNullValue()));
        assertThat(mapResults2.get("name"), is(notNullValue()));
        assertThat(((String) mapResults2.get("name")).startsWith("Name"), is(true));
        assertThat(mapResults2.get("age"), is(notNullValue()));

        mapResults = getAndAssertResult(
                executionContext.execute("mutation { createContactWithName(name: \"tim\") { id name age } }"));
        assertThat(mapResults.size(), is(1));
        mapResults2 = (Map<String, Object>) mapResults.get("createContactWithName");

        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.get("id"), is(notNullValue()));
        assertThat(mapResults2.get("name"), is("tim"));
        assertThat(mapResults2.get("age"), is(notNullValue()));

        mapResults = getAndAssertResult(executionContext.execute("mutation { echoStringValue(value: \"echo\") }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("echoStringValue"), is("echo"));

        mapResults = getAndAssertResult(executionContext.execute(
                "mutation { testStringArrays(places: [\"place1\", \"place2\", \"place3\"]) }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("testStringArrays"), is("place1place2place3"));

       mapResults = getAndAssertResult(
                executionContext.execute("mutation { createAndReturnNewContact(newContact: { name: \"tim\", age: 22, id: \"1\", tShirtSize: XL } ) { id name age tShirtSize } }"));
        assertThat(mapResults.size(), is(1));
        mapResults2 = (Map<String, Object>) mapResults.get("createAndReturnNewContact");
        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.get("name"), is("tim"));
        assertThat(mapResults2.get("age"), is(22));
        assertThat(mapResults2.get("id"), is("1"));
        assertThat(mapResults2.get("tShirtSize"), is("XL"));

    }
}
