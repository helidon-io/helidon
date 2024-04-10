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
package io.helidon.microprofile.cdi;

import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class AsyncPlatformTest {

    static SeContainer seContainer;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void startCdi() {
        seContainer = SeContainerInitializer.newInstance()
                .disableDiscovery()
                .addExtensions(AsyncPlatformExtension.class)
                .addBeanClasses(AsyncPlatformBean.class)
                .initialize();
    }

    @AfterAll
    static void stopCdi() {
        seContainer.close();
    }

    static AtomicBoolean success = new AtomicBoolean();

    static class AsyncPlatformBean {

        @AsyncPlatform
        void cpuIntensive() {
            Thread thread = Thread.currentThread();
            success.set(!thread.isVirtual() && thread.getName().startsWith("my-platform-thread"));
        }

        @AsyncPlatform(10000)
        void evenMoreCpuIntensive() {
            Thread thread = Thread.currentThread();
            success.set(!thread.isVirtual() && thread.getName().startsWith("my-platform-thread"));
        }
    }

    @BeforeEach
    void reset() {
        success.set(false);
    }

    @Test
    void cpuIntensiveTest() {
        AsyncPlatformBean bean = CDI.current().select(AsyncPlatformBean.class).get();
        bean.cpuIntensive();
        assertThat(success.get(), is(true));
    }

    @Test
    void evenMoreCpuIntensiveTest() {
        AsyncPlatformBean bean = CDI.current().select(AsyncPlatformBean.class).get();
        bean.evenMoreCpuIntensive();
        assertThat(success.get(), is(true));
    }
}
