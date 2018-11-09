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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        assertThat(reg.counter.get(), is(1));
        reg.resume();
        assertThat(reg.counter.get(), is(2));
    }

    @Test
    void noProcessIfNoHandler() {
        CountingRegistry<String> reg = new CountingRegistry<>();
        assertThat(reg.canProcess(), is(false));
        reg.resume();
        assertThat(reg.canProcess(), is(false));
        reg.handle((data, psb) -> {}, null, null);
        assertThat(reg.canProcess(), is(true));
        assertThat(reg.paused(), is(false));
        assertThat(reg.canContinueProcessing(), is(true));
    }

    @Test
    void processByHandlerRegistration() {
        CountingRegistry<String> reg = new CountingRegistry<>();
        assertThat(reg.counter.get(), is(0));
        reg.handle((data, psb) -> {}, null, null);
        assertThat(reg.counter.get(), is(1));
    }

    @Test
    void pauseResume() {
        CountingRegistry<String> reg = new CountingRegistry<>();
        reg.handle((data, psb) -> {}, null, null);
        assertThat(reg.canProcess(), is(true));
        assertThat(reg.paused(), is(false));
        assertThat(reg.canContinueProcessing(), is(true));
        reg.pause();
        assertThat(reg.canContinueProcessing(), is(false));
        assertThat(reg.paused(), is(true));
        assertThat(reg.canProcess(), is(false));
        reg.resume();
        assertThat(reg.canProcess(), is(true));
        assertThat(reg.paused(), is(false));
        assertThat(reg.canContinueProcessing(), is(true));
    }

    @Test
    void notTwoProcessors() {
        CountingRegistry<String> reg = new CountingRegistry<>();
        reg.handle((data, psb) -> {}, null, null);
        assertThat(reg.canProcess(), is(true));
        assertThat(reg.canProcess(), is(false));
        reg.releaseProcessing();
        assertThat(reg.canProcess(), is(true));
    }

    @Test
    void reportError() {
        AtomicReference<Throwable> ref = new AtomicReference<>();
        CountingRegistry<String> reg = new CountingRegistry<>();
        reg.handle((data, psb) -> {}, ref::set, null);
        reg.handleError(new IOException());
        assertThat(ref.get(), notNullValue());
        assertThat(ref.get(), instanceOf(IOException.class));
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
