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

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.beans.IntrospectionException;
import java.io.File;
import java.io.IOException;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Common functionality for integration tests.
 */
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

    
    protected void assertInterfaceResults() throws IntrospectionException, ClassNotFoundException {
        SchemaGenerator schemaGenerator = new SchemaGenerator(defaultContext);
        Schema schema = schemaGenerator.generateSchema();
        assertThat(schema, CoreMatchers.is(notNullValue()));
        schema.getTypes().forEach(t -> System.out.println(t.getName()));
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
