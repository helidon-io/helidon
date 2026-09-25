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

package io.helidon.metrics.providers.helidon;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.Tag;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

class TestHelidonMeterIdentity {

    @Test
    void identityNormalizesOrderAndKeepsLastDuplicateValue() {
        Tag a = new HelidonTag("a", "first");
        Tag z = new HelidonTag("z", "last");
        HelidonMeterId expected = new HelidonMeterId("counter", List.of(a, z));
        HelidonMeterId reordered = new HelidonMeterId("counter", List.of(z, new HelidonTag("a", "old"), a));

        assertThat(reordered, is(expected));
        assertThat(expected, is(reordered));
        assertThat(reordered.tags(), contains(a, z));
        assertThat(reordered.hashCode(), is(expected.hashCode()));
        assertThat(reordered.hashCode(), is(Objects.hash("counter", List.of(a, z))));
        assertThat(reordered, not(new HelidonMeterId("other", List.of(a, z))));
        assertThat(reordered, not(new HelidonMeterId("counter", List.of(a))));
        assertThat(reordered, not(new HelidonMeterId("counter", List.of(a, new HelidonTag("z", "other")))));
    }

    @Test
    void identityCopiesInputTagsAndAcceptsForeignIds() {
        Tag a = new HelidonTag("a", "first");
        Tag z = new HelidonTag("z", "last");
        List<Tag> input = new ArrayList<>(List.of(z, a));
        HelidonMeterId id = new HelidonMeterId("counter", input);
        input.clear();
        Meter.Id foreign = new Meter.Id() {
            @Override
            public String name() {
                return "counter";
            }

            @Override
            public Iterable<Tag> tags() {
                return List.of(z, a);
            }
        };

        assertThat(id.tags(), contains(a, z));
        assertThat(id.equals(foreign), is(true));
        assertThat(id.equals(null), is(false));
        assertThat(id.equals("counter"), is(false));
    }

    @Test
    void builderReuseDoesNotChangeRegisteredIdentity() {
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        try {
            MeterRegistry registry = factory.createMeterRegistry(MetricsConfig.create());
            Tag a = new HelidonTag("a", "first");
            Tag z = new HelidonTag("z", "last");
            Counter.Builder builder = factory.counterBuilder("counter").tags(List.of(z, a));
            Counter first = registry.getOrCreate(builder);
            int originalHash = first.id().hashCode();

            Counter second = registry.getOrCreate(builder.addTag(new HelidonTag("a", "changed")));
            assertThat(second, not(sameInstance(first)));
            assertThat(first.id().tags(), contains(a, z));
            assertThat(first.id().hashCode(), is(originalHash));
            assertThat(registry.meter(Counter.class, "counter", List.of(z, a)).orElseThrow(), sameInstance(first));
            assertThat(registry.getOrCreate(builder.tags(List.of(a, z))), sameInstance(first));
            assertThat(registry.getOrCreate(factory.counterBuilder("counter")
                                                   .tags(List.of(z, new HelidonTag("a", "old"), a))),
                       sameInstance(first));
            assertThat(registry.remove("counter", List.of(z, a)).orElseThrow(), sameInstance(first));
            assertThat(registry.meters().size(), is(1));
        } finally {
            factory.close();
        }
    }
}
