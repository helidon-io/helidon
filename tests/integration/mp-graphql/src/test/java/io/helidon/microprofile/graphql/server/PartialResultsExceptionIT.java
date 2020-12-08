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

import javax.inject.Inject;

import io.helidon.graphql.server.InvocationHandler;
import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.exception.ExceptionQueries;
import io.helidon.microprofile.graphql.server.test.types.SimpleContact;
import io.helidon.microprofile.tests.junit5.AddBean;

import org.junit.jupiter.api.Test;

import static io.helidon.graphql.server.GraphQlConstants.DATA;
import static io.helidon.graphql.server.GraphQlConstants.ERRORS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for partial results with exception.
 */
@AddBean(ExceptionQueries.class)
@AddBean(TestDB.class)
class PartialResultsExceptionIT extends AbstractGraphQlCdiIT {

    @Inject
    protected PartialResultsExceptionIT(GraphQlCdiExtension graphQlCdiExtension) {
        super(graphQlCdiExtension);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSimplePartialResults() throws IOException {
        setupIndex(indexFileName, ExceptionQueries.class);

        InvocationHandler executionContext = createInvocationHandler();

        Map<String, Object> results = executionContext.execute("query { failAfterNResults(failAfter: 4) }");
        assertThat(results.size(), is(2));

        List<Map<String, Object>> listErrors = (List<Map<String, Object>>) results.get(ERRORS);
        assertThat(listErrors.size(), is(1));
        Map<String, Object> mapErrors = (Map<String, Object>) listErrors.get(0);

        // since this is a checked exception we should see message
        assertThat(mapErrors.get("message"), is("Partial results"));
        Map<String, Object> mapData = (Map<String, Object>) results.get(DATA);
        assertThat(mapData.size(), is(1));
        List<Integer> listIntegers = (List<Integer>) mapData.get("failAfterNResults");
        assertThat(listIntegers.size(), is(4));
    }

    @Test
    public void testComplexPartialResults() throws IOException {
        setupIndex(indexFileName, ExceptionQueries.class, SimpleContact.class);

        InvocationHandler executionContext = createInvocationHandler();

        Map<String, Object> results = executionContext.execute(
                "query { failAfterNContacts(failAfter: 4) { id name age } }");
        assertThat(results.size(), is(2));
    }
}
