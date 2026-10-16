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

package io.helidon.webserver.http;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoutingResponseTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void resetEntityUsesExistingResetStream(boolean resetSuccessful) {
        RoutingResponse response = mock(RoutingResponse.class, CALLS_REAL_METHODS);
        doReturn(resetSuccessful).when(response).resetStream();

        assertThat(response.resetEntity(), is(resetSuccessful));
        verify(response).resetStream();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void resetEntityFallsBackToExistingReset(boolean resetSuccessful) {
        RoutingResponse response = mock(RoutingResponse.class, CALLS_REAL_METHODS);
        when(response.reset()).thenReturn(resetSuccessful);

        assertThat(response.resetEntity(), is(resetSuccessful));
        verify(response).reset();
    }
}
