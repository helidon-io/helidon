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

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.exception.ExceptionQueries;
import io.helidon.microprofile.graphql.server.test.types.SimpleContact;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import org.eclipse.microprofile.graphql.ConfigKey;
import org.junit.jupiter.api.Test;
import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for deny list and allow list.
 */
@AddBean(ExceptionQueries.class)
@AddBean(SimpleContact.class)
@AddBean(TestDB.class)
@AddConfig(key = ConfigKey.EXCEPTION_WHITE_LIST,
           value = "org.eclipse.microprofile.graphql.tck.apps.superhero.api.WeaknessNotFoundException")
@AddConfig(key = ConfigKey.EXCEPTION_BLACK_LIST, value = "java.io.IOException,java.util.concurrent.TimeoutException")
public class BLListAndWLExceptionIT extends AbstractGraphQLIT {

    @Test
    public void testBlackListAndWhiteList() throws IOException {
        setupIndex(indexFileName);

        ExecutionContext executionContext = new ExecutionContext(defaultContext);
        assertThat(executionContext.getDefaultErrorMessage(), is("Server Error"));
        assertThat(executionContext.getExceptionBlacklist().size(), is(2));
        assertThat(executionContext.getExceptionWhitelist().size(), is(1));
        assertThat(executionContext.getExceptionBlacklist().contains("java.io.IOException"), is(true));
        assertThat(executionContext.getExceptionBlacklist().contains("java.util.concurrent.TimeoutException"), is(true));
        assertThat(executionContext.getExceptionWhitelist()
                           .contains("org.eclipse.microprofile.graphql.tck.apps.superhero.api.WeaknessNotFoundException"),
                   is(true));
    }
}
