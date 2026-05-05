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

package io.helidon.common.concurrency.limits;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.service.registry.Service;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.not;

class LimitMetricsTest {

    @Test
    void customContextTagsAreUsedForMetrics() {
        List<Tag> tags = List.of(Tag.create("origin", "batch-import"),
                                 Tag.create("component", "inventory"));
        CapturingSemaphoreMetrics metrics = new CapturingSemaphoreMetrics();

        metrics.init(Limit.Context.create("batch-import", tags));

        assertThat(metrics.capturedTags(), hasEntry("origin", "batch-import"));
        assertThat(metrics.capturedTags(), hasEntry("component", "inventory"));
        assertThat(metrics.capturedTags(), not(hasEntry("socketName", "batch-import")));
    }

    @Test
    void mapTagsAreConvertedToMetricTags() {
        CapturingSemaphoreMetrics metrics = new CapturingSemaphoreMetrics();

        metrics.init(Limit.Context.create("listener-quic", Map.of("origin", "listener", "transport", "quic")));

        assertThat(metrics.capturedTags(), hasEntry("origin", "listener"));
        assertThat(metrics.capturedTags(), hasEntry("transport", "quic"));
    }

    @Test
    void legacyInitAddsSocketNameTagForNamedOrigin() {
        CapturingSemaphoreMetrics metrics = new CapturingSemaphoreMetrics();

        metrics.init("@admin");

        assertThat(metrics.capturedTags(), hasEntry("socketName", "@admin"));
    }

    @Test
    void legacyInitOmitsSocketNameTagForDefaultOrigin() {
        CapturingSemaphoreMetrics metrics = new CapturingSemaphoreMetrics();

        metrics.init(Service.Named.DEFAULT_NAME);

        assertThat(metrics.capturedTags().containsKey("socketName"), is(false));
    }

    private static class CapturingSemaphoreMetrics extends SemaphoreMetrics {
        private Map<String, String> capturedTags;

        CapturingSemaphoreMetrics() {
            super(true, null, "test", new AtomicInteger(), new AtomicInteger());
        }

        @Override
        void register(MetricsFactory metricsFactory, MeterRegistry meterRegistry, List<Tag> tags) {
            capturedTags = tags.stream()
                    .collect(java.util.stream.Collectors.toMap(Tag::key, Tag::value));
        }

        Map<String, String> capturedTags() {
            return capturedTags;
        }
    }
}
