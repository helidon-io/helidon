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

import java.beans.IntrospectionException;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Common functionality for integration tests.
 */
@HelidonTest
@DisableDiscovery
@AddExtension(GraphQLCdiExtension.class)
public abstract class AbstractGraphQLIT extends AbstractGraphQLTest {

    protected String indexFileName = null;
    protected File indexFile = null;
    protected Context defaultContext;

    @BeforeEach
    public void setupTest() throws IOException {
        System.clearProperty(JandexUtils.PROP_INDEX_FILE);
        indexFileName = getTempIndexFile();
        indexFile = null;
        defaultContext = ExecutionContext.getDefaultContext();
    }

    @AfterEach
    public void teardownTest() {
        if (indexFile != null) {
            indexFile.delete();
        }
    }

    @SuppressWarnings("unchecked")
    protected void assertMessageValue(String query, String expectedMessage, boolean dataExpected) {
        ExecutionContext executionContext = new ExecutionContext(DefaultContext.create());
        Map<String, Object> mapResults = executionContext.execute(query);
        if (dataExpected && mapResults.size() != 2) {
            System.out.println(JsonUtils.convertMapToJson(mapResults));
        }
        assertThat(mapResults.size(), is(dataExpected ? 2 : 1));
        List<Map<String, Object>> listErrors = (List<Map<String, Object>>) mapResults.get(ExecutionContext.ERRORS);
        assertThat(listErrors, is(notNullValue()));
        assertThat(listErrors.size(), is(1));
        Map<String, Object> mapErrors = listErrors.get(0);
        assertThat(mapErrors.get(ExecutionContext.MESSAGE), is(expectedMessage));

        assertThat(mapResults.containsKey(ExecutionContext.DATA), is(dataExpected));
    }

    protected void assertInterfaceResults() throws IntrospectionException, ClassNotFoundException {
        SchemaGenerator schemaGenerator = new SchemaGenerator(defaultContext);
        Schema schema = schemaGenerator.generateSchema();
        assertThat(schema, CoreMatchers.is(notNullValue()));
        schema.getTypes().forEach(t -> System.out.println(t.name()));
        assertThat(schema.getTypes().size(), CoreMatchers.is(6));
        assertThat(schema.getTypeByName("Vehicle"), CoreMatchers.is(notNullValue()));
        assertThat(schema.getTypeByName("Car"), CoreMatchers.is(notNullValue()));
        assertThat(schema.getTypeByName("Motorbike"), CoreMatchers.is(notNullValue()));
        assertThat(schema.getTypeByName("Incident"), CoreMatchers.is(notNullValue()));
        assertThat(schema.getTypeByName("Query"), CoreMatchers.is(notNullValue()));
        assertThat(schema.getTypeByName("Mutation"), CoreMatchers.is(notNullValue()));
        generateGraphQLSchema(schema);
    }
}
