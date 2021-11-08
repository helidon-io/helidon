/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.common.reactive;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SingleLogTest {

    private static final Logger LOGGER = Logger.getLogger(LoggingPublisher.class.getName());

    private final TestHandler testHandler = new TestHandler();

    @BeforeEach
    void setUp() throws IOException {
        LOGGER.addHandler(testHandler);
    }

    @AfterEach
    void tearDown() {
        LOGGER.removeHandler(testHandler);
    }

    @Test
    void testLogWithComplete() {

        Single.just(1)
                .log()
                .await();

        assertLogs(Level.INFO,
                "Multi\\.log\\([0-9]+\\)",
                "SingleJust",
                "log\\(\\)",
                " ⇘ onSubscribe(...)",
                " ⇗ request(Long.MAX_VALUE)",
                " ⇘ onNext(1)",
                " ⇘ onComplete()");
    }

    @Test
    void testLogWithCancel() throws IOException {

        Single.just(1)
                .log()
                .cancel();

        assertLogs(Level.INFO,
                "Multi\\.log\\([0-9]+\\)",
                "SingleJust",
                "log\\(\\)",
                " ⇘ onSubscribe(...)",
                " ⇗ cancel()");
    }

    @Test
    void testLogWithCustomLogName() throws IOException {

        String TEST_LOGGER_NAME = "TEST_LOGGER";

        Single.just(1)
                .log(Level.INFO, TEST_LOGGER_NAME)
                .await();

        assertLogs(Level.INFO,
                "TEST_LOGGER",
                "SingleJust",
                "log\\(\\)",
                " ⇘ onSubscribe(...)",
                " ⇗ request(Long.MAX_VALUE)",
                " ⇘ onNext(1)",
                " ⇘ onComplete()");
    }


    @Test
    void testLogWithError() throws IOException {

        CompletionAwaitable<Void> single =
                Single.just(1)
                        .peek(i -> {
                            throw new RuntimeException("BOOM!");
                        })
                        .log()
                        .ignoreElement();


        assertThat(
                assertThrows(CompletionException.class, () -> single.await(300, TimeUnit.MILLISECONDS))
                        .getCause()
                        .getMessage(),
                equalTo("BOOM!"));


        assertLogs(Level.INFO,
                "Multi\\.log\\([0-9]+\\)",
                "Single.peek",
                "log\\(\\)",
                " ⇘ onSubscribe(...)",
                " ⇗ request(Long.MAX_VALUE)",
                " ⇘ onError(java.lang.RuntimeException: BOOM!)");
    }

    @Test
    void testLogWarningWithCancel() {

        Single.just(1)
                .log(Level.WARNING)
                .cancel()
                .ignoreElement();

        assertLogs(Level.WARNING,
                "Multi\\.log\\([0-9]+\\)",
                "SingleJust",
                "log\\(\\)",
                " ⇘ onSubscribe(...)",
                " ⇗ cancel()");
    }

    @Test
    void testLogTraceWithCancel() {

        Single.just(1)
                .log(Level.INFO, true)
                .cancel();

        assertLogs(Level.INFO,
                "io\\.helidon\\.common\\.reactive\\.SingleLogTest\\.testLogTraceWithCancel\\(SingleLogTest\\.java:[0-9]+\\)",
                "io.helidon.common.reactive.SingleLogTest",
                "testLogTraceWithCancel\\(SingleLogTest\\.java:[0-9]+\\)",
                " ⇘ onSubscribe(...)",
                " ⇗ cancel()");
    }

    private void assertLogs(Level expectedLevel,
                            String expectedLoggerNamePattern,
                            String expectedSourceClassName,
                            String expectedSourceMethodNamePattern,
                            String... expectedMessages) {

        long length = expectedMessages.length;

        assertThat(
                testHandler.recordList.stream()
                        .map(LogRecord::getMessage)
                        .collect(Collectors.toList()), contains(
                        expectedMessages
                ));

        assertThat(
                testHandler.recordList.stream()
                        .map(LogRecord::getLevel)
                        .collect(Collectors.toList()),
                contains(Stream.generate(() -> expectedLevel).limit(length).toArray()));

        assertThat(
                testHandler.recordList.stream()
                        .map(LogRecord::getLoggerName)
                        .filter(d -> d.matches(expectedLoggerNamePattern))
                        .count(), equalTo(length));

        assertThat(
                testHandler.recordList.stream()
                        .map(LogRecord::getSourceMethodName)
                        .filter(d -> d.matches(expectedSourceMethodNamePattern))
                        .count(), equalTo(length));

        assertThat(
                testHandler.recordList.stream()
                        .map(LogRecord::getSourceClassName)
                        .collect(Collectors.toList()),
                contains(Stream.generate(() -> expectedSourceClassName).limit(length).toArray()));
    }

    private static class TestHandler extends Handler {

        List<LogRecord> recordList = new ArrayList<>();

        void reset() {
            recordList.clear();
        }

        @Override
        public void publish(final LogRecord record) {
            recordList.add(record);
        }

        @Override
        public void flush() {

        }

        @Override
        public void close() throws SecurityException {
            reset();
        }
    }
}
