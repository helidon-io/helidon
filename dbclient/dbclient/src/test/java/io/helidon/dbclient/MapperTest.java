/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import io.helidon.common.mapper.MapperManager;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link DbMapperProviderImpl}.
 */
class MapperTest {

    private static MapperManager mm;

    @BeforeAll
    static void init() {
        mm = MapperManager.create();
    }

    /**
     * Current date with time set to 00:00:00.
     *
     * @return current date with time set to 00:00:00
     */
    private static OffsetDateTime currentDate() {
        return OffsetDateTime.now()
                .with(ChronoField.HOUR_OF_DAY, 0)
                .with(ChronoField.MINUTE_OF_HOUR, 0)
                .with(ChronoField.SECOND_OF_MINUTE, 0)
                .with(ChronoField.NANO_OF_SECOND, 0);
    }

    /**
     * Current time with date set to 1. 1. 1970 (epoch).
     *
     * @return current time with date set to 1. 1. 1970 (epoch)
     */
    private static OffsetDateTime currentTime() {
        // this returns time in current timezone, but moved to 1970 January, so daylight savings will cause mayhem
        return OffsetDateTime.now()
                .with(ChronoField.YEAR, 1970)
                .with(ChronoField.MONTH_OF_YEAR, 1)
                .with(ChronoField.DAY_OF_MONTH, 1);
    }

    @Test
    void testSqlDateToLocalDate() {
        OffsetDateTime dt = currentDate();
        java.sql.Date source = new java.sql.Date(dt.toInstant().toEpochMilli());
        LocalDate target = mm.map(source, java.sql.Date.class, LocalDate.class, "dbclient");
        assertThat(target.toEpochSecond(LocalTime.MIN, dt.getOffset()), is(source.getTime()/1000));
    }

    @Test
    void testSqlDateToUtilDate() {
        OffsetDateTime dt = OffsetDateTime.now();
        java.sql.Date source = new java.sql.Date(dt.toInstant().toEpochMilli());
        Date target = mm.map(source, java.sql.Date.class, Date.class, "dbclient");
        assertThat(target.getTime(), is(source.getTime()));
    }

    @Test
    void testSqlTimeToLocalTime() {
        OffsetDateTime dt = currentTime();
        java.sql.Time source = new java.sql.Time(dt.toInstant().toEpochMilli());
        LocalTime target = mm.map(source, java.sql.Time.class, LocalTime.class, "dbclient");

        assertThat(target, is(source.toLocalTime()));
    }

    @Test
    void testSqlTimeToUtilDate() {
        OffsetDateTime dt = OffsetDateTime.now();
        java.sql.Time source = new java.sql.Time(dt.toInstant().toEpochMilli());
        Date target = mm.map(source, java.sql.Time.class, Date.class, "dbclient");
        assertThat(target.getTime(), is(source.getTime()));
    }

    @Test
    void testSqlTimestampToGregorianCalendar() {
        Timestamp source = new Timestamp(System.currentTimeMillis());
        GregorianCalendar target = mm.map(source, Timestamp.class, GregorianCalendar.class, "dbclient");
        assertThat(target.getTimeInMillis(), is(source.getTime()));
    }

    @Test
    void testSqlTimestampToCalendar() {
        Timestamp source = new Timestamp(System.currentTimeMillis());
        Calendar target = mm.map(source, Timestamp.class, Calendar.class, "dbclient");
        assertThat(target.getTimeInMillis(), is(source.getTime()));
    }

    @Test
    void testSqlTimestampToLocalDateTime() {
        // Need to know time zone too.
        OffsetDateTime dt = OffsetDateTime.now();
        Timestamp source = new Timestamp(dt.toInstant().toEpochMilli());
        LocalDateTime target = mm.map(source, Timestamp.class, LocalDateTime.class, "dbclient");
        assertThat(target.atOffset(dt.getOffset()).toInstant().toEpochMilli(), is(source.getTime()));
    }

    @Test
    void testSqlTimestampToUtilDate() {
        Timestamp source = new Timestamp(System.currentTimeMillis());
        Date target = mm.map(source, Timestamp.class, Date.class, "dbclient");
        assertThat(target.getTime(), is(source.getTime()));
    }

    @Test
    void testSqlTimestampToZonedDateTime() {
        Timestamp source = new Timestamp(System.currentTimeMillis());
        ZonedDateTime target = mm.map(source, Timestamp.class, ZonedDateTime.class, "dbclient");
        assertThat(target.toInstant().toEpochMilli(), is(source.getTime()));
    }

}
