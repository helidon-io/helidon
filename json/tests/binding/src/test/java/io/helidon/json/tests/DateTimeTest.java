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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Testing.Test
public class DateTimeTest {

    private final JsonBinding jsonBinding;

    DateTimeTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testLocalDateParameterized(BindingMethod bindingMethod) {
        LocalDate original = LocalDate.of(2023, 10, 15);
        String json = bindingMethod.serialize(jsonBinding, original);
        assertThat(json, is("\"2023-10-15\""));
        LocalDate deserialized = bindingMethod.deserialize(jsonBinding, json, LocalDate.class);
        assertThat(deserialized, is(original));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testLocalTimeParameterized(BindingMethod bindingMethod) {
        LocalTime original = LocalTime.of(14, 30, 45);
        String json = bindingMethod.serialize(jsonBinding, original);
        assertThat(json, is("\"14:30:45\""));
        LocalTime deserialized = bindingMethod.deserialize(jsonBinding, json, LocalTime.class);
        assertThat(deserialized, is(original));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testLocalDateTimeParameterized(BindingMethod bindingMethod) {
        LocalDateTime original = LocalDateTime.of(2023, 10, 15, 14, 30, 45);
        String json = bindingMethod.serialize(jsonBinding, original);
        assertThat(json, is("\"2023-10-15T14:30:45\""));
        LocalDateTime deserialized = bindingMethod.deserialize(jsonBinding, json, LocalDateTime.class);
        assertThat(deserialized, is(original));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testOffsetDateTimeParameterized(BindingMethod bindingMethod) {
        OffsetDateTime original = OffsetDateTime.of(2023, 10, 15, 14, 30, 45, 0, ZoneOffset.ofHours(2));
        String json = bindingMethod.serialize(jsonBinding, original);
        assertThat(json, is("\"2023-10-15T14:30:45+02:00\""));
        OffsetDateTime deserialized = bindingMethod.deserialize(jsonBinding, json, OffsetDateTime.class);
        assertThat(deserialized, is(original));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testZonedDateTimeParameterized(BindingMethod bindingMethod) {
        ZonedDateTime original = ZonedDateTime.of(2023, 10, 15, 14, 30, 45, 0, ZoneOffset.ofHours(2));
        String json = bindingMethod.serialize(jsonBinding, original);
        assertThat(json, is("\"2023-10-15T14:30:45+02:00\""));
        ZonedDateTime deserialized = bindingMethod.deserialize(jsonBinding, json, ZonedDateTime.class);
        assertThat(deserialized, is(original));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testInstantParameterized(BindingMethod bindingMethod) {
        Instant original = Instant.parse("2023-10-15T12:30:45Z");
        String json = bindingMethod.serialize(jsonBinding, original);
        assertThat(json, is("\"2023-10-15T12:30:45Z\""));
        Instant deserialized = bindingMethod.deserialize(jsonBinding, json, Instant.class);
        assertThat(deserialized, is(original));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testPeriodParameterized(BindingMethod bindingMethod) {
        Period original = Period.of(1, 2, 3);
        String json = bindingMethod.serialize(jsonBinding, original);
        assertThat(json, is("\"P1Y2M3D\""));
        Period deserialized = bindingMethod.deserialize(jsonBinding, json, Period.class);
        assertThat(deserialized, is(original));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testDateParameterized(BindingMethod bindingMethod) {
        Date original = Date.from(Instant.parse("2023-10-15T12:30:45Z"));
        String json = bindingMethod.serialize(jsonBinding, original);
        assertThat(json, is("\"2023-10-15T12:30:45Z\""));
        Date deserialized = bindingMethod.deserialize(jsonBinding, json, Date.class);
        assertThat(deserialized, is(original));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testCalendarParameterized(BindingMethod bindingMethod) {
        Calendar original = Calendar.getInstance();
        original.setTime(Date.from(Instant.parse("2023-10-15T12:30:45Z")));
        original.setTimeZone(TimeZone.getTimeZone("UTC"));
        String json = bindingMethod.serialize(jsonBinding, original);
        assertThat(json, is("\"2023-10-15T12:30:45Z[UTC]\""));
        Calendar deserialized = bindingMethod.deserialize(jsonBinding, json, Calendar.class);
        assertThat(deserialized.getTime(), is(original.getTime()));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testGregorianCalendarConverterParameterized(BindingMethod bindingMethod) {
        GregorianCalendar original = new GregorianCalendar(2023, 9, 15, 14, 30, 45);
        original.setTimeZone(TimeZone.getTimeZone("UTC"));
        String json = bindingMethod.serialize(jsonBinding, original);
        assertThat(json, is("\"2023-10-15T14:30:45Z[UTC]\""));
        GregorianCalendar deserialized = bindingMethod.deserialize(jsonBinding, json, GregorianCalendar.class);
        assertThat(deserialized.getTimeInMillis(), is(original.getTimeInMillis()));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testDateTimeBeanParameterized(BindingMethod bindingMethod) {
        DateTimeBean bean = new DateTimeBean();
        bean.setLocalDate(LocalDate.of(2023, 10, 15));
        bean.setInstant(Instant.parse("2023-10-15T12:30:45Z"));
        bean.setPeriod(Period.of(1, 2, 3));

        String json = bindingMethod.serialize(jsonBinding, bean);
        assertThat(json, is("{\"localDate\":\"2023-10-15\",\"instant\":\"2023-10-15T12:30:45Z\",\"period\":\"P1Y2M3D\"}"));
        DateTimeBean deserialized = bindingMethod.deserialize(jsonBinding, json, DateTimeBean.class);

        assertThat(deserialized.getLocalDate(), is(bean.getLocalDate()));
        assertThat(deserialized.getInstant(), is(bean.getInstant()));
        assertThat(deserialized.getPeriod(), is(bean.getPeriod()));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testDateTimeCollectionsParameterized(BindingMethod bindingMethod) {
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(LocalDate.of(2023, 10, 15));
        dateList.add(LocalDate.of(2023, 10, 16));
        dateList.add(LocalDate.of(2023, 10, 17));

        String json = bindingMethod.serialize(jsonBinding, dateList);
        assertThat(json, is("[\"2023-10-15\",\"2023-10-16\",\"2023-10-17\"]"));

        GenericType<List<LocalDate>> listType = new GenericType<>() { };
        List<LocalDate> deserialized = bindingMethod.deserialize(jsonBinding, json, listType);

        assertThat(deserialized.size(), is(3));
        assertThat(deserialized.get(0), is(dateList.get(0)));
        assertThat(deserialized.get(1), is(dateList.get(1)));
        assertThat(deserialized.get(2), is(dateList.get(2)));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testDateTimeMapParameterized(BindingMethod bindingMethod) {
        Map<String, OffsetDateTime> dateTimeMap = new LinkedHashMap<>();
        dateTimeMap.put("start", OffsetDateTime.of(2023, 10, 15, 9, 1, 1, 0, ZoneOffset.ofHours(1)));
        dateTimeMap.put("end", OffsetDateTime.of(2023, 10, 15, 17, 0, 0, 0, ZoneOffset.ofHours(1)));

        String json = bindingMethod.serialize(jsonBinding, dateTimeMap);
        assertThat(json, is("{\"start\":\"2023-10-15T09:01:01+01:00\",\"end\":\"2023-10-15T17:00+01:00\"}"));

        GenericType<Map<String, OffsetDateTime>> mapType = new GenericType<>() { };
        Map<String, OffsetDateTime> deserialized = bindingMethod.deserialize(jsonBinding, json, mapType);

        assertThat(deserialized.size(), is(2));
        assertThat(deserialized.get("start"), is(dateTimeMap.get("start")));
        assertThat(deserialized.get("end"), is(dateTimeMap.get("end")));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testOptionalDateTimeParameterized(BindingMethod bindingMethod) {
        OptionalDateTimeModel model = new OptionalDateTimeModel(
                Optional.of(LocalDate.of(2023, 10, 15)),
                Optional.of(Instant.parse("2023-10-15T12:30:45Z")),
                Optional.empty()
        );

        String json = bindingMethod.serialize(jsonBinding, model);
        assertThat(json, is("{\"optionalLocalDate\":\"2023-10-15\","
                                    + "\"optionalInstant\":\"2023-10-15T12:30:45Z\"}"));
        OptionalDateTimeModel deserialized = bindingMethod.deserialize(jsonBinding, json, OptionalDateTimeModel.class);

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
