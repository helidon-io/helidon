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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.metrics.spi.MeterRegistryFormatterProvider;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("removal")
class TestMeterRegistryFormatterProvider {

    @Test
    void contextDelegatesToExistingSelectionProvider() {
        MetricsConfig metricsConfig = MetricsConfig.create();
        MeterRegistry registry = new NoOpMetricsFactory().globalRegistry();
        Map<String, Collection<String>> tagSelections = Map.of("color", List.of("blue"));
        List<String> nameSelection = List.of("requests");
        AtomicInteger invocationCount = new AtomicInteger();
        MeterRegistryFormatter formatter = new MeterRegistryFormatter() {
            @Override
            public Optional<Object> format() {
                return Optional.of("requests 1");
            }

            @Override
            public Optional<Object> formatMetadata() {
                return Optional.empty();
            }
        };
        MeterRegistryFormatterProvider provider = new MeterRegistryFormatterProvider() {
            @Override
            public Optional<MeterRegistryFormatter> formatter(MediaType mediaType,
                                                               MetricsConfig config,
                                                               MeterRegistry meterRegistry,
                                                               Map<String, Collection<String>> tags,
                                                               Iterable<String> names) {
                assertThat(mediaType, sameInstance(MediaTypes.TEXT_PLAIN));
                assertThat(config, sameInstance(metricsConfig));
                assertThat(meterRegistry, sameInstance(registry));
                assertThat(tags, is(tagSelections));
                assertThat(names, is(nameSelection));
                invocationCount.incrementAndGet();
                return Optional.of(formatter);
            }

            @Override
            public Optional<MeterRegistryFormatter> formatter(MediaType mediaType,
                                                               MetricsConfig config,
                                                               MeterRegistry meterRegistry,
                                                               Optional<String> scopeTagName,
                                                               Iterable<String> scopeSelection,
                                                               Iterable<String> names) {
                throw new AssertionError("The current formatter overload must receive the request");
            }
        };

        for (boolean includeHistogramQuantiles : List.of(false, true)) {
            var context = FormatterContext.builder()
                    .mediaType(MediaTypes.TEXT_PLAIN)
                    .metricsConfig(metricsConfig)
                    .tagSelections(tagSelections)
                    .nameSelection(nameSelection)
                    .includeHistogramQuantiles(includeHistogramQuantiles)
                    .build();
            assertThat(provider.formatter(context, registry).orElseThrow(),
                       sameInstance(formatter));
        }
        assertThat(invocationCount.get(), is(2));
    }

    @Test
    void contextDelegatesToLegacyScopeProviderWithoutTagSelections() {
        MetricsConfig metricsConfig = MetricsConfig.create();
        MeterRegistry registry = new NoOpMetricsFactory().globalRegistry();
        AtomicInteger invocationCount = new AtomicInteger();
        List<String> names = List.of("requests");
        MeterRegistryFormatterProvider provider = (mediaType, config, meterRegistry, scopeTagName, scopes, selectedNames) -> {
            assertThat(mediaType, sameInstance(MediaTypes.TEXT_PLAIN));
            assertThat(config, sameInstance(metricsConfig));
            assertThat(meterRegistry, sameInstance(registry));
            assertThat(scopeTagName, is(Optional.empty()));
            assertThat(scopes, is(List.of()));
            assertThat(selectedNames, is(names));
            invocationCount.incrementAndGet();
            return Optional.empty();
        };

        for (boolean includeHistogramQuantiles : List.of(false, true)) {
            var context = FormatterContext.builder()
                    .mediaType(MediaTypes.TEXT_PLAIN)
                    .metricsConfig(metricsConfig)
                    .nameSelection(names)
                    .includeHistogramQuantiles(includeHistogramQuantiles)
                    .build();
            assertThat(provider.formatter(context, registry), is(Optional.empty()));
            var tagContext = FormatterContext.builder(context)
                    .tagSelections(Map.of("color", List.of("blue")))
                    .build();
            assertThat(provider.formatter(tagContext, registry), is(Optional.empty()));
        }
        assertThat(invocationCount.get(), is(2));
    }

    @Test
    void contextRejectsNullArgumentsBeforeDelegation() {
        MeterRegistryFormatterProvider provider = (_, _, _, _, _, _) -> {
            throw new AssertionError("Null arguments must be rejected before invoking the provider");
        };
        var context = FormatterContext.builder()
                .mediaType(MediaTypes.TEXT_PLAIN)
                .metricsConfig(MetricsConfig.create())
                .build();
        MeterRegistry registry = new NoOpMetricsFactory().globalRegistry();

        assertThrows(NullPointerException.class, () -> provider.formatter(null, registry));
        assertThrows(NullPointerException.class, () -> provider.formatter(context, null));
    }
}
