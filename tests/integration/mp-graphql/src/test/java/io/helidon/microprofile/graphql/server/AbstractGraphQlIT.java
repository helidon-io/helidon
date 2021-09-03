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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.graphql.server.InvocationHandler;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.graphql.ConfigKey;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static io.helidon.graphql.server.GraphQlConstants.DATA;
import static io.helidon.graphql.server.GraphQlConstants.ERRORS;
import static io.helidon.graphql.server.GraphQlConstants.MESSAGE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class AbstractGraphQlIT extends AbstractGraphQLTest {
    private final Set<Class<?>> classes;

    protected String indexFileName = null;
    protected File indexFile = null;

    protected AbstractGraphQlIT(Set<Class<?>> classes) {
        this.classes = classes;
    }

    @BeforeEach
    public void setupTest() throws IOException {
        System.clearProperty(JandexUtils.PROP_INDEX_FILE);
        indexFileName = getTempIndexFile();
        indexFile = null;
    }

    @AfterEach
    public void teardownTest() {
        if (indexFile != null) {
            indexFile.delete();
        }
    }

    @SuppressWarnings("unchecked")
    protected void assertMessageValue(String query, String expectedMessage, boolean dataExpected) {
        InvocationHandler executionContext = createInvocationHandler();
        Map<String, Object> mapResults = executionContext.execute(query);
        if (dataExpected && mapResults.size() != 2) {
            System.out.println(JsonUtils.convertMapToJson(mapResults));
        }
        assertThat(mapResults.size(), is(dataExpected ? 2 : 1));
        List<Map<String, Object>> listErrors = (List<Map<String, Object>>) mapResults.get(ERRORS);
        assertThat(listErrors, is(notNullValue()));
        assertThat(listErrors.size(), is(1));
        Map<String, Object> mapErrors = listErrors.get(0);
        assertThat(mapErrors.get(MESSAGE), is(expectedMessage));

        assertThat(mapResults.containsKey(DATA), is(dataExpected));
    }

    protected void assertInterfaceResults() {
        Schema schema = createSchema();
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

    protected Schema createSchema() {
        return SchemaGenerator.builder()
                .classes(classes)
                .build()
                .generateSchema();
    }

    protected InvocationHandler createInvocationHandler() {
        InvocationHandler.Builder builder = InvocationHandler.builder();
        Config config = ConfigProvider.getConfig();

        config.getOptionalValue(ConfigKey.DEFAULT_ERROR_MESSAGE, String.class)
                .ifPresent(builder::defaultErrorMessage);

        config.getOptionalValue(ConfigKey.EXCEPTION_WHITE_LIST, String[].class)
                .stream()
                .flatMap(Arrays::stream)
                .forEach(builder::addWhitelistedException);

        config.getOptionalValue(ConfigKey.EXCEPTION_BLACK_LIST, String[].class)
                .stream()
                .flatMap(Arrays::stream)
                .forEach(builder::addBlacklistedException);

        return builder
                .schema(createSchema().generateGraphQLSchema())
                .build();
    }
}
