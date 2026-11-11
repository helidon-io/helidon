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

package io.helidon.faulttolerance;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jdk.jfr.EventType;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Execution(ExecutionMode.SAME_THREAD)
class CircuitBreakerEventsTest {
    private static final String EVENT = "io.helidon.CircuitBreakerStateChange";

    @TempDir
    Path directory;

    @Test
    void defaults() {
        var type = EventType.getEventType(CircuitBreakerStateChangeEvent.class);
        assertThat(type.getSettingDescriptors().stream().filter(s -> s.getName().equals("enabled"))
                           .findFirst().orElseThrow().getDefaultValue(), is("true"));
        assertThat(type.getSettingDescriptors().stream().filter(s -> s.getName().equals("stackTrace"))
                           .findFirst().orElseThrow().getDefaultValue(), is("false"));
    }

    @Test
    void automaticTransitionsAndRepeatedOpenRejections() throws Exception {
        String name = UUID.randomUUID().toString();
        var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        // Hold the delayed transition until open-state rejection checks have completed.
        executor.submit(() -> {
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("transition release timeout");
            }
            return true;
        });
        var breaker = CircuitBreaker.builder()
                .name(name)
                .executor(executor)
                .volume(1)
                .errorRatio(100)
                .successThreshold(1)
                .delay(Duration.ZERO)
                .build();
        try (var recording = new Recording()) {
            recording.enable(EVENT);
            recording.start();
            fail(breaker);
            assertThat(breaker.state(), is(CircuitBreaker.State.OPEN));
            for (int i = 0; i < 5; i++) {
                assertThrows(CircuitBreakerOpenException.class, () -> breaker.invoke(() -> "rejected"));
            }
            var transition = ((CircuitBreakerImpl) breaker).schedule();
            release.countDown();
            transition.get(10, TimeUnit.SECONDS);
            assertThat(breaker.state(), is(CircuitBreaker.State.HALF_OPEN));
            assertThat(breaker.invoke(() -> "success"), is("success"));
            assertThat(breaker.state(), is(CircuitBreaker.State.CLOSED));
            executor.shutdown();
            assertThat("transition producer completed", executor.awaitTermination(10, TimeUnit.SECONDS), is(true));
            var events = read(recording, name);
            assertThat(events, hasSize(3));
            assertThat(events.stream().map(e -> e.getString("reason")).sorted().toList(),
                       is(List.of("DELAY_ELAPSED", "FAILURE_THRESHOLD", "SUCCESS_THRESHOLD")));
            assertTransition(events, "CLOSED", "OPEN", "FAILURE_THRESHOLD");
            assertTransition(events, "OPEN", "HALF_OPEN", "DELAY_ELAPSED");
            assertTransition(events, "HALF_OPEN", "CLOSED", "SUCCESS_THRESHOLD");
        } finally {
            release.countDown();
            breaker.state(CircuitBreaker.State.CLOSED);
            executor.shutdownNow();
            assertThat("owned transition executor terminated", executor.awaitTermination(10, TimeUnit.SECONDS), is(true));
        }
    }

    @Test
    void halfOpenFailureReportsReopening() throws Exception {
        String name = UUID.randomUUID().toString();
        var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            release.await(10, TimeUnit.SECONDS);
            return true;
        });
        var breaker = CircuitBreaker.builder()
                .name(name)
                .executor(executor)
                .volume(1)
                .errorRatio(100)
                .delay(Duration.ZERO)
                .build();
        try (var recording = new Recording()) {
            recording.enable(EVENT);
            recording.start();
            fail(breaker);
            var transition = ((CircuitBreakerImpl) breaker).schedule();
            release.countDown();
            transition.get(10, TimeUnit.SECONDS);
            var releaseSecond = new CountDownLatch(1);
            executor.submit(() -> {
                releaseSecond.await(10, TimeUnit.SECONDS);
                return true;
            });
            try {
                fail(breaker);
                assertThat(breaker.state(), is(CircuitBreaker.State.OPEN));
                breaker.state(CircuitBreaker.State.CLOSED);
            } finally {
                releaseSecond.countDown();
            }
            executor.shutdown();
            assertThat("transition producers completed", executor.awaitTermination(10, TimeUnit.SECONDS), is(true));
            var events = read(recording, name);
            assertThat(events, hasSize(4));
            assertTransition(events, "HALF_OPEN", "OPEN", "HALF_OPEN_FAILURE");
        } finally {
            release.countDown();
            breaker.state(CircuitBreaker.State.CLOSED);
            executor.shutdownNow();
            assertThat("owned transition executor terminated", executor.awaitTermination(10, TimeUnit.SECONDS), is(true));
        }
    }

    @Test
    void concurrentFailuresReportOnlyWinningTransition() throws Exception {
        String name = UUID.randomUUID().toString();
        var releaseTransition = new CountDownLatch(1);
        var entered = new CountDownLatch(4);
        var releaseSuppliers = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        var callers = Executors.newFixedThreadPool(4);
        executor.submit(() -> {
            releaseTransition.await(10, TimeUnit.SECONDS);
            return true;
        });
        var breaker = CircuitBreaker.builder()
                .name(name)
                .executor(executor)
                .volume(1)
                .errorRatio(100)
                .delay(Duration.ZERO)
                .build();
        try (var recording = new Recording()) {
            recording.enable(EVENT);
            recording.start();
            List<Future<?>> calls = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                calls.add(callers.submit(() -> assertThrows(IllegalStateException.class, () -> breaker.invoke(() -> {
                    entered.countDown();
                    try {
                        if (!releaseSuppliers.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("supplier release timeout");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    throw new IllegalStateException("private failure detail");
                }))));
            }
            assertThat("all suppliers entered closed breaker", entered.await(10, TimeUnit.SECONDS), is(true));
            releaseSuppliers.countDown();
            for (var call : calls) {
                call.get(10, TimeUnit.SECONDS);
            }
            breaker.state(CircuitBreaker.State.CLOSED);
            releaseTransition.countDown();
            executor.shutdown();
            callers.shutdown();
            assertThat("transition executor completed", executor.awaitTermination(10, TimeUnit.SECONDS), is(true));
            assertThat("all caller producers completed", callers.awaitTermination(10, TimeUnit.SECONDS), is(true));
            var events = read(recording, name);
            assertThat(events, hasSize(2));
            assertTransition(events, "CLOSED", "OPEN", "FAILURE_THRESHOLD");
            assertTransition(events, "OPEN", "CLOSED", "MANUAL");
        } finally {
            releaseSuppliers.countDown();
            releaseTransition.countDown();
            breaker.state(CircuitBreaker.State.CLOSED);
            callers.shutdownNow();
            executor.shutdownNow();
            assertThat("owned callers terminated", callers.awaitTermination(10, TimeUnit.SECONDS), is(true));
            assertThat("owned transition executor terminated", executor.awaitTermination(10, TimeUnit.SECONDS), is(true));
        }
    }

    @Test
    void manualChangesExcludeSameStateAssignments() throws Exception {
        String name = UUID.randomUUID().toString();
        var breaker = CircuitBreaker.builder().name(name).build();
        try (var recording = new Recording()) {
            recording.enable(EVENT);
            recording.start();
            breaker.state(CircuitBreaker.State.CLOSED);
            breaker.state(CircuitBreaker.State.OPEN);
            breaker.state(CircuitBreaker.State.OPEN);
            breaker.state(CircuitBreaker.State.CLOSED);
            breaker.state(CircuitBreaker.State.CLOSED);
            var events = read(recording, name);
            assertThat(events, hasSize(2));
            assertTransition(events, "CLOSED", "OPEN", "MANUAL");
            assertTransition(events, "OPEN", "CLOSED", "MANUAL");
        }
    }

    @Test
    void unnamedBreakerOmitsGeneratedName() throws Exception {
        var breaker = CircuitBreaker.builder().build();
        long owner = Thread.currentThread().threadId();
        try (var recording = new Recording()) {
            recording.enable(EVENT);
            recording.start();
            breaker.state(CircuitBreaker.State.OPEN);
            recording.stop();
            Path file = directory.resolve("unnamed.jfr");
            recording.dump(file);
            var events = RecordingFile.readAllEvents(file).stream()
                    .filter(e -> e.getEventType().getName().equals(EVENT))
                    .filter(e -> e.getThread().getJavaThreadId() == owner)
                    .filter(e -> e.getString("breakerName") == null)
                    .toList();
            assertThat(events, hasSize(1));
            assertThat(events.getFirst().getString("breakerName"), nullValue());
        } finally {
            breaker.state(CircuitBreaker.State.CLOSED);
        }
    }

    @Test
    void configuredNameIsBounded() throws Exception {
        String name = UUID.randomUUID() + "x".repeat(300);
        var breaker = CircuitBreaker.builder().name(name).build();
        try (var recording = new Recording()) {
            recording.enable(EVENT);
            recording.start();
            breaker.state(CircuitBreaker.State.OPEN);
            var events = read(recording, name.substring(0, 256));
            assertThat(events, hasSize(1));
        } finally {
            breaker.state(CircuitBreaker.State.CLOSED);
        }
    }

    private static void assertTransition(List<RecordedEvent> events, String previous, String next, String reason) {
        var matches = events.stream()
                .filter(e -> e.getString("reason").equals(reason))
                .filter(e -> e.getString("previousState").equals(previous))
                .filter(e -> e.getString("state").equals(next))
                .toList();
        assertThat(matches, hasSize(1));
        assertThat(matches.getFirst().getString("previousState"), is(previous));
        assertThat(matches.getFirst().getString("state"), is(next));
        assertThat(matches.getFirst().getStackTrace(), nullValue());
    }

    private static void fail(CircuitBreaker breaker) {
        assertThrows(IllegalStateException.class, () -> breaker.invoke(() -> {
            throw new IllegalStateException("private failure detail");
        }));
    }

    private List<RecordedEvent> read(Recording recording, String name) throws Exception {
        recording.stop();
        Path file = directory.resolve(UUID.randomUUID() + ".jfr");
        recording.dump(file);
        return RecordingFile.readAllEvents(file).stream()
                .filter(e -> e.getEventType().getName().equals(EVENT))
                .filter(e -> name.equals(e.getString("breakerName")))
                .toList();
    }
}
