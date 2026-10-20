/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.webserver;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.helidon.webserver.http.HttpRouting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies that a stopped {@link WebServer} terminates the {@code helidon-idle-connection-timer}
 * thread it created. The timer is owned by the server instance: stopping the server cancels the
 * {@code Timer}, which clears its task queue and lets the timer daemon exit promptly.
 */
class IdleConnectionTimerStopTest {
    private static final String TIMER_THREAD_NAME = "helidon-idle-connection-timer";
    private static final long AWAIT_MILLIS = 15_000;
    private static final long POLL_MILLIS = 100;

    /**
     * This test tracks its own timer thread by identity, so timers of other server instances
     * (live or already eligible for the JDK Cleaner) cannot affect the outcome.
     */
    @Test
    void stopTerminatesIdleConnectionTimerThread() throws Exception {
        Set<Thread> timersBefore = liveIdleTimerThreads();

        WebServer server = WebServer.builder()
                .routing(HttpRouting.builder()
                                  .get("/", (req, res) -> res.send("ok")))
                .build();
        Thread timerThread = null;
        try {
            server.start();
            timerThread = awaitNewIdleTimerThread(timersBefore);
        } finally {
            server.stop();
        }
        awaitThreadTermination(timerThread);
    }

    private static Set<Thread> liveIdleTimerThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.isAlive() && TIMER_THREAD_NAME.equals(thread.getName()))
                .collect(Collectors.toSet());
    }

    private static Thread awaitNewIdleTimerThread(Set<Thread> startedBefore) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(AWAIT_MILLIS);
        do {
            for (Thread thread : liveIdleTimerThreads()) {
                if (!startedBefore.contains(thread)) {
                    return thread;
                }
            }
            TimeUnit.MILLISECONDS.sleep(POLL_MILLIS);
        } while (System.nanoTime() < deadline);
        fail("No new " + TIMER_THREAD_NAME + " thread ran within " + AWAIT_MILLIS + " ms");
        return null; // not reached
    }

    private static void awaitThreadTermination(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(AWAIT_MILLIS);
        do {
            if (!thread.isAlive()) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(POLL_MILLIS);
        } while (System.nanoTime() < deadline);
        fail(TIMER_THREAD_NAME + " thread did not terminate within " + AWAIT_MILLIS + " ms after stop()");
    }
}
