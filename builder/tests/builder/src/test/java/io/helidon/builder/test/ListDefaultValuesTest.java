/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.util.List;

import io.helidon.builder.test.testsubjects.DualValuedDefaultValues;
import io.helidon.builder.test.testsubjects.SingleValuedDefaultValues;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.iterableWithSize;

class ListDefaultValuesTest {

    @Test
    void checkSingleDefaultFromInstance() {
        SingleValuedDefaultValues original = SingleValuedDefaultValues.create();
        assertThat("Original value",
                   original.strings(),
                   allOf(hasItem(SingleValuedDefaultValues.DEFAULT_STRING),
                         iterableWithSize(1)));
        SingleValuedDefaultValues copy = SingleValuedDefaultValues.builder().from(original).build();
        assertThat("Copied value",
                   copy.strings(),
                   allOf(hasItem(SingleValuedDefaultValues.DEFAULT_STRING),
                         iterableWithSize(1)));

    }

    @Test
    void checkSingleDefaultFromBuilder() {
        SingleValuedDefaultValues.Builder builder = SingleValuedDefaultValues.builder();

        SingleValuedDefaultValues copy = SingleValuedDefaultValues.builder().from(builder).build();

        assertThat("Copied value",
                   copy.strings(),
                   allOf(hasItem(SingleValuedDefaultValues.DEFAULT_STRING),
                         iterableWithSize(1)));

    }

    @Test
    void checkDualDefaultFromInstance() {
        DualValuedDefaultValues original = DualValuedDefaultValues.create();
        assertThat("Original values",
                   original.strings(),
                   allOf(hasItems(DualValuedDefaultValues.DEFAULT_1, DualValuedDefaultValues.DEFAULT_2),
                         iterableWithSize(2)));

        DualValuedDefaultValues copy = DualValuedDefaultValues.builder().from(original).build();
        assertThat("Copied values",
                   original.strings(),
                   allOf(hasItems(DualValuedDefaultValues.DEFAULT_1, DualValuedDefaultValues.DEFAULT_2),
                         iterableWithSize(2)));
    }

    @Test
    void checkDualDefaultFromBuilder() {
        DualValuedDefaultValues.Builder builder = DualValuedDefaultValues.builder();

        DualValuedDefaultValues copy = DualValuedDefaultValues.builder().from(builder).build();
        assertThat("Copied values",
                   copy.strings(),
                   allOf(hasItems(DualValuedDefaultValues.DEFAULT_1, DualValuedDefaultValues.DEFAULT_2),
                         iterableWithSize(2)));
    }

    @Test
    void checkSingleDefaultDoubledInBuilder() {
        SingleValuedDefaultValues original = SingleValuedDefaultValues.builder()
                .strings(List.of(SingleValuedDefaultValues.DEFAULT_STRING, SingleValuedDefaultValues.DEFAULT_STRING))
                .build();

        SingleValuedDefaultValues copy = SingleValuedDefaultValues.builder()
                .from(original)
                .build();
        assertThat("Copied values",
                   copy.strings(),
                   allOf(hasItems(SingleValuedDefaultValues.DEFAULT_STRING, SingleValuedDefaultValues.DEFAULT_STRING),
                         iterableWithSize(2)));
    }

    @Test
    void checkSingleDefaultWithUpdate() {
        String value = "non-default";
        SingleValuedDefaultValues original = SingleValuedDefaultValues.builder()
                .addStrings(List.of(value))
                .build();

        assertThat("Original with update",
                   original.strings(),
                   allOf(hasItems(value, SingleValuedDefaultValues.DEFAULT_STRING),
                         iterableWithSize(2)));
    }

    @Test
    void checkDualDefaultWithUpdate() {
        String value = "non-default";
        DualValuedDefaultValues original = DualValuedDefaultValues.builder()
                .addStrings(List.of(value))
                .build();
        assertThat("Original with update", original.strings(), allOf(hasItems(value,
                                                                              DualValuedDefaultValues.DEFAULT_1,
                                                                              DualValuedDefaultValues.DEFAULT_2),
                                                                     iterableWithSize(3)));
    }

    @Test
    void checkSingleDefaultWithoutUpdate() {
        SingleValuedDefaultValues original = SingleValuedDefaultValues.create();

        assertThat("Original with no update", original.strings(), allOf(hasItems(SingleValuedDefaultValues.DEFAULT_STRING),
                                                                        iterableWithSize(1)));
    }

    @Test
    void checkDualDefaultWithoutUpdate() {
        DualValuedDefaultValues original = DualValuedDefaultValues.create();

        assertThat("Original with no updates", original.strings(), allOf(hasItems(DualValuedDefaultValues.DEFAULT_1,
                                                                                  DualValuedDefaultValues.DEFAULT_2),
                                                                         iterableWithSize(2)));
    }
}
