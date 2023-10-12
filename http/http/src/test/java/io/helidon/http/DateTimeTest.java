/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.http;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests {@link DateTime}.
 */
public class DateTimeTest {

    private static final ZonedDateTime ZDT = ZonedDateTime.of(2008, 6, 3, 11, 5, 30, 0, ZoneId.of("Z"));
    private static final ZonedDateTime ZDT2 = ZonedDateTime.of(2008, 6, 17, 11, 5, 30, 0, ZoneId.of("Z"));

    @Test
    public void rfc1123() {
        String text = "Tue, 3 Jun 2008 11:05:30 GMT";
        ZonedDateTime zdt = ZonedDateTime.parse(text, DateTime.RFC_1123_DATE_TIME);
        assertThat(zdt, is(ZDT));
        assertThat(zdt.format(DateTime.RFC_1123_DATE_TIME), is(text));

        text = "Tue, 17 Jun 2008 11:05:30 GMT";
        zdt = ZonedDateTime.parse(text, DateTime.RFC_1123_DATE_TIME);
        assertThat(zdt, is(ZDT2));
        assertThat(zdt.format(DateTime.RFC_1123_DATE_TIME), is(text));
    }

    @Test
    public void rfc850() {
        assumeTrue(LocalDate.now().getYear() < 2057);
        String text = "Tuesday, 03-Jun-08 11:05:30 GMT";
        ZonedDateTime zdt = ZonedDateTime.parse(text, DateTime.RFC_850_DATE_TIME);
        assertThat(zdt, is(ZDT));
        assertThat(zdt.format(DateTime.RFC_850_DATE_TIME), is(text));

        text = "Tuesday, 17-Jun-08 11:05:30 GMT";
        zdt = ZonedDateTime.parse(text, DateTime.RFC_850_DATE_TIME);
        assertThat(zdt, is(ZDT2));
        assertThat(zdt.format(DateTime.RFC_850_DATE_TIME), is(text));
    }

    @Test
    public void rfc851() {
        String text = "Tue Jun  3 11:05:30 2008";
        ZonedDateTime zdt = ZonedDateTime.parse(text, DateTime.ASCTIME_DATE_TIME);
        assertThat(zdt, is(ZDT));
        assertThat(zdt.format(DateTime.ASCTIME_DATE_TIME), is(text));

        text = "Tue Jun 17 11:05:30 2008";
        zdt = ZonedDateTime.parse(text, DateTime.ASCTIME_DATE_TIME);
        assertThat(zdt, is(ZDT2));
        assertThat(zdt.format(DateTime.ASCTIME_DATE_TIME), is(text));
    }

    @Test
    public void parse() {
        assertThat(DateTime.parse("Tue, 3 Jun 2008 11:05:30 GMT"), is(ZDT));
        assertThat(DateTime.parse("Tuesday, 03-Jun-08 11:05:30 GMT"), is(ZDT));
        assertThat(DateTime.parse("Tue Jun  3 11:05:30 2008"), is(ZDT));
    }

}
