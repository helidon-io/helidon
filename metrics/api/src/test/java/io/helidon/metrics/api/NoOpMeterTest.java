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

package io.helidon.metrics.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NoOpMeterTest {

    static Stream<Arguments> builders() {
        var factory = new NoOpMetricsFactory();
        return Stream.of(Arguments.of("counter", factory.counterBuilder("counter").baseUnit(Meter.BaseUnits.BYTES),
                                      Counter.class, Meter.Type.COUNTER),
                         Arguments.of("functional counter",
                                      factory.functionalCounterBuilder("functional", new Object(), _ -> {
                                          throw new AssertionError("Conversion must not sample the functional counter");
                                      }).baseUnit("{request}"), FunctionalCounter.class, Meter.Type.COUNTER),
                         Arguments.of("gauge", factory.gaugeBuilder("gauge", () -> {
                             throw new AssertionError("Conversion must not sample the gauge");
                         }).baseUnit(Meter.BaseUnits.PERCENT), Gauge.class, Meter.Type.GAUGE),
                         Arguments.of("summary", factory.distributionSummaryBuilder("summary",
                                 factory.distributionStatisticsConfigBuilder()).baseUnit(Meter.BaseUnits.BYTES),
                                      DistributionSummary.class, Meter.Type.DISTRIBUTION_SUMMARY),
                         Arguments.of("timer", factory.timerBuilder("timer").baseUnit(TimeUnit.MILLISECONDS),
                                      Timer.class, Meter.Type.TIMER));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("builders")
    void conversionPreservesIndependentMetadata(String kind,
                                               Meter.Builder<?, ?> builder,
                                               Class<? extends Meter> meterType,
                                               Meter.Type expectedType) {
        builder.addTag(new NoOpTag("method", "GET"))
                .addTag(new NoOpTag("route", "/orders"))
                .description("Original description")
                .origin("example.Resource");
        Optional<String> expectedUnit = builder.baseUnit();

        Meter meter = new NoOpMetricsFactory().noOpMeter(builder);
        builder.addTag(new NoOpTag("route", "/changed"))
                .description("Changed description")
                .baseUnit(TimeUnit.SECONDS.name());

        assertAll(kind,
                  () -> assertThat("Meter interface", meter, instanceOf(meterType)),
                  () -> assertThat("Meter type", meter.type(), is(expectedType)),
                  () -> assertThat("Meter name", meter.id().name(), is(builder.name())),
                  () -> assertThat("Tags are preserved independently of subsequent builder changes",
                                   meter.id().tagsMap(), is(Map.of("method", "GET", "route", "/orders"))),
                  () -> assertThat("Description is preserved", meter.description(), is(Optional.of("Original description"))),
                  () -> assertThat("Unit is preserved", meter.baseUnit(), is(expectedUnit)));
    }

    @Test
    void conversionDetachesAndSortsTagsFromMutableSourceMap() {
        var factory = new NoOpMetricsFactory();
        var sourceTags = new LinkedHashMap<String, String>();
        sourceTags.put("route", "/orders");
        sourceTags.put("method", "GET");
        Counter.Builder builder = new NoOpMeter.Counter.Builder("counter") {
            @Override
            public Map<String, String> tags() {
                return sourceTags;
            }
        };
        Meter.Id expectedId = factory.noOpMeter(factory.counterBuilder("counter")
                                                       .addTag(new NoOpTag("method", "GET"))
                                                       .addTag(new NoOpTag("route", "/orders")))
                .id();

        Meter meter = factory.noOpMeter(builder);
        sourceTags.clear();

        assertAll("Converted meter identity",
                  () -> assertThat("Tags remain in key order after the source map changes", meter.id().tags(),
                                   contains(new NoOpTag("method", "GET"), new NoOpTag("route", "/orders"))),
                  () -> assertThat("Identity is independent of source tag order", meter.id(), is(expectedId)),
                  () -> assertThat("Equal identities retain equal hashes", meter.id().hashCode(), is(expectedId.hashCode())));
    }

    @Test
    void convertedGaugesSampleOnlyWhenRead() {
        var factory = new NoOpMetricsFactory();
        var state = new AtomicInteger(7);
        var calls = new AtomicInteger();
        Gauge<?> supplierGauge = (Gauge<?>) factory.noOpMeter(factory.gaugeBuilder("supplier", () -> {
            calls.incrementAndGet();
            return state.get();
        }));
        Gauge<?> functionGauge = (Gauge<?>) factory.noOpMeter(factory.gaugeBuilder("function", state, value -> {
            calls.incrementAndGet();
            return value.doubleValue();
        }));

        assertThat("Conversion must not sample either gauge", calls.get(), is(0));
        state.set(42);
        assertThat("Supplier gauge reads current state", supplierGauge.value(), is(42));
        assertThat("Function gauge reads current state", functionGauge.value(), is(42D));
        assertThat("Each read samples once", calls.get(), is(2));
        state.set(43);
        assertThat("Supplier gauge remains live", supplierGauge.value(), is(43));
        assertThat("Function gauge remains live", functionGauge.value(), is(43D));
        assertThat("Each subsequent read samples once", calls.get(), is(4));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void supplierGaugesRejectNullSamples(boolean converted) {
        var factory = new NoOpMetricsFactory();
        var sample = new AtomicReference<Long>();
        var calls = new AtomicInteger();
        var builder = NoOpMeter.Gauge.builder("non-null-gauge", () -> {
            calls.incrementAndGet();
            return sample.get();
        });
        Gauge<?> gauge = converted ? (Gauge<?>) factory.noOpMeter(builder) : builder.build();

        assertThat("Construction must not sample the gauge", calls.get(), is(0));
        assertThrows(NullPointerException.class, gauge::value);
        assertThat("A rejected sample invokes the supplier once", calls.get(), is(1));
        Long first = 9_007_199_254_740_993L;
        sample.set(first);
        assertThat("Sampling preserves the supplied Long instance", gauge.value(), sameInstance(first));
        sample.set(null);
        assertThrows(NullPointerException.class, gauge::value);
        Long recovered = 9_007_199_254_740_994L;
        sample.set(recovered);
        assertThat("The gauge recovers after an invalid sample", gauge.value(), sameInstance(recovered));
        assertThat("Each read invokes the supplier once", calls.get(), is(4));
    }

    @Test
    void conversionPreservesDefaultMetadataAndNoOpCounterBehavior() {
        var factory = new NoOpMetricsFactory();
        Counter counter = (Counter) factory.noOpMeter(factory.counterBuilder("counter"));

        counter.increment();
        counter.increment(7);

        assertAll("Converted no-op counter",
                  () -> assertThat("Tags default to empty", counter.id().tags(), emptyIterable()),
                  () -> assertThat("Description defaults to an empty string", counter.description(), is(Optional.of(""))),
                  () -> assertThat("Unit defaults to an empty string", counter.baseUnit(), is(Optional.of(""))),
                  () -> assertThat("Increments do not record", counter.count(), is(0L)));
    }
}
