/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import java.util.Map;

import javax.inject.Inject;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.helidon.graphql.server.InvocationHandler;
import io.helidon.microprofile.graphql.server.test.queries.DataFetchingEnvironmentQueriesAndMutations;
import io.helidon.microprofile.tests.junit5.AddBean;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link graphql.schema.DataFetchingEnvironment} injection.
 */
@AddBean(DataFetchingEnvironmentQueriesAndMutations.class)
class DataFetchingEnvironmentIT extends AbstractGraphQlCdiIT {

    @Inject
    DataFetchingEnvironmentIT(GraphQlCdiExtension graphQlCdiExtension) {
        super(graphQlCdiExtension);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testWithNoArgs() throws Exception {
        setupIndex(indexFileName, DataFetchingEnvironmentQueriesAndMutations.class);
        InvocationHandler executionContext = createInvocationHandler();
        String query = "query { testNoArgs }";
        Map<String, Object> mapResults = getAndAssertResult(executionContext.execute(query));
        assertThat(mapResults, is(notNullValue()));
        String results = (String) mapResults.get("testNoArgs");
        assertThat(results, is("testNoArgs"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testWithArgs() throws Exception {
        setupIndex(indexFileName, DataFetchingEnvironmentQueriesAndMutations.class);
        InvocationHandler executionContext = createInvocationHandler();

        String query = "query { testWithArgs(name: \"Tim\") }";
        Map<String, Object> mapResults = getAndAssertResult(executionContext.execute(query));
        assertThat(mapResults, is(notNullValue()));
        String results = (String) mapResults.get("testWithArgs");
        assertThat(results, is("Tim" + "testWithArgs"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testWithArgs2() throws Exception {
        setupIndex(indexFileName, DataFetchingEnvironmentQueriesAndMutations.class);
        InvocationHandler executionContext = createInvocationHandler();

        String query = "query { testWithArgs2(name1: \"Tim\", name2: \"Tim\") }";
        Map<String, Object> mapResults = getAndAssertResult(executionContext.execute(query));
        assertThat(mapResults, is(notNullValue()));
        String results = (String) mapResults.get("testWithArgs2");
        assertThat(results, is("TimTim" + "testWithArgs2" ));
    }
}
