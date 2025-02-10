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

package io.helidon.webserver.testing.junit5;

import java.util.Arrays;

import io.helidon.common.testing.virtualthreads.PinningAssertionError;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.http.HttpRouting;

import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;
import org.junit.platform.testkit.engine.Events;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.testkit.engine.EventConditions.displayName;
import static org.junit.platform.testkit.engine.EventConditions.event;
import static org.junit.platform.testkit.engine.EventConditions.finishedWithFailure;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.instanceOf;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.message;

class TestPinnedThread {

    @Test
    void engineTest() {
        Events events = EngineTestKit.engine("junit-jupiter")
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
                        event(displayClass(PinningTestCase.class), failedWithPinningException("lambda$routing$0")),
                        event(displayClass(PinningExtraThreadTestCase.class), failedWithPinningException("lambda$pinningTest$0"))
                );
    }

    private Condition<org.junit.platform.testkit.engine.Event> failedWithPinningException(String expectedPinningMethodName) {
        return finishedWithFailure(
                instanceOf(PinningAssertionError.class),
                message(m -> m.startsWith("Pinned virtual threads were detected"))
                , new Condition<>(
                        t -> Arrays.stream(t.getStackTrace())
                                .anyMatch(e -> e.getMethodName()
                                        .equals(expectedPinningMethodName)),
                        "Method with pinning is missing from stack strace.")
        );
    }

    private Condition<Event> displayClass(Class<?> clazz) {
        return displayName(Arrays.stream(clazz.getName().split("\\.")).toList().getLast());
    }

    @ServerTest(pinningDetection = true)
    static class PinningTestCase {

        static final Object monitor = new Object();

        @SetUpRoute
        static void routing(HttpRouting.Builder router) {
            router.get("/pinning", (req, res) -> {
                synchronized (monitor) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.interrupted();
                    }
                }
                res.send("pinning");
            });
        }

        @Test
        void pinningTest(WebClient target) {
            target.get("/pinning")
                    .request(String.class);
        }
    }

    @ServerTest(pinningDetection = true)
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

    @ServerTest(pinningDetection = true)
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

    @ServerTest(pinningDetection = true)
    static class NoPinningTestCase {

        static final Object monitor = new Object();

        @SetUpRoute
        static void routing(HttpRouting.Builder router) {
            router.get("/pinning", (req, res) -> {
                synchronized (monitor) {
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.interrupted();
                    }
                }
                res.send("NO PINNING!");
            });
        }

        @Test
        void pinningTest(WebClient target) {
            Assertions.assertEquals("NO PINNING!", target.get("/pinning")
                    .request(String.class));
        }
    }

    @ServerTest(pinningDetection = true)
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
