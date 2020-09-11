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

import io.helidon.microprofile.graphql.server.test.queries.QueriesWithNulls;
import io.helidon.microprofile.graphql.server.test.types.NullPOJO;

import org.eclipse.microprofile.graphql.NonNull;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Integration tests for {@link SchemaGeneratorTest}.
 */
public class JandexUtilsIT extends AbstractGraphQLIT {

    @Test
    public void testJandexUtils() throws IOException {
        setupIndex(indexFileName, NullPOJO.class, QueriesWithNulls.class);
        SchemaGenerator schemaGenerator = new SchemaGenerator(defaultContext);
        String queriesWithNulls = QueriesWithNulls.class.getName();
        String nullPOJO = NullPOJO.class.getName();
        String noNull = NonNull.class.getName();

        JandexUtils jandexUtils = schemaGenerator.getJandexUtils();

        assertThat(jandexUtils.methodParameterHasAnnotation(queriesWithNulls, "query1", 0, noNull), is(false));

        assertThat(jandexUtils.fieldHasAnnotation(nullPOJO, "listNonNullStrings", noNull), is(true));
        assertThat(jandexUtils.fieldHasAnnotation(nullPOJO, "listOfListOfNonNullStrings", noNull), is(true));
        assertThat(jandexUtils.fieldHasAnnotation(nullPOJO, "listOfListOfNullStrings", noNull), is(false));

        assertThat(jandexUtils.methodHasAnnotation(nullPOJO, "getListOfListOfNonNullStrings", noNull), is(false));
    }
}
