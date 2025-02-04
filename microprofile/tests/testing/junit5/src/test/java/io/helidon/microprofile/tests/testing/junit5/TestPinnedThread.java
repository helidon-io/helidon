/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

package io.helidon.microprofile.tests.testing.junit5;

import java.util.Arrays;

import io.helidon.common.testing.virtualthreads.PinningAssertionError;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.WebTarget;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;
import org.junit.platform.testkit.engine.Events;

import static org.junit.platform.commons.util.FunctionUtils.where;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.testkit.engine.EventConditions.displayName;
import static org.junit.platform.testkit.engine.EventConditions.event;
import static org.junit.platform.testkit.engine.EventConditions.finishedWithFailure;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.instanceOf;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.message;

@SuppressWarnings("ALL")
class TestPinnedThread {

    @Test
    void engineTest() {
        Events events = EngineTestKit.engine("junit-jupiter")
                .configurationParameter("TestPinnedThread", "true")
                .selectors(
                        selectClass(PinningTestCase.class),
                        selectClass(PinningExtraThreadTestCase.class),
                        selectClass(NoPinningTestCase.class),
                        selectClass(NoPinningExtraThreadTestCase.class)
                )
                .execute()
                .containerEvents()
                .assertStatistics(stats -> stats
                        .failed(2)
                        .succeeded(3));

        events.failed()
                .assertEventsMatchExactly(
                        event(displayClass(PinningTestCase.class), failedWithPinningException("pinningInResourceMethod")),
                        event(displayClass(PinningExtraThreadTestCase.class), failedWithPinningException("lambda$pinningTest$0"))
                );
    }

    private Condition<Event> failedWithPinningException(String expectedPinningMethodName) {
        return finishedWithFailure(
                instanceOf(PinningAssertionError.class),
                message(m -> m.startsWith("Pinned virtual threads were detected"))
                , new Condition<>(where(
                        t -> Arrays.stream(t.getStackTrace()),
                        s -> s
                                .anyMatch(e -> e.getMethodName()
                                        .equals(expectedPinningMethodName))),
                        "Method with pinning is missing from stack strace.")
        );
    }

    private Condition<Event> displayClass(Class<?> clazz) {
        return displayName(Arrays.stream(clazz.getName().split("\\.")).toList().getLast());
    }

    @EnabledIfParameter(key = "TestPinnedThread", value = "true")
    @HelidonTest(pinningDetection = true)
    @AddBean(PinningTestCase.TestResource.class)
    static class PinningTestCase {

        @Path("/pinning")
        public static class TestResource {
            @GET
            public String pinningInResourceMethod() {
                synchronized (this) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.interrupted();
                    }
                }
                return "pinning";
            }
        }

        @Test
        void pinningTest(WebTarget target) {
            target.path("/pinning")
                    .request()
                    .get(String.class);
        }
    }

    @EnabledIfParameter(key = "TestPinnedThread", value = "true")
    @HelidonTest(pinningDetection = true)
    static class PinningExtraThreadTestCase {

        @Test
        void pinningTest() throws InterruptedException {
            Thread.ofVirtual().start(() -> {
                synchronized (this) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.interrupted();
                    }
                }
            }).join();
        }
    }

    @EnabledIfParameter(key = "TestPinnedThread", value = "true")
    @HelidonTest(pinningDetection = false)
    static class PinningDisabledExtraThreadTestCase {

        @Test
        void pinningTest() throws InterruptedException {
            Thread.ofVirtual().start(() -> {
                synchronized (this) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.interrupted();
                    }
                }
            }).join();
        }
    }

    @EnabledIfParameter(key = "TestPinnedThread", value = "true")
    @HelidonTest(pinningDetection = true)
    static class NoPinningTestCase {

        @Path("/pinning")
        public static class TestResource {
            @GET
            public String pinningInResourceMethod() {
                synchronized (this) {
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.interrupted();
                    }
                }
                return "NO PINNING!";
            }
        }

        @Test
        void pinningTest(WebTarget target) {
            Assertions.assertEquals("NO PINNING!", target.path("/pinning")
                    .request()
                    .get(String.class));
        }
    }

    @EnabledIfParameter(key = "TestPinnedThread", value = "true")
    @HelidonTest(pinningDetection = true)
    static class NoPinningExtraThreadTestCase {

        @Test
        void pinningTest() throws InterruptedException {
            Thread.ofVirtual().start(() -> {
                synchronized (this) {
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.interrupted();
                    }
                }
            }).join();
        }
    }
}
