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

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.enums.EnumTestWithEnumName;
import io.helidon.microprofile.graphql.server.test.queries.SimpleQueriesWithSource;
import io.helidon.microprofile.graphql.server.test.types.SimpleContact;

import io.helidon.microprofile.tests.junit5.AddBean;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for Source annotation.
 */
@AddBean(SimpleQueriesWithSource.class)
@AddBean(TestDB.class)
public class SourceIT extends AbstractGraphQLIT {

    @Test
    @SuppressWarnings("unchecked")
    public void testSimpleQueriesWithSource() throws IOException {
        setupIndex(indexFileName, SimpleQueriesWithSource.class, SimpleContact.class);
        ExecutionContext executionContext =  new ExecutionContext(defaultContext);

        // since there is a @Source annotation in SimpleQueriesWithSource, then this should add a field
        // idAndName to the SimpleContact type
        Map<String, Object> mapResults = getAndAssertResult(executionContext.execute("query { findContact { id idAndName } }"));

        assertThat(mapResults.size(), is(1));
        Map<String, Object> mapResults2 = (Map<String, Object>) mapResults.get("findContact");
        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.get("id"), is(notNullValue()));
        assertThat(mapResults2.get("idAndName"), is(notNullValue()));

        // test the query at the top level
        SimpleContact contact1 = new SimpleContact("c1", "Contact 1", 50, EnumTestWithEnumName.XL);

        String json = "contact: " + getContactAsQueryInput(contact1);

        mapResults = getAndAssertResult(executionContext.execute("query { currentJob (" + json + ") }"));
        assertThat(mapResults.size(), is(1));
        String currentJob = (String) mapResults.get("currentJob");
        assertThat(currentJob, is(notNullValue()));

        // test the query from the object
        mapResults = getAndAssertResult(executionContext.execute("query { findContact { id idAndName currentJob } }"));
        assertThat(mapResults.size(), is(1));
        mapResults2 = (Map<String, Object>) mapResults.get("findContact");
        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.get("id"), is(notNullValue()));
        assertThat(mapResults2.get("idAndName"), is(notNullValue()));
        assertThat(mapResults2.get("currentJob"), is(notNullValue()));

        // test the query from the object
        mapResults = getAndAssertResult(executionContext.execute("query { findContact { id lastNAddress(count: 1) { city } } }"));
        assertThat(mapResults.size(), is(1));
        mapResults2 = (Map<String, Object>) mapResults.get("findContact");
        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.get("id"), is(notNullValue()));
        assertThat(mapResults2.get("lastNAddress"), is(notNullValue()));
    }

}
