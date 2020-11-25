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

import io.helidon.microprofile.graphql.server.test.queries.NoopQueriesAndMutations;
import javax.inject.Inject;

import io.helidon.microprofile.graphql.server.test.types.SimpleContactInputType;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactInputTypeWithAddress;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactInputTypeWithName;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactInputTypeWithNameValue;
import io.helidon.microprofile.tests.junit5.AddBean;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Tests for input types.
 */
@AddBean(SimpleContactInputType.class)
@AddBean(SimpleContactInputTypeWithName.class)
@AddBean(SimpleContactInputTypeWithNameValue.class)
@AddBean(SimpleContactInputTypeWithAddress.class)
@AddBean(NoopQueriesAndMutations.class)
class InputTypeIT extends AbstractGraphQlCdiIT {

    @Inject
    InputTypeIT(GraphQlCdiExtension graphQlCdiExtension) {
        super(graphQlCdiExtension);
    }

    @Test
    void testInputType() throws IOException {
        setupIndex(indexFileName, SimpleContactInputType.class, SimpleContactInputTypeWithName.class,
                   SimpleContactInputTypeWithNameValue.class, SimpleContactInputTypeWithAddress.class,
                   NoopQueriesAndMutations.class);
        Schema schema = createSchema();

        assertAll(
                () -> assertThat(schema.getInputTypes().size(), is(5)),
                () -> assertThat("MyInputType", schema.containsInputTypeWithName("MyInputType"), is(true)),
                () -> assertThat("SimpleContactInputTypeInput",
                                 schema.containsInputTypeWithName("SimpleContactInputTypeInput"),
                                 is(true)),
                () -> assertThat("NameInput", schema.containsInputTypeWithName("NameInput"), is(true)),
                () -> assertThat("SimpleContactInputTypeWithAddressInput",
                                 schema.containsInputTypeWithName("SimpleContactInputTypeWithAddressInput"),
                                 is(true)),
                () -> assertThat("AddressInput", schema.containsInputTypeWithName("AddressInput"), is(true))
        );

    }
}
