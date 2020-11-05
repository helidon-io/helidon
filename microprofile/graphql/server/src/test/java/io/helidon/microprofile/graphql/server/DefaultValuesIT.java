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
import io.helidon.microprofile.graphql.server.test.queries.DefaultValueQueries;
import io.helidon.microprofile.graphql.server.test.queries.DescriptionQueries;
import io.helidon.microprofile.graphql.server.test.queries.OddNamedQueriesAndMutations;
import io.helidon.microprofile.graphql.server.test.types.DefaultValuePOJO;
import io.helidon.microprofile.graphql.server.test.types.DescriptionType;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Tests for default values.
 */
@ExtendWith(WeldJunit5Extension.class)
public class DefaultValuesIT extends AbstractGraphQLIT {

    @WeldSetup
    private final WeldInitiator weld = WeldInitiator.of(WeldInitiator.createWeld()
                                                                .addBeanClass(DefaultValuePOJO.class)
                                                                .addBeanClass(DefaultValueQueries.class)
                                                                .addBeanClass(OddNamedQueriesAndMutations.class)
                                                                .addBeanClass(TestDB.class)
                                                                .addExtension(new GraphQLCdiExtension()));


    @Test
    @SuppressWarnings("unchecked")
    public void setOddNamedQueriesAndMutations() throws IOException {
        setupIndex(indexFileName, DefaultValuePOJO.class, OddNamedQueriesAndMutations.class);
        ExecutionContext executionContext =  new ExecutionContext(defaultContext);

        Schema schema = executionContext.getSchema();
        assertThat(schema, is(notNullValue()));
        SchemaType query = schema.getTypeByName("Query");
        SchemaType mutation = schema.getTypeByName("Mutation");
        assertThat(query, is(notNullValue()));
        assertThat(query.getFieldDefinitions().stream().filter(fd -> fd.getName().equals("settlement")).count(), is(1L));
        assertThat(mutation.getFieldDefinitions().stream().filter(fd -> fd.getName().equals("getaway")).count(), is(1L));
    }


    @Test
    @SuppressWarnings("unchecked")
    public void testDefaultValues() throws IOException {
        setupIndex(indexFileName, DefaultValuePOJO.class, DefaultValueQueries.class);
        ExecutionContext executionContext =  new ExecutionContext(defaultContext);

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

        Schema schema = executionContext.getSchema();
        SchemaType type = schema.getInputTypeByName("DefaultValuePOJOInput");
        assertReturnTypeDefaultValue(type, "id", "ID-123");
        assertReturnTypeDefaultValue(type, "booleanValue", "false");
        assertReturnTypeMandatory(type, "booleanValue", false);

        SchemaFieldDefinition fd = getFieldDefinition(type, "value");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.getDefaultValue(), is("111222"));
    }
}
