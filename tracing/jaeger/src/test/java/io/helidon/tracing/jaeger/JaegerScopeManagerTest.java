/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import io.jaegertracing.internal.JaegerTracer;
import io.opentracing.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

class JaegerScopeManagerTest {

    private static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();

    private final JaegerTracer tracer;

    JaegerScopeManagerTest() {
        JaegerTracer.Builder builder = new JaegerTracer.Builder("test-scopes");
        builder.withScopeManager(new JaegerScopeManager());
        tracer = builder.build();
    }

    /**
     * Clean SCOPES due to other unit tests in this package running on same VM.
     */
    @BeforeEach
    void clearScopes() {
        JaegerScopeManager.SCOPES.clear();
    }

    @Test
    void testScopeManager() {
        JaegerScopeManager scopeManager = (JaegerScopeManager) tracer.scopeManager();
        Span span = tracer.buildSpan("test-span").start();
        JaegerScopeManager.ThreadScope scope = (JaegerScopeManager.ThreadScope) scopeManager.activate(span);
        assertThat(scope.isClosed(), is(false));
        assertThat(scopeManager.activeSpan(), is(span));
        scope.close();
        assertThat(scopeManager.activeSpan(), nullValue());
        assertThat(scope.isClosed(), is(true));
        assertThat(JaegerScopeManager.SCOPES.size(), is(0));
    }

    @Test
    void testScopeManagerThreads() throws Exception {
        JaegerScopeManager scopeManager = (JaegerScopeManager) tracer.scopeManager();
        Span span = tracer.buildSpan("test-span").start();
        JaegerScopeManager.ThreadScope scope = (JaegerScopeManager.ThreadScope) scopeManager.activate(span);
        assertThat(scope.isClosed(), is(false));
        assertThat(scopeManager.activeSpan(), is(span));
        EXECUTOR_SERVICE.submit(scope::close).get(100, TimeUnit.MILLISECONDS);      // different thread
        assertThat(scopeManager.activeSpan(), nullValue());
        assertThat(scope.isClosed(), is(true));
        assertThat(JaegerScopeManager.SCOPES.size(), is(0));
    }

    @Test
    void testScopeManagerStack() {
        JaegerScopeManager scopeManager = (JaegerScopeManager) tracer.scopeManager();
        Span span1 = tracer.buildSpan("test-span1").start();
        JaegerScopeManager.ThreadScope scope1 = (JaegerScopeManager.ThreadScope) scopeManager.activate(span1);
        assertThat(scopeManager.activeSpan(), is(span1));
        Span span2 = tracer.buildSpan("test-span2").start();
        JaegerScopeManager.ThreadScope scope2 = (JaegerScopeManager.ThreadScope) scopeManager.activate(span2);
        assertThat(scopeManager.activeSpan(), is(span2));
        scope2.close();
        assertThat(scopeManager.activeSpan(), is(span1));
        scope1.close();
        assertThat(scopeManager.activeSpan(), nullValue());
        assertThat(JaegerScopeManager.SCOPES.size(), is(0));
    }

    @Test
    void testScopeManagerStackUnorderedClose2() {
        JaegerScopeManager scopeManager = (JaegerScopeManager) tracer.scopeManager();
        Span span1 = tracer.buildSpan("test-span1").start();
        JaegerScopeManager.ThreadScope scope1 = (JaegerScopeManager.ThreadScope) scopeManager.activate(span1);
        assertThat(scopeManager.activeSpan(), is(span1));
        Span span2 = tracer.buildSpan("test-span2").start();
        JaegerScopeManager.ThreadScope scope2 = (JaegerScopeManager.ThreadScope) scopeManager.activate(span2);
        assertThat(scopeManager.activeSpan(), is(span2));

        scope1.close();                                         // out of order
        assertThat(scope1.isClosed(), is(false));
        assertThat(scopeManager.activeSpan(), is(span2));

        scope2.close();                                         // should close both scopes
        assertThat(scope1.isClosed(), is(true));
        assertThat(scope2.isClosed(), is(true));
        assertThat(scopeManager.activeSpan(), nullValue());
        assertThat(JaegerScopeManager.SCOPES.size(), is(0));
    }

    @Test
    void testScopeManagerStackUnorderedClose3() {
        JaegerScopeManager scopeManager = (JaegerScopeManager) tracer.scopeManager();
        Span span1 = tracer.buildSpan("test-span1").start();
        JaegerScopeManager.ThreadScope scope1 = (JaegerScopeManager.ThreadScope) scopeManager.activate(span1);
        assertThat(scopeManager.activeSpan(), is(span1));
        Span span2 = tracer.buildSpan("test-span2").start();
        JaegerScopeManager.ThreadScope scope2 = (JaegerScopeManager.ThreadScope) scopeManager.activate(span2);
        assertThat(scopeManager.activeSpan(), is(span2));
        Span span3 = tracer.buildSpan("test-span3").start();
        JaegerScopeManager.ThreadScope scope3 = (JaegerScopeManager.ThreadScope) scopeManager.activate(span3);
        assertThat(scopeManager.activeSpan(), is(span3));

        scope1.close();                                         // out of order
        assertThat(scope1.isClosed(), is(false));
        assertThat(scopeManager.activeSpan(), is(span3));

        scope2.close();                                         // out of order
        assertThat(scope2.isClosed(), is(false));
        assertThat(scopeManager.activeSpan(), is(span3));

        scope3.close();                                          // should close all scopes
        assertThat(scope1.isClosed(), is(true));
        assertThat(scope2.isClosed(), is(true));
        assertThat(scope3.isClosed(), is(true));
        assertThat(scopeManager.activeSpan(), nullValue());
        assertThat(JaegerScopeManager.SCOPES.size(), is(0));
    }

    @Test
    void testScopeManagerConcurrent() throws InterruptedException {
        int nscopes = 256;
        JaegerScopeManager scopeManager = (JaegerScopeManager) tracer.scopeManager();
        Set<JaegerScopeManager.ThreadScope> scopes = new CopyOnWriteArraySet<>();
        CountDownLatch latch = new CountDownLatch(nscopes);
        IntStream.range(0, nscopes).parallel().forEach(i -> {
            Span span = tracer.buildSpan("test-span").start();
            JaegerScopeManager.ThreadScope scope = (JaegerScopeManager.ThreadScope) scopeManager.activate(span);
            scopes.add(scope);
            latch.countDown();
        });
        assertThat(latch.await(5, TimeUnit.SECONDS), is(true));
        assertThat(JaegerScopeManager.SCOPES.size(), is(greaterThan(0)));
        scopes.stream().parallel().forEach(JaegerScopeManager.ThreadScope::close);
        assertThat(JaegerScopeManager.SCOPES.size(), is(0));
    }
}
