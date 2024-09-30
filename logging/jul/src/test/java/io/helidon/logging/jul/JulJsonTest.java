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

package io.helidon.logging.jul;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import io.helidon.logging.common.HelidonMdc;
import io.helidon.metadata.hson.Hson;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class JulJsonTest {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final Instant INSTANT = Instant.parse("2007-12-03T10:15:30.00Z");
    private static final ZonedDateTime DATE_TIME = INSTANT.atZone(ZoneId.systemDefault());

    private static final LogRecord LOG_RECORD;
    private static final LogRecord LOG_RECORD_WITH_EXCEPTION;

    static {
        LogRecord record = new LogRecord(Level.WARNING, "Message content");
        record.setInstant(INSTANT);
        record.setLoggerName("LoggerName");
        record.setThrown(new IllegalStateException("Thrown"));
        record.setSourceClassName("io.helidon.logging.jul.JulJsonTest");
        record.setSourceMethodName("testJsonDefaultFormat");
        LOG_RECORD_WITH_EXCEPTION = record;

        record = new LogRecord(Level.WARNING, "Message content");
        record.setInstant(INSTANT);
        record.setLoggerName("LoggerName");
        record.setSourceClassName("io.helidon.logging.jul.JulJsonTest");
        record.setSourceMethodName("testJsonDefaultFormat");
        LOG_RECORD = record;
    }

    @Test
    public void testJsonDefaultFormat() throws InterruptedException {
        HelidonJsonFormatter formatter = new HelidonJsonFormatter(HelidonJsonFormatter.DEFAULT_FORMAT, true);

        String threadName = "logging-jul-test-thread";

        AtomicReference<String> resultReference = new AtomicReference<>();
        Thread.ofVirtual()
                .name(threadName)
                .start(() -> {
                    resultReference.set(formatter.format(LOG_RECORD));
                })
                .join(Duration.ofSeconds(5));

        String result = resultReference.get();
        var json = Hson.parse(new ByteArrayInputStream(result.getBytes(StandardCharsets.UTF_8)))
                .asStruct();

        assertThat(json.stringValue("ts"), optionalValue(is(String.valueOf(INSTANT.toEpochMilli()))));
        assertThat(json.stringValue("date"), optionalValue(is(DATE_FORMAT.format(DATE_TIME))));
        assertThat(json.stringValue("time"), optionalValue(is(TIME_FORMAT.format(DATE_TIME))));
        assertThat(json.stringValue("level"), optionalValue(is("WARNING")));
        assertThat(json.stringValue("message"), optionalValue(is("Message content")));
        assertThat(json.stringValue("exception"), optionalEmpty());
        assertThat(json.stringValue("logger"), optionalValue(is("LoggerName")));
        assertThat(json.stringValue("thread"), optionalValue(containsString(threadName)));
    }

    @Test
    public void testJsonWithExceptionDefaultFormat() throws InterruptedException {
        HelidonJsonFormatter formatter = new HelidonJsonFormatter(HelidonJsonFormatter.DEFAULT_FORMAT, true);

        String threadName = "logging-jul-test-thread";

        AtomicReference<String> resultReference = new AtomicReference<>();
        Thread.ofVirtual()
                .name(threadName)
                .start(() -> {
                    resultReference.set(formatter.format(LOG_RECORD_WITH_EXCEPTION));
                })
                .join(Duration.ofSeconds(1000));

        String result = resultReference.get();
        var json = Hson.parse(new ByteArrayInputStream(result.getBytes(StandardCharsets.UTF_8)))
                .asStruct();

        assertThat(json.stringValue("date"), optionalValue(is(DATE_FORMAT.format(DATE_TIME))));
        assertThat(json.stringValue("time"), optionalValue(is(TIME_FORMAT.format(DATE_TIME))));
        assertThat(json.stringValue("level"), optionalValue(is("WARNING")));
        assertThat(json.stringValue("message"), optionalValue(is("Message content")));
        assertThat(json.stringValue("exception"), optionalValue(containsString("Thrown")));
        assertThat(json.stringValue("logger"), optionalValue(is("LoggerName")));
        assertThat(json.stringValue("thread"), optionalValue(containsString(threadName)));
    }

    @Test
    public void testJsonWithExceptionSimpleFormat() throws InterruptedException {
        String simpleFormat = "%1$tY.%1$tm.%1$td %1$tH:%1$tM:%1$tS.%1$tL %4$s %3$s !thread!: %5$s%6$s %X{test}%n";
        HelidonJsonFormatter formatter = new HelidonJsonFormatter(simpleFormat, false);

        String threadName = "logging-jul-test-thread";

        AtomicReference<String> resultReference = new AtomicReference<>();
        Thread.ofVirtual()
                .name(threadName)
                .start(() -> {
                    HelidonMdc.set("test", "testValue");
                    resultReference.set(formatter.format(LOG_RECORD_WITH_EXCEPTION));
                })
                .join(Duration.ofSeconds(1000));

        String result = resultReference.get();
        var json = Hson.parse(new ByteArrayInputStream(result.getBytes(StandardCharsets.UTF_8)))
                .asStruct();

        assertThat(json.stringValue("date"), optionalValue(is(DATE_FORMAT.format(DATE_TIME))));
        assertThat(json.stringValue("time"), optionalValue(is(TIME_FORMAT.format(DATE_TIME))));
        assertThat(json.stringValue("logger"), optionalValue(is("LoggerName")));
        assertThat(json.stringValue("level"), optionalValue(is("WARNING")));
        assertThat(json.stringValue("thread"), optionalValue(containsString(threadName)));
        assertThat(json.stringValue("message"), optionalValue(is("Message content")));
        assertThat(json.stringValue("exception"), optionalValue(containsString("Thrown")));
        assertThat(json.stringValue("X.test"), optionalValue(containsString("testValue")));
    }

    @Test
    public void testJsonWithExceptionCustomFormat() throws InterruptedException {
        String format = "timestamp:%1$tY.%1$tm.%1$td %1$tH:%1$tM:%1$tS.%1$tL,loglevel:%4$s,test:%X{test},msg:%5$s,source:%2$s";
        HelidonJsonFormatter formatter = new HelidonJsonFormatter(format, true);

        String threadName = "logging-jul-test-thread";

        AtomicReference<String> resultReference = new AtomicReference<>();
        Thread.ofVirtual()
                .name(threadName)
                .start(() -> {
                    HelidonMdc.set("test", "testValue");
                    resultReference.set(formatter.format(LOG_RECORD_WITH_EXCEPTION));
                })
                .join(Duration.ofSeconds(1000));

        String result = resultReference.get();
        var json = Hson.parse(new ByteArrayInputStream(result.getBytes(StandardCharsets.UTF_8)))
                .asStruct();

        assertThat(json.stringValue("timestamp"), optionalValue(is(DATE_FORMAT.format(DATE_TIME) + " "
                                                                           + TIME_FORMAT.format(DATE_TIME))));
        assertThat(json.stringValue("loglevel"), optionalValue(is("WARNING")));
        assertThat(json.stringValue("msg"), optionalValue(is("Message content")));
        assertThat(json.stringValue("test"), optionalValue(is("testValue")));
        assertThat(json.stringValue("source"), optionalValue(is("io.helidon.logging.jul.JulJsonTest"
                                                                        + " testJsonDefaultFormat")));
    }
}

