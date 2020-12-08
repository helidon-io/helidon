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

import javax.inject.Inject;

import io.helidon.graphql.server.InvocationHandler;
import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.exception.ExceptionQueries;
import io.helidon.microprofile.tests.junit5.AddBean;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for exception handling with all defaults.
 */
@AddBean(ExceptionQueries.class)
@AddBean(TestDB.class)
class AllDefaultsExceptionIT extends AbstractGraphQlCdiIT {

    @Inject
    AllDefaultsExceptionIT(GraphQlCdiExtension graphQlCdiExtension) {
        super(graphQlCdiExtension);
    }

    @Test
    public void testAllDefaultsForConfig() throws IOException {
        setupIndex(indexFileName);
        InvocationHandler executionContext = createInvocationHandler();
        assertThat(executionContext, is(notNullValue()));
        assertThat(executionContext.defaultErrorMessage(), is("Server Error"));
        assertThat(executionContext.blacklistedExceptions().size(), is(0));
        assertThat(executionContext.whitelistedExceptions().size(), is(0));
    }
}
