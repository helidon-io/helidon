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

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.exception.ExceptionQueries;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddConfig;

import org.eclipse.microprofile.graphql.ConfigKey;
import org.junit.jupiter.api.Test;

/**
 * Tests for deny list.
 */
@AddBean(ExceptionQueries.class)
@AddBean(TestDB.class)
@AddConfig(key = ConfigKey.EXCEPTION_BLACK_LIST, value = "java.io.IOException,java.util.concurrent.TimeoutException")
class BLOfIOExceptionIT extends AbstractGraphQlCdiIT {

    @Inject
    BLOfIOExceptionIT(GraphQlCdiExtension graphQlCdiExtension) {
        super(graphQlCdiExtension);
    }

    @Test
    public void testDenyListOfIOException() throws IOException {
        setupIndex(indexFileName, ExceptionQueries.class);
        assertMessageValue("query { checkedException }", "Server Error", true);
        assertMessageValue("query { checkedException2 }", "Server Error", true);
    }
}
