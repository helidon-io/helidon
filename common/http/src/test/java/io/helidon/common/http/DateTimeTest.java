/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.http;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests {@link Http.DateTime}.
 */
public class DateTimeTest {

    private static final ZonedDateTime ZDT = ZonedDateTime.of(2008, 6, 3, 11, 5, 30, 0, ZoneId.of("Z"));
    private static final ZonedDateTime ZDT2 = ZonedDateTime.of(2008, 6, 17, 11, 5, 30, 0, ZoneId.of("Z"));

    @Test
    public void rfc1123() throws Exception {
        String text = "Tue, 3 Jun 2008 11:05:30 GMT";
        ZonedDateTime zdt = ZonedDateTime.parse(text, Http.DateTime.RFC_1123_DATE_TIME);
        assertEquals(ZDT, zdt);
        assertEquals(text, zdt.format(Http.DateTime.RFC_1123_DATE_TIME));

        text = "Tue, 17 Jun 2008 11:05:30 GMT";
        zdt = ZonedDateTime.parse(text, Http.DateTime.RFC_1123_DATE_TIME);
        assertEquals(ZDT2, zdt);
        assertEquals(text, zdt.format(Http.DateTime.RFC_1123_DATE_TIME));
    }

    @Test
    public void rfc850() throws Exception {
        assumeTrue(LocalDate.now().getYear() < 2057);
        String text = "Tuesday, 03-Jun-08 11:05:30 GMT";
        ZonedDateTime zdt = ZonedDateTime.parse(text, Http.DateTime.RFC_850_DATE_TIME);
        assertEquals(ZDT, zdt);
        assertEquals(text, zdt.format(Http.DateTime.RFC_850_DATE_TIME));

        text = "Tuesday, 17-Jun-08 11:05:30 GMT";
        zdt = ZonedDateTime.parse(text, Http.DateTime.RFC_850_DATE_TIME);
        assertEquals(ZDT2, zdt);
        assertEquals(text, zdt.format(Http.DateTime.RFC_850_DATE_TIME));
    }

    @Test
    public void rfc851() throws Exception {
        String text = "Tue Jun  3 11:05:30 2008";
        ZonedDateTime zdt = ZonedDateTime.parse(text, Http.DateTime.ASCTIME_DATE_TIME);
        assertEquals(ZDT, zdt);
        assertEquals(text, zdt.format(Http.DateTime.ASCTIME_DATE_TIME));

        text = "Tue Jun 17 11:05:30 2008";
        zdt = ZonedDateTime.parse(text, Http.DateTime.ASCTIME_DATE_TIME);
        assertEquals(ZDT2, zdt);
        assertEquals(text, zdt.format(Http.DateTime.ASCTIME_DATE_TIME));
    }

    @Test
    public void parse() throws Exception {
        assertEquals(ZDT, Http.DateTime.parse("Tue, 3 Jun 2008 11:05:30 GMT"));
        assertEquals(ZDT, Http.DateTime.parse("Tuesday, 03-Jun-08 11:05:30 GMT"));
        assertEquals(ZDT, Http.DateTime.parse("Tue Jun  3 11:05:30 2008"));
    }

}
