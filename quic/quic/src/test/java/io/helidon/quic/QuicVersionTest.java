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

package io.helidon.quic;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicVersionTest {
    @Test
    void shouldResolveKnownVersionNumbers() {
        assertThat(QuicVersion.of(1).orElseThrow(), is(QuicVersion.QUIC_V1));
        assertThat(QuicVersion.of(0x6b3343cf).orElseThrow(), is(QuicVersion.QUIC_V2));
    }

    @Test
    void shouldReturnEmptyForUnknownVersionNumber() {
        assertThat(QuicVersion.of(0x12345678).stream().toList(), empty());
    }

    @Test
    void shouldHonorConfiguredOrderForFirstFlight() {
        assertThat(QuicVersion.firstFlightVersion(List.of(QuicVersion.QUIC_V2, QuicVersion.QUIC_V1)),
                   is(QuicVersion.QUIC_V2));
        assertThat(QuicVersion.firstFlightVersion(List.of(QuicVersion.QUIC_V1, QuicVersion.QUIC_V2)),
                   is(QuicVersion.QUIC_V1));
    }

    @Test
    void shouldUseOnlyAvailableVersionForFirstFlight() {
        assertThat(QuicVersion.firstFlightVersion(List.of(QuicVersion.QUIC_V2)), is(QuicVersion.QUIC_V2));
        assertThat(QuicVersion.firstFlightVersion(List.of(QuicVersion.QUIC_V1)), is(QuicVersion.QUIC_V1));
    }

    @Test
    void shouldRejectEmptyVersionsForFirstFlight() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                                   () -> QuicVersion.firstFlightVersion(List.of()));
        assertThat(ex.getMessage(), is("Empty quic versions"));
    }

    @Test
    void shouldRejectNullVersionsForFirstFlight() {
        List<QuicVersion> firstNull = new ArrayList<>();
        firstNull.add(null);
        firstNull.add(QuicVersion.QUIC_V1);
        List<QuicVersion> lastNull = new ArrayList<>(List.of(QuicVersion.QUIC_V2));
        lastNull.add(null);

        assertThrows(NullPointerException.class, () -> QuicVersion.firstFlightVersion(null));
        assertThrows(NullPointerException.class, () -> QuicVersion.firstFlightVersion(firstNull));
        assertThrows(NullPointerException.class, () -> QuicVersion.firstFlightVersion(lastNull));
    }
}
