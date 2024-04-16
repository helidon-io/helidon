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

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanListener;

class TestSpanListener implements SpanListener {


    private Consumer<Span.Builder<?>> beforeStartCheck;
    private Consumer<Span> afterStartCheck;
    private BiConsumer<Span, Scope> afterActivateCheck;
    private BiConsumer<Span, Scope> afterCloseCheck;
    private Consumer<Span> afterEndOkCheck;
    private BiConsumer<Span, Throwable> afterEndFailureCheck;

    @Override
    public void starting(Span.Builder<?> spanBuilder) throws UnsupportedOperationException {
        if (beforeStartCheck != null) {
            beforeStartCheck.accept(spanBuilder);
        }
    }

    @Override
    public void started(Span span) throws UnsupportedOperationException {
        if (beforeStartCheck != null) {
            afterStartCheck.accept(span);
        }
    }

    @Override
    public void activated(Span span, Scope scope) throws UnsupportedOperationException {
        if (afterActivateCheck != null) {
            afterActivateCheck.accept(span, scope);
        }
    }

    @Override
    public void closed(Span span, Scope scope) throws UnsupportedOperationException {
        if (afterCloseCheck != null) {
            afterCloseCheck.accept(span, scope);
        }
    }

    @Override
    public void ended(Span span) {
        if (afterEndOkCheck != null) {
            afterEndOkCheck.accept(span);
        }
    }

    @Override
    public void ended(Span span, Throwable t) {
        if (afterEndFailureCheck != null) {
            afterEndFailureCheck.accept(span, t);
        }
    }

    TestSpanListener beforeStart(Consumer<Span.Builder<?>> beforeStart) {
        this.beforeStartCheck = beforeStart;
        return this;
    }

    TestSpanListener afterStart(Consumer<Span> afterStart) {
        this.afterStartCheck = afterStart;
        return this;
    }

    TestSpanListener afterActivate(BiConsumer<Span, Scope> afterActivate) {
        this.afterActivateCheck = afterActivate;
        return this;
    }

    TestSpanListener afterClose(BiConsumer<Span, Scope> afterClose) {
        this.afterCloseCheck = afterClose;
        return this;
    }

    TestSpanListener afterEndOk(Consumer<Span> afterEnd) {
        this.afterEndOkCheck = afterEnd;
        return this;
    }

    TestSpanListener afterEndFailure(BiConsumer<Span, Throwable> afterEnd) {
        this.afterEndFailureCheck = afterEnd;
        return this;
    }
}
