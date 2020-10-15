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

import org.eclipse.microprofile.graphql.ConfigKey;
import org.junit.jupiter.api.Test;

import java.io.IOException;

@AddBean(ExceptionQueries.class)
@AddBean(SimpleContact.class)
@AddBean(TestDB.class)
@AddConfig(key = ConfigKey.EXCEPTION_WHITE_LIST, value = "java.io.IOError")
public class WLOfCheckedExceptionIT extends AbstractGraphQLIT {

    @Test
    public void testWhiteListOfCheckedException() throws IOException {
        setupIndex(indexFileName, ExceptionQueries.class);
        assertMessageValue("query { uncheckedQuery1 }",
                           "java.security.AccessControlException: my exception", true);
        assertMessageValue("query { uncheckedQuery2 }",
                           "java.security.AccessControlException: my exception", true);
    }
}
