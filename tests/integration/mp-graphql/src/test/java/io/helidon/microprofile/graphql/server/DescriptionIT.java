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

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.queries.DescriptionQueries;
import io.helidon.microprofile.graphql.server.test.types.DescriptionType;

import io.helidon.microprofile.tests.junit5.AddBean;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for descriptions.
 */
@AddBean(DescriptionQueries.class)
@AddBean(TestDB.class)
public class DescriptionIT extends AbstractGraphQLIT {
    
    @Test
    public void testDescriptions() throws IOException {
        setupIndex(indexFileName, DescriptionType.class, DescriptionQueries.class);
        ExecutionContext executionContext =  new ExecutionContext(defaultContext);

        Schema schema = executionContext.getSchema();
        assertThat(schema, is(notNullValue()));
        SchemaType type = schema.getTypeByName("DescriptionType");
        assertThat(type, is(notNullValue()));
        type.fieldDefinitions().forEach(fd -> {
            if (fd.name().equals("id")) {
                assertThat(fd.description(), is("this is the description"));
            }
            if (fd.name().equals("value")) {
                assertThat(fd.description(), is("description of value"));
            }
            if (fd.name().equals("longValue1")) {
                // no description so include the format
                assertThat(fd.description(), is(nullValue()));
                assertThat(fd.format()[0], is("L-########"));
            }
            if (fd.name().equals("longValue2")) {
                // both description and formatting
                assertThat(fd.description(), is("Description"));
            }
        });

        SchemaInputType inputType = schema.getInputTypeByName("DescriptionTypeInput");
        assertThat(inputType, is(notNullValue()));
        inputType.fieldDefinitions().forEach(fd -> {
            if (fd.name().equals("value")) {
                assertThat(fd.description(), is("description on set for input"));
            }
        });

        SchemaType query = schema.getTypeByName("Query");
        assertThat(query, is(notNullValue()));
        SchemaFieldDefinition fd = getFieldDefinition(query, "descriptionOnParam");
        assertThat(fd, (is(notNullValue())));

        fd.arguments().forEach(a -> {
            if (a.argumentName().equals("param1")) {
                assertThat(a.description(), is("Description for param1"));
            }
        });
    }

}
