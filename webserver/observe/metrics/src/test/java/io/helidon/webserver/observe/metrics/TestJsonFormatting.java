/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.webserver.observe.metrics;


import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.ScopingConfig;
import io.helidon.metrics.api.SystemTagsManager;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;

import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class TestJsonFormatting {
    private static final String SCOPE_TAG_NAME = "the-scope";

    @Test
    void testRetrievingAll() {
        MetricsConfig metricsConfig = MetricsConfig.builder()
                .scoping(ScopingConfig.builder()
                                 .tagName(SCOPE_TAG_NAME)
                                 .defaultValue("application")
                                 .tagEnabled(true))
                .build();

        MeterRegistry meterRegistry = Metrics.createMeterRegistry(metricsConfig);
        SystemTagsManager.instance(metricsConfig);

        Counter c = meterRegistry.getOrCreate(Counter.builder("c1"));
        assertThat("Initial counter value", c.count(), is(0L));
        c.increment();
        assertThat("After increment", c.count(), is(1L));

        Counter c1WithTag = meterRegistry.getOrCreate(Counter.builder("c1")
                                                              .tags(Set.of(Tag.create("t1", "v1"))));
        c1WithTag.increment(4L);

        Timer d = meterRegistry.getOrCreate(Timer.builder("t1"));
        d.record(3, TimeUnit.SECONDS);


        JsonFormatter formatter = JsonFormatter.builder(metricsConfig, meterRegistry)
                .scopeTagName(SCOPE_TAG_NAME)
                .build();

        JsonObject jsonOutput = checkAndCast(formatter.format());
        JsonObject app = jsonOutput.getJsonObject("application");
        assertThat("Counter 1",
                   app.getJsonNumber("c1;t1=v1;the-scope=application").intValue(),
                   is(4));
        assertThat("Counter 2",
                   app.getJsonNumber("c1;the-scope=application").intValue(),
                   is(1));
        JsonObject timerJson = app.getJsonObject("t1");
        assertThat("Timer", timerJson, notNullValue());
        assertThat("Timer count", timerJson.getJsonNumber("count;the-scope=application").intValue(), is(1));
    }


    @Test
    void testRetrievingByName() {
        MetricsConfig metricsConfig = MetricsConfig.builder()
                .scoping(ScopingConfig.builder()
                                 .tagName(SCOPE_TAG_NAME)
                                 .defaultValue("application")
                                 .tagEnabled(true))
                .build();

        MeterRegistry meterRegistry = Metrics.createMeterRegistry(metricsConfig);
        SystemTagsManager.instance(metricsConfig);

        Counter c = meterRegistry.getOrCreate(Counter.builder("c2"));
        assertThat("Initial counter value", c.count(), is(0L));
        c.increment();
        assertThat("After increment", c.count(), is(1L));

        Timer d = meterRegistry.getOrCreate(Timer.builder("t2"));
        d.record(7, TimeUnit.SECONDS);

        JsonFormatter formatter = JsonFormatter.builder(metricsConfig, meterRegistry)
                .meterNameSelection(Set.of("c2"))
                .build();

        JsonObject jsonOutput = checkAndCast(formatter.format());
        JsonObject app = jsonOutput.getJsonObject("application");
        assertThat("Counter 2", app.getJsonNumber("c2;the-scope=application").intValue(), is(1));

        assertThat("Timer", app.getJsonObject("t2"), nullValue());

    }

    private static JsonObject checkAndCast(Optional<Object> metricsOutput) {
        assertThat("Result", metricsOutput, OptionalMatcher.optionalPresent());
        assertThat("Result", metricsOutput.get(), is(instanceOf(JsonObject.class)));
        return (JsonObject) metricsOutput.get();
    }
}