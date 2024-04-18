/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.tracing.providers.tests;

import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanListener;

/**
 * Test listener.
 */
public class AutoLoadedSpanListener implements SpanListener {

    /**
     * After start value.
     */
    public static final String AFTER_START = "101";

    /**
     * After activate value.
     */
    public static final String AFTER_ACTIVATE = "102";

    /**
     * After close value.
     */
    public static final String AFTER_CLOSE = "103";

    /**
     * After end OK value.
     */
    public static final String AFTER_END_OK = "104";

    /**
     * After end not OK value.
     */
    public static final String AFTER_END_BAD = "105";

    /**
     * For service loading.
     */
    public AutoLoadedSpanListener() {
    }

    @Override
    public void started(Span span) throws SpanListener.ForbiddenOperationException {
        span.baggage().set("auto-started", AFTER_START);
    }

    @Override
    public void activated(Span span, Scope scope) throws SpanListener.ForbiddenOperationException {
        span.baggage().set("auto-activated", AFTER_ACTIVATE);
    }

    @Override
    public void closed(Span span, Scope scope) throws SpanListener.ForbiddenOperationException {
        span.baggage().set("auto-closed", AFTER_CLOSE);
    }

    @Override
    public void ended(Span span) {
        span.baggage().set("auto-afterEndOk", AFTER_END_OK);
    }

    @Override
    public void ended(Span span, Throwable t) {
        span.baggage().set("auto-afterEndBad", AFTER_END_BAD);
    }
}
