/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link DateTime}.
 */
public class DateTimeTest {

    private static final ZonedDateTime ZDT = ZonedDateTime.of(2008, 6, 3, 11, 5, 30, 0, ZoneId.of("Z"));
    private static final ZonedDateTime ZDT2 = ZonedDateTime.of(2008, 6, 17, 11, 5, 30, 0, ZoneId.of("Z"));

    @Test
    void http1BytesAreIsolatedFromCallerChanges() {
        byte[] first = DateTime.http1Bytes();
        byte[] second = DateTime.http1Bytes();
        first[first.length - 2] = 0;
        first[first.length - 1] = 0;
        assertThat(second[second.length - 2], is((byte) '\r'));
        assertThat(second[second.length - 1], is((byte) '\n'));
    }

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
        var referenceTime = ZonedDateTime.parse("2026-01-01T00:00:00Z");
        String text = "Tuesday, 03-Jun-08 11:05:30 GMT";
        assertThat(DateTime.parse(text, referenceTime), is(ZDT));
        assertThat(ZDT.format(DateTime.RFC_850_DATE_TIME), is(text));

        text = "Tuesday, 17-Jun-08 11:05:30 GMT";
        assertThat(DateTime.parse(text, referenceTime), is(ZDT2));
        assertThat(ZDT2.format(DateTime.RFC_850_DATE_TIME), is(text));
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
        var expected = ZDT.withYear(ZonedDateTime.now(ZDT.getZone()).getYear());
        assertThat(DateTime.parse(expected.format(DateTime.RFC_850_DATE_TIME)), is(expected));
        assertThat(DateTime.parse("Tue Jun  3 11:05:30 2008"), is(ZDT));
    }

    @Test
    public void parseUsesReferenceTimeForRfc850YearWindow() {
        var referenceTime = ZonedDateTime.parse("2026-01-01T00:00:00Z");

        assertThat(DateTime.parse("Tuesday, 08-Jun-76 18:32:30 GMT", referenceTime),
                   is(ZonedDateTime.parse("1976-06-08T18:32:30Z")));
        assertThat(DateTime.parse("Saturday, 08-Jun-75 18:32:30 GMT", referenceTime),
                   is(ZonedDateTime.parse("2075-06-08T18:32:30Z")));
        assertThat(DateTime.parse("Monday, 08-Jun-76 18:32:30 GMT", referenceTime.plusYears(1)),
                   is(ZonedDateTime.parse("2076-06-08T18:32:30Z")));
    }

    @Test
    public void parseRejectsNullReferenceTime() {
        assertThrows(NullPointerException.class,
                     () -> DateTime.parse("Tuesday, 03-Jun-08 11:05:30 GMT", null));
    }

}
