/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.data.jdbc.tests.declarative.h2;

import java.util.Arrays;
import java.util.List;

import io.helidon.data.jdbc.tests.contract.AbstractDeclarativeScalarContract;
import io.helidon.data.jdbc.tests.database.H2Database;
import io.helidon.data.jdbc.tests.support.TestConfigFactory;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class DeclarativeScalarBindingH2Test extends AbstractDeclarativeScalarContract {
    @Override
    protected void beforeStartApplication() {
        TestConfigFactory.config(H2Database.config());
    }

    /**
     * Proves repeated named null markers and positional null markers bind every physical placeholder independently.
     */
    @Test
    void storesRepeatedNamedAndPositionalNullsAtEveryPhysicalPosition() {
        repository().bindRepeated("repeated");
        assertThat(readStrings(), is(List.of("repeated", "repeated")));

        repository().bindRepeated(null);
        assertThat(readStrings(), is(Arrays.asList(null, null)));

        repository().bindPositional("first", "second");
        assertThat(readStrings(), is(List.of("first", "second")));

        repository().bindPositional(null, null);
        assertThat(readStrings(), is(Arrays.asList(null, null)));
    }

    private List<String> readStrings() {
        return client().create("""
                        SELECT STRING_VALUE, SECOND_STRING_VALUE
                        FROM SCALAR_VALUE
                        WHERE ID = 1
                        """)
                .map(row -> Arrays.asList(row.optional(1, String.class).orElse(null),
                                          row.optional(2, String.class).orElse(null)))
                .one();
    }
}
