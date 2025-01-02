/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.metrics.systemmeters;

import java.util.List;

import io.helidon.common.testing.junit5.OptionalMatcher;

import org.junit.jupiter.api.Test;

import static io.helidon.metrics.systemmeters.VThreadSystemMetersProvider.COUNT;
import static io.helidon.metrics.systemmeters.VThreadSystemMetersProvider.METER_NAME_PREFIX;
import static io.helidon.metrics.systemmeters.VThreadSystemMetersProvider.STARTS;
import static org.hamcrest.MatcherAssert.assertThat;

class TestVirtualThreadsMetersWithoutCounts extends TestVirtualThreadsMetersBase {

    @Test
    void checkVthreadCountsAreAbsent() {
        assertThat("Starts meter",
                   meterRegistry().gauge(METER_NAME_PREFIX + STARTS, List.of()),
                   OptionalMatcher.optionalEmpty());
        assertThat("Count gauge",
                   meterRegistry().gauge(METER_NAME_PREFIX + COUNT, List.of()),
                   OptionalMatcher.optionalEmpty());
    }
}
