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

package io.helidon.webclient.api;

import java.util.List;

import io.helidon.http.Header;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class ClientRequestSharedHeadersTest {

    @Test
    void sharedHeaderValuesCannotBeMutatedThroughReturnedList() {
        assertAll(
                () -> assertValuesAreIsolated(ClientRequestBase.USER_AGENT_HEADER),
                () -> assertValuesAreIsolated(ClientRequestBase.PROXY_CONNECTION));
    }

    private static void assertValuesAreIsolated(Header header) {
        String expected = header.get();
        List<String> exposedValues = header.allValues();
        assertThat(exposedValues, is(List.of(expected)));

        try {
            try {
                exposedValues.clear();
            } catch (UnsupportedOperationException ignored) {
                // An immutable values view is the preferred implementation.
            }
            assertThat(header.allValues(), is(List.of(expected)));
        } finally {
            // Restore the mutable implementation so a failing regression does not contaminate this test fork.
            List<String> currentValues = header.allValues();
            if (currentValues.isEmpty()) {
                currentValues.add(expected);
            }
        }
    }
}
