/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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

package io.helidon.faulttolerance;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.config.ConfigException;
import io.helidon.service.inject.InjectRegistryManager;
import io.helidon.service.inject.api.InjectRegistry;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.registry.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FallbackTest {
    private final AtomicInteger primaryCounter = new AtomicInteger();
    private final AtomicInteger fallbackCounter = new AtomicInteger();

    @BeforeEach
    void reset() {
        primaryCounter.set(0);
        fallbackCounter.set(0);
    }

    @Test
    void testFallback() {
        String result = Fallback.createFromMethod(this::fallback).invoke(this::primary);

        assertThat(result, is("fallback"));
        assertThat(primaryCounter.get(), is(1));
        assertThat(fallbackCounter.get(), is(1));
    }

    @Test
    void testFallbackFails() {
        ConfigException configException = assertThrows(ConfigException.class,
                                                       () -> Fallback.createFromMethod(this::fallbackFail).invoke(this::primary));
        Throwable[] suppressed = configException.getSuppressed();
        assertThat("Should have a suppressed exception: " + Arrays.toString(suppressed), suppressed.length, is(1));
        assertThat(suppressed[0], instanceOf(IllegalArgumentException.class));
    }

    @Test
    void testFallbackWithRegistry() {
        InjectRegistryManager injectRegistryManager = InjectRegistryManager.create();

        try {
            InjectRegistry registry = injectRegistryManager.registry();
            FallbackService fallbackService = registry.get(FallbackService.class);

            FallbackService.reset();

            fallbackService.method1();
            assertThat(FallbackService.T_1.get(), notNullValue());
            assertThat(FallbackService.T_1.get().getMessage(), is("method1"));
            assertThat(FallbackService.T_2.get(), nullValue());
            assertThat(FallbackService.PARAM_2.get(), nullValue());

            FallbackService.reset();
            fallbackService.method1("testParam");
            assertThat(FallbackService.T_1.get(), nullValue());
            assertThat(FallbackService.T_2.get(), notNullValue());
            assertThat(FallbackService.T_2.get().getMessage(), is("method2"));
            assertThat(FallbackService.PARAM_2.get(), is("testParam"));

            FallbackService.reset();
            String response = fallbackService.method1("testParam", 4);
            assertThat(response, is("testParam_4"));
            assertThat(FallbackService.T_3.get(), notNullValue());
            assertThat(FallbackService.T_3.get().getMessage(), is("method3"));
        } finally {
            injectRegistryManager.shutdown();
        }
    }

    private String primary() {
        primaryCounter.incrementAndGet();
        throw new IllegalArgumentException("Intentional failure");
    }

    private String fallback(Throwable throwable) {
        fallbackCounter.incrementAndGet();
        return "fallback";
    }

    private String fallbackFail(Throwable throwable) {
        fallbackCounter.incrementAndGet();
        throw new ConfigException("Intentional failure");
    }

    @Service.Contract
    @Injection.Singleton
    static class FallbackService {
        private static final AtomicReference<Throwable> T_1 = new AtomicReference<>();
        private static final AtomicReference<String> PARAM_2 = new AtomicReference<>();
        private static final AtomicReference<Throwable> T_2 = new AtomicReference<>();
        private static final AtomicReference<Throwable> T_3 = new AtomicReference<>();

        static void reset() {
            T_1.set(null);
            PARAM_2.set(null);
            T_2.set(null);
            T_3.set(null);
        }

        @FaultTolerance.Fallback("fallback1")
        void method1() {
            throw new RuntimeException("method1");
        }

        void fallback1(Throwable t) {
            T_1.set(t);
        }

        @FaultTolerance.Fallback("fallback1")
        void method1(String param) {
            throw new RuntimeException("method2");
        }

        void fallback1(String param, Throwable t) {
            T_2.set(t);
            PARAM_2.set(param);
        }

        @FaultTolerance.Fallback("fallback1")
        String method1(String param, int param2) {
            throw new RuntimeException("method3");
        }

        String fallback1(String param, int param2, Throwable t) {
            T_3.set(t);
            return param + "_" + param2;
        }
    }
}