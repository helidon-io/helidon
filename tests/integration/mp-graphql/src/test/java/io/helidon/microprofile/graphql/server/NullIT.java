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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.queries.QueriesAndMutationsWithNulls;
import io.helidon.microprofile.graphql.server.test.types.NullPOJO;

import io.helidon.microprofile.tests.junit5.AddBean;

import org.junit.jupiter.api.Test;

/**
 * Tests for Nulls.
 */
@AddBean(QueriesAndMutationsWithNulls.class)
@AddBean(TestDB.class)
public class NullIT extends AbstractGraphQLIT {

    @Test
    @SuppressWarnings("unchecked")
    public void testNulls() throws IOException {
        setupIndex(indexFileName, NullPOJO.class, QueriesAndMutationsWithNulls.class);
        ExecutionContext executionContext =  new ExecutionContext(defaultContext);
        Schema schema = executionContext.getSchema();
        assertThat(schema, is(notNullValue()));

        // test primitives should be not null be default
        SchemaType type = schema.getTypeByName("NullPOJO");
        assertReturnTypeMandatory(type, "id", true);
        assertReturnTypeMandatory(type, "longValue", false);
        assertReturnTypeMandatory(type, "stringValue", true);
        assertReturnTypeMandatory(type, "testNullWithGet", true);
        assertReturnTypeMandatory(type, "listNonNullStrings", false);
        assertArrayReturnTypeMandatory(type, "listNonNullStrings", true);
        assertArrayReturnTypeMandatory(type, "listOfListOfNonNullStrings", true);
        assertReturnTypeMandatory(type, "listOfListOfNonNullStrings", false);
        assertReturnTypeMandatory(type, "listOfListOfNullStrings", false);
        assertArrayReturnTypeMandatory(type, "listOfListOfNullStrings", false);
        assertReturnTypeMandatory(type, "testNullWithSet", false);
        assertReturnTypeMandatory(type, "listNullStringsWhichIsMandatory", true);
        assertArrayReturnTypeMandatory(type, "listNullStringsWhichIsMandatory", false);
        assertReturnTypeMandatory(type, "testInputOnly", false);
        assertArrayReturnTypeMandatory(type, "testInputOnly", false);
        assertReturnTypeMandatory(type, "testOutputOnly", false);
        assertArrayReturnTypeMandatory(type, "testOutputOnly", true);

        SchemaType query = schema.getTypeByName("Query");
        assertReturnTypeMandatory(query, "method1NotNull", true);
        assertReturnTypeMandatory(query, "method2NotNull", true);
        assertReturnTypeMandatory(query, "method3NotNull", false);

        assertReturnTypeArgumentMandatory(query, "paramShouldBeNonMandatory", "value", false);
        assertReturnTypeArgumentMandatory(query, "paramShouldBeNonMandatory2", "value", false);
        assertReturnTypeArgumentMandatory(query, "paramShouldBeNonMandatory3", "value", false);

        SchemaType input = schema.getInputTypeByName("NullPOJOInput");
        assertReturnTypeMandatory(input, "nonNullForInput", true);
        assertReturnTypeMandatory(input, "testNullWithGet", false);
        assertReturnTypeMandatory(input, "testNullWithSet", true);
        assertReturnTypeMandatory(input, "listNonNullStrings", false);
        assertArrayReturnTypeMandatory(input, "listNonNullStrings", true);

        assertArrayReturnTypeMandatory(input, "listOfListOfNonNullStrings", true);

        assertReturnTypeMandatory(input, "testInputOnly", false);
        assertArrayReturnTypeMandatory(input, "testInputOnly", true);

        assertReturnTypeMandatory(input, "testOutputOnly", false);
        assertArrayReturnTypeMandatory(input, "testOutputOnly", false);

        Map<String, Object> mapResults = getAndAssertResult(executionContext.execute("mutation { returnNullValues { longValue stringValue } }"));
        assertThat(mapResults, is(notNullValue()));
        Map<String, Object> mapResults2 = (Map<String, Object>) mapResults.get("returnNullValues");
        assertThat(mapResults2.size(), is(2));
        assertThat(mapResults2.get("longValue"), is(nullValue()));

        mapResults = getAndAssertResult(executionContext.execute("mutation { echoNullValue }"));
        assertThat(mapResults, is(notNullValue()));
        assertThat(mapResults.get("echoNullValue"), is(nullValue()));

        mapResults = getAndAssertResult(executionContext.execute("mutation { echoNullDateValue }"));
        assertThat(mapResults, is(notNullValue()));
        assertThat(mapResults.get("echoNullDateValue"), is(nullValue()));
    }

}
