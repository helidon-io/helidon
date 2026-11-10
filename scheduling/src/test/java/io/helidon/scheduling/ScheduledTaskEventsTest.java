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

package io.helidon.scheduling;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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

@Execution(ExecutionMode.SAME_THREAD)
class ScheduledTaskEventsTest {
    private static final String EXECUTION = "io.helidon.ScheduledTaskExecution";
    private static final String FAILURE = "io.helidon.ScheduledTaskFailure";

    @TempDir
    Path directory;

    @Test
    void defaults() {
        var execution = EventType.getEventType(ScheduledTaskExecutionEvent.class);
        assertThat(setting(execution, "enabled"), is("false"));
        assertThat(setting(execution, "threshold"), is("1 s"));
        assertThat(setting(execution, "stackTrace"), is("true"));
        var failure = EventType.getEventType(ScheduledTaskFailureEvent.class);
        assertThat(setting(failure, "enabled"), is("true"));
        assertThat(setting(failure, "stackTrace"), is("true"));
    }

    @Test
    void fixedRateCallbackSuccess() throws Exception {
        String id = UUID.randomUUID().toString();
        var events = record(false, false, id, Duration.ZERO, true);
        var executions = events.stream().filter(e -> e.getEventType().getName().equals(EXECUTION)).toList();
        assertThat(executions, hasSize(1));
        var execution = executions.getFirst();
        assertThat(execution.getString("taskId"), is(id));
        assertThat(execution.getString("scheduleKind"), is("FIXED_RATE"));
        assertThat(execution.getLong("iteration"), is(1L));
        assertThat(execution.getString("outcome"), is("SUCCESS"));
        assertThat(events.stream().filter(e -> e.getEventType().getName().equals(FAILURE)).toList(), hasSize(0));
    }

    @Test
    void failedCallbackHasExecutionOutcome() throws Exception {
        String id = UUID.randomUUID().toString();
        var events = record(false, true, id, Duration.ZERO, true);
        assertThat(events, hasSize(2));
        var execution = events.stream().filter(e -> e.getEventType().getName().equals(EXECUTION))
                .findFirst().orElseThrow();
        assertThat(execution.getString("taskId"), is(id));
        assertThat(execution.getString("outcome"), is("FAILURE"));
        assertThat(execution.getLong("iteration"), is(1L));
        assertFailure(events.stream().filter(e -> e.getEventType().getName().equals(FAILURE)).toList(), id, "FIXED_RATE");
    }

    @Test
    void fastFailureSurvivesExecutionThreshold() throws Exception {
        String id = UUID.randomUUID().toString();
        var events = record(false, true, id, Duration.ofDays(1), true);
        assertFailure(events, id, "FIXED_RATE");
    }

    @Test
    void failureSurvivesDisabledExecution() throws Exception {
        String id = UUID.randomUUID().toString();
        assertFailure(record(false, true, id, Duration.ZERO, false), id, "FIXED_RATE");
    }

    @Test
    void unnamedCronFailureOmitsGeneratedId() throws Exception {
        assertFailure(record(true, true, null, Duration.ZERO, false), null, "CRON");
    }

    @Test
    void configuredIdIsBounded() throws Exception {
        String id = UUID.randomUUID() + "x".repeat(300);
        var events = record(false, true, id, Duration.ZERO, false);
        assertFailure(events, id.substring(0, 256), "FIXED_RATE");
    }

    @Test
    void cronCallbackSuccess() throws Exception {
        String id = UUID.randomUUID().toString();
        var events = record(true, false, id, Duration.ZERO, true);
        assertThat(events, hasSize(1));
        assertThat(events.getFirst().getString("scheduleKind"), is("CRON"));
        assertThat(events.getFirst().getString("outcome"), is("SUCCESS"));
    }

    private static void callback(CountDownLatch entered, CountDownLatch release, boolean fail) throws InterruptedException {
        entered.countDown();
        if (!release.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("callback release timeout");
        }
        if (fail) {
            throw new IllegalArgumentException("private exception message");
        }
    }

    private static void assertFailure(List<RecordedEvent> events, String id, String kind) {
        assertThat(events, hasSize(1));
        var failure = events.getFirst();
        assertThat(failure.getEventType().getName(), is(FAILURE));
        assertThat(failure.getString("taskId"), id == null ? nullValue() : is(id));
        assertThat(failure.getString("scheduleKind"), is(kind));
        assertThat(failure.getLong("iteration"), is(1L));
        assertThat(failure.getString("exceptionClass"), is(IllegalArgumentException.class.getName()));
        assertThat(failure.getEventType().getField("exceptionMessage"), nullValue());
    }

    private static String setting(EventType type, String name) {
        return type.getSettingDescriptors().stream()
                .filter(s -> s.getName().equals(name))
                .findFirst().orElseThrow().getDefaultValue();
    }

    private List<RecordedEvent> record(boolean cron,
                                       boolean fail,
                                       String id,
                                       Duration threshold,
                                       boolean executionEnabled) throws Exception {
        String owner = UUID.randomUUID().toString();
        var executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name(owner).factory());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var recording = new Recording()) {
            if (executionEnabled) {
                recording.enable(EXECUTION).withThreshold(threshold);
            } else {
                recording.disable(EXECUTION);
            }
            recording.enable(FAILURE);
            recording.start();
            Task task;
            if (cron) {
                var builder = Cron.builder()
                        .executor(executor)
                        .taskManager(new TaskManagerImpl())
                        .expression("* * * * * ?")
                        .task(_ -> callback(entered, release, fail));
                if (id != null) {
                    builder.id(id);
                }
                task = builder.build();
            } else {
                var builder = FixedRate.builder()
                        .executor(executor)
                        .taskManager(new TaskManagerImpl())
                        .delayBy(Duration.ZERO)
                        .interval(Duration.ofDays(1))
                        .task(_ -> callback(entered, release, fail));
                if (id != null) {
                    builder.id(id);
                }
                task = builder.build();
            }
            try {
                assertThat("scheduled callback entered", entered.await(10, TimeUnit.SECONDS), is(true));
            } finally {
                task.close();
                release.countDown();
            }
            executor.shutdown();
            assertThat("scheduled callback and event producer completed", executor.awaitTermination(10, TimeUnit.SECONDS), is(true));
            recording.stop();
            Path file = directory.resolve(owner + ".jfr");
            recording.dump(file);
            return RecordingFile.readAllEvents(file).stream()
                    .filter(e -> e.getEventType().getName().equals(EXECUTION) || e.getEventType().getName().equals(FAILURE))
                    .filter(e -> e.getThread().getJavaName().equals(owner))
                    .toList();
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat("owned scheduler terminated", executor.awaitTermination(10, TimeUnit.SECONDS), is(true));
        }
    }
}
