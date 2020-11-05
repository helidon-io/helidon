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
import io.helidon.microprofile.graphql.server.test.queries.DefaultValueQueries;
import io.helidon.microprofile.graphql.server.test.queries.OddNamedQueriesAndMutations;
import io.helidon.microprofile.graphql.server.test.types.DefaultValuePOJO;
import io.helidon.microprofile.tests.junit5.AddBean;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for default values.
 */
@AddBean(DefaultValueQueries.class)
@AddBean(OddNamedQueriesAndMutations.class)
@AddBean(TestDB.class)
public class DefaultValuesIT extends AbstractGraphQLIT {
    
    @Test
    @SuppressWarnings("unchecked")
    public void setOddNamedQueriesAndMutations() throws IOException {
        setupIndex(indexFileName, DefaultValuePOJO.class, OddNamedQueriesAndMutations.class);
        ExecutionContext executionContext =  createContext(defaultContext);

        Schema schema = executionContext.getSchema();
        assertThat(schema, is(notNullValue()));
        SchemaType query = schema.getTypeByName("Query");
        SchemaType mutation = schema.getTypeByName("Mutation");
        assertThat(query, is(notNullValue()));
        assertThat(query.fieldDefinitions().stream().filter(fd -> fd.name().equals("settlement")).count(), is(1L));
        assertThat(mutation.fieldDefinitions().stream().filter(fd -> fd.name().equals("getaway")).count(), is(1L));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDefaultValues() throws IOException {
        setupIndex(indexFileName, DefaultValuePOJO.class, DefaultValueQueries.class);
        ExecutionContext executionContext =  createContext(defaultContext);

        // test with both fields as default
        Map<String, Object> mapResults = getAndAssertResult(
                executionContext.execute("mutation { generateDefaultValuePOJO { id value } }"));
        assertThat(mapResults.size(), is(1));
        Map<String, Object> results = (Map<String, Object>) mapResults.get("generateDefaultValuePOJO");
        assertThat(results, is(notNullValue()));
        assertThat(results.get("id"), is("ID-1"));
        assertThat(results.get("value"), is(1000));

        // test with a field overridden
        mapResults = getAndAssertResult(
                executionContext.execute("mutation { generateDefaultValuePOJO(id: \"ID-123\") { id value } }"));
        assertThat(mapResults.size(), is(1));
        results = (Map<String, Object>) mapResults.get("generateDefaultValuePOJO");
        assertThat(results, is(notNullValue()));
        assertThat(results.get("id"), is("ID-123"));
        assertThat(results.get("value"), is(1000));

        mapResults = getAndAssertResult(executionContext.execute("query { echoDefaultValuePOJO { id value } }"));
        assertThat(mapResults.size(), is(1));
        results = (Map<String, Object>) mapResults.get("echoDefaultValuePOJO");
        assertThat(results, is(notNullValue()));
        assertThat(results.get("id"), is("ID-1"));
        assertThat(results.get("value"), is(1000));

        // check that the generated default value has the fields in correct order
        Schema schema = executionContext.getSchema();
        SchemaType query = schema.getTypeByName(Schema.QUERY);
        assertThat(query, is(notNullValue()));
        SchemaFieldDefinition fd = query.getFieldDefinitionByName("echoDefaultValuePOJO");
        assertThat(fd, is(notNullValue()));
        SchemaArgument argument = fd.arguments().get(0);
        assertThat(argument, is(notNullValue()));
        assertThat(argument.defaultValue(), is(
                "{ \"id\": \"ID-1\", \"value\": 1000, \"booleanValue\": true, \"dateObject\": \"1968-02-17\","
                 + " \"formattedIntWithDefault\": \"2 value\", \"offsetDateTime\": \"29 Jan 2020 at 09:45 in zone +0200\"}"));

        mapResults = getAndAssertResult(
                executionContext.execute("query { echoDefaultValuePOJO(input: {id: \"X123\" value: 1}) { id value } }"));
        assertThat(mapResults.size(), is(1));
        results = (Map<String, Object>) mapResults.get("echoDefaultValuePOJO");
        assertThat(results, is(notNullValue()));
        assertThat(results.get("id"), is("X123"));
        assertThat(results.get("value"), is(1));

        mapResults = getAndAssertResult(
                executionContext.execute("query { echoDefaultValuePOJO(input: {value: 1}) { id value } }"));
        assertThat(mapResults.size(), is(1));
        results = (Map<String, Object>) mapResults.get("echoDefaultValuePOJO");
        assertThat(results, is(notNullValue()));
        assertThat(results.get("id"), is("ID-123"));
        assertThat(results.get("value"), is(1));

        schema = executionContext.getSchema();
        SchemaType type = schema.getInputTypeByName("DefaultValuePOJOInput");
        assertReturnTypeDefaultValue(type, "id", "ID-123");
        assertReturnTypeDefaultValue(type, "booleanValue", "false");
        assertReturnTypeMandatory(type, "booleanValue", false);

        fd = getFieldDefinition(type, "value");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.defaultValue(), is("111222"));
    }
}
