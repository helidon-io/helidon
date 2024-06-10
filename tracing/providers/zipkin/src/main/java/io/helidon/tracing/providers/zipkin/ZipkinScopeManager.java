/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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
package io.helidon.tracing.providers.zipkin;

import java.util.List;

import io.helidon.tracing.SpanListener;

import brave.opentracing.BraveScopeManager;
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;

/**
 * Scope manager delegating to zipkin.
 * We need to "unwrap" the span before activating it, as otherwise we got a class cast exception.
 */
class ZipkinScopeManager implements ScopeManager {
    private static final System.Logger LOGGER = System.getLogger(ZipkinScopeManager.class.getName());
    private final BraveScopeManager scopeManager;
    private final List<SpanListener> spanListeners;

    ZipkinScopeManager(BraveScopeManager scopeManager, List<SpanListener> spanListeners) {
        this.scopeManager = scopeManager;
        this.spanListeners = spanListeners;
    }

    @Override
    public Scope activate(Span span) {
        ZipkinSpan zSpan = (span instanceof ZipkinSpan z) ? z : new ZipkinSpan(span, false, spanListeners);
        return new ZipkinScope(scopeManager.activate(unwrap(span)), zSpan, spanListeners);
    }

    private Span unwrap(Span span) {
        Span unwrapped = span;

        if (span instanceof ZipkinSpan) {
            unwrapped = ((ZipkinSpan) span).unwrap();
        }

        return unwrapped;
    }

    @Override
    public Span activeSpan() {
        return scopeManager.activeSpan();
    }

    private static class ZipkinScope implements Scope {

        private final Scope delegate;
        private final ZipkinSpan zSpan;
        private final List<SpanListener> spanListeners;
        private Limited limited;
        private boolean isClosed;

        private ZipkinScope(Scope delegate, ZipkinSpan zSpan, List<SpanListener> spanListeners) {
            this.delegate = delegate;
            this.zSpan = zSpan;
            this.spanListeners = spanListeners;
        }

        @Override
        public void close() {
            delegate.close();
            isClosed = true;
            ZipkinTracer.invokeListeners(spanListeners, LOGGER, listener -> listener.closed(zSpan.limited(), limited()));
        }

        Limited limited() {
            if (limited == null) {
                if (!spanListeners.isEmpty()) {
                    limited = new Limited(this);
                }
            }
            return limited;
        }

        static class Limited implements io.helidon.tracing.Scope {

            private final ZipkinScope delegate;

            private Limited(ZipkinScope delegate) {
                this.delegate = delegate;
            }

            @Override
            public void close() {
                throw new SpanListener.ForbiddenOperationException();
            }

            @Override
            public boolean isClosed() {
                return delegate.isClosed;
            }
        }
    }
}
