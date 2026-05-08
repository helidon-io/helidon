/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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
package io.helidon.dbclient;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import io.helidon.common.mapper.Mappers;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link DbMapperProviderImpl}.
 */
class MapperTest {
    private static final LocalDate TEST_DATE = LocalDate.of(2026, 3, 29);
    private static final LocalTime TEST_TIME = LocalTime.of(12, 34, 56);
    private static final Instant TEST_INSTANT = Instant.parse("2026-03-29T10:34:56Z");

    private static Mappers mm;

    @BeforeAll
    static void init() {
        mm = Services.get(Mappers.class);
    }

    @Test
    void testSqlDateToLocalDate() {
        java.sql.Date source = java.sql.Date.valueOf(TEST_DATE);
        LocalDate target = mm.map(source, java.sql.Date.class, LocalDate.class, "dbclient");
        assertThat(target, is(TEST_DATE));
    }

    @Test
    void testSqlDateToUtilDate() {
        java.sql.Date source = java.sql.Date.valueOf(TEST_DATE);
        Date target = mm.map(source, java.sql.Date.class, Date.class, "dbclient");
        assertThat(target.getTime(), is(source.getTime()));
    }

    @Test
    void testSqlTimeToLocalTime() {
        java.sql.Time source = java.sql.Time.valueOf(TEST_TIME);
        LocalTime target = mm.map(source, java.sql.Time.class, LocalTime.class, "dbclient");

        assertThat(target, is(TEST_TIME));
    }

    @Test
    void testSqlTimeToUtilDate() {
        java.sql.Time source = java.sql.Time.valueOf(TEST_TIME);
        Date target = mm.map(source, java.sql.Time.class, Date.class, "dbclient");
        assertThat(target.getTime(), is(source.getTime()));
    }

    @Test
    void testSqlTimestampToGregorianCalendar() {
        Timestamp source = Timestamp.from(TEST_INSTANT);
        GregorianCalendar target = mm.map(source, Timestamp.class, GregorianCalendar.class, "dbclient");
        assertThat(target.getTimeInMillis(), is(source.getTime()));
    }

    @Test
    void testSqlTimestampToCalendar() {
        Timestamp source = Timestamp.from(TEST_INSTANT);
        Calendar target = mm.map(source, Timestamp.class, Calendar.class, "dbclient");
        assertThat(target.getTimeInMillis(), is(source.getTime()));
    }

    @Test
    void testSqlTimestampToLocalDateTime() {
        Timestamp source = Timestamp.from(TEST_INSTANT);
        LocalDateTime target = mm.map(source, Timestamp.class, LocalDateTime.class, "dbclient");
        assertThat(target, is(LocalDateTime.ofInstant(TEST_INSTANT, ZoneId.systemDefault())));
    }

    @Test
    void testSqlTimestampToUtilDate() {
        Timestamp source = Timestamp.from(TEST_INSTANT);
        Date target = mm.map(source, Timestamp.class, Date.class, "dbclient");
        assertThat(target.getTime(), is(source.getTime()));
    }

    @Test
    void testSqlTimestampToZonedDateTime() {
        Timestamp source = Timestamp.from(TEST_INSTANT);
        ZonedDateTime target = mm.map(source, Timestamp.class, ZonedDateTime.class, "dbclient");
        assertThat(target, is(TEST_INSTANT.atZone(ZoneOffset.UTC)));
    }

}
