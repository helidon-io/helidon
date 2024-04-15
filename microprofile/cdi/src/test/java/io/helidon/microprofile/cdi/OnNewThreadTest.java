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

import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.microprofile.cdi.OnNewThread.ThreadType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class OnNewThreadTest {

    static SeContainer seContainer;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void startCdi() {
        seContainer = SeContainerInitializer.newInstance()
                .disableDiscovery()
                .addExtensions(OnNewThreadExtension.class)
                .addBeanClasses(AsyncPlatformBean.class)
                .initialize();
    }

    @AfterAll
    static void stopCdi() {
        seContainer.close();
    }

    static class AsyncPlatformBean {

        @OnNewThread
        boolean cpuIntensive() {
            Thread thread = Thread.currentThread();
            return !thread.isVirtual() && thread.getName().startsWith("my-platform-thread");
        }

        @OnNewThread(ThreadType.PLATFORM)
        boolean cpuIntensiveWithType() {
            Thread thread = Thread.currentThread();
            return !thread.isVirtual() && thread.getName().startsWith("my-platform-thread");
        }

        @OnNewThread(timeout = 10000)
        boolean evenMoreCpuIntensive() {
            Thread thread = Thread.currentThread();
            return !thread.isVirtual() && thread.getName().startsWith("my-platform-thread");
        }

        @OnNewThread(ThreadType.VIRTUAL)
        boolean onVirtualThread() {
            Thread thread = Thread.currentThread();
            return thread.isVirtual() && thread.getName().startsWith("my-platform-thread");
        }
    }

    @Test
    void cpuIntensiveTest() {
        AsyncPlatformBean bean = CDI.current().select(AsyncPlatformBean.class).get();
        assertThat(bean.cpuIntensive(), is(true));
    }

    @Test
    void cpuIntensiveWithTypeTest() {
        AsyncPlatformBean bean = CDI.current().select(AsyncPlatformBean.class).get();
        assertThat(bean.cpuIntensiveWithType(), is(true));
    }

    @Test
    void evenMoreCpuIntensiveTest() {
        AsyncPlatformBean bean = CDI.current().select(AsyncPlatformBean.class).get();
        assertThat(bean.evenMoreCpuIntensive(), is(true));
    }

    @Test
    void onVirtualThread() {
        AsyncPlatformBean bean = CDI.current().select(AsyncPlatformBean.class).get();
        assertThat(bean.onVirtualThread(), is(true));
    }
}
