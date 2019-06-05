/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
 * Unit test for {@link TracedConfig}.
 */
class TracedConfigTest {
    private static final Optional<?> EMPTY = Optional.empty();
    private static final Optional<Boolean> FALSE = Optional.of(false);
    private static final Optional<Boolean> TRUE = Optional.of(true);
    private static Config config;

    @BeforeAll
    static void initClass() {
        config = Config.create();
    }

    @Test
    void testDisabledConfig() {
        testDisabled(TracedConfig.create(config.get("unit1.tracing")));
    }

    @Test
    void testDisabledBuilder() {
        testDisabled(TracedConfig.builder()
                             .enabled(false)
                             .addComponent("web-server", TracedComponent.builder()
                                     .enabled(true)
                                     .addSpan("HTTP Request", TracedSpan.builder()
                                             .enabled(true)
                                             .addSpanLog("content-read", TracedSpanLog.ENABLED)
                                             .build())
                                     .build())
                             .build());
    }

    void testDisabled(TracedConfig tracedConfig) {

        // make sure everything is disabled
        assertThat(tracedConfig.enabled(), is(FALSE));
        Optional<TracedComponent> component = tracedConfig.component("web-server");
        assertThat(component, not(EMPTY));
        TracedComponent ws = component.get();
        assertThat(ws.enabled(), is(FALSE));
        Optional<TracedSpan> span = ws.span("HTTP Request");
        assertThat(span, not(EMPTY));
        TracedSpan tracedSpan = span.get();
        assertThat(tracedSpan.enabled(), is(FALSE));
        Optional<TracedSpanLog> tracedSpanLog = tracedSpan.spanLog("content-read");
        assertThat(tracedSpanLog, not(EMPTY));
        TracedSpanLog log = tracedSpanLog.get();
        assertThat(log.enabled(), is(FALSE));
        tracedSpanLog = tracedSpan.spanLog("undefined");
        assertThat(tracedSpanLog, not(EMPTY));
        log = tracedSpanLog.get();
        assertThat(log.enabled(), is(FALSE));

        // if disabled, than all under it should be disabled (even if not defined in config)
        component = tracedConfig.component("not-defined");
        assertThat(component, not(EMPTY));
        ws = component.get();
        assertThat(ws.enabled(), is(FALSE));
        span = ws.span("also-undefined");
        assertThat(span, not(EMPTY));
        tracedSpan = span.get();
        assertThat(tracedSpan.enabled(), is(FALSE));
    }

    @Test
    void testMerge() {
        TracedConfig older = TracedConfig.create(config.get("unit3.tracing-older"));
        TracedConfig newer = TracedConfig.create(config.get("unit3.tracing-newer"));

        TracedConfig merged = TracedConfig.merge(older, newer);

        // always enabled
        assertThat(older.enabled(), is(TRUE));
        assertThat(newer.enabled(), is(EMPTY));
        assertThat(merged.enabled(), is(TRUE));

        // webserver in each
        Optional<TracedComponent> component = older.component("web-server");
        assertThat(component, not(EMPTY));
        TracedComponent ws = component.get();
        assertThat(ws.enabled(), is(TRUE));

        component = newer.component("web-server");
        assertThat(component, not(EMPTY));
        ws = component.get();
        // in newer disabled
        assertThat(ws.enabled(), is(FALSE));

        component = merged.component("web-server");
        assertThat(component, not(EMPTY));
        ws = component.get();
        // and merged should be disabled as well
        assertThat(ws.enabled(), is(FALSE));

        // security only in new and merged
        component = older.component("security");
        assertThat(component, is(EMPTY));

        component = newer.component("security");
        assertThat(component, not(EMPTY));
        TracedComponent security = component.get();
        assertThat(security.enabled(), is(EMPTY));
        // now make sure the span is disabled
        Optional<TracedSpan> tracedSpan = security.span("security:atn");
        assertThat(tracedSpan, not(EMPTY));
        TracedSpan span = tracedSpan.get();
        assertThat(span.enabled(), is(FALSE));

        component = merged.component("security");
        assertThat(component, not(EMPTY));
        security = component.get();
        assertThat(security.enabled(), is(EMPTY));
        // now make sure the span is disabled
        tracedSpan = security.span("security:atn");
        assertThat(tracedSpan, not(EMPTY));
        span = tracedSpan.get();
        assertThat(span.enabled(), is(FALSE));
        assertThat(span.logEnabled("any"), is(false));

    }

    @Test
    void testEnabled() {
        Config unitConfig = config.get("unit2.tracing");
        TracedConfig tracedConfig = TracedConfig.create(unitConfig);

        assertThat(tracedConfig.enabled(), is(TRUE));
        Optional<TracedComponent> component = tracedConfig.component("web-server");
        assertThat(component, not(EMPTY));
        TracedComponent ws = component.get();
        assertThat(ws.enabled(), is(TRUE));

        Optional<TracedSpan> span = ws.span("HTTP Request");
        assertThat(span, not(EMPTY));
        TracedSpan tracedSpan = span.get();
        assertThat(tracedSpan.enabled(), is(TRUE));

        Optional<TracedSpanLog> tracedSpanLog = tracedSpan.spanLog("content-read");
        assertThat(tracedSpanLog, not(EMPTY));
        TracedSpanLog log = tracedSpanLog.get();
        assertThat(log.enabled(), is(TRUE));
        tracedSpanLog = tracedSpan.spanLog("undefined");
        assertThat(tracedSpanLog, is(EMPTY));

        span = ws.span("disabled");
        assertThat(span, not(EMPTY));
        tracedSpan = span.get();
        assertThat(tracedSpan.enabled(), is(FALSE));

        tracedSpanLog = tracedSpan.spanLog("content-write");
        assertThat(tracedSpanLog, not(EMPTY));
        log = tracedSpanLog.get();
        assertThat(log.enabled(), is(FALSE));
        tracedSpanLog = tracedSpan.spanLog("undefined");
        assertThat(tracedSpanLog, not(EMPTY));
        log = tracedSpanLog.get();
        assertThat(log.enabled(), is(FALSE));

        // if parent enabled, than return empty if not defined
        span = ws.span("undefined");
        assertThat(span, is(EMPTY));

        // if tracing enabled, return empty if undefined
        component = tracedConfig.component("not-defined");
        assertThat(component, is(EMPTY));

        // if component disabled, than all under it should be disabled (even if not defined in config)
        component = tracedConfig.component("security");
        assertThat(component, not(EMPTY));
        ws = component.get();
        assertThat(ws.enabled(), is(FALSE));

        // even spans that have enabled set to true should be disabled if component is disabled
        span = ws.span("security:atn");
        assertThat(span, not(EMPTY));
        tracedSpan = span.get();
        assertThat(tracedSpan.enabled(), is(FALSE));

        span = ws.span("security:atz");
        assertThat(span, not(EMPTY));
        tracedSpan = span.get();
        assertThat(tracedSpan.enabled(), is(FALSE));
    }
}