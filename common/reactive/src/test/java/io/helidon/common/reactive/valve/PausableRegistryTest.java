/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.reactive.valve;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PausableRegistryTest {

    @Test
    void doubleHandlers() {
        CountingRegistry<String> reg = new CountingRegistry<>();
        reg.handle((data, psb) -> {}, null, null);
        assertThrows(IllegalStateException.class, () -> reg.handle((data, psb) -> {
        }, null, null));
    }

    @Test
    void resumeCallsTryProcess() {
        CountingRegistry reg = new CountingRegistry();
        reg.resume();
        assertEquals(1, reg.counter.get());
        reg.resume();
        assertEquals(2, reg.counter.get());
    }

    @Test
    void noProcessIfNoHandler() {
        CountingRegistry<String> reg = new CountingRegistry<>();
        assertFalse(reg.canProcess());
        reg.resume();
        assertFalse(reg.canProcess());
        reg.handle((data, psb) -> {}, null, null);
        assertTrue(reg.canProcess());
        assertFalse(reg.paused());
        assertTrue(reg.canContinueProcessing());
    }

    @Test
    void processByHandlerRegistration() {
        CountingRegistry<String> reg = new CountingRegistry<>();
        assertEquals(0, reg.counter.get());
        reg.handle((data, psb) -> {}, null, null);
        assertEquals(1, reg.counter.get());
    }

    @Test
    void pauseResume() {
        CountingRegistry<String> reg = new CountingRegistry<>();
        reg.handle((data, psb) -> {}, null, null);
        assertTrue(reg.canProcess());
        assertFalse(reg.paused());
        assertTrue(reg.canContinueProcessing());
        reg.pause();
        assertFalse(reg.canContinueProcessing());
        assertFalse(reg.canProcess());
        reg.resume();
        assertTrue(reg.canProcess());
        assertTrue(reg.canContinueProcessing());
    }

    @Test
    void notTwoProcessors() {
        CountingRegistry<String> reg = new CountingRegistry<>();
        reg.handle((data, psb) -> {}, null, null);
        assertTrue(reg.canProcess());
        assertFalse(reg.canProcess());
        reg.releaseProcessing();
        assertTrue(reg.canProcess());
    }

    @Test
    void reportError() {
        AtomicReference<Throwable> ref = new AtomicReference<>();
        CountingRegistry<String> reg = new CountingRegistry<>();
        reg.handle((data, psb) -> {}, ref::set, null);
        reg.handleError(new IOException());
        assertNotNull(ref.get());
        assertEquals(IOException.class, ref.get().getClass());
    }

    @Test
    void reportErrorIfNoRegisteredErrorHandler() {
        CountingRegistry<String> reg = new CountingRegistry<>();
        reg.handle((data, psb) -> {}, null, null);
        reg.handleError(new Exception("Just for test!"));
    }

    static class CountingRegistry<T> extends PausableRegistry<T> {

        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        protected void tryProcess() {
            counter.incrementAndGet();
        }
    }
}
