/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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
package io.helidon.metrics.providers.micrometer;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.StreamSupport;

import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.service.registry.Services;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestGauge {

    private static MetricsFactory metricsFactory;
    private static MeterRegistry meterRegistry;

    @BeforeAll
    static void prep() {
        metricsFactory = Services.get(MetricsFactory.class);
        meterRegistry = Services.get(MeterRegistry.class);
    }

    @Test
    void testUnwrap() {
        long initialValue = 4L;
        long incr = 2L;
        AtomicLong value = new AtomicLong(initialValue);
        Gauge.Builder<Double> builder = metricsFactory.gaugeBuilder("a", value, v -> (double) v.get());
        builder.unwrap(io.micrometer.core.instrument.Gauge.Builder.class).strongReference(true);
        Gauge<Double> g = meterRegistry.getOrCreate(builder);

        io.micrometer.core.instrument.Gauge mGauge = g.unwrap(io.micrometer.core.instrument.Gauge.class);
        assertThat("Initial value", mGauge.value(), is((double) initialValue));
        value.addAndGet(incr);
        assertThat("Updated value", mGauge.value(), is((double) initialValue + incr));
    }

    @Test
    void testAddedTagSupplierAndRemoval() {
        checkFilteredSupplierAndRemoval(IdRewrite.ADD_TAG);
    }

    @Test
    void testRemovedTagSupplierAndRemoval() {
        checkFilteredSupplierAndRemoval(IdRewrite.REMOVE_TAG);
    }

    @Test
    void testRenamedSupplierAndRemoval() {
        checkFilteredSupplierAndRemoval(IdRewrite.RENAME);
    }

    @Test
    void testFilteredDeduplicationPreservesBacking() {
        MeterRegistry registry = metricsFactory.createMeterRegistry(MetricsConfig.create());
        try {
            registry.unwrap(io.micrometer.core.instrument.MeterRegistry.class).config()
                    .meterFilter(MeterFilter.ignoreTags("source"));
            AtomicLong original = new AtomicLong(7);
            Gauge<AtomicLong> first = registry.getOrCreate(metricsFactory.gaugeBuilder("collapsed", () -> original)
                                                                 .addTag(metricsFactory.tagCreate("source", "first")));
            Gauge<AtomicLong> second = registry.getOrCreate(metricsFactory.gaugeBuilder("collapsed", () -> new AtomicLong(19))
                                                                  .addTag(metricsFactory.tagCreate("source", "second")));
            assertThat("Native deduplication retains wrapper", second, sameInstance(first));
            assertThat("Native deduplication retains original supplier", second.value(), sameInstance(original));
            assertThat("Native backing remains original", second.unwrap(io.micrometer.core.instrument.Gauge.class).value(),
                       is(7.0));
        } finally {
            registry.close();
        }
    }

    @Test
    void testReentrantNativeRegistrationDoesNotUsePendingSupplier() {
        MeterRegistry registry = metricsFactory.createMeterRegistry(MetricsConfig.create());
        try {
            io.micrometer.core.instrument.MeterRegistry nativeRegistry =
                    registry.unwrap(io.micrometer.core.instrument.MeterRegistry.class);
            AtomicBoolean registering = new AtomicBoolean();
            nativeRegistry.config().meterFilter(new MeterFilter() {
                @Override
                public Meter.Id map(Meter.Id id) {
                    if (id.getName().equals("outer") && registering.compareAndSet(false, true)) {
                        io.micrometer.core.instrument.Gauge.builder("unrelated", () -> 23).register(nativeRegistry);
                        registry.getOrCreate(metricsFactory.gaugeBuilder("nested", () -> new AtomicLong(11)));
                    }
                    return id.withName("mapped." + id.getName());
                }
            });
            AtomicLong value = new AtomicLong(7);
            Gauge<AtomicLong> outer = registry.getOrCreate(metricsFactory.gaugeBuilder("outer", () -> value));
            assertThat("Outer supplier retained", outer.value(), sameInstance(value));
            assertThat("Unrelated native registration keeps its own value",
                       registry.meter(Gauge.class, "mapped.unrelated", List.of()).orElseThrow().value(), is(23.0));
            assertThat("Nested Helidon registration keeps its supplier type",
                       registry.meter(Gauge.class, "mapped.nested", List.of()).orElseThrow().value().longValue(), is(11L));
        } finally {
            registry.close();
        }
    }

    @Test
    void testRegistrationFailureDoesNotRetainPendingSupplier() {
        MeterRegistry registry = metricsFactory.createMeterRegistry(MetricsConfig.create());
        try {
            io.micrometer.core.instrument.MeterRegistry nativeRegistry =
                    registry.unwrap(io.micrometer.core.instrument.MeterRegistry.class);
            AtomicBoolean fail = new AtomicBoolean(true);
            nativeRegistry.config().onMeterAdded(meter -> {
                if (meter.getId().getName().equals("failure") && fail.getAndSet(false)) {
                    throw new IllegalStateException("registration failure");
                }
            });
            assertThrows(IllegalStateException.class,
                         () -> registry.getOrCreate(metricsFactory.gaugeBuilder("failure", () -> new AtomicLong(7))));
            io.micrometer.core.instrument.Gauge nativeGauge =
                    io.micrometer.core.instrument.Gauge.builder("failure", () -> 19).register(nativeRegistry);
            Gauge<?> wrapper = registry.meter(Gauge.class, "failure", List.of()).orElseThrow();
            assertThat("Later native registration has its own backing", wrapper.value(), is(19.0));
            assertThat("Later native registration has its own identity",
                       wrapper.unwrap(io.micrometer.core.instrument.Gauge.class), sameInstance(nativeGauge));
        } finally {
            registry.close();
        }
    }

    @Test
    void testListenerFailureCompletesReentrantNativeRegistration() {
        MeterRegistry registry = metricsFactory.createMeterRegistry(MetricsConfig.create());
        try {
            io.micrometer.core.instrument.MeterRegistry nativeRegistry =
                    registry.unwrap(io.micrometer.core.instrument.MeterRegistry.class);
            nativeRegistry.config().meterFilter(new MeterFilter() {
                @Override
                public Meter.Id map(Meter.Id id) {
                    if (id.getName().equals("outer")) {
                        io.micrometer.core.instrument.Gauge.builder("unrelated", () -> 23).register(nativeRegistry);
                    }
                    return id.withName("mapped." + id.getName());
                }
            });
            IllegalStateException listenerFailure = new IllegalStateException("listener failure");
            registry.onMeterAdded(meter -> {
                throw listenerFailure;
            });
            AtomicLong value = new AtomicLong(7);
            assertThat("Original listener failure propagates without self-suppression",
                       assertThrows(IllegalStateException.class,
                                    () -> registry.getOrCreate(metricsFactory.gaugeBuilder("outer", () -> value))),
                       sameInstance(listenerFailure));
            Gauge<AtomicLong> retry = registry.getOrCreate(metricsFactory.gaugeBuilder("outer", () -> new AtomicLong(19)));
            assertThat("Retry retains the original supplier backing", retry.value(), sameInstance(value));
            assertThat("Retry retains the original native backing",
                       retry.unwrap(io.micrometer.core.instrument.Gauge.class).value(), is(7.0));
            Gauge<?> unrelated = registry.meter(Gauge.class, "mapped.unrelated", List.of()).orElseThrow();
            assertThat("Unrelated native callback is completed despite listener failure", unrelated.value(), is(23.0));
            assertThat("Unrelated wrapper retains exact native meter", unrelated.unwrap(io.micrometer.core.instrument.Gauge.class),
                       sameInstance(nativeRegistry.find("mapped.unrelated").gauge()));
        } finally {
            registry.close();
        }
    }

    private void checkFilteredSupplierAndRemoval(IdRewrite rewrite) {
        MeterRegistry registry = metricsFactory.createMeterRegistry(MetricsConfig.create());
        try {
            io.micrometer.core.instrument.MeterRegistry nativeRegistry =
                    registry.unwrap(io.micrometer.core.instrument.MeterRegistry.class);
            nativeRegistry.config().meterFilter(rewrite.filter());
            AtomicLong value = new AtomicLong(7);
            Gauge<AtomicLong> gauge = registry.getOrCreate(metricsFactory.gaugeBuilder("filtered", () -> value)
                                                                 .addTag(metricsFactory.tagCreate("original", "value")));
            assertThat("Supplier value survives native ID mapping", gauge.value(), sameInstance(value));
            io.micrometer.core.instrument.Gauge nativeGauge = gauge.unwrap(io.micrometer.core.instrument.Gauge.class);
            assertThat("Wrapper exposes actual native name", gauge.id().name(), is(nativeGauge.getId().getName()));
            assertThat("Wrapper exposes actual native tags", StreamSupport.stream(gauge.id().tags().spliterator(), false)
                               .map(tag -> tag.key() + "=" + tag.value()).sorted().toList(),
                       is(nativeGauge.getId().getTags().stream()
                                  .map(tag -> tag.getKey() + "=" + tag.getValue()).sorted().toList()));
            value.set(13);
            assertThat("Native gauge shares supplier backing", nativeGauge.value(), is(13.0));
            assertThat("Filtered gauge can be removed by its identity", registry.remove(gauge).orElseThrow(),
                       sameInstance(gauge));
            assertThat("Native gauge is removed", nativeRegistry.getMeters().contains(nativeGauge), is(false));
            assertThat("Provider gauge is deleted", registry.isDeleted(gauge), is(true));
        } finally {
            registry.close();
        }
    }

    private enum IdRewrite {
        ADD_TAG,
        REMOVE_TAG,
        RENAME;

        MeterFilter filter() {
            return switch (this) {
            case ADD_TAG -> MeterFilter.commonTags(Tags.of("filtered", "value"));
            case REMOVE_TAG -> MeterFilter.ignoreTags("original");
            case RENAME -> new MeterFilter() {
                @Override
                public Meter.Id map(Meter.Id id) {
                    return id.withName("mapped." + id.getName());
                }
            };
            };
        }
    }
}
