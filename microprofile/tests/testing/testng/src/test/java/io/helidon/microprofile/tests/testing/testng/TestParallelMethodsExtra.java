/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.tests.testing.testng;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import io.helidon.microprofile.testing.testng.AddBean;
import io.helidon.microprofile.testing.testng.HelidonTest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@HelidonTest
@AddBean(TestParallelMethodsExtra.State.class)
@EnabledIfParameter(key = "TestParallelMethods", value = "true")
public class TestParallelMethodsExtra {

    @Inject
    State state;

    @Test
    void testA() throws InterruptedException {
        state.add("a");
        state.countDownA();
        state.awaitB();
        assertThat(state.get(), is(Set.of("a", "b")));
    }

    @Test
    void testB() throws InterruptedException {
        state.awaitA();
        assertThat(state.get(), is(Set.of("a")));
        state.add("b");
        state.countDownB();
    }

    @ApplicationScoped
    static class State {

        final Set<String> events = ConcurrentHashMap.newKeySet();
        final CountDownLatch latchA = new CountDownLatch(1);
        final CountDownLatch latchB = new CountDownLatch(1);

        void add(String event) {
            events.add(event);
        }

        Set<String> get() {
            return events;
        }

        void countDownA() {
            latchA.countDown();
        }

        void awaitA() throws InterruptedException {
            latchA.await();
        }

        void countDownB() {
            latchB.countDown();
        }

        void awaitB() throws InterruptedException {
            latchB.await();
        }
    }

}
