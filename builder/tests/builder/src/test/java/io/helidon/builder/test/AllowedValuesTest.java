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

import io.helidon.builder.test.testsubjects.AllowedValues;
import io.helidon.common.Errors;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AllowedValuesTest {
    @Test
    void testDefault() {
        AllowedValues allowedValues = AllowedValues.create();

        assertThat(allowedValues.restrictedOptions(), optionalEmpty());
        assertThat(allowedValues.restrictedOptionsList(), hasSize(0));
    }

    @Test
    void testSingleValueAllowed() {
        AllowedValues allowedValues = AllowedValues.builder()
                .restrictedOptions("GOOD_1")
                .build();

        assertThat(allowedValues.restrictedOptions(), optionalValue(is("GOOD_1")));
        assertThat(allowedValues.restrictedOptionsList(), hasSize(0));
    }

    @Test
    void testListValuesAllowed() {
        AllowedValues allowedValues = AllowedValues.builder()
                .addRestrictedOptionToList("GOOD_1")
                .addRestrictedOptionToList("GOOD_2")
                .build();

        assertThat(allowedValues.restrictedOptions(), optionalEmpty());
        assertThat(allowedValues.restrictedOptionsList(), hasItems("GOOD_1", "GOOD_2"));
    }

    @Test
    void testSingleValueNotAllowed() {
        var thrown = assertThrows(Errors.ErrorMessagesException.class, () -> AllowedValues.builder()
                .restrictedOptions("BAD")
                .build());

        String message = thrown.getMessage();
        assertThat(message,
                   containsString("Property \"restricted-options\" value is not within allowed values. "
                                      + "Configured: \"BAD\", expected one of: \""));
        // we use a set, may be different order
        assertThat(message, containsString("GOOD_1"));
        assertThat(message, containsString("GOOD_2"));
    }

    @Test
    void testListValuesNotAllowed() {
        var thrown = assertThrows(Errors.ErrorMessagesException.class,
                                  () -> AllowedValues.builder()
                                          .addRestrictedOptionToList("GOOD_1")
                                          .addRestrictedOptionToList("BAD")
                                          .addRestrictedOptionToList("GOOD_2")
                                          .build());

        String message = thrown.getMessage();
        assertThat(message,
                   containsString("Property \"restricted-options-list\" contains value that is not within allowed values. "
                                      + "Configured: \"BAD\", expected one of: \""));
        // we use a set, may be different order
        assertThat(message, containsString("GOOD_1"));
        assertThat(message, containsString("GOOD_2"));
    }

}
