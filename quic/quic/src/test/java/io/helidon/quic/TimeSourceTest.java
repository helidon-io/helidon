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

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class TimeSourceTest {

    @Test
    void shouldInitializeDefaultSource() {
        assertThat(TimeSource.source().instant(), notNullValue());
    }

    @Test
    void shouldMapZeroReservationReplacementPrecisely() {
        long firstNanos = -1;
        long secondNanos = 0;

        Deadline first = TimeSource.source().instant(firstNanos);
        Deadline second = TimeSource.source().instant(secondNanos);

        assertThat(Deadline.between(first, second), is(Duration.ofNanos(1)));
    }
}
