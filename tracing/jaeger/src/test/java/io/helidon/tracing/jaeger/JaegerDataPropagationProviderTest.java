/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.tracing.jaeger;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

class JaegerDataPropagationProviderTest {

    private JaegerDataPropagationProvider provider = new JaegerDataPropagationProvider();
    
    @Test
    void dataPropagationTest() {
        Context context = Context.create();
        Tracer tracer = new TestTracer();
        context.register(tracer);
        Span span = tracer.buildSpan("span").start();
        context.register(span);
        Scope scope = tracer.scopeManager().activate(span);
        context.register(scope);

        Contexts.runInContext(context, () -> {
            assertThat(closed(scope), is(false));
            JaegerDataPropagationProvider.JaegerContext data = provider.data();
            assertThat(closed(scope), is(true));
            provider.propagateData(data);
            assertThat(closed(data.scope()), is(false));
            provider.clearData(data);
            assertThat(closed(data.scope()), is(true));
        });
    }

    private boolean closed(Scope scope) {
        return ((TestScope) scope).closed;
    }

    static class TestScope implements Scope {

        private final Scope delegate;
        private boolean closed = false;

        TestScope(Scope delegate) {
            this.delegate = delegate;
        }

        @Override
        public void close() {
            delegate.close();
            closed = true;
        }
    }

    static class TestScopeManager implements ScopeManager {

        private final ScopeManager delegate;

        TestScopeManager(ScopeManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public Scope activate(Span span) {
            return new TestScope(delegate.activate(span));
        }

        @Override
        public Span activeSpan() {
            return delegate.activeSpan();
        }
    }

    static class TestTracer implements Tracer {

        private final Tracer delegate = JaegerTracerBuilder.create().serviceName("test-service").build();

        @Override
        public ScopeManager scopeManager() {
            return new TestScopeManager(delegate.scopeManager());
        }

        @Override
        public Span activeSpan() {
            return delegate.activeSpan();
        }

        @Override
        public Scope activateSpan(Span span) {
            return delegate.activateSpan(span);
        }

        @Override
        public SpanBuilder buildSpan(String s) {
            return delegate.buildSpan(s);
        }

        @Override
        public <C> void inject(SpanContext spanContext, Format<C> format, C c) {
            delegate.inject(spanContext, format, c);
        }

        @Override
        public <C> SpanContext extract(Format<C> format, C c) {
            return delegate.extract(format, c);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
