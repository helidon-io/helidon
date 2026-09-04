/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Tag;
import io.helidon.webserver.testing.junit5.ServerTest;

import org.junit.jupiter.api.Test;

import static io.helidon.metrics.systemmeters.VThreadSystemMetersProvider.METER_NAME_PREFIX;
import static io.helidon.metrics.systemmeters.VThreadSystemMetersProvider.PINNED;
import static io.helidon.metrics.systemmeters.VThreadSystemMetersProvider.RECENT_PINNED;
import static io.helidon.metrics.systemmeters.VThreadSystemMetersProvider.SUBMIT_FAILURES;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class TestVirtualThreadsMetersBase {

    private final MeterRegistry meterRegistry;
    private final List<Tag> meterTags;

    TestVirtualThreadsMetersBase(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.meterTags = List.of();
    }

    MeterRegistry meterRegistry() {
        return meterRegistry;
    }

    List<Tag> meterTags() {
        return meterTags;
    }

    @Test
    void checkNonCountVthreadMetersArePresentAfterStartup() {
        assertThat("Submit failures gauge",
                   meterRegistry().gauge(METER_NAME_PREFIX + SUBMIT_FAILURES, meterTags()),
                   OptionalMatcher.optionalPresent());
        assertThat("Pinned gauge",
                   meterRegistry().gauge(METER_NAME_PREFIX + PINNED, meterTags()),
                   OptionalMatcher.optionalPresent());
        assertThat("Pinned distribution summary",
                   meterRegistry().timer(METER_NAME_PREFIX + RECENT_PINNED, meterTags()),
                   OptionalMatcher.optionalPresent());
    }
}
