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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

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
}
