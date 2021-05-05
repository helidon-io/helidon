/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver.accesslog;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link TimestampLogEntry}.
 */
class TimestampLogEntryTest {
    private static final ZonedDateTime TEST_TIME = ZonedDateTime.now();
    @Test
    void testDefaultFormat() {
        TimestampLogEntry entry = TimestampLogEntry.create();

        AccessLogContext context = mock(AccessLogContext.class);
        when(context.requestDateTime()).thenReturn(TEST_TIME);

        String value = entry.doApply(context);

        assertThat(value, startsWith("["));
        assertThat(value, CoreMatchers.endsWith("]"));

        // this is as used by Apache HTTP server for common log format
        DateTimeFormatter defaultPattern = DateTimeFormatter.ofPattern("dd/MMM/YYYY:HH:mm:ss ZZZ");
        assertThat(value.substring(1, value.length() -1), is(defaultPattern.format(TEST_TIME)));
    }

    @Test
    void testCustomFormat() {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("'-'YYYYMMdd-HHmmss.SSS Z'-'");

        TimestampLogEntry entry = TimestampLogEntry.builder()
                .formatter(dateTimeFormatter)
                .build();

        AccessLogContext context = mock(AccessLogContext.class);
        when(context.requestDateTime()).thenReturn(TEST_TIME);

        String value = entry.doApply(context);


        assertThat(value, is(dateTimeFormatter.format(TEST_TIME)));
    }
}