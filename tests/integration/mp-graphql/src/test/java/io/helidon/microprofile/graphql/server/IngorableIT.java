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

import javax.inject.Inject;

import io.helidon.graphql.server.InvocationHandler;
import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.queries.QueriesWithIgnorable;
import io.helidon.microprofile.tests.junit5.AddBean;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for ignorable fields.
 */
@AddBean(QueriesWithIgnorable.class)
@AddBean(TestDB.class)
class IngorableIT extends AbstractGraphQlCdiIT {

    @Inject
    IngorableIT(GraphQlCdiExtension graphQlCdiExtension) {
        super(graphQlCdiExtension);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testIgnorable() throws IOException {
        setupIndex(indexFileName, QueriesWithIgnorable.class);
        InvocationHandler executionContext =  createInvocationHandler();
        Map<String, Object> mapResults = getAndAssertResult(
                executionContext.execute("query { testIgnorableFields { id dontIgnore } }"));
        assertThat(mapResults.size(), is(1));

        Map<String, Object> mapResults2 = (Map<String, Object>) mapResults.get("testIgnorableFields");
        assertThat(mapResults2.size(), is(2));
        assertThat(mapResults2.get("id"), is("id"));
        assertThat(mapResults2.get("dontIgnore"), is(true));

        // ensure getting the fields generates an error that is caught by the getAndAssertResult
        assertThrows(AssertionFailedError.class, () -> getAndAssertResult(executionContext
                                                                                  .execute(
                                                                                          "query { testIgnorableFields { id "
                                                                                                  + "dontIgnore pleaseIgnore "
                                                                                                  + "ignoreThisAsWell } }")));

        Schema schema = createSchema();
        SchemaType type = schema.getTypeByName("ObjectWithIgnorableFieldsAndMethods");
        assertThat(type, is(notNullValue()));
        assertThat(type.fieldDefinitions().stream().filter(fd -> fd.name().equals("ignoreGetMethod")).count(), is(0L));

        SchemaInputType inputType = schema.getInputTypeByName("ObjectWithIgnorableFieldsAndMethodsInput");
        assertThat(inputType, is(notNullValue()));
        assertThat(inputType.fieldDefinitions().stream().filter(fd -> fd.name().equals("ignoreBecauseOfMethod")).count(),
                   is(0L));
        assertThat(inputType.fieldDefinitions().stream().filter(fd -> fd.name().equals("valueSetter")).count(), is(1L));
    }
}
