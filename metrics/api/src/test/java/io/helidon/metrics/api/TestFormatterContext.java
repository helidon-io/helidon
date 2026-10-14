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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.helidon.common.Errors;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestFormatterContext {

    @Test
    void defaultsDoNotRestrictSelectionOrRequestHistogramQuantiles() {
        MetricsConfig config = MetricsConfig.create();
        var context = FormatterContext.builder()
                .mediaType(MediaTypes.TEXT_PLAIN)
                .metricsConfig(config)
                .build();

        assertThat(context.mediaType(), sameInstance(MediaTypes.TEXT_PLAIN));
        assertThat(context.metricsConfig(), sameInstance(config));
        assertThat(context.tagSelections(), is(Map.of()));
        assertThat(context.nameSelection(), is(List.of()));
        assertThat(context.includeHistogramQuantiles(), is(false));
    }

    @Test
    void mediaTypeAndMetricsConfigAreRequired() {
        var missingMediaType = assertThrows(Errors.ErrorMessagesException.class,
                                            () -> FormatterContext.builder()
                                                    .metricsConfig(MetricsConfig.create())
                                                    .build());
        assertThat(missingMediaType.getMessage(), containsString("mediaType"));
        var missingMetricsConfig = assertThrows(Errors.ErrorMessagesException.class,
                                                () -> FormatterContext.builder()
                                                        .mediaType(MediaTypes.TEXT_PLAIN)
                                                        .build());
        assertThat(missingMetricsConfig.getMessage(), containsString("metricsConfig"));
        assertThrows(NullPointerException.class, () -> FormatterContext.builder().mediaType((MediaType) null));
        assertThrows(NullPointerException.class, () -> FormatterContext.builder().metricsConfig((MetricsConfig) null));
    }

    @Test
    void selectionsAreImmutableSnapshotsIncludingTagValues() {
        List<String> colors = new ArrayList<>(List.of("blue"));
        Map<String, Collection<String>> tags = new LinkedHashMap<>();
        tags.put("color", colors);
        List<String> names = new ArrayList<>(List.of("requests"));
        var builder = FormatterContext.builder()
                .mediaType(MediaTypes.TEXT_PLAIN)
                .metricsConfig(MetricsConfig.create())
                .tagSelections(tags)
                .nameSelection(names);
        var context = builder.build();

        colors.add("red");
        tags.put("status", List.of("success"));
        names.add("duration");
        builder.tagSelections(Map.of("status", List.of("failure"))).nameSelection(List.of("other"));

        assertThat(context.tagSelections(), is(Map.of("color", List.of("blue"))));
        assertThat(context.nameSelection(), is(List.of("requests")));
        assertThrows(UnsupportedOperationException.class, () -> context.tagSelections().put("status", List.of("success")));
        assertThrows(UnsupportedOperationException.class, () -> context.tagSelections().get("color").add("red"));
        assertThrows(UnsupportedOperationException.class, () -> context.nameSelection().add("other"));
    }

    @Test
    void iterableNamesAreConsumedOnceWhenSet() {
        List<String> names = new ArrayList<>(List.of("requests", "duration"));
        var iterator = names.iterator();
        Iterable<String> singleUseNames = () -> iterator;
        var builder = FormatterContext.builder()
                .mediaType(MediaTypes.TEXT_PLAIN)
                .metricsConfig(MetricsConfig.create())
                .nameSelection(singleUseNames);
        names.clear();

        assertThat(builder.build().nameSelection(), is(List.of("requests", "duration")));
        assertThat(builder.build().nameSelection(), is(List.of("requests", "duration")));
        builder.nameSelection((Iterable<String>) List.of("replacement"));
        assertThat(builder.build().nameSelection(), is(List.of("replacement")));
    }

    @Test
    void nullSelectionCollectionsAndEntriesAreRejected() {
        var builder = FormatterContext.builder()
                .mediaType(MediaTypes.TEXT_PLAIN)
                .metricsConfig(MetricsConfig.create());

        assertThrows(NullPointerException.class, () -> builder.tagSelections(null));
        assertThrows(NullPointerException.class, () -> builder.nameSelection((List<String>) null));
        assertThrows(NullPointerException.class, () -> builder.nameSelection((Iterable<String>) null));

        Map<String, Collection<String>> tags = new LinkedHashMap<>();
        tags.put(null, List.of("blue"));
        assertThrows(NullPointerException.class, () -> builder.tagSelections(tags).build());
        tags.clear();
        tags.put("color", null);
        assertThrows(NullPointerException.class, () -> builder.tagSelections(tags).build());
        List<String> values = new ArrayList<>();
        values.add(null);
        tags.put("color", values);
        assertThrows(NullPointerException.class, () -> builder.tagSelections(tags).build());
        builder.tagSelections(Map.of());
        assertThrows(NullPointerException.class, () -> builder.nameSelection(values).build());
        assertThrows(NullPointerException.class, () -> builder.nameSelection((Iterable<String>) values));
    }
}
