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

package io.helidon.metrics.benchmark.jmh;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MeterRegistryFormatter;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.spi.MeterRegistryFormatterProvider;
import io.helidon.service.registry.Services;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class MetricsFormatterJmhBenchmark {
    private static final String METER_NAME = "metrics.formatter.cardinality";
    private static final String SERIES_TAG_NAME = "series";
    private static final String SELECTION_TAG_NAME = "selection";
    private static final String SELECTED_TAG_VALUE = "selected";
    private static final String UNSELECTED_TAG_VALUE = "unselected";

    @Param({"10", "100", "1000"})
    private int cardinality;

    private MeterRegistry meterRegistry;
    private MeterRegistryFormatter unfilteredFormatter;
    private MeterRegistryFormatter tagSelectedFormatter;

    @Setup(Level.Trial)
    public void setUp() {
        MetricsConfig metricsConfig = MetricsConfig.builder()
                .warnOnMultipleRegistries(false)
                .build();
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        meterRegistry = metricsFactory.createMeterRegistry(metricsConfig);

        for (int i = 0; i < cardinality; i++) {
            String selectionTagValue = i == cardinality - 1 ? SELECTED_TAG_VALUE : UNSELECTED_TAG_VALUE;
            var gaugeBuilder = metricsFactory.gaugeBuilder(METER_NAME, () -> 1)
                    .addTag(metricsFactory.tagCreate(SERIES_TAG_NAME, Integer.toString(i)))
                    .addTag(metricsFactory.tagCreate(SELECTION_TAG_NAME, selectionTagValue));
            meterRegistry.getOrCreate(gaugeBuilder);
        }

        unfilteredFormatter = formatter(metricsConfig, Map.of());
        tagSelectedFormatter = formatter(metricsConfig,
                                          Map.<String, Collection<String>>of(
                                                  SELECTION_TAG_NAME, Set.of(SELECTED_TAG_VALUE)));
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        meterRegistry.close();
    }

    @Benchmark
    public Object formatUnfiltered() {
        return unfilteredFormatter.format().orElseThrow();
    }

    @Benchmark
    public Object formatTagSelected() {
        return tagSelectedFormatter.format().orElseThrow();
    }

    private MeterRegistryFormatter formatter(MetricsConfig metricsConfig,
                                              Map<String, Collection<String>> tagSelection) {
        return Services.all(MeterRegistryFormatterProvider.class).stream()
                .map(provider -> provider.formatter(MediaTypes.TEXT_PLAIN,
                                                    metricsConfig,
                                                    meterRegistry,
                                                    tagSelection,
                                                    List.of()))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseThrow();
    }
}
