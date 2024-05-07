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

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Named;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.microprofile.cdi.ExecuteOn.ThreadType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ExecuteOnTest {

    static SeContainer seContainer;
    static OnNewThreadBean bean;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void startCdi() {
        seContainer = SeContainerInitializer.newInstance()
                .disableDiscovery()
                .addExtensions(ExecuteOnExtension.class)
                .addBeanClasses(OnNewThreadBean.class)
                .initialize();
        bean = CDI.current().select(OnNewThreadBean.class).get();
    }

    @AfterAll
    static void stopCdi() {
        seContainer.close();
    }

    static class OnNewThreadBean {

        @ExecuteOn(ThreadType.PLATFORM)
        Thread cpuIntensive() {
            return Thread.currentThread();
        }

        @ExecuteOn(value = ThreadType.PLATFORM, timeout = 10000)
        Thread evenMoreCpuIntensive() {
            return Thread.currentThread();
        }

        @ExecuteOn(ThreadType.VIRTUAL)
        Thread onVirtualThread() {
            return Thread.currentThread();
        }

        @ExecuteOn(value = ThreadType.EXECUTOR, executorName = "my-executor")
        Thread onMyExecutor() {
            return Thread.currentThread();
        }

        @ExecuteOn(ThreadType.VIRTUAL)
        Optional<String> verifyContextVirtual() {
            return Contexts.context().flatMap(context -> context.get("hello", String.class));
        }

        @ExecuteOn(ThreadType.PLATFORM)
        Optional<String> verifyContextPlatform() {
            return Contexts.context().flatMap(context -> context.get("hello", String.class));
        }

        @Produces
        @Named("my-executor")
        ExecutorService myExecutor() {
            return Executors.newFixedThreadPool(2);
        }
    }

    @Test
    void cpuIntensiveTest() {
        Thread thread = bean.cpuIntensive();
        assertThat(thread.isVirtual(), is(false));
        assertThat(thread.getName().startsWith("my-platform-thread"), is(true));
    }

    @Test
    void evenMoreCpuIntensiveTest() {
        Thread thread = bean.evenMoreCpuIntensive();
        assertThat(thread.isVirtual(), is(false));
        assertThat(thread.getName().startsWith("my-platform-thread"), is(true));
    }

    @Test
    void onVirtualThread() {
        Thread thread = bean.onVirtualThread();
        assertThat(thread.isVirtual(), is(true));
        assertThat(thread.getName().startsWith("my-virtual-thread"), is(true));
    }

    @Test
    void onMyExecutor() {
        Thread thread = bean.onMyExecutor();
        assertThat(thread.isVirtual(), is(false));
        assertThat(thread.getName().startsWith("pool"), is(true));
    }

    @Test
    void verifyContextVirtual() {
        Context context = Contexts.globalContext();
        context.register("hello", "world");
        Optional<String> optional = Contexts.runInContext(context, bean::verifyContextVirtual);
        assertThat(optional.orElseThrow(), is("world"));
    }

    @Test
    void verifyContextPlatform() {
        Context context = Contexts.globalContext();
        context.register("hello", "world");
        Optional<String> optional = Contexts.runInContext(context, bean::verifyContextPlatform);
        assertThat(optional.orElseThrow(), is("world"));
    }
}
