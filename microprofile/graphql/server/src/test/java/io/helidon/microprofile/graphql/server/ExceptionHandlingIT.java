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

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import io.helidon.microprofile.graphql.server.test.exception.ExceptionQueries;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Integration tests for testing exception handing in {@link SchemaGeneratorTest}.
 */
@SuppressWarnings("unchecked")
public class ExceptionHandlingIT
        extends AbstractGraphQLIT {

    @Test
    public void testAllDefaultsForConfig() throws IOException {
        setupConfig(null);

        setupIndex(indexFileName);
        ExecutionContext<DefaultContext> executionContext = new ExecutionContext<>(defaultContext);
        assertThat(executionContext, is(notNullValue()));
        assertThat(executionContext.getDefaultErrorMessage(), is("Server Error"));
        assertThat(executionContext.getExceptionBlacklist().size(), is(0));
        assertThat(executionContext.getExceptionWhitelist().size(), is(0));
    }

    @Test
    public void testDifferentMessage() throws IOException {
        Config config = setupConfig("config/config1.properties");
        assertThat(config.get("mp").get("graphql").get("defaultErrorMessage").asString().get(), is("new message"));

        setupIndex(indexFileName);

        ExecutionContext<DefaultContext> executionContext = new ExecutionContext<>(defaultContext);
        assertThat(executionContext.getDefaultErrorMessage(), is("new message"));
    }

    @Test
    public void testBlackListAndWhiteList() throws IOException {
        setupConfig("config/config2.properties");

        setupIndex(indexFileName);

        ExecutionContext<DefaultContext> executionContext = new ExecutionContext<>(defaultContext);
        assertThat(executionContext.getDefaultErrorMessage(), is("Server Error"));
        assertThat(executionContext.getExceptionBlacklist().size(), is(2));
        assertThat(executionContext.getExceptionWhitelist().size(), is(1));
        assertThat(executionContext.getExceptionBlacklist().contains("java.io.IOException"), is(true));
        assertThat(executionContext.getExceptionBlacklist().contains("java.util.concurrent.TimeoutException"), is(true));
        assertThat(executionContext.getExceptionWhitelist()
                           .contains("org.eclipse.microprofile.graphql.tck.apps.superhero.api.WeaknessNotFoundException"),
                   is(true));
    }

    @Test
    public void testEmptyErrorPayloads() throws IOException {
        setupIndex(indexFileName, ExceptionQueries.class);
        ExecutionContext<DefaultContext> executionContext = new ExecutionContext<>(new DefaultContext());

        Map<String, Object> errorMap = executionContext.newErrorPayload();
        assertPayload(errorMap);

        List<Map<String, Object>> listMessages = (List<Map<String, Object>>) errorMap.get(ExecutionContext.ERRORS);
        assertThat(listMessages, is(notNullValue()));
        assertThat(listMessages.size(), is(0));
    }

    @Test
    public void testErrorPayLoadWithMessages() throws IOException {
        setupIndex(indexFileName, ExceptionQueries.class);
        ExecutionContext<DefaultContext> executionContext = new ExecutionContext<>(new DefaultContext());

        Map<String, Object> errorMap = executionContext.newErrorPayload();
        assertPayload(errorMap);

        List<Map<String, Object>> listMessages = (List<Map<String, Object>>) errorMap.get(ExecutionContext.ERRORS);
        assertThat(listMessages, is(notNullValue()));
        assertThat(listMessages.size(), is(0));

        executionContext.addErrorPayload(errorMap, "error message 1");
        executionContext.addErrorPayload(errorMap, "error message 2");
        assertThat(listMessages.size(), is(2));

        for (Map<String, Object> mapMessage : listMessages) {
            assertThat(mapMessage.get(ExecutionContext.MESSAGE), is(notNullValue()));
        }
    }

    @Test
    public void testErrorPayLoadWithMessagesAndLocations() throws IOException {
        setupIndex(indexFileName, ExceptionQueries.class);
        ExecutionContext<DefaultContext> executionContext = new ExecutionContext<>(new DefaultContext());

        Map<String, Object> errorMap = executionContext.newErrorPayload();
        assertPayload(errorMap);

        List<Map<String, Object>> listMessages = (List<Map<String, Object>>) errorMap.get(ExecutionContext.ERRORS);
        assertThat(listMessages, is(notNullValue()));
        assertThat(listMessages.size(), is(0));

        executionContext.addErrorPayload(errorMap, "error message 1", 1, 10, ExecutionContext.EMPTY_MAP);
        executionContext.addErrorPayload(errorMap, "error message 2", 1, 10, ExecutionContext.EMPTY_MAP);
        assertThat(listMessages.size(), is(2));

        for (Map<String, Object> mapMessage : listMessages) {
            assertThat(mapMessage.get(ExecutionContext.MESSAGE), is(notNullValue()));
            List<Map<String, Object>> listLocations = (List<Map<String, Object>>) mapMessage.get(ExecutionContext.LOCATIONS);
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
        ExecutionContext<DefaultContext> executionContext = new ExecutionContext<>(new DefaultContext());

        Map<String, Object> errorMap = executionContext.newErrorPayload();
        assertPayload(errorMap);

        List<Map<String, Object>> listMessages = (List<Map<String, Object>>) errorMap.get(ExecutionContext.ERRORS);
        assertThat(listMessages, is(notNullValue()));
        assertThat(listMessages.size(), is(0));

        executionContext.addErrorPayload(errorMap, "error message 1", 1, 10, Map.of("key", "value"));
        executionContext.addErrorPayload(errorMap, "error message 2", 1, 10, Map.of("key", "value"));
        assertThat(listMessages.size(), is(2));

        for (Map<String, Object> mapMessage : listMessages) {
            assertThat(mapMessage.get(ExecutionContext.MESSAGE), is(notNullValue()));
            List<Map<String, Object>> listLocations = (List<Map<String, Object>>) mapMessage.get(ExecutionContext.LOCATIONS);
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
    public void testExceptions() throws IOException {
        setupIndex(indexFileName, ExceptionQueries.class);
        ExecutionContext<DefaultContext> executionContext = new ExecutionContext<>(new DefaultContext());
        Map<String, Object> mapResults = executionContext.execute("query { checkedQuery1(throwException: true) }");
        assertThat(mapResults.size(), is(2));
        List<Map<String, Object>> listErrors = (List<Map<String, Object>>) mapResults.get(ExecutionContext.ERRORS);
        assertThat(listErrors, is(notNullValue()));
        assertThat(listErrors.size(), is(1));

        System.out.println(JsonUtils.convertMapToJson(mapResults));
    }

    private void assertPayload(Map<String, Object> errorMap) {
        assertThat(errorMap, is(Matchers.notNullValue()));
        assertThat(errorMap.size(), is(2));
        assertThat(errorMap.get(ExecutionContext.DATA), is(nullValue()));
    }

    protected Config setupConfig(String propertiesFile) {
        Config config = propertiesFile == null ? Config.create() : Config.create(ConfigSources.classpath(propertiesFile));
        ConfigProviderResolver.instance()
                .registerConfig((org.eclipse.microprofile.config.Config) config, Thread.currentThread().getContextClassLoader());

        return config;
    }

}
