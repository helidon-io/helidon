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

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.exception.ExceptionQueries;
import io.helidon.microprofile.graphql.server.test.types.SimpleContact;
import io.helidon.microprofile.tests.junit5.AddBean;

import org.hamcrest.Matchers;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for testing exception handing in {@link SchemaGeneratorTest}.
 */
@AddBean(ExceptionQueries.class)
@AddBean(SimpleContact.class)
@AddBean(TestDB.class)
@SuppressWarnings("unchecked")
public class ExceptionHandlingIT extends AbstractGraphQLIT {

    @Test
    public void testEmptyErrorPayloads() throws IOException {
        setupIndex(indexFileName, ExceptionQueries.class);
        ExecutionContext executionContext = new ExecutionContext(new DefaultContext());

        Map<String, Object> errorMap = executionContext.newErrorPayload();
        assertPayload(errorMap);

        List<Map<String, Object>> listMessages = (List<Map<String, Object>>) errorMap.get(ExecutionContext.ERRORS);
        assertThat(listMessages, is(notNullValue()));
        assertThat(listMessages.size(), is(0));
    }

    @Test
    public void testErrorPayLoadWithMessages() throws IOException {
        setupIndex(indexFileName, ExceptionQueries.class);
        ExecutionContext executionContext = new ExecutionContext(new DefaultContext());

        Map<String, Object> errorMap = executionContext.newErrorPayload();
        assertPayload(errorMap);

        List<Map<String, Object>> listMessages = (List<Map<String, Object>>) errorMap.get(ExecutionContext.ERRORS);
        assertThat(listMessages, is(notNullValue()));
        assertThat(listMessages.size(), is(0));

        executionContext.addErrorPayload(errorMap, "error message 1", (String) null);
        executionContext.addErrorPayload(errorMap, "error message 2", (String) null);
        assertThat(listMessages.size(), is(2));

        for (Map<String, Object> mapMessage : listMessages) {
            assertThat(mapMessage.get(ExecutionContext.MESSAGE), is(notNullValue()));
        }
    }

    @Test
    public void testErrorPayLoadWithMessagesAndLocations() throws IOException {
        setupIndex(indexFileName, ExceptionQueries.class);
        ExecutionContext executionContext = new ExecutionContext(new DefaultContext());

        Map<String, Object> errorMap = executionContext.newErrorPayload();
        assertPayload(errorMap);

        List<Map<String, Object>> listMessages = (List<Map<String, Object>>) errorMap.get(ExecutionContext.ERRORS);
        assertThat(listMessages, is(notNullValue()));
        assertThat(listMessages.size(), is(0));

        executionContext.addErrorPayload(errorMap, "error message 1", 1, 10, ExecutionContext.EMPTY_MAP, "/path");
        executionContext.addErrorPayload(errorMap, "error message 2", 1, 10, ExecutionContext.EMPTY_MAP, "/path");
        assertThat(listMessages.size(), is(2));

        for (Map<String, Object> mapMessage : listMessages) {
            assertThat(mapMessage.get(ExecutionContext.MESSAGE), is(notNullValue()));
            List<Map<String, Object>> listLocations = (List<Map<String, Object>>) mapMessage.get(ExecutionContext.LOCATIONS);
            List<String> listPaths = (List<String>) mapMessage.get(ExecutionContext.PATH);
            assertThat(listPaths.size(), is(1));
            assertThat(listPaths.get(0), is("/path"));
            for (Map<String, Object> mapLocations : listLocations) {
                assertThat(mapLocations, is(notNullValue()));
                assertThat(mapLocations.get(ExecutionContext.LINE), is(1));
                assertThat(mapLocations.get(ExecutionContext.COLUMN), is(10));
            }

        }

    }

    @Test
    public void testErrorPayLoadWithMessagesLocationsAndExtensions() throws IOException {
        setupIndex(indexFileName, ExceptionQueries.class);
        ExecutionContext executionContext = new ExecutionContext(new DefaultContext());

        Map<String, Object> errorMap = executionContext.newErrorPayload();
        assertPayload(errorMap);

        List<Map<String, Object>> listMessages = (List<Map<String, Object>>) errorMap.get(ExecutionContext.ERRORS);
        assertThat(listMessages, is(notNullValue()));
        assertThat(listMessages.size(), is(0));

        executionContext.addErrorPayload(errorMap, "error message 1", 1, 10, Map.of("key", "value"), "/path");
        executionContext.addErrorPayload(errorMap, "error message 2", 1, 10, Map.of("key", "value"), "/path");
        assertThat(listMessages.size(), is(2));

        for (Map<String, Object> mapMessage : listMessages) {
            assertThat(mapMessage.get(ExecutionContext.MESSAGE), is(notNullValue()));
            List<Map<String, Object>> listLocations = (List<Map<String, Object>>) mapMessage.get(ExecutionContext.LOCATIONS);
            List<String> listPaths = (List<String>) mapMessage.get(ExecutionContext.PATH);
            assertThat(listPaths.size(), is(1));
            assertThat(listPaths.get(0), is("/path"));
            for (Map<String, Object> mapLocations : listLocations) {
                assertThat(mapLocations, is(notNullValue()));
                assertThat(mapLocations.get(ExecutionContext.LINE), is(1));
                assertThat(mapLocations.get(ExecutionContext.COLUMN), is(10));
            }
            Map<String, Object> mapExtensions = (Map<String, Object>) mapMessage.get(ExecutionContext.EXTENSIONS);
            assertThat(mapExtensions, is(notNullValue()));
            assertThat(mapExtensions.get("key"), is("value"));
        }
    }

    @Test
    public void testUnknownField() throws IOException {
        setupIndex(indexFileName, ExceptionQueries.class, SimpleContact.class);
        assertMessageValue("query { defaultContact { invalidField } }",
                           "Validation error of type FieldUndefined: Field 'invalidField' in" +
                                   " type 'SimpleContact' is undefined @ 'defaultContact/invalidField'", true);
    }

    @Test
    public void testError() throws IOException {
        setupIndex(indexFileName, ExceptionQueries.class, SimpleContact.class);
        ExecutionContext executionContext = new ExecutionContext(defaultContext);
        Assertions.assertThrows(Error.class, () -> executionContext.execute("query { throwOOME }"));
    }

    private void assertPayload(Map<String, Object> errorMap) {
        assertThat(errorMap, is(Matchers.notNullValue()));
        assertThat(errorMap.size(), is(2));
        assertThat(errorMap.get(ExecutionContext.DATA), is(nullValue()));
    }
}
