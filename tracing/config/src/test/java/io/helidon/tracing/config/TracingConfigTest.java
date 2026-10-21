/*
 * Copyright (c) 2019, 2026 Oracle and/or its affiliates.
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

package io.helidon.tracing.config;

import java.util.Optional;

import io.helidon.config.Config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link TracingConfig}.
 */
class TracingConfigTest {
    private static final Optional<?> EMPTY = Optional.empty();
    private static Config config;

    @BeforeAll
    static void initClass() {
        config = Config.create();
    }

    @Test
    void testDisabledConfig() {
        testDisabled(TracingConfig.create(config.get("unit1.tracing")));
    }

    @Test
    void testDisabledBuilder() {
        testDisabled(TracingConfig.builder()
                             .enabled(false)
                             .addComponent(ComponentTracingConfig.builder("web-server")
                                     .enabled(true)
                                     .addSpan(SpanTracingConfig.builder("HTTP Request")
                                             .enabled(true)
                                             .addSpanLog(SpanLogTracingConfig.builder("content-read").build())
                                             .build())
                                     .build())
                             .build());
    }

    @Test
    void testEnabledComponentBuilderReuse() {
        testComponentBuilderReuse(ComponentTracingConfig.builder("component").enabled(true), Optional.of(true));
    }

    @Test
    void testDefaultEnabledComponentBuilderReuse() {
        testComponentBuilderReuse(ComponentTracingConfig.builder("component"), Optional.empty());
    }

    @Test
    void testEnabledSpanBuilderReuse() {
        testSpanBuilderReuse(SpanTracingConfig.builder("span").enabled(true), Optional.of(true));
    }

    @Test
    void testDefaultEnabledSpanBuilderReuse() {
        testSpanBuilderReuse(SpanTracingConfig.builder("span"), Optional.empty());
    }

    @Test
    void testMergeAfterBuilderReuse() {
        var componentBuilder = ComponentTracingConfig.builder("component");
        var spanBuilder = SpanTracingConfig.builder("span")
                .addSpanLog(SpanLogTracingConfig.builder("disabled").enabled(false).build());
        var newerSpan = spanBuilder.build();
        var newerComponent = componentBuilder.addSpan(newerSpan).build();
        var olderSpan = SpanTracingConfig.builder("span")
                .enabled(true)
                .newName("renamed")
                .addSpanLog(SpanLogTracingConfig.builder("inherited").enabled(true).build())
                .addSpanLog(SpanLogTracingConfig.builder("disabled").enabled(true).build())
                .build();
        var older = TracingConfig.builder()
                .addComponent(ComponentTracingConfig.builder("component")
                                      .enabled(true)
                                      .addSpan(olderSpan)
                                      .build())
                .build();
        var newer = TracingConfig.builder().addComponent(newerComponent).build();

        componentBuilder.enabled(false);
        spanBuilder.enabled(false);

        var merged = TracingConfig.merge(older, newer);
        var component = merged.component("component");
        var span = component.span("span");
        assertThat(component.enabled(), is(true));
        assertThat(component.span("undefined", false).enabled(), is(false));
        assertThat(span.enabled(), is(true));
        assertThat(span.newName(), is(Optional.of("renamed")));
        assertThat(span.spanLog("inherited").enabled(), is(true));
        assertThat(span.spanLog("disabled").enabled(), is(false));
        assertThat(span.logEnabled("undefined", false), is(false));
    }

    void testDisabled(TracingConfig tracingConfig) {

        // make sure everything is disabled
        assertThat(tracingConfig.enabled(), is(false));

        ComponentTracingConfig ws = tracingConfig.component("web-server");
        assertThat(ws.enabled(), is(false));

        SpanTracingConfig spanTracingConfig = ws.span("HTTP Request");
        assertThat(spanTracingConfig.enabled(), is(false));

        SpanLogTracingConfig log = spanTracingConfig.spanLog("content-read");
        assertThat(log.enabled(), is(false));

        log = spanTracingConfig.spanLog("undefined");
        assertThat(log.enabled(), is(false));

        // if disabled, than all under it should be disabled (even if not defined in config)
        ws = tracingConfig.component("not-defined");
        assertThat(ws.enabled(), is(false));

        spanTracingConfig = ws.span("also-undefined");
        assertThat(spanTracingConfig.enabled(), is(false));
    }

    @Test
    void testMerge() {
        TracingConfig older = TracingConfig.create(config.get("unit3.tracing-older"));
        TracingConfig newer = TracingConfig.create(config.get("unit3.tracing-newer"));

        TracingConfig merged = TracingConfig.merge(older, newer);

        // always enabled
        assertThat(older.enabled(), is(true));
        assertThat(newer.isEnabled(), is(EMPTY));
        assertThat(merged.enabled(), is(true));

        // webserver in each
        ComponentTracingConfig ws = older.component("web-server");
        assertThat(ws.enabled(), is(true));

        ws = newer.component("web-server");
        // in newer disabled
        assertThat(ws.enabled(), is(false));

        ws = merged.component("web-server");
        // and merged should be disabled as well
        assertThat(ws.enabled(), is(false));

        // security only in new and merged
        Optional<ComponentTracingConfig> component = older.getComponent("security");
        assertThat(component, is(EMPTY));

        component = newer.getComponent("security");
        assertThat(component, not(EMPTY));
        ComponentTracingConfig security = component.get();
        assertThat(security.isEnabled(), is(EMPTY));

        // now make sure the span is disabled
        Optional<SpanTracingConfig> tracedSpan = security.getSpan("security:atn");
        assertThat(tracedSpan, not(EMPTY));
        SpanTracingConfig span = tracedSpan.get();
        assertThat(span.enabled(), is(false));

        component = merged.getComponent("security");
        assertThat(component, not(EMPTY));
        security = component.get();
        assertThat(security.isEnabled(), is(EMPTY));
        // now make sure the span is disabled
        tracedSpan = security.getSpan("security:atn");
        assertThat(tracedSpan, not(EMPTY));
        span = tracedSpan.get();
        assertThat(span.enabled(), is(false));
        assertThat(span.logEnabled("any", true), is(false));

    }

    @Test
    void testEnabled() {
        Config unitConfig = config.get("unit2.tracing");
        TracingConfig tracingConfig = TracingConfig.create(unitConfig);

        assertThat(tracingConfig.enabled(), is(true));
        Optional<ComponentTracingConfig> component = tracingConfig.getComponent("web-server");
        assertThat(component, not(EMPTY));
        ComponentTracingConfig ws = component.get();
        assertThat(ws.enabled(), is(true));

        Optional<SpanTracingConfig> span = ws.getSpan("HTTP Request");
        assertThat(span, not(EMPTY));
        SpanTracingConfig spanTracingConfig = span.get();
        assertThat(spanTracingConfig.enabled(), is(true));

        Optional<SpanLogTracingConfig> tracedSpanLog = spanTracingConfig.getSpanLog("content-read");
        assertThat(tracedSpanLog, not(EMPTY));
        SpanLogTracingConfig log = tracedSpanLog.get();
        assertThat(log.enabled(), is(true));
        tracedSpanLog = spanTracingConfig.getSpanLog("undefined");
        assertThat(tracedSpanLog, is(EMPTY));

        span = ws.getSpan("disabled");
        assertThat(span, not(EMPTY));
        spanTracingConfig = span.get();
        assertThat(spanTracingConfig.enabled(), is(false));

        tracedSpanLog = spanTracingConfig.getSpanLog("content-write");
        assertThat(tracedSpanLog, not(EMPTY));
        log = tracedSpanLog.get();
        assertThat(log.enabled(), is(false));
        tracedSpanLog = spanTracingConfig.getSpanLog("undefined");
        assertThat(tracedSpanLog, not(EMPTY));
        log = tracedSpanLog.get();
        assertThat(log.enabled(), is(false));

        // if parent enabled, than return empty if not defined
        span = ws.getSpan("undefined");
        assertThat(span, is(EMPTY));

        // if tracing enabled, return empty if undefined
        component = tracingConfig.getComponent("not-defined");
        assertThat(component, is(EMPTY));

        // if component disabled, than all under it should be disabled (even if not defined in config)
        component = tracingConfig.getComponent("security");
        assertThat(component, not(EMPTY));
        ws = component.get();
        assertThat(ws.enabled(), is(false));

        // even spans that have enabled set to true should be disabled if component is disabled
        span = ws.getSpan("security:atn");
        assertThat(span, not(EMPTY));
        spanTracingConfig = span.get();
        assertThat(spanTracingConfig.enabled(), is(false));

        span = ws.getSpan("security:atz");
        assertThat(span, not(EMPTY));
        spanTracingConfig = span.get();
        assertThat(spanTracingConfig.enabled(), is(false));
    }

    private static void testComponentBuilderReuse(ComponentTracingConfig.Builder builder, Optional<Boolean> expectedEnabled) {
        var original = builder.addSpan(SpanTracingConfig.builder("enabled").enabled(true).newName("renamed").build())
                .addSpan(SpanTracingConfig.builder("disabled").enabled(false).build())
                .build();
        var disabled = builder.enabled(false).build();

        assertThat(original.isEnabled(), is(expectedEnabled));
        assertThat(original.enabled(), is(true));
        assertThat(original.span("enabled", false).enabled(), is(true));
        assertThat(original.span("enabled").newName(), is(Optional.of("renamed")));
        assertThat(original.span("disabled").enabled(), is(false));
        assertThat(original.getSpan("undefined"), is(Optional.empty()));
        assertThat(original.span("undefined").enabled(), is(true));
        assertThat(original.span("undefined", false).enabled(), is(false));

        var reenabled = builder.enabled(true).build();

        assertThat(disabled.isEnabled(), is(Optional.of(false)));
        assertThat(disabled.enabled(), is(false));
        assertThat(disabled.getSpan("enabled").orElseThrow().enabled(), is(false));
        assertThat(disabled.span("enabled").enabled(), is(false));
        assertThat(disabled.span("undefined").enabled(), is(false));
        assertThat(reenabled.isEnabled(), is(Optional.of(true)));
        assertThat(reenabled.span("enabled").enabled(), is(true));
        assertThat(reenabled.span("disabled").enabled(), is(false));
    }

    private static void testSpanBuilderReuse(SpanTracingConfig.Builder builder, Optional<Boolean> expectedEnabled) {
        var original = builder.newName("original")
                .addSpanLog(SpanLogTracingConfig.builder("enabled").enabled(true).build())
                .addSpanLog(SpanLogTracingConfig.builder("disabled").enabled(false).build())
                .build();
        var disabled = builder.enabled(false).newName("updated").build();

        assertThat(original.isEnabled(), is(expectedEnabled));
        assertThat(original.enabled(), is(true));
        assertThat(original.newName(), is(Optional.of("original")));
        assertThat(original.spanLog("enabled").enabled(), is(true));
        assertThat(original.logEnabled("enabled", false), is(true));
        assertThat(original.spanLog("disabled").enabled(), is(false));
        assertThat(original.logEnabled("disabled", true), is(false));
        assertThat(original.getSpanLog("undefined"), is(Optional.empty()));
        assertThat(original.spanLog("undefined").enabled(), is(true));
        assertThat(original.logEnabled("undefined", true), is(true));
        assertThat(original.logEnabled("undefined", false), is(false));

        var reenabled = builder.enabled(true).build();

        assertThat(disabled.isEnabled(), is(Optional.of(false)));
        assertThat(disabled.enabled(), is(false));
        assertThat(disabled.getSpanLog("enabled").orElseThrow().enabled(), is(false));
        assertThat(disabled.spanLog("enabled").enabled(), is(false));
        assertThat(disabled.logEnabled("undefined", true), is(false));
        assertThat(reenabled.isEnabled(), is(Optional.of(true)));
        assertThat(reenabled.newName(), is(Optional.of("updated")));
        assertThat(reenabled.spanLog("enabled").enabled(), is(true));
        assertThat(reenabled.spanLog("disabled").enabled(), is(false));
    }
}
