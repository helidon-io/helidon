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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.jaegertracing.internal.JaegerTracer;
import io.opentracing.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JaegerScopeManagerTest {

    private final JaegerTracer tracer;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    JaegerScopeManagerTest() {
        JaegerTracer.Builder builder = new JaegerTracer.Builder("test-scopes");
        builder.withScopeManager(new JaegerScopeManager());
        tracer = builder.build();
    }

    @BeforeEach
    void clearScopes() {
        JaegerScopeManager scopeManager = (JaegerScopeManager) tracer.scopeManager();
        scopeManager.clearScopes();
    }

    @Test
    void testScopeManager() {
        JaegerScopeManager scopeManager = (JaegerScopeManager) tracer.scopeManager();
        Span span = tracer.buildSpan("test-span").start();
        JaegerScopeManager.ThreadScope scope = (JaegerScopeManager.ThreadScope) scopeManager.activate(span);
        assertFalse(scope.isClosed());
        assertEquals(scopeManager.activeSpan(), span);
        scope.close();
        assertNull(scopeManager.activeSpan());
        assertTrue(scope.isClosed());
    }

    @Test
    void testScopeManagerThreads() throws Exception {
        JaegerScopeManager scopeManager = (JaegerScopeManager) tracer.scopeManager();
        Span span = tracer.buildSpan("test-span").start();
        JaegerScopeManager.ThreadScope scope = (JaegerScopeManager.ThreadScope) scopeManager.activate(span);
        assertFalse(scope.isClosed());
        assertEquals(scopeManager.activeSpan(), span);
        executor.submit(scope::close).get(100, TimeUnit.MILLISECONDS);      // different thread
        assertNull(scopeManager.activeSpan());
        assertTrue(scope.isClosed());
    }

    @Test
    void testScopeManagerStack() {
        JaegerScopeManager scopeManager = (JaegerScopeManager) tracer.scopeManager();
        Span span1 = tracer.buildSpan("test-span1").start();
        JaegerScopeManager.ThreadScope scope1 = (JaegerScopeManager.ThreadScope) scopeManager.activate(span1);
        assertEquals(scopeManager.activeSpan(), span1);
        Span span2 = tracer.buildSpan("test-span2").start();
        JaegerScopeManager.ThreadScope scope2 = (JaegerScopeManager.ThreadScope) scopeManager.activate(span2);
        assertEquals(scopeManager.activeSpan(), span2);
        scope2.close();
        assertEquals(scopeManager.activeSpan(), span1);
        scope1.close();
        assertNull(scopeManager.activeSpan());
    }
}
