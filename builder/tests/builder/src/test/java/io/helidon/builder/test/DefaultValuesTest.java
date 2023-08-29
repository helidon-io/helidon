/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.test;

import io.helidon.builder.test.testsubjects.DefaultValues;

import org.junit.jupiter.api.Test;

import static io.helidon.builder.test.testsubjects.DefaultValues.DEFAULT_BOOLEAN;
import static io.helidon.builder.test.testsubjects.DefaultValues.DEFAULT_DOUBLE;
import static io.helidon.builder.test.testsubjects.DefaultValues.DEFAULT_INT;
import static io.helidon.builder.test.testsubjects.DefaultValues.DEFAULT_LONG;
import static io.helidon.builder.test.testsubjects.DefaultValues.DEFAULT_STRING;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;

class DefaultValuesTest {
    @Test
    void testDefaults() {
        // the create method must be available, as all fields have defaults
        DefaultValues values = DefaultValues.create();

        assertThat(values.codeStrings(), hasItems("From code"));

        assertThat(values.methodString(), is(DEFAULT_STRING));
        assertThat(values.methodStrings(), hasItems(DEFAULT_STRING, DEFAULT_STRING));

        assertThat(values.string(), is(DEFAULT_STRING));
        assertThat(values.strings(), hasItems(DEFAULT_STRING, DEFAULT_STRING));
        assertThat(values.stringSupplier().get(), is(DEFAULT_STRING));

        assertThat(values.integer(), is(DEFAULT_INT));
        assertThat(values.integers(), hasItems(DEFAULT_INT, DEFAULT_INT));

        assertThat(values.aBoolean(), is(DEFAULT_BOOLEAN));
        assertThat(values.booleans(), hasItems(DEFAULT_BOOLEAN, DEFAULT_BOOLEAN));

        assertThat(values.aDouble(), is(DEFAULT_DOUBLE));
        assertThat(values.doubles(), hasItems(DEFAULT_DOUBLE, DEFAULT_DOUBLE));

        assertThat(values.aLong(), is(DEFAULT_LONG));
        assertThat(values.longs(), hasItems(DEFAULT_LONG, DEFAULT_LONG));

        assertThat(values.anEnum(), is(DefaultValues.AnEnum.FIRST));
        assertThat(values.enums(), hasItems(DefaultValues.AnEnum.FIRST, DefaultValues.AnEnum.SECOND, DefaultValues.AnEnum.THIRD));

        assertThat(values.stringMap(), hasEntry("key1", "value1"));
        assertThat(values.stringMap(), hasEntry("key2", "value2"));
    }
}
