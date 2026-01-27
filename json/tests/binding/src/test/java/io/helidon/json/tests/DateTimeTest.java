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

package io.helidon.json.tests;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

import io.helidon.common.GenericType;
import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Testing.Test
public class DateTimeTest {

    private final JsonBinding jsonBinding;

    DateTimeTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Test
    public void testLocalDate() {
        LocalDate original = LocalDate.of(2023, 10, 15);
        String json = jsonBinding.serialize(original);
        assertThat(json, is("\"2023-10-15\""));
        LocalDate deserialized = jsonBinding.deserialize(json, LocalDate.class);
        assertThat(deserialized, is(original));
    }

    @Test
    public void testLocalTime() {
        LocalTime original = LocalTime.of(14, 30, 45);
        String json = jsonBinding.serialize(original);
        assertThat(json, is("\"14:30:45\""));
        LocalTime deserialized = jsonBinding.deserialize(json, LocalTime.class);
        assertThat(deserialized, is(original));
    }

    @Test
    public void testLocalDateTime() {
        LocalDateTime original = LocalDateTime.of(2023, 10, 15, 14, 30, 45);
        String json = jsonBinding.serialize(original);
        assertThat(json, is("\"2023-10-15T14:30:45\""));
        LocalDateTime deserialized = jsonBinding.deserialize(json, LocalDateTime.class);
        assertThat(deserialized, is(original));
    }

    @Test
    public void testOffsetDateTime() {
        OffsetDateTime original = OffsetDateTime.of(2023, 10, 15, 14, 30, 45, 0, ZoneOffset.ofHours(2));
        String json = jsonBinding.serialize(original);
        assertThat(json, is("\"2023-10-15T14:30:45+02:00\""));
        OffsetDateTime deserialized = jsonBinding.deserialize(json, OffsetDateTime.class);
        assertThat(deserialized, is(original));
    }

    @Test
    public void testZonedDateTime() {
        ZonedDateTime original = ZonedDateTime.of(2023, 10, 15, 14, 30, 45, 0, ZoneOffset.ofHours(2));
        String json = jsonBinding.serialize(original);
        assertThat(json, is("\"2023-10-15T14:30:45+02:00\""));
        ZonedDateTime deserialized = jsonBinding.deserialize(json, ZonedDateTime.class);
        assertThat(deserialized, is(original));
    }

    @Test
    public void testInstant() {
        Instant original = Instant.parse("2023-10-15T12:30:45Z");
        String json = jsonBinding.serialize(original);
        assertThat(json, is("\"2023-10-15T12:30:45Z\""));
        Instant deserialized = jsonBinding.deserialize(json, Instant.class);
        assertThat(deserialized, is(original));
    }

    @Test
    public void testPeriod() {
        Period original = Period.of(1, 2, 3);
        String json = jsonBinding.serialize(original);
        assertThat(json, is("\"P1Y2M3D\""));
        Period deserialized = jsonBinding.deserialize(json, Period.class);
        assertThat(deserialized, is(original));
    }

    @Test
    public void testDate() {
        Date original = Date.from(Instant.parse("2023-10-15T12:30:45Z"));
        String json = jsonBinding.serialize(original);
        assertThat(json, is("\"2023-10-15T12:30:45Z\""));
        Date deserialized = jsonBinding.deserialize(json, Date.class);
        assertThat(deserialized, is(original));
    }

    @Test
    public void testCalendar() {
        Calendar original = Calendar.getInstance();
        original.setTime(Date.from(Instant.parse("2023-10-15T12:30:45Z")));
        original.setTimeZone(TimeZone.getTimeZone("UTC"));
        String json = jsonBinding.serialize(original);
        assertThat(json, is("\"2023-10-15T12:30:45Z[UTC]\""));
        // Calendar serialization depends on the default timezone, so we'll just verify it deserializes correctly
        Calendar deserialized = jsonBinding.deserialize(json, Calendar.class);
        assertThat(deserialized.getTime(), is(original.getTime()));
    }

    @Test
    public void testGregorianCalendarConverter() {
        // Note: month is 0-based
        GregorianCalendar original = new GregorianCalendar(2023, 9, 15, 14, 30, 45);
        original.setTimeZone(TimeZone.getTimeZone("UTC"));
        String json = jsonBinding.serialize(original);
        assertThat(json, is("\"2023-10-15T14:30:45Z[UTC]\""));
        GregorianCalendar deserialized = jsonBinding.deserialize(json, GregorianCalendar.class);
        assertThat(deserialized.getTimeInMillis(), is(original.getTimeInMillis()));
    }

    @Test
    public void testDateTimeBean() {
        DateTimeBean bean = new DateTimeBean();
        bean.setLocalDate(LocalDate.of(2023, 10, 15));
        bean.setInstant(Instant.parse("2023-10-15T12:30:45Z"));
        bean.setPeriod(Period.of(1, 2, 3));

        String json = jsonBinding.serialize(bean);
        assertThat(json, is("{\"localDate\":\"2023-10-15\",\"instant\":\"2023-10-15T12:30:45Z\",\"period\":\"P1Y2M3D\"}"));
        DateTimeBean deserialized = jsonBinding.deserialize(json, DateTimeBean.class);

        assertThat(deserialized.getLocalDate(), is(bean.getLocalDate()));
        assertThat(deserialized.getInstant(), is(bean.getInstant()));
        assertThat(deserialized.getPeriod(), is(bean.getPeriod()));
    }

    @Test
    public void testDateTimeCollections() {
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(LocalDate.of(2023, 10, 15));
        dateList.add(LocalDate.of(2023, 10, 16));
        dateList.add(LocalDate.of(2023, 10, 17));

        String json = jsonBinding.serialize(dateList);
        assertThat(json, is("[\"2023-10-15\",\"2023-10-16\",\"2023-10-17\"]"));

        GenericType<List<LocalDate>> listType = new GenericType<>() { };
        List<LocalDate> deserialized = jsonBinding.deserialize(json, listType);

        assertThat(deserialized.size(), is(3));
        assertThat(deserialized.get(0), is(dateList.get(0)));
        assertThat(deserialized.get(1), is(dateList.get(1)));
        assertThat(deserialized.get(2), is(dateList.get(2)));
    }

    @Test
    public void testDateTimeMap() {
        Map<String, OffsetDateTime> dateTimeMap = new LinkedHashMap<>();
        dateTimeMap.put("start", OffsetDateTime.of(2023, 10, 15, 9, 1, 1, 0, ZoneOffset.ofHours(1)));
        dateTimeMap.put("end", OffsetDateTime.of(2023, 10, 15, 17, 0, 0, 0, ZoneOffset.ofHours(1)));

        String json = jsonBinding.serialize(dateTimeMap);
        assertThat(json, is("{\"start\":\"2023-10-15T09:01:01+01:00\",\"end\":\"2023-10-15T17:00+01:00\"}"));

        GenericType<Map<String, OffsetDateTime>> mapType = new GenericType<>() { };
        Map<String, OffsetDateTime> deserialized = jsonBinding.deserialize(json, mapType);

        assertThat(deserialized.size(), is(2));
        assertThat(deserialized.get("start"), is(dateTimeMap.get("start")));
        assertThat(deserialized.get("end"), is(dateTimeMap.get("end")));
    }

    @Test
    public void testOptionalDateTime() {
        OptionalDateTimeModel model = new OptionalDateTimeModel(
                Optional.of(LocalDate.of(2023, 10, 15)),
                Optional.of(Instant.parse("2023-10-15T12:30:45Z")),
                Optional.empty()
        );

        String json = jsonBinding.serialize(model);
        assertThat(json, is("{\"optionalLocalDate\":\"2023-10-15\","
                                    + "\"optionalInstant\":\"2023-10-15T12:30:45Z\"}"));
        OptionalDateTimeModel deserialized = jsonBinding.deserialize(json, OptionalDateTimeModel.class);

        assertThat(deserialized.optionalLocalDate, is(model.optionalLocalDate));
        assertThat(deserialized.optionalInstant, is(model.optionalInstant));
        assertThat(deserialized.optionalPeriod.isEmpty(), is(true));
    }

    @Json.Entity
    static class DateTimeBean {
        private LocalDate localDate;
        private Instant instant;
        private Period period;

        public LocalDate getLocalDate() {
            return localDate;
        }

        public void setLocalDate(LocalDate localDate) {
            this.localDate = localDate;
        }

        public Instant getInstant() {
            return instant;
        }

        public void setInstant(Instant instant) {
            this.instant = instant;
        }

        public Period getPeriod() {
            return period;
        }

        public void setPeriod(Period period) {
            this.period = period;
        }
    }

    @Json.Entity
    record OptionalDateTimeModel(Optional<LocalDate> optionalLocalDate,
                                 Optional<Instant> optionalInstant,
                                 Optional<Period> optionalPeriod) {
    }

}
